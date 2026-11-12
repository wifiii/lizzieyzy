package featurecat.lizzie.analysis;

import static org.junit.Assert.*;

import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.database.AnalysisDatabase;
import featurecat.lizzie.rules.SGFParser;
import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.*;

/** Public board operations with a real KataGo process, its real reader and real GTP ACKs. */
public class DatabaseWorkflowTest {
  private Leelaz engine;
  private Path database;

  @BeforeClass public static void ui() throws Exception {
    Assume.assumeTrue(System.getenv("DISPLAY") != null);
    Assume.assumeTrue(System.getProperty("katago.binary") != null);
    Lizzie.main(new String[0]);
    await(() -> Lizzie.engineManager != null);
  }

  @Before public void start() throws Exception {
    database = Files.createTempDirectory("database-workflow").resolve("analysis.sqlite3");
    SwingUtilities.invokeAndWait(() -> {
      Lizzie.config.analysisDatabaseEnabled = false;
      Lizzie.config.isAutoAna = false;
      Lizzie.config.analyzeSgfBranches = false;
      Lizzie.config.supplementCandidates = false;
      Lizzie.config.analyzePassValue = true;
      Lizzie.config.passValueVisits = 8;
      Lizzie.config.limitPlayout = true;
      Lizzie.config.limitPlayouts = 20;
      Lizzie.config.limitTime = false;
      Lizzie.config.stopAtEmptyBoard = false;
      Lizzie.config.analyzeBlack = Lizzie.config.analyzeWhite = true;
      Lizzie.config.playSound = false;
      Lizzie.config.analysisDatabasePath = database.toString();
      Lizzie.config.analysisDatabaseRead = Lizzie.config.analysisDatabaseWrite = true;
      Lizzie.config.analysisDatabaseSymmetries = Lizzie.config.analysisDatabaseSkipSatisfied = true;
      Lizzie.config.analysisDatabaseMaxMoves = 20;
      Lizzie.config.analysisDatabaseModel = Lizzie.config.analysisDatabaseConfig = "";
      SGFParser.loadFromString("(;GM[1]FF[4]SZ[19]KM[7.5])");
    });
    engine = new Leelaz("\"" + System.getProperty("katago.binary") + "\" gtp -model \""
        + System.getProperty("katago.model") + "\" -config \""
        + System.getProperty("katago.config") + "\"");
    Lizzie.leelaz = engine;
    engine.startEngine(-1);
    await(() -> engine.isLoaded() && !engine.recentRulesLine.isEmpty() && engine.isResponseUpToDate());
    SwingUtilities.invokeAndWait(() -> {
      engine.komi(7.5);
      Lizzie.config.analysisDatabaseEnabled = true;
    });
  }

  @After public void stop() throws Exception {
    Lizzie.config.analysisDatabaseEnabled = false;
    if (engine != null) engine.normalQuit();
    AnalysisDatabase.IO.submit(() -> {}).get(10, TimeUnit.SECONDS);
  }

  private static void await(BooleanSupplier condition) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < end) {
      final boolean[] ready = {false};
      SwingUtilities.invokeAndWait(() -> ready[0] = condition.getAsBoolean());
      if (ready[0]) return;
      Thread.sleep(20);
    }
    fail("Timed out waiting for real engine/UI workflow");
  }

  private void complete() throws Exception {
    SwingUtilities.invokeAndWait(() -> engine.ponder());
    await(() -> !engine.isPondering() && Lizzie.board.getData().getPlayouts() >= 20
        && Lizzie.board.getData().passAnalysisVisits >= 8);
    AnalysisDatabase.IO.submit(() -> {}).get(10, TimeUnit.SECONDS);
  }

  private void load(String sgf) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      assertTrue(SGFParser.loadFromString(sgf));
      while (Lizzie.board.nextMove(false)) {}
      Lizzie.frame.refresh();
    });
  }

  @Test public void stoppedNavigationRestoresOrdinaryAndPassWithoutPondering() throws Exception {
    load("(;GM[1]FF[4]SZ[19]KM[7.5];B[dd];W[pq])");
    complete();
    load("(;GM[1]FF[4]SZ[19]KM[7.5];B[dd];W[pq])");
    await(() -> Lizzie.board.getData().databaseNormalVisits >= 20
        && Lizzie.board.getData().databasePassVisits >= 8);
    assertFalse(engine.isPondering());
    assertTrue(Double.isFinite(Lizzie.board.getData().passAnalysisScore));
  }

  @Test public void lookupStartedBeforeBoardCommitDoesNotStrandAnalysis() throws Exception {
    complete();
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    Future<?> blocker = AnalysisDatabase.IO.submit(() -> {
      entered.countDown();
      try { release.await(10, TimeUnit.SECONDS); }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    });
    assertTrue(entered.await(5, TimeUnit.SECONDS));
    try {
      SwingUtilities.invokeAndWait(() -> {
        engine.ponder();
        Lizzie.board.place(3, 3);
      });
    } finally { release.countDown(); }
    blocker.get(10, TimeUnit.SECONDS);
    await(() -> Lizzie.board.getData().moveNumber == 1 && !engine.isPondering()
        && Lizzie.board.getData().getPlayouts() >= 20
        && Lizzie.board.getData().passAnalysisVisits >= 8);
  }

  @Test public void publicRotationAndMirrorRestoreTheFullFingerprint() throws Exception {
    load("(;GM[1]FF[4]SZ[19]KM[7.5];B[dd];W[pq])");
    complete();
    String candidate = Lizzie.board.getData().bestMoves.get(0).coordinate;
    for (int type : new int[] {1, 1, 1, 1, 3, 1, 1, 1}) {
      candidate = transform(candidate, type);
      SwingUtilities.invokeAndWait(() -> Lizzie.board.SpinAndMirror(type));
      await(() -> Lizzie.board.getData().databaseNormalVisits >= 20
          && Lizzie.board.getData().databasePassVisits >= 8);
      assertFalse(engine.isPondering());
      assertEquals(candidate, Lizzie.board.getData().bestMoves.get(0).coordinate);
      assertTrue(MoveValue.available(Lizzie.board.getData()));
      assertEquals(7.5, Lizzie.board.getHistory().getGameInfo().getKomi(), 0);
    }
  }

  private static String transform(String coordinate, int type) {
    if (coordinate.equalsIgnoreCase("pass")) return coordinate;
    String letters = "ABCDEFGHJKLMNOPQRST";
    int x = letters.indexOf(coordinate.charAt(0)), y = 19 - Integer.parseInt(coordinate.substring(1));
    return type == 1 ? letters.charAt(18 - y) + Integer.toString(19 - x)
        : letters.charAt(18 - x) + Integer.toString(19 - y);
  }

  @Test public void missingPassIsToppedUpWithoutReplacingOrdinaryResults() throws Exception {
    complete();
    int ordinary = Lizzie.board.getData().getPlayouts();
    int pass = Lizzie.board.getData().passAnalysisVisits;
    SwingUtilities.invokeAndWait(() -> {
      engine.clearPonderLimit();
      Lizzie.config.passValueVisits = pass + 16;
    });
    load("(;GM[1]FF[4]SZ[19]KM[7.5])");
    await(() -> Lizzie.board.getData().databasePassVisits >= pass);
    SwingUtilities.invokeAndWait(() -> engine.ponder());
    await(() -> !engine.isPondering() && Lizzie.board.getData().databasePassVisits >= pass + 16);
    assertEquals(ordinary, Lizzie.board.getData().getPlayouts());
    assertTrue(MoveValue.available(Lizzie.board.getData()));
  }

  @Test public void missingOrdinaryAnalysisReusesTheIndependentPassResult() throws Exception {
    complete();
    int ordinary = Lizzie.board.getData().getPlayouts();
    int pass = Lizzie.board.getData().passAnalysisVisits;
    double passScore = Lizzie.board.getData().passAnalysisScore;
    SwingUtilities.invokeAndWait(() -> {
      engine.clearPonderLimit();
      Lizzie.config.limitPlayouts = ordinary + 20;
    });
    load("(;GM[1]FF[4]SZ[19]KM[7.5])");
    await(() -> Lizzie.board.getData().databasePassVisits >= pass);
    SwingUtilities.invokeAndWait(() -> engine.ponder());
    await(() -> !engine.isPondering() && Lizzie.board.getData().databaseNormalVisits >= ordinary + 20);
    assertEquals(pass, Lizzie.board.getData().passAnalysisVisits);
    assertEquals(passScore, Lizzie.board.getData().passAnalysisScore, 0);
  }

  @Test public void consecutiveMovesAndUndoRestorePassValues() throws Exception {
    complete();
    for (int[] point : new int[][] {{3, 3}, {15, 16}, {16, 3}}) {
      SwingUtilities.invokeAndWait(() -> Lizzie.board.place(point[0], point[1]));
      await(() -> !engine.isPondering() && Lizzie.board.getData().databaseNormalVisits >= 20
          && Lizzie.board.getData().databasePassVisits >= 8);
      assertTrue(MoveValue.available(Lizzie.board.getData()));
    }
    for (int i = 0; i < 3; i++) {
      SwingUtilities.invokeAndWait(() -> {
        assertTrue(Lizzie.board.previousMove(true));
        Lizzie.board.getData().invalidateAnalysisCache();
        Lizzie.frame.refresh();
      });
      await(() -> Lizzie.board.getData().databaseNormalVisits >= 20
          && Lizzie.board.getData().databasePassVisits >= 8);
      assertFalse(engine.isPondering());
      assertTrue(MoveValue.available(Lizzie.board.getData()));
    }
  }

  @Test public void disablingReadsAlsoPreventsRestoringAnOlderLargerRowAfterSave() throws Exception {
    complete();
    // A completed new search must not implicitly read an older, higher-visit cache.
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + database);
         Statement s = c.createStatement();
         ResultSet rows = s.executeQuery("SELECT payload FROM analysis WHERE kind='normal'")) {
      assertTrue(rows.next());
      org.json.JSONObject payload = new org.json.JSONObject(rows.getString(1));
      payload.put("visits", 1000000);
      try (PreparedStatement update = c.prepareStatement(
          "UPDATE analysis SET visits=1000000,payload=? WHERE kind='normal'")) {
        update.setString(1, payload.toString());
        update.executeUpdate();
      }
    }
    SwingUtilities.invokeAndWait(() -> {
      Lizzie.config.analysisDatabaseRead = false;
      engine.clearPonderLimit();
    });
    load("(;GM[1]FF[4]SZ[19]KM[7.5])");
    complete();
    SwingUtilities.invokeAndWait(() -> {});
    assertTrue(Lizzie.board.getData().getPlayouts() < 1000000);
    assertEquals(0, Lizzie.board.getData().databaseNormalVisits);
  }

  @Test public void completedSaveImmediatelyShowsTheDurableCache() throws Exception {
    complete();
    await(() -> Lizzie.board.getData().databaseNormalVisits >= 20
        && Lizzie.board.getData().databasePassVisits >= 8
        && !Lizzie.board.getData().databaseStatus.isEmpty());
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + database);
         Statement s = c.createStatement(); ResultSet rows = s.executeQuery("SELECT count(*) FROM analysis")) {
      assertTrue(rows.next());
      assertEquals(2, rows.getInt(1));
    }
  }
}
