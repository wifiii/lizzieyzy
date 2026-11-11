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
    Lizzie.config.enableLizzieCache = true;
    Lizzie.config.supplementCandidates = false;
    Lizzie.config.analyzePassValue = false;
    Lizzie.config.showMoveValue = false;
    Lizzie.config.suggestionInfoWinrate = 1;
    Lizzie.config.suggestionInfoPlayouts = 2;
    Lizzie.config.suggestionInfoScoreLead = 3;
    Lizzie.config.suggestionInfoValue = 4;
    Lizzie.config.analyzeSgfBranches = false;
    engine.isKatago = false;
    SGFParser.loadFromString("(;GM[1]FF[4]SZ[19](;B[aa])(;B[bb])(;B[]))");
    engine.isKatago = true;
    Lizzie.config.analyzeSgfBranches = true;
    Lizzie.config.sgfBranchMinVisits = 1000;
    Lizzie.config.limitMaxSuggestion = 1;
    Lizzie.config.minPlayoutRatioForStats = 0;
    Lizzie.config.showNoSuggCircle = false;
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

  private void completeCachedBranches() throws Exception {
    info(move("Q16", 5000, 0.6));
    info(move("A19", 1000, 0.5));
    info(move("B18", 1000, 0.4));
    info(move("pass", 1000, 0.3));
  }

  @Test public void cachedBranchVisitsSurviveLeavingAndRevisitingTheNode() throws Exception {
    completeCachedBranches();
    Object root = Lizzie.board.getHistory().getCurrentHistoryNode();
    assertTrue(Lizzie.board.nextMove(false));
    assertTrue(Lizzie.board.previousMove(false));
    assertSame(root, Lizzie.board.getHistory().getCurrentHistoryNode());
    commands.reset();
    info(move("Q16", 1, 0.6) + " info " + move("A19", 1, 0.5).replace("order 0", "order 1"));
    assertEquals(1000, find("A19").playouts);
    assertEquals(1000, find("B18").playouts);
    assertFalse(commands.toString("UTF-8").contains("allow B A19 1"));
  }

  @Test public void disablingCacheDoesNotRestorePreviousBranchVisits() throws Exception {
    completeCachedBranches();
    assertTrue(Lizzie.board.nextMove(false));
    assertTrue(Lizzie.board.previousMove(false));
    Lizzie.config.enableLizzieCache = false;
    commands.reset();
    info(move("Q16", 1, 0.6) + " info " + move("A19", 1, 0.5).replace("order 0", "order 1"));
    assertEquals(1, find("A19").playouts);
    assertTrue(commands.toString("UTF-8").contains("allow B A19 1"));
  }

  @Test public void explicitCacheClearReallyClearsSupplementalResults() throws Exception {
    completeCachedBranches();
    Lizzie.board.clearbestmoves();
    commands.reset();
    info(move("Q16", 1, 0.6) + " info " + move("A19", 1, 0.5).replace("order 0", "order 1"));
    assertEquals(1, find("A19").playouts);
    assertTrue(commands.toString("UTF-8").contains("allow B A19 1"));
  }

  @Test public void branchAnalysisHasPriorityOverPassAndNormalLimits() throws Exception {
    Lizzie.config.analyzePassValue = true;
    Lizzie.config.passValueVisits = 2000;
    Lizzie.config.limitPlayout = true;
    Lizzie.config.limitPlayouts = 10000;
    set("searchSliceStarted", 0L);
    info(move("Q16", 50, 0.6));
    assertTrue(commands.toString("UTF-8").contains("allow B A19 1"));
    assertFalse(commands.toString("UTF-8").contains("allow B pass 1"));
    assertFalse(commands.toString("UTF-8").contains("focus pass 1"));
    info(move("A19", 1000, 0.5));
    assertTrue(commands.toString("UTF-8").contains("allow B B18 1"));
    assertFalse(commands.toString("UTF-8").contains("allow B pass 1"));
    assertFalse(commands.toString("UTF-8").contains("focus pass 1"));
  }

  @Test public void symmetryCopiesOfVisibleSourcesRetainTheirStatistics() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.supplementCandidates = true;
    Lizzie.config.limitMaxSuggestion = 1;
    info(move("D4", 5000, 0.6) + " info " + move("Q16", 5000, 0.6)
        .replace("order 0", "order 15 isSymmetryOf D4"));
    assertEquals(5000, find("Q16").playouts);
    assertTrue(find("Q16").isSymmetry);
    assertEquals(0, find("Q16").displayOrder());
    assertEquals(5000, engine.getBestMovesPlayouts());
  }

  @Test public void candidateTopUpWaitsForOrdinaryLimitAndUsesEligibleMoves() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.supplementCandidates = true;
    Lizzie.config.limitMaxSuggestion = 2;
    Lizzie.config.candidateMinVisits = 200;
    Lizzie.config.limitPlayout = true;
    Lizzie.config.limitPlayouts = 10000;
    info(move("Q16", 80, 0.6) + " info " + move("T1", 1, 0.01).replace("order 0", "order 1"));
    assertFalse(commands.toString("UTF-8").contains("allow"));
    commands.reset();
    info(move("Q16", 10000, 0.6) + " info " + move("T1", 1, 0.01).replace("order 0", "order 1"));
    assertTrue(commands.toString("UTF-8").contains("allow B T1 1"));
    assertTrue(engine.isPondering());
    info(move("T1", 200, 0.01));
    assertFalse(engine.isPondering());
    assertEquals(200, find("T1").playouts);
    assertEquals(10001, engine.getBestMovesPlayouts());
  }

  @Test public void generalTopUpRunsAfterBranchMinimumAndPrimaryLimit() throws Exception {
    Lizzie.config.supplementCandidates = true;
    Lizzie.config.limitMaxSuggestion = 2;
    Lizzie.config.candidateMinVisits = 200;
    Lizzie.config.limitPlayout = true;
    Lizzie.config.limitPlayouts = 10000;
    info(move("Q16", 5000, 0.6) + " info " + move("T1", 1, 0.01).replace("order 0", "order 1"));
    assertTrue(commands.toString("UTF-8").contains("allow B A19 1"));
    info(move("A19", 1000, 0.5));
    info(move("B18", 1000, 0.4));
    info(move("pass", 1000, 0.3));
    assertFalse(commands.toString("UTF-8").contains("allow B T1 1"));
    info(move("Q16", 10000, 0.6) + " info " + move("T1", 1, 0.01).replace("order 0", "order 1"));
    assertTrue(commands.toString("UTF-8").contains("allow B T1 1"));
    info(move("T1", 200, 0.01));
    assertFalse(engine.isPondering());
  }

  @Test public void manualPauseCancelsCandidateTopUp() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.supplementCandidates = true;
    Lizzie.config.limitMaxSuggestion = 2;
    Lizzie.config.limitPlayout = true;
    Lizzie.config.limitPlayouts = 100;
    info(move("Q16", 100, 0.6) + " info " + move("T1", 1, 0.01).replace("order 0", "order 1"));
    engine.togglePonder();
    commands.reset();
    line("info " + move("T1", 200, 0.01));
    assertFalse(engine.isPondering());
    assertFalse(commands.toString("UTF-8").contains("allow"));
    assertEquals(1, find("T1").playouts);
  }

  @Test public void historicalAndOutOfLimitMovesAreNotToppedUpOrRestored() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.supplementCandidates = true;
    Lizzie.config.limitMaxSuggestion = 2;
    Lizzie.config.limitPlayout = true;
    Lizzie.config.limitPlayouts = 1000;
    info(move("Q16", 80, 0.6) + " info " + move("T1", 1, 0.01).replace("order 0", "order 1"));
    commands.reset();
    info(move("Q16", 1000, 0.6) + " info " + move("D4", 1, 0.5).replace("order 0", "order 1"));
    assertTrue(commands.toString("UTF-8").contains("allow B D4 1"));
    assertFalse(commands.toString("UTF-8").contains("allow B T1 1"));
    for (MoveData move : engine.getBestMoves()) assertFalse(move.coordinate.equals("T1"));
    info(move("D4", 200, 0.5));
    assertFalse(engine.isPondering());
    assertEquals(2, engine.getBestMoves().size());
  }

  @Test public void shrinkingCandidateLimitCancelsTheNowExcludedTarget() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.supplementCandidates = true;
    Lizzie.config.limitMaxSuggestion = 2;
    Lizzie.config.limitPlayout = true;
    Lizzie.config.limitPlayouts = 1000;
    info(move("Q16", 1000, 0.6) + " info " + move("T1", 1, 0.01).replace("order 0", "order 1"));
    assertTrue(commands.toString("UTF-8").contains("allow B T1 1"));
    Lizzie.config.limitMaxSuggestion = 1;
    commands.reset();
    info(move("T1", 200, 0.01));
    assertFalse(engine.isPondering());
    assertEquals(1, engine.getBestMoves().size());
    assertEquals("Q16", engine.getBestMoves().get(0).coordinate);
  }

  @Test public void aFullBoardEngineReportOnlyTopsUpTheLimitedCandidates() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.supplementCandidates = true;
    Lizzie.config.limitMaxSuggestion = 3;
    Lizzie.config.showNoSuggCircle = true;
    Lizzie.config.limitPlayout = true;
    Lizzie.config.limitPlayouts = 1000;
    StringBuilder report = new StringBuilder();
    for (int x = 0; x < 19; x++) for (int y = 0; y < 19; y++) {
      int rank = x * 19 + y;
      if (report.length() > 0) report.append(" info ");
      report.append(move(featurecat.lizzie.rules.Board.convertCoordinatesToName(x, y),
          rank == 0 ? 1000 : 1, 0.5).replace("order 0", "order " + rank));
    }
    info(report.toString());
    assertEquals(361, engine.getBestMoves().size());
    assertTrue(commands.toString("UTF-8").contains("allow B A18 1"));
    info(move("A18", 200, 0.5));
    assertTrue(commands.toString("UTF-8").contains("allow B A17 1"));
    info(move("A17", 200, 0.5));
    assertFalse(engine.isPondering());
    assertFalse(commands.toString("UTF-8").contains("allow B A16 1"));
    assertEquals(1, find("A16").playouts);
    assertEquals(361, engine.getBestMoves().size());
  }

  @Test public void passSearchAlternatesAndDoesNotCorruptNormalStatistics() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.analyzePassValue = true;
    Lizzie.config.passValueVisits = 1000;
    set("searchSliceStarted", 0L);
    info(move("Q16", 5000, 0.6).replace("scoreMean 2", "scoreMean 5"));
    assertTrue(commands.toString("UTF-8").contains("focus pass 1"));
    assertFalse(commands.toString("UTF-8").contains("allow"));
    set("searchSliceStarted", 0L);
    set("passSliceStarted", System.currentTimeMillis() - 600);
    info(move("Q16", 9000, 0.9).replace("scoreMean 2", "scoreMean 20")
        + " info " + move("pass", 250, 0.1).replace("scoreMean 2", "scoreMean -6.3"));
    assertEquals(250, Lizzie.board.getData().passAnalysisVisits);
    assertEquals(5.0, Lizzie.board.getData().scoreMean, 0);
    assertEquals(11.3, MoveValue.nextValue(Lizzie.board.getData().scoreMean,
        Lizzie.board.getData().passAnalysisScore), 0.0001);
    assertTrue(engine.isPondering());
    assertFalse(engine.getBestMoves().get(0).coordinate.equals("pass"));
    set("searchSliceStarted", 0L);
    info(move("Q16", 6000, 0.6).replace("scoreMean 2", "scoreMean 6"));
    info(move("pass", 1000, 0.1).replace("scoreMean 2", "scoreMean -5.3"));
    assertEquals(1000, Lizzie.board.getData().passAnalysisVisits);
    assertEquals(6.0, Lizzie.board.getData().scoreMean, 0);
    assertTrue(MoveValue.comment(Lizzie.board.getData()).contains("+11.3"));
    MoveData candidate = MoveData.fromInfoKatago(move("D4", 200, 0.5).replace("scoreMean 2", "scoreMean 4"));
    assertEquals("+9.3", MoveValue.candidateText(candidate, Lizzie.board.getData()));
  }

  @Test public void passProgressIsNotReusedAtAnotherPosition() throws Exception {
    passSearchAlternatesAndDoesNotCorruptNormalStatistics();
    assertTrue(Lizzie.board.nextMove(false));
    assertFalse(MoveValue.available(Lizzie.board.getData()));
  }

  @Test public void whiteToMoveUsesTheSameValueSignEvenInBlackWinrateDisplay() throws Exception {
    assertTrue(Lizzie.board.nextMove(false));
    assertFalse(Lizzie.board.getData().blackToPlay);
    boolean oldPerspective = Lizzie.config.winrateAlwaysBlack;
    try {
      Lizzie.config.winrateAlwaysBlack = true;
      Lizzie.config.analyzeSgfBranches = false;
      Lizzie.config.analyzePassValue = true;
      Lizzie.config.passValueVisits = 1000;
      engine.refreshSgfBranchAnalysis();
      set("searchSliceStarted", 0L);
      commands.reset();
      info(move("Q16", 5000, 0.6).replace("scoreMean 2", "scoreMean 5"));
      assertTrue(commands.toString("UTF-8").contains("focus pass 1"));
      assertFalse(commands.toString("UTF-8").contains("allow"));
      info(move("pass", 1000, 0.1).replace("scoreMean 2", "scoreMean -6.3"));
      assertEquals("+11.3", MoveValue.candidateText(find("Q16"), Lizzie.board.getData()));
    } finally { Lizzie.config.winrateAlwaysBlack = oldPerspective; }
  }

  @Test public void engineParameterChangeInvalidatesCachedPassValue() throws Exception {
    passSearchAlternatesAndDoesNotCorruptNormalStatistics();
    assertTrue(MoveValue.available(Lizzie.board.getData()));
    engine.sendCommand("kata-set-param analysisWideRootNoise 0.05");
    assertFalse(MoveValue.available(Lizzie.board.getData()));
  }

  @Test public void highVisitBadCandidateUsesThemeQualityNotVisitColor() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.limitMaxSuggestion = 10;
    java.util.Map<Double, java.awt.Color> colors = new java.util.HashMap<>();
    colors.put(-24.0, java.awt.Color.MAGENTA);
    colors.put(-12.0, java.awt.Color.RED);
    colors.put(3.0, java.awt.Color.GREEN);
    Lizzie.config.blunderWinrateThresholds = java.util.Optional.of(Arrays.asList(3.0, -24.0, -12.0));
    Lizzie.config.blunderNodeColors = java.util.Optional.of(colors);
    info(move("Q16", 8600, 0.533).replace("scoreMean 2", "scoreMean 0")
        + " info " + move("T1", 11000, 0.005).replace("order 0", "order 1").replace("scoreMean 2", "scoreMean -14.9"));
    java.awt.Color color = SuggestionStyle.color(find("T1"), engine.getBestMoves(), 11000);
    assertEquals(java.awt.Color.MAGENTA.getRGB(), color.getRGB());
    assertEquals(255, color.getAlpha());
  }

  @Test public void scoreLossStillAffectsColorWhenWinrateIsSaturated() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.limitMaxSuggestion = 10;
    java.util.Map<Double, java.awt.Color> colors = new java.util.HashMap<>();
    colors.put(-12.0, java.awt.Color.RED);
    colors.put(3.0, java.awt.Color.GREEN);
    Lizzie.config.blunderWinrateThresholds = java.util.Optional.of(Arrays.asList(-12.0, 3.0));
    Lizzie.config.blunderNodeColors = java.util.Optional.of(colors);
    Lizzie.config.useScoreDiffInVariationTree = true;
    Lizzie.config.scoreDiffInVariationTreeFactor = 0.5;
    info(move("Q16", 5000, 0.99).replace("scoreMean 2", "scoreMean 10")
        + " info " + move("T1", 1000, 0.989).replace("order 0", "order 1").replace("scoreMean 2", "scoreMean 4"));
    java.awt.Color color = SuggestionStyle.color(find("T1"), engine.getBestMoves(), 5000);
    assertEquals(java.awt.Color.RED.getRed(), color.getRed());
    assertEquals(0, color.getGreen());
    Lizzie.config.useScoreDiffInVariationTree = false;
    color = SuggestionStyle.color(find("T1"), engine.getBestMoves(), 5000);
    assertEquals(java.awt.Color.GREEN.getGreen(), color.getGreen());
  }

  @Test public void valueRowsAndCommentRenderWithoutEditingSgfComment() throws Exception {
    Lizzie.config.analyzeSgfBranches = false;
    Lizzie.config.limitMaxSuggestion = 10;
    info(move("Q16", 8600, 0.533).replace("scoreMean 2", "scoreMean 0")
        + " info " + move("T1", 11000, 0.005).replace("order 0", "order 1").replace("scoreMean 2", "scoreMean -14.9"));
    Lizzie.config.analyzePassValue = true;
    Lizzie.config.showMoveValue = true;
    Lizzie.config.showWinrateInSuggestion = true;
    Lizzie.config.showPlayoutsInSuggestion = true;
    Lizzie.config.showScoremeanInSuggestion = true;
    Lizzie.config.showComment = true;
    java.util.Map<Double, java.awt.Color> colors = new java.util.HashMap<>();
    colors.put(-24.0, java.awt.Color.MAGENTA);
    colors.put(3.0, java.awt.Color.GREEN);
    Lizzie.config.blunderWinrateThresholds = java.util.Optional.of(Arrays.asList(-24.0, 3.0));
    Lizzie.config.blunderNodeColors = java.util.Optional.of(colors);
    Lizzie.board.getData().passAnalysisScore = -11.3;
    Lizzie.board.getData().passAnalysisVisits = 1000;
    Lizzie.board.getData().passAnalysisEngine = engine.analysisContext();
    String originalComment = Lizzie.board.getData().comment;
    boolean appendedBefore = Lizzie.config.appendWinrateToComment;
    Lizzie.config.appendWinrateToComment = false;
    boolean wasEmpty = EngineManager.isEmpty;
    EngineManager.isEmpty = false;
    engine.isLoaded = true;
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        try {
          Lizzie.frame.setSize(1280, 900);
          Lizzie.frame.validate();
          Lizzie.frame.refresh(1);
          Method comment = Lizzie.frame.getClass().getDeclaredMethod("setComment", boolean.class);
          comment.setAccessible(true);
          comment.invoke(Lizzie.frame, false);
          Field area = Lizzie.frame.getClass().getDeclaredField("commentTextArea");
          area.setAccessible(true);
          Field pane = Lizzie.frame.getClass().getDeclaredField("commentTextPane");
          pane.setAccessible(true);
          String displayed = ((javax.swing.JLabel) area.get(Lizzie.frame)).getText()
              + ((javax.swing.JTextPane) pane.get(Lizzie.frame)).getText();
          assertTrue(displayed.contains("+11.3"));
          java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1280, 900,
              java.awt.image.BufferedImage.TYPE_INT_RGB);
          java.awt.Graphics2D graphics = image.createGraphics();
          Lizzie.frame.paint(graphics);
          graphics.dispose();
          javax.imageio.ImageIO.write(image, "png", new File("supplemental-value-ui.png"));
          int purplePixels = 0;
          for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++) {
            java.awt.Color color = new java.awt.Color(image.getRGB(x, y));
            if (color.getRed() > 220 && color.getBlue() > 220 && color.getGreen() < 30) purplePixels++;
          }
          assertTrue("Bad candidate was not painted with theme's purple color", purplePixels > 20);
          for (boolean withValue : new boolean[] {false, true}) {
            Lizzie.config.showMoveValue = withValue;
            Lizzie.config.showScoremeanInSuggestion = !withValue;
            Lizzie.frame.refresh(1);
            java.awt.image.BufferedImage threeRows = new java.awt.image.BufferedImage(1280, 900,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D threeGraphics = threeRows.createGraphics();
            Lizzie.frame.paint(threeGraphics);
            threeGraphics.dispose();
            javax.imageio.ImageIO.write(threeRows, "png", new File(withValue
                ? "supplemental-3row-value-ui.png" : "supplemental-3row-score-ui.png"));
          }
          Lizzie.config.showMoveValue = true;
          Lizzie.config.showScoremeanInSuggestion = true;
        } catch (Exception error) { throw new RuntimeException(error); }
      });
      assertEquals(originalComment, Lizzie.board.getData().comment);
      assertEquals("-3.6", MoveValue.candidateText(find("T1"), Lizzie.board.getData()));
    } finally {
      EngineManager.isEmpty = wasEmpty;
      Lizzie.config.appendWinrateToComment = appendedBefore;
    }
  }

  private Object dialogField(Object dialog, String name) throws Exception {
    Field field = dialog.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(dialog);
  }

  @SuppressWarnings("unchecked")
  private List<featurecat.lizzie.gui.SuggestionLabels.Row> previewRows(Object dialog, boolean kata)
      throws Exception {
    Method method = dialog.getClass().getDeclaredMethod("previewRows", boolean.class);
    method.setAccessible(true);
    return (List<featurecat.lizzie.gui.SuggestionLabels.Row>) method.invoke(dialog, kata);
  }

  private javax.swing.JButton dialogButton(java.awt.Container root, String text) {
    for (java.awt.Component component : root.getComponents()) {
      if (component instanceof javax.swing.JButton
          && text.equals(((javax.swing.JButton) component).getText()))
        return (javax.swing.JButton) component;
      if (component instanceof java.awt.Container) {
        javax.swing.JButton found = dialogButton((java.awt.Container) component, text);
        if (found != null) return found;
      }
    }
    return null;
  }

  @Test public void cancellingFourItemOrderEditorLeavesSettingsUntouched() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      featurecat.lizzie.gui.SuggestionInfoOrderSettings dialog =
          new featurecat.lizzie.gui.SuggestionInfoOrderSettings(Lizzie.frame);
      try {
        javax.swing.JCheckBox[] enabled = (javax.swing.JCheckBox[]) dialogField(dialog, "enabled");
        javax.swing.JRadioButton[][] choices =
            (javax.swing.JRadioButton[][]) dialogField(dialog, "choices");
        enabled[3].doClick();
        choices[0][3].doClick();
        assertEquals("+11.3", previewRows(dialog, true).get(0).text);
        dialogButton(dialog, Lizzie.resourceBundle.getString(
            "SuggestionInfoOrderSettings.cancelButton")).doClick();
        assertFalse(Lizzie.config.showMoveValue);
        assertFalse(Lizzie.config.analyzePassValue);
        assertEquals(4, Lizzie.config.suggestionInfoValue);
        assertEquals(1, Lizzie.config.suggestionInfoWinrate);
      } catch (Exception error) { throw new RuntimeException(error); }
      finally { dialog.dispose(); }
    });
  }

  @Test public void fourItemOrderEditorPreviewsPersistsAndRestoresValueOrder() throws Exception {
    java.nio.file.Path configPath = java.nio.file.Paths.get(Lizzie.config.getConfigFilePath());
    byte[] originalConfig = java.nio.file.Files.readAllBytes(configPath);
    try {
      javax.swing.SwingUtilities.invokeAndWait(() -> {
        java.util.ResourceBundle previous = Lizzie.resourceBundle;
        Lizzie.resourceBundle = java.util.ResourceBundle.getBundle("l10n.DisplayStrings", Locale.CHINA);
        featurecat.lizzie.gui.SuggestionInfoOrderSettings dialog =
            new featurecat.lizzie.gui.SuggestionInfoOrderSettings(Lizzie.frame);
        try {
          javax.swing.JCheckBox[] enabled = (javax.swing.JCheckBox[]) dialogField(dialog, "enabled");
          javax.swing.JRadioButton[][] choices =
              (javax.swing.JRadioButton[][]) dialogField(dialog, "choices");
          for (int i = 0; i < 3; i++) if (!enabled[i].isSelected()) enabled[i].doClick();
          enabled[3].doClick();
          assertEquals(4, previewRows(dialog, true).size());
          choices[0][3].doClick();
          assertTrue(choices[3][0].isSelected()); // displaced winrate moves to the fourth row
          assertEquals("+11.3", previewRows(dialog, true).get(0).text);
          assertEquals("68.5", previewRows(dialog, true).get(3).text);
          assertEquals("730", previewRows(dialog, false).get(0).text);
          assertEquals(2, previewRows(dialog, false).size());
          enabled[0].doClick();
          assertEquals(3, previewRows(dialog, true).size());
          assertEquals(1, previewRows(dialog, false).size());
          enabled[0].doClick();
          dialog.setVisible(true);
          dialog.validate();
          java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
              dialog.getWidth(), dialog.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
          java.awt.Graphics2D graphics = image.createGraphics();
          dialog.paint(graphics);
          graphics.dispose();
          javax.imageio.ImageIO.write(image, "png", new File("suggestion-order-4rows-ui.png"));
          dialogButton(dialog, Lizzie.resourceBundle.getString(
              "SuggestionInfoOrderSettings.okButton")).doClick();
          assertTrue(Lizzie.config.showMoveValue);
          assertTrue(Lizzie.config.analyzePassValue);
          assertEquals(1, Lizzie.config.suggestionInfoValue);
          assertEquals(4, Lizzie.config.suggestionInfoWinrate);
          featurecat.lizzie.gui.SuggestionInfoOrderSettings reopened =
              new featurecat.lizzie.gui.SuggestionInfoOrderSettings(Lizzie.frame);
          try {
            javax.swing.JRadioButton[][] saved =
                (javax.swing.JRadioButton[][]) dialogField(reopened, "choices");
            assertTrue(saved[0][3].isSelected());
            assertEquals("+11.3", previewRows(reopened, true).get(0).text);
          } finally { reopened.dispose(); }
        } catch (Exception error) { throw new RuntimeException(error); }
        finally { dialog.dispose(); Lizzie.resourceBundle = previous; }
      });
      org.json.JSONObject saved = new org.json.JSONObject(new String(
          java.nio.file.Files.readAllBytes(configPath), java.nio.charset.StandardCharsets.UTF_8))
          .getJSONObject("ui");
      assertEquals(1, saved.getInt("suggestion-info-value"));
      assertEquals(4, saved.getInt("suggestion-info-winrate"));
      assertTrue(saved.getBoolean("show-move-value"));
      assertTrue(saved.getBoolean("analyze-pass-value"));
    } finally { java.nio.file.Files.write(configPath, originalConfig); }
  }

  @Test public void realKataGoMeetsEveryBranchMinimum() throws Exception {
    runRealKataGo(false);
  }

  @Test public void realKataGoCompletesNormalPassBranchesAndCandidates() throws Exception {
    runRealKataGo(true);
  }

  private void runRealKataGo(boolean extended) throws Exception {
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
      if (extended) {
        Lizzie.config.analyzePassValue = true;
        Lizzie.config.passValueVisits = 8;
        Lizzie.config.supplementCandidates = true;
        Lizzie.config.candidateMinVisits = 2;
        Lizzie.config.limitPlayout = true;
        Lizzie.config.limitPlayouts = 20;
      }
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
          boolean allCandidates = true;
          if (extended) {
            for (MoveData move : moves) if (!move.coordinate.equalsIgnoreCase("pass")
                && move.playouts < 2) allCandidates = false;
          }
          if (met == 3 && (!extended || (allCandidates && !engine.isPondering()
              && Lizzie.board.getData().passAnalysisVisits >= 8))) {
            complete = true; break;
          }
        }
        Thread.sleep(100);
      }
      assertTrue("Real KataGo did not complete all SGF branches; inspect katago-test-stderr.log", complete);
      System.out.println(extended
          ? "Real KataGo: normal limit, pass value, all branches and limited candidates completed"
          : "Real KataGo: A19, B18 and pass each reached >=8 visits");
      engine.sendCommand("stop");
    } finally {
      process.destroy();
      if (reader != null) reader.join(5000);
    }
  }

}
