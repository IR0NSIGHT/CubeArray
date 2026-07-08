package org.ironsight.CubeArray.swing;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

public class SearchableTextField extends JPanel {

  private final JTextField textField;
  private final JPopupMenu popup;
  private final JList<String> suggestionList;
  private final DefaultListModel<String> listModel;
  private final List<String> allItems;
  private final List<Runnable> commitListeners = new ArrayList<>();
  private final JLabel iconLabel = new JLabel();
  private final JLabel indicatorLabel = new JLabel();
  private Function<String, Icon> iconProvider;
  private Predicate<String> syntaxValidator;
  private Runnable textChangeCallback;
  private int commitGeneration = 0;
  private String committedText = "";

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

    iconLabel.setPreferredSize(new Dimension(32, 32));
    iconLabel.setVisible(false);
    add(iconLabel, BorderLayout.WEST);
    add(textField, BorderLayout.CENTER);
    indicatorLabel.setPreferredSize(new Dimension(20, 20));
    indicatorLabel.setHorizontalAlignment(SwingConstants.CENTER);
    indicatorLabel.setVisible(false);
    add(indicatorLabel, BorderLayout.EAST);

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
                commitSelected();
              }
              case KeyEvent.VK_ESCAPE -> {
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
              commitSelected();
            }
          }
        });

    textField.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent e) {
            SwingUtilities.invokeLater(() -> popup.setVisible(false));
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
    iconLabel.setVisible(iconProvider != null);
    updateCurrentIcon();
  }

  public void setSyntaxValidator(Predicate<String> validator) {
    this.syntaxValidator = validator;
    updateIndicator();
  }

  public void setOnTextChangedCallback(Runnable callback) {
    this.textChangeCallback = callback;
  }

  private void updateCurrentIcon() {
    if (iconProvider == null) return;
    String text = textField.getText();
    if (text.isEmpty()) {
      iconLabel.setIcon(null);
    } else {
      iconLabel.setIcon(iconProvider.apply(text));
    }
  }

  private void updateIndicator() {
    if (syntaxValidator == null) {
      indicatorLabel.setVisible(false);
      return;
    }
    String text = textField.getText();
    if (text.isEmpty()) {
      indicatorLabel.setVisible(true);
      indicatorLabel.setText(" ");
      indicatorLabel.setForeground(UIManager.getColor("Panel.background"));
      return;
    }
    if (syntaxValidator.test(text)) {
      indicatorLabel.setVisible(true);
      indicatorLabel.setText("\u2713");
      indicatorLabel.setForeground(new Color(0, 160, 0));
    } else {
      indicatorLabel.setVisible(true);
      indicatorLabel.setText("\u2717");
      indicatorLabel.setForeground(new Color(200, 0, 0));
    }
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

  private void onTextChanged() {
    updateCurrentIcon();
    updateIndicator();
    if (textChangeCallback != null) {
      textChangeCallback.run();
    }
    String query = textField.getText();
    int gen = commitGeneration;
    SwingUtilities.invokeLater(
        () -> {
          if (commitGeneration != gen) return;
          updateFilter(query);
        });
  }

  private void updateFilter(String query) {
    if (query.equals(committedText)) {
      popup.setVisible(false);
      return;
    }
    listModel.clear();
    for (String item : allItems) {
      if (item.toUpperCase().contains(query.toUpperCase())) {
        listModel.addElement(item);
      }
    }
    if (listModel.getSize() > 0 && textField.hasFocus()) {
      showPopup();
      suggestionList.repaint();
    } else {
      popup.setVisible(false);
    }
  }

  private void showPopup() {
    if (!textField.hasFocus()) return;
    int prefWidth = Math.max(suggestionList.getPreferredSize().width + 20, textField.getWidth());
    int rowEstimate = iconProvider != null ? 38 : 22;
    int prefHeight = Math.min(300, Math.max(60, listModel.getSize() * rowEstimate + 5));
    popup.setPreferredSize(new Dimension(prefWidth, prefHeight));
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
    committedText = selected;
    commitGeneration++;
    textField.setText(selected);
    textField.setCaretPosition(selected.length());
    updateCurrentIcon();
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
