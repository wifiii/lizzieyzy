package featurecat.lizzie.gui;

import featurecat.lizzie.Config;
import featurecat.lizzie.Lizzie;
import featurecat.lizzie.analysis.database.AnalysisDatabase;
import java.nio.file.*;
import java.util.ResourceBundle;
import javax.swing.*;

public final class AnalysisDatabaseSettings {
  private AnalysisDatabaseSettings() {}

  public static void show() {
    Config c = Lizzie.config;
    ResourceBundle t = Lizzie.resourceBundle;
    JCheckBox enabled = new JCheckBox(t.getString("Database.enabled"), c.analysisDatabaseEnabled);
    JTextField path = new JTextField(c.analysisDatabasePath, 30);
    JSpinner moves = new JSpinner(new SpinnerNumberModel(c.analysisDatabaseMaxMoves, 0, 100000, 1));
    JCheckBox read = new JCheckBox(t.getString("Database.read"), c.analysisDatabaseRead);
    JCheckBox write = new JCheckBox(t.getString("Database.write"), c.analysisDatabaseWrite);
    JCheckBox symmetries =
        new JCheckBox(t.getString("Database.symmetries"), c.analysisDatabaseSymmetries);
    JCheckBox skip = new JCheckBox(t.getString("Database.skip"), c.analysisDatabaseSkipSatisfied);
    JTextField model = new JTextField(c.analysisDatabaseModel, 30);
    JTextField config = new JTextField(c.analysisDatabaseConfig, 30);
    JPanel form = new JPanel();
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.add(enabled);
    form.add(new JLabel(t.getString("Database.path")));
    form.add(filePicker(path, true));
    JButton reset = new JButton(t.getString("Database.defaultPath"));
    reset.addActionListener(e -> path.setText(Config.defaultAnalysisDatabasePath()));
    form.add(reset);
    form.add(new JLabel(t.getString("Database.moves")));
    form.add(moves);
    form.add(read);
    form.add(write);
    form.add(symmetries);
    form.add(skip);
    form.add(new JLabel(t.getString("Database.model")));
    form.add(filePicker(model, false));
    form.add(new JLabel(t.getString("Database.config")));
    form.add(filePicker(config, false));
    form.add(new JLabel(t.getString("Database.historyNote")));
    if (JOptionPane.showConfirmDialog(
            Lizzie.frame,
            form,
            t.getString("Database.title"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        != JOptionPane.OK_OPTION) return;
    try {
      moves.commitEdit();
      if (path.getText().trim().isEmpty())
        throw new IllegalArgumentException(t.getString("Database.emptyPath"));
      Paths.get(path.getText().trim());
      c.analysisDatabaseEnabled = enabled.isSelected();
      c.analysisDatabasePath = path.getText().trim();
      c.analysisDatabaseMaxMoves = ((Number) moves.getValue()).intValue();
      c.analysisDatabaseRead = read.isSelected();
      c.analysisDatabaseWrite = write.isSelected();
      c.analysisDatabaseSymmetries = symmetries.isSelected();
      c.analysisDatabaseSkipSatisfied = skip.isSelected();
      c.analysisDatabaseModel = model.getText().trim();
      c.analysisDatabaseConfig = config.getText().trim();
      save();
    } catch (Exception error) {
      JOptionPane.showMessageDialog(Lizzie.frame, error.getMessage());
    }
  }

  private static JPanel filePicker(JTextField field, boolean save) {
    JPanel row = new JPanel(new java.awt.BorderLayout(4, 0));
    row.add(field);
    JButton browse = new JButton("…");
    row.add(browse, java.awt.BorderLayout.EAST);
    browse.addActionListener(
        e -> {
          JFileChooser chooser = new JFileChooser();
          if (!field.getText().trim().isEmpty())
            chooser.setSelectedFile(new java.io.File(field.getText().trim()));
          int answer =
              save ? chooser.showSaveDialog(Lizzie.frame) : chooser.showOpenDialog(Lizzie.frame);
          if (answer == JFileChooser.APPROVE_OPTION)
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        });
    return row;
  }

  public static void save() throws java.io.IOException {
    Config c = Lizzie.config;
    c.uiConfig.put("analysis-database-enabled", c.analysisDatabaseEnabled);
    c.uiConfig.put("analysis-database-path", c.analysisDatabasePath);
    c.uiConfig.put("analysis-database-max-moves", c.analysisDatabaseMaxMoves);
    c.uiConfig.put("analysis-database-read", c.analysisDatabaseRead);
    c.uiConfig.put("analysis-database-write", c.analysisDatabaseWrite);
    c.uiConfig.put("analysis-database-symmetries", c.analysisDatabaseSymmetries);
    c.uiConfig.put("analysis-database-skip-satisfied", c.analysisDatabaseSkipSatisfied);
    c.uiConfig.put("analysis-database-model", c.analysisDatabaseModel);
    c.uiConfig.put("analysis-database-config", c.analysisDatabaseConfig);
    c.save();
    if (Lizzie.leelaz != null) Lizzie.leelaz.databaseSettingsChanged();
  }

  public static void clear() {
    ResourceBundle t = Lizzie.resourceBundle;
    if (JOptionPane.showConfirmDialog(
            Lizzie.frame,
            t.getString("Database.clearConfirm"),
            t.getString("Database.title"),
            JOptionPane.YES_NO_OPTION)
        != JOptionPane.YES_OPTION) return;
    final Path path = Paths.get(Lizzie.config.analysisDatabasePath);
    AnalysisDatabase.IO.execute(
        () -> {
          try {
            AnalysisDatabase.clear(path);
            SwingUtilities.invokeLater(
                () -> {
                  if (Lizzie.leelaz != null) Lizzie.leelaz.databaseSettingsChanged();
                  JOptionPane.showMessageDialog(Lizzie.frame, t.getString("Database.cleared"));
                });
          } catch (Exception error) {
            SwingUtilities.invokeLater(
                () -> JOptionPane.showMessageDialog(Lizzie.frame, error.getMessage()));
          }
        });
  }
}
