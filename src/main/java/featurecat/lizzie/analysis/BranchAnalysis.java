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
  private String active;

  public synchronized void clear() {
    position = null;
    normal.clear();
    supplemental.clear();
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
    for (MoveData move : moves) normal.put(move.coordinate, move);
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

  private MoveData best(String coordinate) {
    MoveData a = normal.get(coordinate);
    MoveData b = supplemental.get(coordinate);
    return b != null && (a == null || b.playouts > a.playouts) ? b : a;
  }

  /** Keep ordinary engine ordering, replacing/adding only the requested continuations. */
  public synchronized List<MoveData> display(List<MoveData> ordinary, Set<String> targets) {
    List<MoveData> result = new ArrayList<>(ordinary);
    for (MoveData move : result) move.isSgfBranch = targets.contains(move.coordinate);
    for (String target : targets) {
      MoveData move = best(target);
      if (move == null) continue;
      int index = -1;
      for (int i = 0; i < result.size(); i++) {
        if (target.equalsIgnoreCase(result.get(i).coordinate)) { index = i; break; }
      }
      move.isSgfBranch = true;
      // A restricted search always reports order 0; it is NOT the overall best move.
      move.order = index >= 0 ? result.get(index).order : result.size();
      if (index >= 0) result.set(index, move);
      else result.add(move);
    }
    return result;
  }
}
