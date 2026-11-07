package featurecat.lizzie.gui;

import static org.junit.Assert.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import org.junit.Test;

public class SuggestionLabelsTest {
  private List<SuggestionLabels.Row> rows(Font font, int count) {
    List<SuggestionLabels.Row> rows = new ArrayList<>();
    String[] texts = {"100.0", "123.4k", "-123.4", "+123.4"};
    for (int i = 0; i < count; i++) rows.add(new SuggestionLabels.Row(texts[i], font, Color.RED));
    return rows;
  }

  @Test public void glyphInkFitsTheCircleForAllLineCountsFontsAndSizes() {
    BufferedImage image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    for (String family : Arrays.asList(Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED)) {
      for (int style : new int[] {Font.PLAIN, Font.BOLD, Font.ITALIC}) {
        for (int radius : new int[] {6, 10, 16, 24, 31, 45}) {
          for (int count = 1; count <= 4; count++) {
            List<SuggestionLabels.Placement> placed = SuggestionLabels.layout(g,
                rows(new Font(family, style, 70), count), 100, 100, radius);
            assertEquals(count, placed.size());
            Rectangle2D previous = null;
            for (SuggestionLabels.Placement row : placed) {
              Rectangle2D box = row.bounds;
              java.awt.geom.Area outside = new java.awt.geom.Area(row.glyphs.getOutline(row.x, row.baseline));
              outside.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
                  99 - radius, 99 - radius, 2 * radius + 2, 2 * radius + 2)));
              assertTrue("Actual glyph outline outside circle: " + family + " style=" + style + " r=" + radius + " count=" + count + " text=" + row.row.text, outside.isEmpty());
              if (previous != null) assertTrue("Adjacent lines overlap", previous.getMaxY() < box.getMinY());
              previous = box;
            }
          }
        }
      }
    }
    g.dispose();
  }

  @Test public void rasterizedThreeAndFourLineLabelsDoNotLeakAtDisplayScaling() {
    for (double scale : new double[] {1, 1.25, 1.5, 2}) {
      for (int count : new int[] {3, 4}) {
        for (int radius : new int[] {12, 24, 31}) {
          BufferedImage image = new BufferedImage(260, 260, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = image.createGraphics();
          g.scale(scale, scale);
          g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
          SuggestionLabels.draw(g, rows(new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 90), count),
              60, 60, radius);
          g.dispose();
          int pixels = 0;
          for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++) {
            if ((image.getRGB(x, y) >>> 24) == 0) continue;
            pixels++;
            assertTrue("Rendered text exceeded circle: scale=" + scale + " radius=" + radius + " count=" + count + " pixel=" + x + "," + y,
                Math.hypot((x + 0.5) / scale - 60, (y + 0.5) / scale - 60) <= radius + 1);
          }
          assertTrue("Labels disappeared instead of being fitted", pixels > 8);
        }
      }
    }
  }

  @Test public void drawingPreservesTheCallersGraphicsState() {
    BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.BLUE);
    Font original = new Font(Font.MONOSPACED, Font.BOLD, 30);
    g.setFont(original);
    SuggestionLabels.draw(g, rows(original, 4), 100, 100, 30);
    assertEquals(Color.BLUE, g.getColor());
    assertEquals(original, g.getFont());
    g.dispose();
  }
  @Test public void ordinaryThreeLineFontSizesMatchTheOriginalReadableSizes() {
    BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 100);
    List<SuggestionLabels.Row> rows = Arrays.asList(
        new SuggestionLabels.Row("38.0", font, Color.BLACK),
        new SuggestionLabels.Row("3.6k", font, Color.BLACK),
        new SuggestionLabels.Row("-0.1", font, Color.BLACK));
    List<SuggestionLabels.Placement> actual = SuggestionLabels.layout(g, rows, 100, 100, 30);
    double[] heights = {62 * 0.35, 62 * 0.34, 62 * 0.273};
    double[] widths = {62 * 0.67, 30 * 1.8, 30 * 1.6};
    for (int i = 0; i < 3; i++) {
      Font widthFit = font.deriveFont((float) (100 * widths[i]
          / g.getFontMetrics(font).stringWidth(rows.get(i).text)));
      double legacy = Math.min(heights[i], widthFit.getSize());
      assertTrue("Three-line text became smaller than legacy", actual.get(i).glyphs.getFont().getSize2D() >= legacy * 0.95);
    }
    g.dispose();
  }

  @Test public void fourLineLayoutUsesAvailableSpaceInsteadOfFixedSmallSlots() {
    BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 100);
    List<SuggestionLabels.Row> rows = new ArrayList<>();
    for (String text : Arrays.asList("34.8", "3.6k", "-0.1", "+14.7"))
      rows.add(new SuggestionLabels.Row(text, font, Color.BLACK));
    List<SuggestionLabels.Placement> placed = SuggestionLabels.layout(g, rows, 100, 100, 30);
    // The approved four-line preview gives the middle rows more room than the outer rows.
    double[] minimum = {15.5, 17, 16, 12.5};
    for (int i = 0; i < 4; i++)
      assertTrue("Four-line font is unnecessarily small: " + placed.get(i).row.text,
          placed.get(i).glyphs.getFont().getSize2D() >= minimum[i]);
    assertTrue(placed.get(1).glyphs.getFont().getSize2D()
        > placed.get(3).glyphs.getFont().getSize2D());
    g.dispose();
  }


  @Test public void threeLineLayoutKeepsApprovedOriginalBaselinesAndFonts() throws Exception {
    Font numbers = Font.createFont(Font.TRUETYPE_FONT,
        getClass().getResourceAsStream("/fonts/OpenSans-Semibold.ttf"));
    Font visits = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    List<SuggestionLabels.Row> rows = Arrays.asList(
        new SuggestionLabels.Row("65.3", numbers, Color.RED),
        new SuggestionLabels.Row("1.7k", visits, Color.RED),
        new SuggestionLabels.Row("-0.0", numbers, Color.RED));
    Graphics2D g = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
    List<SuggestionLabels.Placement> placed = SuggestionLabels.layout(g, rows, 100, 100, 30);
    double[] baseline = {92, 111, 127};
    double[] size = {62 * .35, 62 * .34, 62 * .273};
    for (int i = 0; i < 3; i++) {
      assertEquals(baseline[i], placed.get(i).baseline, .01);
      Font base = rows.get(i).font.deriveFont(Font.PLAIN, 100f);
      java.util.Map<java.awt.font.TextAttribute, Object> attributes = new java.util.HashMap<>();
      attributes.put(java.awt.font.TextAttribute.KERNING, java.awt.font.TextAttribute.KERNING_ON);
      base = base.deriveFont(attributes);
      double width = i == 0 ? 62 * .67 : 30 * (i == 1 ? 1.8 : 1.6);
      double expected = Math.min(size[i], (int) (100 * width
          / g.getFontMetrics(base).stringWidth(rows.get(i).text)));
      assertEquals(expected, placed.get(i).glyphs.getFont().getSize2D(), .01);
    }
    g.dispose();
  }
}
