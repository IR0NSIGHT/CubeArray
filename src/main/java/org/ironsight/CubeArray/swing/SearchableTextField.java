package org.ironsight.CubeArray.swing;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

public class SearchableTextField extends JPanel {

  private final JTextField textField;
  private final JPopupMenu popup;
  private final JList<String> suggestionList;
  private final DefaultListModel<String> listModel;
  private final List<String> allItems;
  private final List<Runnable> commitListeners = new ArrayList<>();
  private Function<String, Icon> iconProvider;
  private boolean multiSelect;
  private final Set<String> selectedItems = new LinkedHashSet<>();
  private int commitGeneration = 0;

  public SearchableTextField(List<String> items) {
    super(new BorderLayout());
    this.allItems = new ArrayList<>(items);
    this.textField = new JTextField();
    this.listModel = new DefaultListModel<>();
    this.suggestionList = new JList<>(listModel);
    suggestionList.setCellRenderer(createDefaultRenderer());
    this.popup = new JPopupMenu();

    suggestionList.setFocusable(false);
    popup.setFocusable(false);
    popup.setLayout(new BorderLayout());
    popup.setBorder(BorderFactory.createLineBorder(Color.GRAY));
    popup.add(new JScrollPane(suggestionList), BorderLayout.CENTER);

    add(textField, BorderLayout.CENTER);

    textField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                onTextChanged();
              }

              public void removeUpdate(DocumentEvent e) {
                onTextChanged();
              }

              public void changedUpdate(DocumentEvent e) {}
            });

    textField.addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            switch (e.getKeyCode()) {
              case KeyEvent.VK_DOWN -> {
                e.consume();
                if (!popup.isVisible()) showPopup();
                else moveSelection(+1);
              }
              case KeyEvent.VK_UP -> {
                e.consume();
                moveSelection(-1);
              }
              case KeyEvent.VK_ENTER -> {
                e.consume();
                if (multiSelect) toggleSelected();
                else commitSelected();
              }
              case KeyEvent.VK_ESCAPE -> {
                if (multiSelect) syncTextFieldToSelection();
                popup.setVisible(false);
                for (Runnable r : commitListeners) r.run();
              }
            }
          }
        });

    suggestionList.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseReleased(MouseEvent e) {
            int index = suggestionList.locationToIndex(e.getPoint());
            if (index >= 0) {
              suggestionList.setSelectedIndex(index);
              if (multiSelect) toggleSelected();
              else commitSelected();
            }
          }
        });

    textField.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            SwingUtilities.invokeLater(
                () -> {
                  if (multiSelect) syncTextFieldToSelection();
                  popup.setVisible(false);
                  for (Runnable r : commitListeners) r.run();
                });
          }
        });
  }

  public JTextField getTextField() {
    return textField;
  }

  public void addCommitListener(Runnable listener) {
    commitListeners.add(listener);
  }

  public void setIconProvider(Function<String, Icon> iconProvider) {
    this.iconProvider = iconProvider;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void setSuggestionRenderer(ListCellRenderer renderer) {
    suggestionList.setCellRenderer(renderer != null ? renderer : createDefaultRenderer());
  }

  private DefaultListCellRenderer createDefaultRenderer() {
    return new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        JLabel label =
            (JLabel)
                super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
        if (value instanceof String s) {
          if (multiSelect) {
            label.setText((selectedItems.contains(s) ? "[x] " : "[ ] ") + s);
          }
          if (iconProvider != null) {
            Icon icon = iconProvider.apply(s);
            if (icon != null) {
              label.setIcon(icon);
              label.setIconTextGap(6);
            }
          }
        }
        return label;
      }
    };
  }

  public void setMultiSelect(boolean multiSelect) {
    this.multiSelect = multiSelect;
  }

  public Set<String> getSelectedItems() {
    return new LinkedHashSet<>(selectedItems);
  }

  public boolean isMultiSelect() {
    return multiSelect;
  }

  private void toggleSelected() {
    String item = suggestionList.getSelectedValue();
    if (item == null && listModel.getSize() > 0) {
      item = listModel.getElementAt(0);
    }
    if (item == null) return;
    if (selectedItems.contains(item)) selectedItems.remove(item);
    else selectedItems.add(item);
    syncTextFieldToSelection();
    suggestionList.repaint();
  }

  private void syncTextFieldToSelection() {
    String joined = String.join(", ", selectedItems);
    textField.setText(joined);
    textField.setCaretPosition(joined.length());
  }

  private void onTextChanged() {
    String query = textField.getText();
    int gen = commitGeneration;
    SwingUtilities.invokeLater(
        () -> {
          if (commitGeneration != gen) return;
          updateFilter(query);
        });
  }

  private void updateFilter(String query) {
    listModel.clear();
    for (String item : allItems) {
      if (item.toUpperCase().contains(query.toUpperCase())) {
        listModel.addElement(item);
      }
    }
    if (listModel.getSize() > 0) {
      showPopup();
      suggestionList.repaint();
    } else {
      popup.setVisible(false);
    }
  }

  private void showPopup() {
    int rowEstimate = iconProvider != null ? 38 : 22;
    int prefHeight = Math.min(300, Math.max(60, listModel.getSize() * rowEstimate + 5));
    popup.setPopupSize(new Dimension(textField.getWidth(), prefHeight));
    popup.show(textField, 0, textField.getHeight());
  }

  private void moveSelection(int delta) {
    int size = listModel.getSize();
    if (size == 0) return;
    int next = Math.max(0, Math.min(size - 1, suggestionList.getSelectedIndex() + delta));
    suggestionList.setSelectedIndex(next);
    suggestionList.ensureIndexIsVisible(next);
  }

  private void commitSelected() {
    String selected = suggestionList.getSelectedValue();
    if (selected == null && listModel.getSize() > 0) {
      selected = listModel.getElementAt(0);
    }
    if (selected == null) return;
    commitGeneration++;
    textField.setText(selected);
    textField.setCaretPosition(selected.length());
    popup.setVisible(false);
    textField.requestFocusInWindow();
    for (Runnable r : commitListeners) r.run();
  }

  public static void main(String[] args) {
    List<String> options =
        List.of(
            "Apple", "Banana", "Cherry", "Date", "Elderberry",
            "Fig", "Grape", "Honeydew", "Kiwi", "Lemon");

    SwingUtilities.invokeLater(
        () -> {
          JFrame frame = new JFrame("SearchableTextField Demo");
          frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
          frame.setLayout(new FlowLayout());
          SearchableTextField stf = new SearchableTextField(options);
          stf.setPreferredSize(new Dimension(250, 30));
          frame.add(stf);
          frame.pack();
          frame.setLocationRelativeTo(null);
          frame.setVisible(true);
        });
  }
}
