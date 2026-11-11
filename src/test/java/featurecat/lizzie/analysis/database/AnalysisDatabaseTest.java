package featurecat.lizzie.analysis.database;

import static org.junit.Assert.*;

import featurecat.lizzie.analysis.MoveData;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;

public class AnalysisDatabaseTest {
  private void different(Object a, Object b) {
    assertFalse(java.util.Objects.equals(a, b));
  }

  private PositionKey key(String board, int koX, int koY) {
    return new PositionKey(5, 5, board, true, koX, koY, true);
  }

  private String board() {
    char[] b = new char[25];
    Arrays.fill(b, '0');
    b[0] = '1';
    b[8] = '1';
    b[22] = '2';
    return new String(b);
  }

  private int[] rotated(int x, int y, int t) {
    for (int i = 0; i < (t & 3); i++) {
      int old = x;
      x = 4 - y;
      y = old;
    }
    if (t >= 4) x = 4 - x;
    return new int[] {x, y};
  }

  private String board(int t) {
    char[] b = new char[25];
    String original = board();
    for (int x = 0; x < 5; x++)
      for (int y = 0; y < 5; y++) {
        int[] p = rotated(x, y, t);
        b[p[0] * 5 + p[1]] = original.charAt(x * 5 + y);
      }
    return new String(b);
  }

  private MoveData move(String coordinate, int visits, int order) {
    MoveData m = new MoveData();
    m.coordinate = coordinate;
    m.playouts = visits;
    m.order = order;
    m.winrate = 34.7;
    m.scoreMean = -7.9;
    m.scoreStdev = 12.8;
    m.isKataData = true;
    m.variation = Arrays.asList(coordinate, "pass", "D2");
    m.pvVisits = Arrays.asList("100", "50", "20");
    return m;
  }

  private AnalysisSnapshot snapshot(int visits, int pass) {
    AnalysisSnapshot s = new AnalysisSnapshot();
    s.visits = visits;
    s.passVisits = pass;
    s.passScore = -22.8;
    s.winrate = 34.7;
    s.score = -7.9;
    s.stdev = 12.8;
    s.normal.add(move("A5", visits, 0));
    s.supplemental.add(move("C3", 2000, 19));
    s.ownership = new ArrayList<>();
    for (int i = 0; i < 25; i++) s.ownership.add(i / 25.0);
    return s;
  }

  @Test
  public void eightSymmetriesRoundTripMovesPvOwnershipAndKo() throws Exception {
    Path db = Files.createTempDirectory("analysis-db").resolve("cache.sqlite3");
    PositionKey original = key(board(), 1, 2);
    AnalysisSnapshot saved = snapshot(40000, 10000);
    saved.normal.get(0).symmetryOf = "C3";
    AnalysisDatabase.save(db, original, "model-context", saved);
    for (int t = 0; t < 8; t++) {
      int[] ko = rotated(1, 2, t);
      PositionKey rotated = key(board(t), ko[0], ko[1]);
      assertEquals(original.hash, rotated.hash);
      AnalysisSnapshot loaded = AnalysisDatabase.load(db, rotated, "model-context");
      int[] p = rotated(0, 0, t);
      String expected = "ABCDEFGHJKLMNOPQRSTUVWXYZ".charAt(p[0]) + String.valueOf(5 - p[1]);
      assertEquals(expected, loaded.normal.get(0).coordinate);
      assertEquals("pass", loaded.normal.get(0).variation.get(1));
      int[] pv = rotated(3, 3, t);
      assertEquals(
          "ABCDE".charAt(pv[0]) + String.valueOf(5 - pv[1]), loaded.normal.get(0).variation.get(2));
      assertEquals(Arrays.asList("100", "50", "20"), loaded.normal.get(0).pvVisits);
      assertEquals(0, loaded.normal.get(0).order);
      assertEquals(19, loaded.supplemental.get(0).order);
      int[] own = rotated(1, 3, t);
      assertEquals(saved.ownership.get(3 * 5 + 1), loaded.ownership.get(own[1] * 5 + own[0]));
    }
  }

  @Test
  public void independentComponentsNeverDowngradeAndSurviveReopen() throws Exception {
    Path db = Files.createTempDirectory("analysis-db").resolve("cache.sqlite3");
    PositionKey key = key(board(), -1, -1);
    AnalysisDatabase.save(db, key, "a", snapshot(40000, 10000));
    AnalysisDatabase.save(db, key, "a", snapshot(5000, 20000));
    AnalysisSnapshot loaded = AnalysisDatabase.load(db, key, "a");
    assertEquals(40000, loaded.visits);
    assertEquals(40000, loaded.normal.get(0).playouts);
    assertEquals(20000, loaded.passVisits);
    assertTrue(loaded.savedAt > 0);
    assertEquals(0, AnalysisDatabase.load(db, key, "different-model-or-rules").visits);
    AnalysisDatabase.clear(db);
    assertEquals(0, AnalysisDatabase.load(db, key, "a").visits);
  }

  @Test
  public void keyIncludesColorKoAndDimensionsButCanIgnoreOrientation() {
    PositionKey k = key(board(), -1, -1);
    different(k.hash, new PositionKey(5, 5, board(), false, -1, -1, true).hash);
    different(k.hash, key(board(), 1, 2).hash);
    different(
        new PositionKey(5, 5, board(), true, -1, -1, false).hash,
        new PositionKey(5, 5, board(1), true, -1, -1, false).hash);
    String rectangle = "100000200000";
    PositionKey r = new PositionKey(3, 4, rectangle, true, -1, -1, true);
    assertEquals("A4", r.coordinate(r.coordinate("A4", true), false));
    different(r.hash, new PositionKey(4, 3, rectangle, true, -1, -1, true).hash);
  }

  @Test
  public void modelContentRulesKomiAndPdaDefineIdentityNotExecutableVersion() throws Exception {
    Path directory = Files.createTempDirectory("model-identity");
    Path model = directory.resolve("model.bin");
    Files.write(model, new byte[] {1, 2, 3});
    Path copied = directory.resolve("renamed.bin");
    Files.copy(model, copied);
    Path gzip = directory.resolve("model.bin.gz");
    try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gzip))) {
      out.write(new byte[] {1, 2, 3});
    }
    assertEquals(EngineIdentity.modelHash(model), EngineIdentity.modelHash(gzip));
    Map<String, String> options = new HashMap<>();
    String a =
        EngineIdentity.create(
            "engine-v1 -model " + model,
            "",
            "",
            "{\"koRule\":\"SIMPLE\",\"scoringRule\":\"AREA\"}",
            7.5,
            options,
            0,
            false);
    String b =
        EngineIdentity.create(
            "engine-v2 -model " + copied,
            "",
            "",
            "{\"scoringRule\":\"AREA\",\"koRule\":\"SIMPLE\"}",
            7.5,
            options,
            0,
            false);
    assertEquals(a, b);
    different(
        a,
        EngineIdentity.create(
            "x -model " + model,
            "",
            "",
            "{\"koRule\":\"SIMPLE\",\"scoringRule\":\"AREA\"}",
            6.5,
            options,
            0,
            false));
    different(
        a,
        EngineIdentity.create(
            "x -model " + model,
            "",
            "",
            "{\"koRule\":\"SIMPLE\",\"scoringRule\":\"AREA\"}",
            7.5,
            options,
            1,
            false));
    Files.write(copied, new byte[] {4, 5, 6, 7});
    different(
        a,
        EngineIdentity.create(
            "x -model " + copied,
            "",
            "",
            "{\"koRule\":\"SIMPLE\",\"scoringRule\":\"AREA\"}",
            7.5,
            options,
            0,
            false));
  }

  @Test
  public void semanticConfigurationAndRuntimeChangesMissButVisitLimitsDoNot() throws Exception {
    Path directory = Files.createTempDirectory("config-identity");
    Path model = directory.resolve("model.bin"), config = directory.resolve("gtp.cfg");
    Files.write(model, new byte[] {1, 2, 3});
    String command = "katago -model " + model + " -config " + config;
    Map<String, String> runtime = new HashMap<>();
    Files.write(
        config,
        "# comment\nconservativePass = true\nmaxVisits=1000\nnumSearchThreads=1\n"
            .getBytes("UTF-8"));
    String a =
        EngineIdentity.create(command, "", "", "{\"ko\":\"SIMPLE\"}", 7.5, runtime, 0, false);
    Files.write(
        config,
        "numSearchThreads=4\nmaxVisits=40000\nconservativePass=true # comment\n".getBytes("UTF-8"));
    assertEquals(
        a, EngineIdentity.create(command, "", "", "{\"ko\":\"SIMPLE\"}", 7.5, runtime, 0, false));
    Files.write(config, "conservativePass=false\n".getBytes("UTF-8"));
    String b =
        EngineIdentity.create(command, "", "", "{\"ko\":\"SIMPLE\"}", 7.5, runtime, 0, false);
    different(a, b);
    different(
        b,
        EngineIdentity.create(command, "", "", "{\"ko\":\"POSITIONAL\"}", 7.5, runtime, 0, false));
    runtime.put(
        "kata-set-param playoutDoublingAdvantagePla",
        "kata-set-param playoutDoublingAdvantagePla B");
    different(
        b, EngineIdentity.create(command, "", "", "{\"ko\":\"SIMPLE\"}", 7.5, runtime, 0, false));
  }

  @Test
  public void corruptOrFutureDatabaseIsNeverOverwritten() throws Exception {
    Path db = Files.createTempFile("broken-analysis-db", ".sqlite3");
    byte[] bytes = "not sqlite".getBytes("UTF-8");
    Files.write(db, bytes);
    try {
      AnalysisDatabase.load(db, key(board(), -1, -1), "a");
      fail();
    } catch (java.sql.SQLException expected) {
    }
    assertArrayEquals(bytes, Files.readAllBytes(db));
    Path future = Files.createTempFile("future-analysis-db", ".sqlite3");
    try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + future);
        java.sql.Statement s = c.createStatement()) {
      s.execute("PRAGMA user_version=999");
    }
    try {
      AnalysisDatabase.load(future, key(board(), -1, -1), "a");
      fail();
    } catch (java.sql.SQLException expected) {
      assertTrue(expected.getMessage().contains("999"));
    }
  }
}
