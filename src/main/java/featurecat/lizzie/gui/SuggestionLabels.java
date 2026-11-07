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

  /** Fit the entire glyph ink, including bearings/descenders, inside each circular row slot. */
  public static List<Placement> layout(Graphics2D g, List<Row> rows,
      double centerX, double centerY, double radius) {
    List<Placement> result = new ArrayList<>();
    if (rows.isEmpty() || radius <= 3) return result;
    double innerRadius = radius - Math.max(1.25, radius * 0.08);
    double stackHeight = innerRadius * 1.62;
    double slotHeight = stackHeight / rows.size();
    double inkHeight = slotHeight - Math.max(0.7, innerRadius * 0.035);
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      if (row.text.isEmpty() || inkHeight <= 0) continue;
      double rowCenter = (i - (rows.size() - 1) / 2.0) * slotHeight;
      Font base = row.font == null ? g.getFont() : row.font;
      GlyphVector glyphs = base.deriveFont(100f).createGlyphVector(g.getFontRenderContext(), row.text);
      Rectangle2D bounds = glyphs.getVisualBounds();
      // Solve for the largest glyph rectangle whose farthest corner fits the circle.
      double width = bounds.getWidth(), height = bounds.getHeight();
      double a = Math.max(0.01, (width * width + height * height) / 4);
      double b = Math.abs(rowCenter) * height;
      double c = rowCenter * rowCenter - innerRadius * innerRadius;
      double circularScale = (-b + Math.sqrt(b * b - 4 * a * c)) / (2 * a);
      double scale = Math.min(circularScale, inkHeight / Math.max(0.1, height));
      float size = (float) Math.max(0.1, 100 * scale);
      for (int attempt = 0; attempt < 12; attempt++) {
        glyphs = base.deriveFont(size).createGlyphVector(g.getFontRenderContext(), row.text);
        bounds = glyphs.getVisualBounds();
        if (fits(bounds, rowCenter, inkHeight, innerRadius)) break;
        size *= 0.95f;
      }
      if (!fits(bounds, rowCenter, inkHeight, innerRadius)) continue;
      result.add(new Placement(row, glyphs, centerX - bounds.getCenterX(),
          centerY + rowCenter - bounds.getCenterY()));
    }
    return result;
  }

  private static boolean fits(Rectangle2D bounds, double centerY, double height, double radius) {
    return bounds.getHeight() <= height
        && Math.hypot(bounds.getWidth() / 2, Math.abs(centerY) + bounds.getHeight() / 2) <= radius;
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
