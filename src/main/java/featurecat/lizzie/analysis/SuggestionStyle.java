package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import java.awt.Color;
import java.util.List;
import java.util.Map;

/** Candidate quality and confidence are independent of search allocation. */
public final class SuggestionStyle {
  private SuggestionStyle() {}

  public static void setReference(List<MoveData> moves) {
    if (moves.isEmpty()) return;
    double winrate = moves.get(0).winrate;
    double score = moves.get(0).scoreMean;
    for (MoveData move : moves) {
      move.hasOrdinaryReference = true;
      move.ordinaryWinrate = winrate;
      move.ordinaryScoreMean = score;
    }
  }

  /** Logarithmic confidence: at a 10k maximum, 500/1k visits have alpha 193/207. */
  public static int alpha(int visits, int maximum) {
    double ratio = Math.log1p(Math.max(0, visits)) / Math.log1p(Math.max(1, maximum));
    return (int) Math.round(64 + 191 * Math.min(1, ratio));
  }

  public static Color thresholdColor(double delta, List<Double> thresholds,
      Map<Double, Color> colors, Color fallback) {
    Double selected = null;
    for (Double threshold : thresholds) {
      if (Double.isFinite(threshold) && threshold >= delta && colors.get(threshold) != null
          && (selected == null || threshold < selected)) selected = threshold;
    }
    return selected == null ? fallback : colors.get(selected);
  }

  public static Color color(MoveData move, List<MoveData> moves, int maximum) {
    MoveData first = moves.get(0);
    double referenceWinrate = first.hasOrdinaryReference ? first.ordinaryWinrate : first.winrate;
    double referenceScore = first.hasOrdinaryReference ? first.ordinaryScoreMean : first.scoreMean;
    double delta = move.winrate - referenceWinrate;
    if (move.isKataData && Lizzie.config.useScoreDiffInVariationTree
        && Lizzie.config.scoreDiffInVariationTreeFactor > 0) {
      delta = Math.min(delta,
          (move.scoreMean - referenceScore) / Lizzie.config.scoreDiffInVariationTreeFactor);
    }
    Color base = new Color(0, 220, 0);
    if (Lizzie.config.blunderWinrateThresholds.isPresent()
        && Lizzie.config.blunderNodeColors.isPresent()) {
      base = thresholdColor(delta, Lizzie.config.blunderWinrateThresholds.get(),
          Lizzie.config.blunderNodeColors.get(), base);
    }
    // Retain the theme's first-choice color only for an actually equal/better evaluation.
    if (move == first && delta >= -0.000001) base = Lizzie.config.bestMoveColorC;
    return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha(move.playouts, maximum));
  }
}
