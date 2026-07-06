package org.ironsight.CubeArray.swing;

import java.awt.*;
import java.io.File;
import java.util.Optional;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Modal dialog shown after the block-replacement mapping is confirmed. Lets the user choose:
 *
 * <ul>
 *   <li>A postfix string appended to each output filename (default: {@code "_replaced"})
 *   <li>Optionally, a target folder to write all output files into (instead of each file's own
 *       directory)
 * </ul>
 *
 * <p>A live example label shows {@code <firstBaseName><postfix>.schem} and updates as the user
 * types. If the folder checkbox is ticked, a {@link JFileChooser} is shown immediately; if the user
 * cancels the picker the checkbox is unticked again.
 *
 * <p>Call {@link #show(Component, File)} and inspect the returned {@link Options} record.
 */
public class OutputOptionsDialog extends JDialog {

  /**
   * The result of a confirmed dialog.
   *
   * @param affix the string to prepend or append to each base filename (may be empty)
   * @param isPrefix {@code true} → prepend, {@code false} → append (suffix)
   * @param outputFolder the folder to write outputs into, or {@code null} to use each file's own
   *     parent directory
   */
  public record Options(String affix, boolean isPrefix, File outputFolder) {

    /**
     * Computes the output {@link File} for a given source file using these options.
     *
     * @param sourceFile the original schematic file
     * @return the resolved output file path
     */
    public File outputFileFor(File sourceFile) {
      String name = sourceFile.getName();
      String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
      String outName = isPrefix ? affix + base + ".schem" : base + affix + ".schem";
      File dir = outputFolder != null ? outputFolder : sourceFile.getParentFile();
      return new File(dir, outName);
    }
  }

  private final JTextField postfixField = new JTextField("_replaced", 20);
  private final JButton toggleBtn = new JButton("Suffix");
  private final JLabel exampleLabel = new JLabel();
  private final JCheckBox folderCheckBox = new JCheckBox("Group new files into folder");
  private final JLabel folderLabel = new JLabel("(same folder as source)");

  private final File firstFile;
  private File chosenFolder = null;
  private boolean confirmed = false;
  private boolean isPrefix = false; // false = suffix (default)

  private OutputOptionsDialog(Frame owner, File firstFile) {
    super(owner, "Output options", true);
    this.firstFile = firstFile;

    setLayout(new BorderLayout(8, 8));
    getRootPane().setBorder(new EmptyBorder(12, 12, 12, 12));

    add(buildForm(), BorderLayout.CENTER);
    add(buildButtonPanel(), BorderLayout.SOUTH);

    updateExample();

    pack();
    setMinimumSize(new Dimension(460, getHeight()));
    setResizable(false);
    setLocationRelativeTo(owner);
  }

  // -------------------------------------------------------------------------
  // UI construction
  // -------------------------------------------------------------------------

  private JPanel buildForm() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints lbl = new GridBagConstraints();
    lbl.gridx = 0;
    lbl.gridy = 0;
    lbl.anchor = GridBagConstraints.WEST;
    lbl.insets = new Insets(0, 0, 6, 8);

    GridBagConstraints fld = new GridBagConstraints();
    fld.gridx = 1;
    fld.gridy = 0;
    fld.fill = GridBagConstraints.HORIZONTAL;
    fld.weightx = 1.0;
    fld.insets = new Insets(0, 0, 6, 4);

    GridBagConstraints btn = new GridBagConstraints();
    btn.gridx = 2;
    btn.gridy = 0;
    btn.anchor = GridBagConstraints.WEST;
    btn.insets = new Insets(0, 0, 6, 0);

    // Row 0 — label + text field + toggle button
    JLabel affixLabel = new JLabel("Filename suffix:");
    panel.add(affixLabel, lbl);
    panel.add(postfixField, fld);

    toggleBtn.setToolTipText("Toggle between prefix and suffix mode");
    toggleBtn.addActionListener(
        e -> {
          isPrefix = !isPrefix;
          toggleBtn.setText(isPrefix ? "Prefix" : "Suffix");
          affixLabel.setText(isPrefix ? "Filename prefix:" : "Filename suffix:");
          updateExample();
        });
    panel.add(toggleBtn, btn);

    // Row 1 — live example
    lbl.gridy = 1;
    fld.gridy = 1;
    lbl.insets = new Insets(0, 0, 10, 8);
    fld.insets = new Insets(0, 0, 10, 0);
    panel.add(new JLabel("Example:"), lbl);
    exampleLabel.setFont(exampleLabel.getFont().deriveFont(Font.ITALIC));
    panel.add(exampleLabel, fld);

    // Row 2 — folder checkbox (spans both columns)
    GridBagConstraints full = new GridBagConstraints();
    full.gridx = 0;
    full.gridy = 2;
    full.gridwidth = 2;
    full.anchor = GridBagConstraints.WEST;
    full.insets = new Insets(0, 0, 4, 0);
    panel.add(folderCheckBox, full);

    // Row 3 — folder path label (spans both columns)
    full.gridy = 3;
    full.insets = new Insets(0, 20, 0, 0);
    folderLabel.setFont(folderLabel.getFont().deriveFont(Font.ITALIC));
    panel.add(folderLabel, full);

    // Live example updates on every keystroke
    postfixField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                updateExample();
              }

              public void removeUpdate(DocumentEvent e) {
                updateExample();
              }

              public void changedUpdate(DocumentEvent e) {}
            });

    // Folder checkbox — open picker when ticked, clear when unticked
    folderCheckBox.addActionListener(
        e -> {
          if (folderCheckBox.isSelected()) {
            File picked = pickFolder();
            if (picked != null) {
              chosenFolder = picked;
              folderLabel.setText(picked.getAbsolutePath());
            } else {
              folderCheckBox.setSelected(false);
            }
          } else {
            chosenFolder = null;
            folderLabel.setText("(same folder as source)");
          }
          updateExample();
        });

    return panel;
  }

  private JPanel buildButtonPanel() {
    JButton okBtn = new JButton("OK");
    JButton cancelBtn = new JButton("Cancel");

    okBtn.addActionListener(
        e -> {
          confirmed = true;
          dispose();
        });
    cancelBtn.addActionListener(e -> dispose());

    getRootPane().setDefaultButton(okBtn);

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    panel.add(okBtn);
    panel.add(cancelBtn);
    return panel;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private void updateExample() {
    if (firstFile == null) {
      exampleLabel.setText("");
      return;
    }

    String name = firstFile.getName();
    String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
    String affix = postfixField.getText();
    String outName = isPrefix ? affix + base + ".schem" : base + affix + ".schem";

    File dir = chosenFolder != null ? chosenFolder : firstFile.getParentFile();
    String dirPath = dir != null ? dir.getAbsolutePath() + File.separator : "";

    exampleLabel.setText(dirPath + outName);
    pack(); // resize if the example text grows
  }

  private File pickFolder() {
    JFileChooser chooser = new JFileChooser(firstFile != null ? firstFile.getParentFile() : null);
    chooser.setDialogTitle("Select output folder");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
        ? chooser.getSelectedFile()
        : null;
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  /**
   * Opens the dialog modally.
   *
   * @param parent parent component (may be {@code null})
   * @param firstFile the first source file — used to build the live example; may be {@code null} if
   *     no files are selected
   * @return the chosen options, or {@link Optional#empty()} if the user cancelled
   */
  public static Optional<Options> show(Component parent, File firstFile) {
    Frame owner =
        parent == null
            ? null
            : (parent instanceof Frame f
                ? f
                : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent));

    OutputOptionsDialog dialog = new OutputOptionsDialog(owner, firstFile);
    dialog.setVisible(true); // blocks until dispose()

    if (!dialog.confirmed) return Optional.empty();
    return Optional.of(
        new Options(dialog.postfixField.getText(), dialog.isPrefix, dialog.chosenFolder));
  }
}
