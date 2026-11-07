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

  @Test public void observationsSurviveBeingOmittedFromLaterEngineUpdates() {
    BranchAnalysis search = new BranchAnalysis();
    Object node = new Object();
    MoveData a = new MoveData(); a.coordinate = "D4"; a.playouts = 1000;
    MoveData b = new MoveData(); b.coordinate = "T1"; b.playouts = 1;
    search.normal(node, Arrays.asList(a, b));
    search.normal(node, Arrays.asList(a));
    assertTrue(search.observedCoordinates().contains("T1"));
    assertEquals("T1", search.next(search.observedCoordinates(), 200));
    MoveData supplement = new MoveData(); supplement.coordinate = "T1"; supplement.playouts = 200;
    search.accept(Arrays.asList(supplement), 200);
    List<MoveData> display = search.display(Arrays.asList(a), Collections.emptySet(), true);
    assertEquals(2, display.size());
    assertTrue(display.get(1).isSupplementalCandidate);
    assertFalse(display.get(1).isSgfBranch);
    assertNull(search.next(search.observedCoordinates(), 200));
  }
}
