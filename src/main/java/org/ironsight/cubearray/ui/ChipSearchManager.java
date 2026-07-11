package org.ironsight.cubearray.ui;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class ChipSearchManager {

  public record SearchCondition(CaColumn column, String searchTerm) {}

  private static final List<CaColumn> SEARCHABLE_COLUMNS =
      Arrays.stream(CaColumn.values())
          .filter(c -> c != CaColumn.ICON)
          .sorted(Comparator.comparing((CaColumn c) -> c.displayName.toLowerCase()))
          .toList();

  private final List<SearchCondition> conditions = new ArrayList<>();
  private final JTextField searchField;
  private final JPanel chipRow;
  private final Runnable onFilterChanged;
  private final Window parentWindow;

  public ChipSearchManager(
      JTextField searchField, JPanel chipRow, Runnable onFilterChanged, Window parentWindow) {
    this.searchField = searchField;
    this.chipRow = chipRow;
    this.onFilterChanged = onFilterChanged;
    this.parentWindow = parentWindow;
  }

  public List<SearchCondition> getConditions() {
    return conditions;
  }

  public boolean hasConditions() {
    return !conditions.isEmpty();
  }

  public JButton createAddConditionButton() {
    JButton btn = new JButton();
    btn.setToolTipText("Add search term as column-specific condition");
    btn.setIcon(
        new Icon() {
          @Override
          public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(c.isEnabled() ? new Color(60, 60, 60) : new Color(180, 180, 180));
            int cx = x + 9, cy = y + 8, r = 9;
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            int hx = cx + (int) (r * 0.7), hy = cy + (int) (r * 0.7);
            int hx2 = hx + 8, hy2 = hy + 8;
            g2.drawLine(hx, hy, hx2, hy2);
            g2.dispose();
          }

          @Override
          public int getIconWidth() {
            return 28;
          }

          @Override
          public int getIconHeight() {
            return 28;
          }
        });
    btn.setEnabled(false);
    btn.addActionListener(e -> showAddConditionDialog());

    searchField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                toggle();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                toggle();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                toggle();
              }

              private void toggle() {
                btn.setEnabled(!searchField.getText().trim().isEmpty());
              }
            });

    return btn;
  }

  private void showAddConditionDialog() {
    String searchText = searchField.getText().trim();
    if (searchText.isEmpty()) return;

    JDialog dialog =
        new JDialog(parentWindow, "Add Search Condition", Dialog.ModalityType.APPLICATION_MODAL);

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(8, 8, 8, 8);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    JLabel searchTextLabel = new JLabel("\u201C" + searchText + "\u201D");
    searchTextLabel.setFont(searchTextLabel.getFont().deriveFont(Font.BOLD));

    gbc.gridx = 0;
    gbc.gridy = 0;
    panel.add(new JLabel("Search for:"), gbc);
    gbc.gridx = 1;
    panel.add(searchTextLabel, gbc);

    JComboBox<CaColumn> columnCombo =
        new JComboBox<>(SEARCHABLE_COLUMNS.toArray(new CaColumn[0]));
    columnCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof CaColumn col) {
              setText(col.displayName);
            }
            return this;
          }
        });
    if (!SEARCHABLE_COLUMNS.isEmpty()) {
      columnCombo.setSelectedIndex(0);
    }

    gbc.gridx = 0;
    gbc.gridy = 1;
    panel.add(new JLabel("In column:"), gbc);
    gbc.gridx = 1;
    panel.add(columnCombo, gbc);

    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    JButton okButton = new JButton("OK");
    JButton cancelButton = new JButton("Cancel");
    buttonPanel.add(okButton);
    buttonPanel.add(cancelButton);

    gbc.gridx = 0;
    gbc.gridy = 2;
    gbc.gridwidth = 2;
    gbc.anchor = GridBagConstraints.CENTER;
    panel.add(buttonPanel, gbc);

    okButton.addActionListener(
        e -> {
          CaColumn selectedCol = (CaColumn) columnCombo.getSelectedItem();
          if (selectedCol != null) {
            addCondition(new SearchCondition(selectedCol, searchText));
            searchField.setText("");
            dialog.dispose();
          }
        });
    cancelButton.addActionListener(e -> dialog.dispose());
    dialog.getContentPane().add(panel);
    dialog.pack();
    dialog.setLocationRelativeTo(parentWindow);
    dialog.setVisible(true);
  }

  private void addCondition(SearchCondition cond) {
    conditions.add(cond);

    JPanel chip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
    chip.setBackground(new Color(220, 230, 250));
    chip.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 200, 240)),
            BorderFactory.createEmptyBorder(2, 6, 2, 2)));

    JLabel label = new JLabel(cond.column().displayName + ": " + cond.searchTerm());
    JButton closeButton = new JButton("\u00D7");
    closeButton.setFont(closeButton.getFont().deriveFont(Font.BOLD, 12f));
    closeButton.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));
    closeButton.setContentAreaFilled(false);
    closeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    chip.add(label);
    chip.add(closeButton);

    closeButton.addActionListener(
        e -> {
          conditions.remove(cond);
          chipRow.remove(chip);
          if (conditions.isEmpty()) {
            chipRow.setVisible(false);
          }
          chipRow.revalidate();
          chipRow.repaint();
          onFilterChanged.run();
        });

    chipRow.add(chip);
    chipRow.setVisible(true);
    chipRow.revalidate();
    chipRow.repaint();

    onFilterChanged.run();
  }
}
