package featurecat.lizzie.analysis;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class BranchAnalysisTest {
  private MoveData move(String coordinate, int visits, int order) {
    MoveData move = new MoveData();
    move.coordinate = coordinate;
    move.playouts = visits;
    move.order = order;
    return move;
  }

  private Set<String> targets(String... moves) {
    return new LinkedHashSet<>(Arrays.asList(moves));
  }

  @Test public void eachContinuationMustReachMinimum() {
    BranchAnalysis search = new BranchAnalysis();
    search.normal(new Object(), Arrays.asList(move("D4", 5000, 0), move("Q16", 300, 1)));
    assertEquals("Q16", search.next(targets("D4", "Q16", "C3"), 1000));
    assertFalse(search.accept(Arrays.asList(move("Q16", 999, 0)), 1000));
    assertTrue(search.accept(Arrays.asList(move("Q16", 1002, 0)), 1000));
    assertEquals("C3", search.next(targets("D4", "Q16", "C3"), 1000));
    assertTrue(search.accept(Arrays.asList(move("C3", 1000, 0)), 1000));
    assertNull(search.next(targets("D4", "Q16", "C3"), 1000));
    assertFalse(search.isActive());
  }

  @Test public void resultsSurviveOrdinaryRefreshWithoutChangingBestMove() {
    BranchAnalysis search = new BranchAnalysis();
    Object node = new Object();
    MoveData ordinary = move("D4", 5000, 0);
    search.normal(node, Arrays.asList(ordinary));
    search.next(targets("Q16"), 1000);
    search.accept(Arrays.asList(move("Q16", 1000, 0)), 1000);
    search.next(targets("Q16"), 1000);
    search.normal(node, Arrays.asList(move("D4", 6000, 0)));
    List<MoveData> display = search.display(Arrays.asList(ordinary), targets("Q16"));
    assertSame(ordinary, display.get(0));
    assertEquals(1, display.get(1).order);
    assertEquals(1000, display.get(1).playouts);
    assertTrue(display.get(1).isSgfBranch);
    assertNull(search.next(targets("Q16"), 1000));
  }

  @Test public void neverReplaceHigherVisitEstimateWithLowerOne() {
    BranchAnalysis search = new BranchAnalysis();
    Object node = new Object();
    search.normal(node, Arrays.asList(move("D4", 500, 0)));
    search.next(targets("D4"), 1000);
    search.accept(Arrays.asList(move("D4", 1000, 0)), 1000);
    search.accept(Arrays.asList(move("D4", 200, 0)), 1000);
    assertEquals(1000, search.display(Collections.emptyList(), targets("D4")).get(0).playouts);
    search.normal(node, Arrays.asList(move("D4", 2000, 0)));
    assertEquals(2000, search.display(Collections.emptyList(), targets("D4")).get(0).playouts);
  }

  @Test public void nodeChangeAndCancellationDiscardWork() {
    BranchAnalysis search = new BranchAnalysis();
    Object first = new Object(), second = new Object();
    search.normal(first, Collections.emptyList());
    search.next(targets("D4"), 1000);
    search.accept(Arrays.asList(move("D4", 1000, 0)), 1000);
    search.normal(second, Collections.emptyList());
    assertFalse(search.isPosition(first));
    assertEquals("D4", search.next(targets("D4"), 1000));
    search.clear();
    assertFalse(search.accept(Arrays.asList(move("D4", 2000, 0)), 1000));
    assertTrue(search.display(Collections.emptyList(), targets("D4")).isEmpty());
  }

  @Test public void unrelatedOutputDoesNotCompleteActiveMove() {
    BranchAnalysis search = new BranchAnalysis();
    search.normal(new Object(), Collections.emptyList());
    search.next(targets("pass"), 1000);
    assertFalse(search.accept(Arrays.asList(move("D4", 2000, 0)), 1000));
    assertTrue(search.accept(Arrays.asList(move("PASS", 1000, 0)), 1000));
    assertNull(search.next(targets("pass"), 1000));
  }

  @Test public void removedTargetsDisappearAndRaisedMinimumReschedules() {
    BranchAnalysis search = new BranchAnalysis();
    search.normal(new Object(), Arrays.asList(move("D4", 1000, 0)));
    assertNull(search.next(targets("D4"), 1000));
    assertTrue(search.display(Collections.emptyList(), targets()).isEmpty());
    assertEquals("D4", search.next(targets("D4"), 2000));
  }
}
