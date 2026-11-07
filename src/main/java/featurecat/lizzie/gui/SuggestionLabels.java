package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.MoveData;
import featurecat.lizzie.analysis.MoveValue;
import featurecat.lizzie.rules.BoardData;
import featurecat.lizzie.util.Utils;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Shared, baseline-aware label layout for the main, independent and floating boards. */
public final class SuggestionLabels {
  private SuggestionLabels() {}

  public static final class Row {
    public final String text;
    public final Font font;
    public final Color color;
    private final int order;

    public Row(String text, Font font, Color color) { this(text, font, color, 0); }

    private Row(String text, Font font, Color color, int order) {
      this.text = text;
      this.font = font;
      this.color = color;
      this.order = order;
    }
  }

  public static final class Placement {
    public final Row row;
    public final GlyphVector glyphs;
    public final float x;
    public final float baseline;
    public final Rectangle2D bounds;

    private Placement(Row row, GlyphVector glyphs, double x, double baseline) {
      this.row = row;
      this.glyphs = glyphs;
      this.x = (float) x;
      this.baseline = (float) baseline;
      Rectangle2D ink = glyphs.getVisualBounds();
      this.bounds = new Rectangle2D.Double(this.x + ink.getX(), this.baseline + ink.getY(),
          ink.getWidth(), ink.getHeight());
    }
  }

  /** Prefer the legacy three-line point sizes, then tightly pack the actual glyph ink. */
  public static List<Placement> layout(Graphics2D g, List<Row> rows,
      double centerX, double centerY, double radius) {
    if (rows.isEmpty() || radius <= 3) return new ArrayList<>();
    List<Placement> full = packed(g, rows, centerX, centerY, radius, 1);
    if (fits(full, centerX, centerY, radius)) return full;
    double low = 0.001, high = 1;
    List<Placement> result = packed(g, rows, centerX, centerY, radius, low);
    for (int attempt = 0; attempt < 28; attempt++) {
      double scale = (low + high) / 2;
      List<Placement> candidate = packed(g, rows, centerX, centerY, radius, scale);
      if (fits(candidate, centerX, centerY, radius)) {
        result = candidate;
        low = scale;
      } else high = scale;
    }
    return result;
  }

  private static List<Placement> packed(Graphics2D g, List<Row> rows,
      double x, double y, double radius, double scale) {
    List<GlyphVector> glyphs = new ArrayList<>();
    double gap = Math.max(0.45, radius * 0.02) * scale;
    double height = Math.max(0, rows.size() - 1) * gap;
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      Font base = row.font == null ? g.getFont() : row.font;
      // These are the old .35/.34/.273 cell-width sizes (cell width = 2r + 2).
      double cellWidth = 2 * radius + 2;
      double pointSize = rows.size() == 3
          ? cellWidth * new double[] {0.35, 0.34, 0.273}[i]
          : radius * (rows.size() == 4 ? 0.78 : rows.size() == 2 ? 0.85 : 1.05);
      double widthLimit = rows.size() == 3
          ? (i == 0 ? cellWidth * 0.67 : radius * (i == 1 ? 1.8 : 1.6)) : radius * 1.9;
      GlyphVector vector = base.deriveFont((float) Math.max(0.1, pointSize * scale))
          .createGlyphVector(g.getFontRenderContext(), row.text);
      Rectangle2D ink = vector.getVisualBounds();
      if (ink.getWidth() > widthLimit) {
        vector = base.deriveFont((float) Math.max(0.1,
            pointSize * scale * widthLimit / ink.getWidth()))
            .createGlyphVector(g.getFontRenderContext(), row.text);
      }
      glyphs.add(vector);
      height += vector.getVisualBounds().getHeight();
    }
    double top = y - height / 2;
    List<Placement> result = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      GlyphVector vector = glyphs.get(i);
      Rectangle2D ink = vector.getVisualBounds();
      result.add(new Placement(rows.get(i), vector, x - ink.getCenterX(), top - ink.getY()));
      top += ink.getHeight() + gap;
    }
    return result;
  }

  private static boolean fits(List<Placement> placed, double x, double y, double radius) {
    double inside = radius - Math.max(0.7, radius * 0.015);
    for (Placement row : placed) {
      java.awt.geom.PathIterator path = row.glyphs.getOutline(row.x, row.baseline).getPathIterator(null);
      double[] points = new double[6];
      while (!path.isDone()) {
        int type = path.currentSegment(points);
        int count = type == java.awt.geom.PathIterator.SEG_CLOSE ? 0
            : type == java.awt.geom.PathIterator.SEG_CUBICTO ? 3
            : type == java.awt.geom.PathIterator.SEG_QUADTO ? 2 : 1;
        for (int i = 0; i < count; i++)
          if (Math.hypot(points[2 * i] - x, points[2 * i + 1] - y) > inside) return false;
        path.next();
      }
    }
    return true;
  }

  public static void draw(Graphics2D graphics, List<Row> rows,
      double centerX, double centerY, double radius) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      for (Placement placed : layout(g, rows, centerX, centerY, radius)) {
        g.setColor(placed.row.color);
        g.drawGlyphVector(placed.glyphs, placed.x, placed.baseline);
      }
    } finally { g.dispose(); }
  }

  public static void drawCandidate(Graphics2D g, int x, int y, int radius, MoveData move,
      BoardData data, double roundedWinrate, boolean maximumWinrate, int maximumVisits,
      double maximumScore, Color highlight, boolean canHighlight) {
    List<Row> rows = new ArrayList<>();
    Color normal = g.getColor();
    Font winrateFont = LizzieFrame.winrateFont;
    Font visitsFont = LizzieFrame.playoutsFont;
    if (Lizzie.config.showWinrateInSuggestion) rows.add(new Row(
        String.format(Locale.ENGLISH, "%.1f", roundedWinrate), winrateFont,
        canHighlight && maximumWinrate ? highlight : normal, Lizzie.config.suggestionInfoWinrate));
    if (Lizzie.config.showPlayoutsInSuggestion) rows.add(new Row(Utils.getPlayoutsString(move.playouts),
        visitsFont, canHighlight && move.playouts == maximumVisits ? highlight : normal,
        Lizzie.config.suggestionInfoPlayouts));
    if (move.isKataData && Lizzie.config.showScoremeanInSuggestion) rows.add(new Row(
        Utils.convertScoreToString(move.scoreMean, maximumScore), winrateFont,
        canHighlight && move.scoreMean == maximumScore ? highlight : normal,
        Lizzie.config.suggestionInfoScoreLead));
    rows.sort(Comparator.comparingInt(row -> row.order));
    if (move.isKataData && Lizzie.config.showMoveValue)
      rows.add(new Row(MoveValue.candidateText(move, data), winrateFont, normal));
    draw(g, rows, x, y, radius);
  }
}
