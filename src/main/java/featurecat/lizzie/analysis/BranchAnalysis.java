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
  private final Map<String, MoveData> observed = new LinkedHashMap<>();
  private String active;

  public synchronized void clear() {
    position = null;
    normal.clear();
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
    for (MoveData move : moves) {
      normal.put(move.coordinate, move);
      MoveData previous = observed.get(move.coordinate);
      if (previous == null || previous.playouts <= move.playouts) observed.put(move.coordinate, move);
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

  public synchronized java.util.Set<String> observedCoordinates() {
    java.util.Set<String> result = new java.util.LinkedHashSet<>(observed.keySet());
    result.removeIf(c -> c.equalsIgnoreCase("pass"));
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
    return display(ordinary, targets, false);
  }

  public synchronized List<MoveData> display(List<MoveData> ordinary, Set<String> branches,
      boolean includeObserved) {
    List<MoveData> result = new ArrayList<>(ordinary);
    Set<String> targets = new java.util.LinkedHashSet<>(branches);
    if (includeObserved) targets.addAll(observedCoordinates());
    for (MoveData move : result) {
      move.isSgfBranch = branches.contains(move.coordinate);
      move.isSupplementalCandidate = includeObserved && targets.contains(move.coordinate);
    }
    for (String target : targets) {
      MoveData move = best(target);
      if (move == null) continue;
      int index = -1;
      for (int i = 0; i < result.size(); i++) {
        if (target.equalsIgnoreCase(result.get(i).coordinate)) { index = i; break; }
      }
      move.isSgfBranch = branches.contains(target);
      move.isSupplementalCandidate = includeObserved;
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
