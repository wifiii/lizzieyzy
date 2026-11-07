package featurecat.lizzie.analysis;

import static org.junit.Assert.*;
import java.awt.Color;
import java.util.*;
import org.junit.Test;

public class SuggestionStyleTest {
  private Map<Double, Color> colors() {
    Map<Double, Color> colors = new HashMap<>();
    colors.put(-24.0, Color.MAGENTA);
    colors.put(-12.0, Color.RED);
    colors.put(-6.0, Color.ORANGE);
    colors.put(-3.0, Color.YELLOW);
    colors.put(-1.0, Color.GREEN);
    colors.put(3.0, Color.GREEN);
    return colors;
  }

  @Test public void themeThresholdsHaveStableInclusiveBoundaries() {
    List<Double> thresholds = Arrays.asList(3.0, -3.0, -24.0, -12.0, -1.0, -6.0);
    assertEquals(Color.MAGENTA, SuggestionStyle.thresholdColor(-52.8, thresholds, colors(), Color.WHITE));
    assertEquals(Color.MAGENTA, SuggestionStyle.thresholdColor(-24, thresholds, colors(), Color.WHITE));
    assertEquals(Color.RED, SuggestionStyle.thresholdColor(-12, thresholds, colors(), Color.WHITE));
    assertEquals(Color.ORANGE, SuggestionStyle.thresholdColor(-10, thresholds, colors(), Color.WHITE));
    assertEquals(Color.GREEN, SuggestionStyle.thresholdColor(0, thresholds, colors(), Color.WHITE));
  }

  @Test public void mediumVisitCountsRemainLegibleAtTenThousandMaximum() {
    assertEquals(255, SuggestionStyle.alpha(10000, 10000));
    assertTrue(SuggestionStyle.alpha(1000, 10000) >= 200);
    assertTrue(SuggestionStyle.alpha(500, 10000) >= 190);
    assertTrue(SuggestionStyle.alpha(1, 10000) < SuggestionStyle.alpha(500, 10000));
    assertEquals(255, SuggestionStyle.alpha(20000, 10000));
    assertTrue(SuggestionStyle.alpha(0, 0) >= 0);
  }

  @Test public void candidateValueMeansImprovementComparedWithPassing() {
    assertEquals(11.3, MoveValue.nextValue(5.0, -6.3), 0.00001);
    assertEquals(9.3, MoveValue.candidateValue(3.0, -6.3), 0.00001);
    assertEquals(-2.0, MoveValue.candidateValue(-8.3, -6.3), 0.00001);
    assertEquals(0.0, MoveValue.candidateValue(-6.3, -6.3), 0);
  }

  @Test public void candidateAdmissionUsesOnlyCurrentNormalLimits() {
    BranchAnalysis search = new BranchAnalysis();
    Object node = new Object();
    MoveData a = new MoveData(); a.coordinate = "D4"; a.playouts = 1000; a.order = 0;
    MoveData b = new MoveData(); b.coordinate = "T1"; b.playouts = 1; b.order = 1;
    MoveData c = new MoveData(); c.coordinate = "Q16"; c.playouts = 2; c.order = 1;
    search.normal(node, Arrays.asList(a, b));
    assertFalse(search.candidateCoordinates(1, 0).contains("T1"));
    assertFalse(search.candidateCoordinates(2, 0.01).contains("T1"));
    search.normal(node, Arrays.asList(a, c));
    assertFalse(search.candidateCoordinates(2, 0).contains("T1"));
    assertTrue(search.candidateCoordinates(2, 0).contains("Q16"));
    assertEquals("Q16", search.next(search.candidateCoordinates(2, 0), 200));
    MoveData supplement = new MoveData(); supplement.coordinate = "Q16"; supplement.playouts = 200;
    search.accept(Arrays.asList(supplement), 200);
    List<MoveData> display = search.display(Arrays.asList(a), Collections.emptySet(),
        search.candidateCoordinates(2, 0), 2);
    assertEquals(2, display.size());
    assertTrue(display.get(1).isSupplementalCandidate);
    assertNull(search.next(search.candidateCoordinates(2, 0), 200));
  }

  @Test public void rawFullBoardOutputCannotExpandTheCandidateLimit() {
    BranchAnalysis search = new BranchAnalysis();
    List<MoveData> raw = new ArrayList<>();
    for (int i = 0; i < 361; i++) {
      MoveData move = new MoveData(); move.coordinate = "point" + i;
      move.playouts = i == 0 ? 1000 : 1; move.order = i;
      raw.add(move);
    }
    search.normal(new Object(), raw);
    Set<String> admitted = search.candidateCoordinates(10, 0);
    assertEquals(10, admitted.size());
    assertEquals(10, search.display(raw, Collections.emptySet(), admitted, 10).size());
    assertFalse(admitted.contains("point360"));
    Set<String> branches = new HashSet<>(Arrays.asList("point360"));
    List<MoveData> withBranch = search.display(raw, branches, admitted, 10);
    assertEquals(11, withBranch.size());
    assertTrue(withBranch.get(10).isSgfBranch);
    assertFalse(withBranch.get(10).isSupplementalCandidate);
    assertEquals(admitted, search.candidateCoordinates(10, 0));
  }
  @Test public void outOfLimitVisitsDoNotDistortTheNormalVisitRatio() {
    BranchAnalysis search = new BranchAnalysis();
    MoveData first = new MoveData(); first.coordinate = "D4"; first.order = 0; first.playouts = 100;
    MoveData second = new MoveData(); second.coordinate = "Q16"; second.order = 1; second.playouts = 20;
    MoveData outside = new MoveData(); outside.coordinate = "T1"; outside.order = 2; outside.playouts = 1000000;
    search.normal(new Object(), Arrays.asList(first, second, outside));
    Set<String> targets = search.candidateCoordinates(2, 0.1);
    assertEquals(2, targets.size());
    assertTrue(targets.contains("Q16"));
    assertFalse(targets.contains("T1"));
  }

}
