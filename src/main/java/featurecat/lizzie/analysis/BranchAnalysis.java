package featurecat.lizzie.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-position supplemental search. Never uses restricted-search values as root statistics. */
public final class BranchAnalysis {
  private Object position;
  private final Map<String, MoveData> normal = new LinkedHashMap<>();
  private final Map<String, MoveData> supplemental = new LinkedHashMap<>();
  private final Map<String, Integer> normalRanks = new LinkedHashMap<>();
  private final Map<String, MoveData> observed = new LinkedHashMap<>();
  private String active;

  public synchronized void clear() {
    position = null;
    normal.clear();
    normalRanks.clear();
    supplemental.clear();
    observed.clear();
    active = null;
  }

  public synchronized boolean isActive() {
    return active != null;
  }

  public synchronized String activeMove() {
    return active;
  }

  public synchronized boolean isPosition(Object node) {
    return position == node;
  }

  public synchronized void normal(Object node, List<MoveData> moves) {
    if (position != node) clear();
    position = node;
    normal.clear();
    normalRanks.clear();
    for (MoveData move : moves) {
      normal.put(move.coordinate, move);
      normalRanks.put(move.coordinate, move.order);
      MoveData previous = observed.get(move.coordinate);
      if (previous == null || previous.playouts <= move.playouts) observed.put(move.coordinate, move);
    }
  }

  public synchronized List<MoveData> normalMoves() {
    return new ArrayList<>(normal.values());
  }

  public synchronized void remember(List<MoveData> moves) {
    for (MoveData move : moves) {
      MoveData previous = observed.get(move.coordinate);
      if (previous == null || previous.playouts < move.playouts) observed.put(move.coordinate, move);
    }
  }

  public synchronized String next(Set<String> targets, int minimum) {
    active = null;
    for (String target : targets) {
      MoveData move = best(target);
      if (move == null || move.playouts < minimum) {
        active = target;
        break;
      }
    }
    return active;
  }

  public synchronized boolean accept(List<MoveData> moves, int minimum) {
    if (active == null) return false;
    for (MoveData move : moves) {
      if (active.equalsIgnoreCase(move.coordinate)) {
        MoveData old = supplemental.get(active);
        if (old == null || move.playouts >= old.playouts) supplemental.put(active, move);
        return move.playouts >= minimum;
      }
    }
    return false;
  }

  /** Only the current ordinary ranking is eligible; raw/history-only moves are not admitted. */
  public synchronized Set<String> candidateCoordinates(int maximum, double minimumRatio) {
    Set<String> result = new java.util.LinkedHashSet<>();
    List<MoveData> ranked = new ArrayList<>(normal.values());
    ranked.sort(java.util.Comparator.comparingInt(move -> normalRanks.get(move.coordinate)));
    int maxVisits = 0;
    int counted = 0;
    for (MoveData move : ranked) {
      if (maximum > 0 && (normalRanks.get(move.coordinate) >= maximum || counted >= maximum)) continue;
      maxVisits = Math.max(maxVisits, move.playouts);
      counted++;
    }
    for (MoveData move : ranked) {
      int rank = normalRanks.get(move.coordinate);
      if (maximum > 0 && (rank >= maximum || result.size() >= maximum)) continue;
      if (move.coordinate.equalsIgnoreCase("pass") || move.playouts <= 0 || move.isSymmetry) continue;
      if (rank > 0 && (double) move.playouts / Math.max(1, maxVisits) <= minimumRatio) continue;
      result.add(move.coordinate);
    }
    return result;
  }

  public synchronized void start(String coordinate) { active = coordinate; }

  public synchronized MoveData best(String coordinate) {
    MoveData a = observed.get(coordinate);
    MoveData b = supplemental.get(coordinate);
    return b != null && (a == null || b.playouts > a.playouts) ? b : a;
  }

  /** Keep ordinary engine ordering; restricted-search order 0 is not an overall ranking. */
  public synchronized List<MoveData> display(List<MoveData> ordinary, Set<String> targets) {
    return display(ordinary, targets, java.util.Collections.emptySet(), 0);
  }

  public synchronized List<MoveData> display(List<MoveData> ordinary, Set<String> branches,
      Set<String> candidates, int maximum) {
    List<MoveData> result = new ArrayList<>();
    for (MoveData move : ordinary) {
      Integer rank = normalRanks.get(move.coordinate);
      if (rank == null) continue;
      if (maximum > 0 && (rank >= maximum || result.size() >= maximum)
          && !move.lastTimeUnlimited && !branches.contains(move.coordinate)
          && !(move.isSymmetry && move.symmetrySourceOrder >= 0
              && move.symmetrySourceOrder < maximum)) continue;
      MoveData cached = best(move.coordinate);
      if (cached != null && cached.playouts > move.playouts) {
        MoveData latest = move;
        move = cached.copy();
        move.isSymmetry = latest.isSymmetry;
        move.symmetryOf = latest.symmetryOf;
        move.symmetrySourceOrder = latest.symmetrySourceOrder;
      }
      move.order = rank;
      result.add(move);
    }
    result.sort(java.util.Comparator.comparingInt(move -> normalRanks.get(move.coordinate)));
    Set<String> targets = new java.util.LinkedHashSet<>(branches);
    targets.addAll(candidates);
    for (MoveData move : result) {
      move.isSgfBranch = branches.contains(move.coordinate);
      move.isSupplementalCandidate = candidates.contains(move.coordinate);
    }
    for (String target : targets) {
      MoveData move = best(target);
      if (move == null) continue;
      int index = -1;
      for (int i = 0; i < result.size(); i++) {
        if (target.equalsIgnoreCase(result.get(i).coordinate)) { index = i; break; }
      }
      move.isSgfBranch = branches.contains(target);
      move.isSupplementalCandidate = candidates.contains(target);
      move.order = index >= 0 ? result.get(index).order : result.size();
      if (index >= 0) result.set(index, move);
      else result.add(move);
    }
    MoveData reference = normal.isEmpty() ? null : normal.values().iterator().next();
    if (reference != null) {
      for (MoveData move : result) {
        move.hasOrdinaryReference = true;
        move.ordinaryWinrate = reference.winrate;
        move.ordinaryScoreMean = reference.scoreMean;
      }
    }
    return result;
  }
}
