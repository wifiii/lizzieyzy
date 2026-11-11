package featurecat.lizzie.analysis.database;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Stable, history-independent board key. Coordinates use the application's x-major layout. */
public final class PositionKey {
  private static final String LETTERS = "ABCDEFGHJKLMNOPQRSTUVWXYZ";
  public final int width, height, transform;
  public final String hash;

  public PositionKey(
      int width, int height, String stones, boolean black, int koX, int koY, boolean symmetries) {
    this.width = width;
    this.height = height;
    if (stones.length() != width * height) throw new IllegalArgumentException("board size");
    String best = null;
    int chosen = 0;
    for (int t = 0; t < (symmetries ? 8 : 1); t++) {
      if (width != height && (t & 1) != 0) continue;
      char[] board = new char[stones.length()];
      for (int x = 0; x < width; x++)
        for (int y = 0; y < height; y++) {
          int[] p = point(x, y, t, false);
          board[p[0] * height + p[1]] = stones.charAt(x * height + y);
        }
      int[] ko = koX < 0 ? new int[] {-1, -1} : point(koX, koY, t, false);
      String key =
          width + ":" + height + ":" + black + ":" + new String(board) + ":" + ko[0] + ":" + ko[1];
      if (best == null || key.compareTo(best) < 0) {
        best = key;
        chosen = t;
      }
    }
    transform = chosen;
    hash = sha256(best);
  }

  private int[] point(int x, int y, int code, boolean inverse) {
    if (inverse && code >= 4) x = width - 1 - x;
    int r = code & 3;
    if (inverse) r = (4 - r) & 3;
    if (r == 2) {
      x = width - 1 - x;
      y = height - 1 - y;
    } else if (r == 1) {
      int old = x;
      x = width - 1 - y;
      y = old;
    } else if (r == 3) {
      int old = x;
      x = y;
      y = height - 1 - old;
    }
    if (!inverse && code >= 4) x = width - 1 - x;
    return new int[] {x, y};
  }

  public String coordinate(String coord, boolean toCanonical) {
    if (coord == null || coord.equalsIgnoreCase("pass") || coord.equalsIgnoreCase("resign"))
      return coord;
    int x, y;
    try {
      if (coord.startsWith("(")) {
        String[] parts = coord.substring(1, coord.length() - 1).split(",");
        x = Integer.parseInt(parts[0]);
        y = Integer.parseInt(parts[1]);
      } else {
        x = LETTERS.indexOf(Character.toUpperCase(coord.charAt(0)));
        y = height - Integer.parseInt(coord.substring(1));
      }
      if (x < 0 || x >= width || y < 0 || y >= height)
        throw new IllegalArgumentException("coordinate");
    } catch (RuntimeException error) {
      throw new IllegalArgumentException("Invalid GTP coordinate: " + coord);
    }
    int[] p = point(x, y, transform, !toCanonical);
    return width > 25 || height > 25
        ? "(" + p[0] + "," + p[1] + ")"
        : LETTERS.charAt(p[0]) + String.valueOf(height - p[1]);
  }

  public List<Double> ownership(List<Double> values, boolean toCanonical) {
    if (values == null || values.size() != width * height) return null;
    List<Double> result = new ArrayList<>(Collections.nCopies(values.size(), 0.0));
    // KataGo ownership uses row-major order; BoardData stones use x-major order.
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        int[] p = point(x, y, transform, !toCanonical);
        result.set(p[1] * width + p[0], values.get(y * width + x));
      }
    return result;
  }

  public static String sha256(String value) {
    try {
      return hex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public static String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder();
    for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 255));
    return out.toString();
  }
}
