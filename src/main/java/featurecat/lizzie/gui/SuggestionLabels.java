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
    final int order;

    public Row(String text, Font font, Color color) { this(text, font, color, 0); }

    public Row(String text, Font font, Color color, int order) {
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

  /** The approved preview: legacy baselines for two/three lines, independent fitting for four. */
  public static List<Placement> layout(Graphics2D g, List<Row> rows,
      double centerX, double centerY, double radius) {
    List<Placement> result = new ArrayList<>();
    if (rows.isEmpty() || radius <= 3) return result;
    double cell = 2 * radius + 2;
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      if (rows.size() == 4) {
        double unit = radius / 30;
        double[] top = {-27, -12, 2, 15.5}, bottom = {-13, 1.3, 14.5, 27.2};
        double[] maximum = {21, 21, 19, 19};
        result.add(fitBand(g, row, centerX, centerY, radius,
            top[i] * unit, bottom[i] * unit, maximum[i] * unit));
      } else {
        double pointSize, width, baseline;
        if (rows.size() == 3) {
          pointSize = cell * new double[] {.35, .34, .273}[i];
          width = i == 0 ? cell * .67
              : radius * (i == 1 && row.text.matches("[0-9]{1,3}") ? 1.3 : i == 1 ? 1.8 : 1.6);
          baseline = centerY + new double[] {-Math.round(cell * .125),
              Math.round(cell * .18), Math.round(cell * .435)}[i];
        } else if (rows.size() == 2) {
          pointSize = radius * (i == 0 ? 1 : .77);
          width = i == 0 ? cell * .735 : radius * 1.8;
          Font fitted = legacyFont(g, row, pointSize, width);
          java.awt.FontMetrics metrics = g.getFontMetrics(fitted);
          baseline = centerY + (i == 0 ? -(int) (cell / 16)
              : (int) (radius * 15 / 35) + (metrics.getAscent() - metrics.getDescent()) / 2);
        } else {
          pointSize = radius * 1.05;
          width = radius * 1.9;
          Font fitted = legacyFont(g, row, pointSize, width);
          java.awt.FontMetrics metrics = g.getFontMetrics(fitted);
          baseline = centerY + (metrics.getAscent() - metrics.getDescent()) / 2.0;
        }
        baseline = Math.min(baseline, centerY + radius + .4);
        Font fitted = legacyFont(g, row, pointSize, width);
        // Long bottom-row values can keep a little overhang, with a small size reduction.
        if (rows.size() == 3 && i == 2
            && !fits(atBaseline(g, row, centerX, baseline, fitted), centerX, centerY, radius, 0))
          fitted = fitted.deriveFont(fitted.getSize2D() * .94f);
        double maximumInkHeight = i == 0 ? 2 * radius
            : baseline - result.get(i - 1).bounds.getMaxY() - Math.max(.4, radius * .015);
        result.add(fitBaseline(g, row, centerX, centerY, radius,
            baseline, fitted, maximumInkHeight));
      }
    }
    return result;
  }

  private static Font font(Graphics2D g, Row row, double size) {
    Font base = row.font == null ? g.getFont() : row.font;
    java.util.Map<java.awt.font.TextAttribute, Object> attributes = new java.util.HashMap<>();
    attributes.put(java.awt.font.TextAttribute.KERNING, java.awt.font.TextAttribute.KERNING_ON);
    return base.deriveFont(Font.PLAIN, (float) Math.max(.1, size)).deriveFont(attributes);
  }

  private static Font legacyFont(Graphics2D g, Row row, double height, double width) {
    Font base = font(g, row, 100);
    double fitWidth = 100 * width / Math.max(1, g.getFontMetrics(base).stringWidth(row.text));
    return font(g, row, Math.min(height, (int) fitWidth));
  }

  private static Placement atBaseline(Graphics2D g, Row row, double x, double baseline, Font font) {
    GlyphVector glyphs = font.createGlyphVector(g.getFontRenderContext(), row.text);
    return new Placement(row, glyphs,
        x - g.getFontMetrics(font).stringWidth(row.text) / 2.0, baseline);
  }

  private static Placement fitBaseline(Graphics2D g, Row row, double x, double y, double radius,
      double baseline, Font preferred, double maximumInkHeight) {
    Placement result = atBaseline(g, row, x, baseline, preferred);
    if (result.bounds.getHeight() <= maximumInkHeight && fits(result, x, y, radius)) return result;
    result = atBaseline(g, row, x, baseline, font(g, row, .1));
    double low = .1, high = preferred.getSize2D();
    for (int attempt = 0; attempt < 18; attempt++) {
      double size = (low + high) / 2;
      Placement candidate = atBaseline(g, row, x, baseline, font(g, row, size));
      if (candidate.bounds.getHeight() <= maximumInkHeight && fits(candidate, x, y, radius)) {
        result = candidate;
        low = size;
      } else high = size;
    }
    return result;
  }

  private static Placement fitBand(Graphics2D g, Row row, double x, double y, double radius,
      double top, double bottom, double maximum) {
    Placement result = null;
    double low = .1, high = maximum;
    for (int attempt = 0; attempt < 18; attempt++) {
      double size = (low + high) / 2;
      GlyphVector glyphs = font(g, row, size).createGlyphVector(g.getFontRenderContext(), row.text);
      Rectangle2D ink = glyphs.getVisualBounds();
      Placement candidate = new Placement(row, glyphs,
          x - ink.getCenterX(), y + (top + bottom) / 2 - ink.getCenterY());
      if (ink.getHeight() <= bottom - top && fits(candidate, x, y, radius)) {
        result = candidate;
        low = size;
      } else high = size;
    }
    return result;
  }

  private static boolean fits(Placement row, double x, double y, double radius) {
    // Prefer readable text over strict containment at the round corners. The painted circle
    // has radius + 1; permit a small radial overhang, while row fitting still prevents overlaps.
    return fits(row, x, y, radius, Math.max(1, radius * .12));
  }

  private static boolean fits(Placement row, double x, double y, double radius, double overhang) {
    double inside = radius + 1 + overhang;
    java.awt.geom.PathIterator path = row.glyphs.getOutline(row.x, row.baseline)
        .getPathIterator(null, .02);
    double[] point = new double[6];
    while (!path.isDone()) {
      if (path.currentSegment(point) != java.awt.geom.PathIterator.SEG_CLOSE
          && Math.hypot(point[0] - x, point[1] - y) > inside) return false;
      path.next();
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
    if (move.isKataData && Lizzie.config.showMoveValue)
      rows.add(new Row(MoveValue.candidateText(move, data), winrateFont,
          canHighlight && move.order == 0 ? highlight : normal, Lizzie.config.suggestionInfoValue));
    rows.sort(Comparator.comparingInt(row -> row.order));
    draw(g, rows, x, y, radius);
  }
}
