package featurecat.lizzie.gui;

import featurecat.lizzie.Lizzie;
import java.io.IOException;
import java.text.ParseException;
import java.util.ResourceBundle;
import javax.swing.*;

/** Settings shared by candidate top-up, pass analysis, and the candidate value display. */
public final class SupplementalSettings {
  private SupplementalSettings() {}

  public static void show() {
    ResourceBundle text = Lizzie.resourceBundle;
    JCheckBox candidates = new JCheckBox(text.getString("Supplemental.candidates.enabled"),
        Lizzie.config.supplementCandidates);
    JSpinner candidateVisits = new JSpinner(new SpinnerNumberModel(
        Lizzie.config.candidateMinVisits, 1, Integer.MAX_VALUE, 50));
    JCheckBox pass = new JCheckBox(text.getString("Supplemental.pass.enabled"),
        Lizzie.config.analyzePassValue);
    JSpinner passVisits = new JSpinner(new SpinnerNumberModel(
        Lizzie.config.passValueVisits, 1, Integer.MAX_VALUE, 100));
    JCheckBox value = new JCheckBox(text.getString("Supplemental.value.enabled"),
        Lizzie.config.showMoveValue);
    value.addActionListener(e -> { if (value.isSelected()) pass.setSelected(true); });
    pass.addActionListener(e -> { if (!pass.isSelected()) value.setSelected(false); });
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(candidates);
    panel.add(new JLabel(text.getString("Supplemental.candidates.visits")));
    panel.add(candidateVisits);
    panel.add(pass);
    panel.add(new JLabel(text.getString("Supplemental.pass.visits")));
    panel.add(passVisits);
    panel.add(value);
    panel.add(new JLabel(text.getString("Supplemental.priority")));
    if (JOptionPane.showConfirmDialog(Lizzie.frame, panel, text.getString("Supplemental.title"),
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
    try {
      candidateVisits.commitEdit();
      passVisits.commitEdit();
    } catch (ParseException error) {
      JOptionPane.showMessageDialog(Lizzie.frame, text.getString("Supplemental.invalid"));
      return;
    }
    Lizzie.config.supplementCandidates = candidates.isSelected();
    Lizzie.config.candidateMinVisits = ((Number) candidateVisits.getValue()).intValue();
    Lizzie.config.analyzePassValue = pass.isSelected();
    Lizzie.config.passValueVisits = ((Number) passVisits.getValue()).intValue();
    Lizzie.config.showMoveValue = value.isSelected();
    saveAndApply();
  }

  public static void saveAndApply() {
    if (Lizzie.config.showMoveValue) Lizzie.config.analyzePassValue = true;
    Lizzie.config.uiConfig.put("supplement-candidates", Lizzie.config.supplementCandidates);
    Lizzie.config.uiConfig.put("candidate-min-visits", Lizzie.config.candidateMinVisits);
    Lizzie.config.uiConfig.put("analyze-pass-value", Lizzie.config.analyzePassValue);
    Lizzie.config.uiConfig.put("pass-value-visits", Lizzie.config.passValueVisits);
    Lizzie.config.uiConfig.put("show-move-value", Lizzie.config.showMoveValue);
    try { Lizzie.config.save(); }
    catch (IOException error) {
      JOptionPane.showMessageDialog(Lizzie.frame,
          Lizzie.resourceBundle.getString("Supplemental.save.error") + error.getMessage());
    }
    if (Lizzie.leelaz != null) Lizzie.leelaz.refreshSgfBranchAnalysis();
    Lizzie.frame.refresh(1);
  }
}
