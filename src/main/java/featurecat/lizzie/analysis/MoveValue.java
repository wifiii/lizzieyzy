package featurecat.lizzie.analysis;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.util.Utils;
import java.util.Locale;

/** All scores here are from the player to move's perspective, including a pass child. */
public final class MoveValue {
  public static final String SAVED_VALUE_PROPERTY = "LZNV";

  private MoveValue() {}

  public static double nextValue(double ordinaryScore, double passScore) {
    return ordinaryScore - passScore;
  }

  public static double candidateValue(double candidateScore, double passScore) {
    return candidateScore - passScore;
  }

  public static boolean available(BoardData data) {
    return data.passAnalysisVisits > 0 && Double.isFinite(data.passAnalysisScore)
        && Lizzie.leelaz != null
        && data.passAnalysisEngine.equals(Lizzie.leelaz.analysisContext());
  }

  public static String candidateText(MoveData move, BoardData data) {
    return available(data)
        ? String.format(Locale.ENGLISH, "%+.1f", candidateValue(move.scoreMean, data.passAnalysisScore))
        : "…";
  }

  public static String comment(BoardData data) {
    if (!available(data)) {
      String saved = data.getProperty(SAVED_VALUE_PROPERTY);
      if (saved != null && Lizzie.leelaz != null && !Lizzie.leelaz.isPassValueEnabled()) return saved;
      return Lizzie.resourceBundle.getString("MoveValue.pending");
    }
    return String.format(Locale.ENGLISH, Lizzie.resourceBundle.getString("MoveValue.comment"),
        nextValue(data.scoreMean, data.passAnalysisScore), Utils.getPlayoutsString(data.passAnalysisVisits));
  }
}
