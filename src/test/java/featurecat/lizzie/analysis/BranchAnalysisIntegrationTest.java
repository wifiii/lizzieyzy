package featurecat.lizzie.analysis;

import static org.junit.Assert.*;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.rules.SGFParser;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;

/** Run with a graphical Java runtime and DISPLAY; no model or external engine is needed. */
public class BranchAnalysisIntegrationTest {
  private Leelaz engine;
  private ByteArrayOutputStream commands;

  @BeforeClass public static void startUi() throws Exception {
    Assume.assumeTrue(System.getenv("DISPLAY") != null);
    Lizzie.main(new String[0]);
    javax.swing.SwingUtilities.invokeAndWait(() -> {});
    Thread.sleep(1500);
  }

  private void set(String name, Object value) throws Exception {
    Field field = Leelaz.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(engine, value);
  }

  private void info(String moves) throws Exception {
    engine.setResponseUpToDate();
    line("info " + moves);
  }

  private void line(String text) throws Exception {
    Method parse = Leelaz.class.getDeclaredMethod("parseLine", String.class);
    parse.setAccessible(true);
    parse.invoke(engine, text);
  }

  private String move(String coord, int visits, double winrate) {
    return "move " + coord + " visits " + visits + " winrate " + winrate
        + " scoreMean 2 scoreStdev 1 prior 0.1 lcb 0.4 order 0 pv " + coord;
  }

  @Before public void setupPosition() throws Exception {
    engine = Lizzie.leelaz;
    Lizzie.config.analyzeSgfBranches = false;
    engine.isKatago = false;
    SGFParser.loadFromString("(;GM[1]FF[4]SZ[19](;B[aa])(;B[bb])(;B[]))");
    engine.isKatago = true;
    Lizzie.config.analyzeSgfBranches = true;
    Lizzie.config.sgfBranchMinVisits = 1000;
    Lizzie.config.limitMaxSuggestion = 1;
    Lizzie.config.limitPlayout = false;
    Lizzie.config.limitTime = false;
    Lizzie.config.stopAtEmptyBoard = false;
    commands = new ByteArrayOutputStream();
    set("outputStream", new BufferedOutputStream(commands));
    set("cmdQueue", new ArrayDeque<String>());
    set("cmdNumber", 1);
    set("currentCmdNum", 0);
    set("modifyNumber", 0);
    set("isPondering", true);
    engine.refreshSgfBranchAnalysis();
    commands.reset();
  }

  private MoveData find(String coord) {
    for (MoveData move : Lizzie.board.getData().bestMoves)
      if (coord.equalsIgnoreCase(move.coordinate)) return move;
    throw new AssertionError("Missing " + coord);
  }

  @Test public void fillsAllBranchesAndResumesWithoutCorruptingRootStats() throws Exception {
    info(move("Q16", 5000, 0.6));
    assertTrue(commands.toString("UTF-8").contains("allow B A19 1"));
    double winrate = Lizzie.board.getData().winrate;
    int total = Lizzie.board.getData().getPlayouts();
    info(move("A19", 300, 0.01));
    assertEquals(300, find("A19").playouts);
    assertEquals(winrate, Lizzie.board.getData().winrate, 0);
    info(move("A19", 1000, 0.01));
    assertTrue(commands.toString("UTF-8").contains("allow B B18 1"));
    info(move("B18", 1000, 0.02));
    assertTrue(commands.toString("UTF-8").contains("allow B pass 1"));
    info(move("pass", 1000, 0.03));
    assertEquals("Q16", engine.getBestMoves().get(0).coordinate);
    assertEquals(total, Lizzie.board.getData().getPlayouts());
    assertEquals(winrate, Lizzie.board.getData().winrate, 0);
    commands.reset();
    info(move("Q16", 6000, 0.61));
    assertEquals(1000, find("A19").playouts);
    assertEquals(1000, find("B18").playouts);
    assertEquals(1000, find("pass").playouts);
    assertFalse(commands.toString("UTF-8").contains("allow"));
    assertTrue(find("B18").order > 0);
  }

  @Test public void supplementaryBestCandidateDoesNotChangeRootStats() throws Exception {
    info(move("A19", 300, 0.6));
    double winrate = Lizzie.board.getData().winrate;
    double score = Lizzie.board.getData().scoreMean;
    info(move("A19", 1000, 0.01).replace("scoreMean 2", "scoreMean -40"));
    assertEquals(1.0, find("A19").winrate, 0.001);
    assertEquals(winrate, Lizzie.board.getData().winrate, 0);
    assertEquals(score, Lizzie.board.getData().scoreMean, 0);
    assertEquals(winrate, engine.getWinrateStats().maxWinrate, 0);
    assertEquals(score, engine.getWinrateStats().scoreLead, 0);
  }

  @Test public void disablingLeavesNormalAnalysis() throws Exception {
    info(move("Q16", 5000, 0.6));
    Lizzie.config.analyzeSgfBranches = false;
    engine.refreshSgfBranchAnalysis();
    commands.reset();
    info(move("Q16", 6000, 0.6));
    assertEquals(1, engine.getBestMoves().size());
    assertFalse(commands.toString("UTF-8").contains("allow"));
  }

  @Test public void commandCancellationRejectsLateOutput() throws Exception {
    info(move("Q16", 5000, 0.6));
    engine.sendCommand("stop");
    commands.reset();
    line("info " + move("A19", 3000, 0.01)); // unacknowledged command: stale output
    assertEquals("Q16", Lizzie.board.getData().bestMoves.get(0).coordinate);
    assertEquals(1, Lizzie.board.getData().bestMoves.size());
    assertFalse(commands.toString("UTF-8").contains("allow"));
  }

  @Test public void engineErrorRestoresNormalSearchWithoutRetryLoop() throws Exception {
    info(move("Q16", 5000, 0.6));
    line("? illegal move");
    commands.reset();
    info(move("Q16", 6000, 0.6));
    assertFalse(commands.toString("UTF-8").contains("allow"));
    assertEquals("Q16", engine.getBestMoves().get(0).coordinate);
  }
  @Test public void switchingPositionIgnoresOldBranchOutput() throws Exception {
    info(move("Q16", 5000, 0.6));
    assertTrue(Lizzie.board.nextMove(false));
    double before = Lizzie.board.getData().winrate;
    line("info " + move("A19", 3000, 0.01));
    assertEquals(before, Lizzie.board.getData().winrate, 0);
    commands.reset();
    info(move("Q16", 100, 0.55));
    assertFalse(commands.toString("UTF-8").contains("allow")); // new position is a leaf
  }

  @Test public void deletingActiveBranchStartsNextContinuation() throws Exception {
    info(move("Q16", 5000, 0.6));
    Lizzie.board.getHistory().getCurrentHistoryNode().variations.remove(0);
    commands.reset();
    info(move("A19", 300, 0.01));
    assertTrue(commands.toString("UTF-8").contains("allow B B18 1"));
    for (MoveData move : Lizzie.board.getData().bestMoves)
      assertFalse("Deleted branch result leaked", "A19".equals(move.coordinate));
  }

  @Test public void realKataGoMeetsEveryBranchMinimum() throws Exception {
    String binary = System.getProperty("katago.binary");
    Assume.assumeTrue(binary != null);
    Process process = new ProcessBuilder(binary, "gtp", "-model",
        System.getProperty("katago.model"), "-config", System.getProperty("katago.config"))
        .redirectError(new File("katago-test-stderr.log")).start();
    Thread reader = null;
    try {
      set("process", process);
      set("inputStream", new BufferedReader(new InputStreamReader(process.getInputStream())));
      set("outputStream", new BufferedOutputStream(process.getOutputStream()));
      set("cmdQueue", new ArrayDeque<String>());
      set("cmdNumber", 1);
      set("currentCmdNum", 0);
      engine.isLoaded = true;
      Lizzie.config.sgfBranchMinVisits = 8;
      Method read = Leelaz.class.getDeclaredMethod("read");
      read.setAccessible(true);
      reader = new Thread(() -> {
        try { read.invoke(engine); } catch (Exception e) { throw new RuntimeException(e); }
      });
      reader.setDaemon(true);
      reader.start();
      engine.ponder();
      long deadline = System.currentTimeMillis() + 90000;
      boolean complete = false;
      while (System.currentTimeMillis() < deadline && process.isAlive()) {
        synchronized (engine) {
          List<MoveData> moves = new ArrayList<>(engine.getBestMoves());
          int met = 0;
          for (String target : Arrays.asList("A19", "B18", "pass")) {
            for (MoveData move : moves)
              if (target.equalsIgnoreCase(move.coordinate) && move.playouts >= 8) met++;
          }
          if (met == 3) { complete = true; break; }
        }
        Thread.sleep(100);
      }
      assertTrue("Real KataGo did not complete all SGF branches; inspect katago-test-stderr.log", complete);
      System.out.println("Real KataGo: A19, B18 and pass each reached >=8 visits");
      engine.sendCommand("stop");
    } finally {
      process.destroy();
      if (reader != null) reader.join(5000);
    }
  }

}
