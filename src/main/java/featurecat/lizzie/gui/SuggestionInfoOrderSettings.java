package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Four-item ordering editor. Preview and board share the same label renderer. */
public class SuggestionInfoOrderSettings extends JDialog {
  private final JFontCheckBox[] enabled = new JFontCheckBox[4];
  private final JFontRadioButton[][] choices = new JFontRadioButton[4][4];
  // Indexed by metric: winrate, visits, score, value. Values are the one-based row order.
  private final int[] order = {Lizzie.config.suggestionInfoWinrate,
      Lizzie.config.suggestionInfoPlayouts, Lizzie.config.suggestionInfoScoreLead,
      Lizzie.config.suggestionInfoValue};
  private final JPanel previewPanel;

  public SuggestionInfoOrderSettings(Window owner) {
    super(owner);
    setTitle(text("title"));
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setResizable(false);
    getContentPane().setLayout(new BorderLayout());
    previewPanel = new JPanel() {
      @Override public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        paintPreview((Graphics2D) graphics);
      }
    };
    previewPanel.setPreferredSize(new Dimension(580, 190));
    getContentPane().add(previewPanel, BorderLayout.NORTH);

    String[] names = {text("rdoWinrate"), text("rdoPlayouts"), text("rdoScoreLead"), text("rdoValue")};
    String[] rowNames = {text("lblFirstRow"), text("lblSecondRow"),
        text("lblThirdRow"), text("lblFourthRow")};
    boolean[] visible = {Lizzie.config.showWinrateInSuggestion,
        Lizzie.config.showPlayoutsInSuggestion, Lizzie.config.showScoremeanInSuggestion,
        Lizzie.config.showMoveValue};
    JPanel matrix = new JPanel(new GridLayout(5, 5, 8, 8));
    matrix.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
    matrix.add(new JFontLabel(text("lblSuggestionInfo")));
    for (int metric = 0; metric < 4; metric++) {
      enabled[metric] = new JFontCheckBox(names[metric]);
      enabled[metric].setSelected(visible[metric]);
      enabled[metric].addActionListener(e -> previewPanel.repaint());
      matrix.add(enabled[metric]);
    }
    boolean[] used = new boolean[4];
    for (int value : order) {
      if (value < 1 || value > 4 || used[value - 1]) {
        for (int i = 0; i < 4; i++) order[i] = i + 1;
        break;
      }
      used[value - 1] = true;
    }
    for (int row = 0; row < 4; row++) {
      matrix.add(new JFontLabel(rowNames[row]));
      ButtonGroup group = new ButtonGroup();
      for (int metric = 0; metric < 4; metric++) {
        final int selectedRow = row, selectedMetric = metric;
        JFontRadioButton choice = new JFontRadioButton(names[metric]);
        choices[row][metric] = choice;
        group.add(choice);
        choice.addActionListener(e -> selectOrder(selectedRow, selectedMetric));
        matrix.add(choice);
      }
    }
    updateChoices();
    getContentPane().add(matrix, BorderLayout.CENTER);
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
    JFontButton ok = new JFontButton(text("okButton"));
    ok.setPreferredSize(new Dimension(100, 28));
    ok.addActionListener(e -> applySettings());
    actions.add(ok);
    JFontButton cancel = new JFontButton(text("cancelButton"));
    cancel.setPreferredSize(new Dimension(100, 28));
    cancel.addActionListener(e -> dispose());
    actions.add(cancel);
    getContentPane().add(actions, BorderLayout.SOUTH);
    getRootPane().setDefaultButton(ok);
    try {
      setIconImage(ImageIO.read(MoreEngines.class.getResourceAsStream("/assets/logo.png")));
    } catch (IOException error) { error.printStackTrace(); }
    pack();
    setLocationRelativeTo(owner);
  }

  private String text(String key) {
    return Lizzie.resourceBundle.getString("SuggestionInfoOrderSettings." + key);
  }

  /** Selecting a used item swaps its previous row, keeping the four orders unique. */
  private void selectOrder(int row, int metric) {
    int previous = order[metric];
    for (int i = 0; i < 4; i++) if (order[i] == row + 1) order[i] = previous;
    order[metric] = row + 1;
    updateChoices();
    previewPanel.repaint();
  }

  private void updateChoices() {
    for (int row = 0; row < 4; row++)
      for (int metric = 0; metric < 4; metric++)
        choices[row][metric].setSelected(order[metric] == row + 1);
  }

  private void applySettings() {
    boolean startPass = enabled[3].isSelected() && !Lizzie.config.analyzePassValue;
    Lizzie.config.showWinrateInSuggestion = enabled[0].isSelected();
    Lizzie.config.showPlayoutsInSuggestion = enabled[1].isSelected();
    Lizzie.config.showScoremeanInSuggestion = enabled[2].isSelected();
    Lizzie.config.showMoveValue = enabled[3].isSelected();
    if (Lizzie.config.showMoveValue) Lizzie.config.analyzePassValue = true;
    String[] keys = {"show-winrate-in-suggestion", "show-playouts-in-suggestion",
        "show-scoremean-in-suggestion", "show-move-value"};
    for (int i = 0; i < 4; i++) Lizzie.config.uiConfig.put(keys[i], enabled[i].isSelected());
    Lizzie.config.uiConfig.put("analyze-pass-value", Lizzie.config.analyzePassValue);
    Lizzie.config.setSuggestionInfoOrdr(order[0], order[1], order[2], order[3]);
    if (startPass && Lizzie.leelaz != null) Lizzie.leelaz.refreshSgfBranchAnalysis();
    if (LizzieFrame.menu != null) LizzieFrame.menu.refreshDoubleMoveInfoStatus();
    if (Lizzie.frame.configDialog2 != null && Lizzie.frame.configDialog2.isVisible())
      Lizzie.frame.configDialog2.setChkSuggestionInfo();
    if (Lizzie.firstUseSettings != null && Lizzie.firstUseSettings.isVisible())
      Lizzie.firstUseSettings.setChkSuggestionInfo();
    dispose();
    Lizzie.frame.refresh();
  }

  // Leela cannot report score or pass value; hidden items do not leave empty rows.
  List<SuggestionLabels.Row> previewRows(boolean kata) {
    String[] values = kata ? new String[] {"68.5", "1.3k", "+0.7", "+11.3"}
        : new String[] {"46.5", "730", "", ""};
    List<SuggestionLabels.Row> rows = new ArrayList<>();
    for (int i = 0; i < (kata ? 4 : 2); i++) {
      if (enabled[i].isSelected()) rows.add(new SuggestionLabels.Row(values[i],
          i == 1 ? LizzieFrame.playoutsFont : LizzieFrame.winrateFont,
          kata && i < 3 ? Color.RED : Color.BLACK, order[i]));
    }
    rows.sort(Comparator.comparingInt(row -> row.order));
    return rows;
  }

  private void paintPreview(Graphics2D graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      int width = previewPanel.getWidth(), height = previewPanel.getHeight();
      if (Lizzie.config.usePureBoard) g.setColor(Lizzie.config.pureBoardColor);
      else {
        BufferedImage board = Lizzie.config.theme.board();
        g.setPaint(new TexturePaint(board, new Rectangle(0, 0, board.getWidth(), board.getHeight())));
      }
      g.fillRect(0, 0, width, height);
      g.setColor(Color.BLACK);
      g.setFont(new Font(Config.sysDefaultFontName, Font.BOLD, 25));
      g.drawString(text("preview"), 12, 116);
      int kataX = width * 2 / 5, leelaX = width * 4 / 5;
      g.drawString("KataGo", kataX - g.getFontMetrics().stringWidth("KataGo") / 2, 32);
      g.drawString("Leela", leelaX - g.getFontMetrics().stringWidth("Leela") / 2, 32);
      int radius = 40, y = 112;
      for (int i = 0; i < 2; i++) {
        int x = i == 0 ? kataX : leelaX;
        g.setColor(i == 0 ? new Color(0, 217, 240) : new Color(11, 227, 0));
        g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        if (i == 0) {
          g.setColor(Color.BLUE);
          g.setStroke(new BasicStroke(2));
          g.drawOval(x - radius, y - radius, radius * 2, radius * 2);
        }
        SuggestionLabels.draw(g, previewRows(i == 0), x, y, radius);
      }
    } finally { g.dispose(); }
  }
}
