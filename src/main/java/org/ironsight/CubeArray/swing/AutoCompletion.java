package org.ironsight.CubeArray.swing;

// Source - https://stackoverflow.com/a/16419169
// Posted by Laksitha Ranasingha, modified by community. See post 'Timeline' for change history
// Retrieved 2026-07-04, License - CC BY-SA 3.0
// Rewritten: DocumentListener-based filtering, explicit commit on Enter/click only

import java.awt.Dimension;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.text.JTextComponent;

public class AutoCompletion {

    private final JComboBox        comboBox;
    private final Object[]         allItems;      // full item list, never mutated
    private final JTextComponent   editor;
    private final Runnable         onCommit;      // called after each committed selection
    private boolean                selecting = false;
    /** Incremented on every commit; stale invokeLater callbacks compare against this and no-op. */
    private int                    commitGeneration = 0;

    private AutoCompletion(JComboBox comboBox, JTextComponent textComponent) {
        this(comboBox, textComponent, null);
    }

    private AutoCompletion(JComboBox comboBox, JTextComponent textComponent, Runnable onCommit) {
        this.onCommit = onCommit;
        this.comboBox = comboBox;
        this.editor   = textComponent;

        // Snapshot the full item list once
        allItems = new Object[comboBox.getModel().getSize()];
        for (int i = 0; i < allItems.length; i++) allItems[i] = comboBox.getModel().getElementAt(i);

        // Close popup initially — only open when the user types
        comboBox.setPopupVisible(false);

        // Observe text changes — purely read, never write
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { onTextChanged(); }
            public void removeUpdate(DocumentEvent e)  { onTextChanged(); }
            public void changedUpdate(DocumentEvent e) { }
        });

        // Keyboard: navigate with arrows, commit with Enter, dismiss with Escape
        editor.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN  -> {
                        e.consume();
                        if (!comboBox.isPopupVisible()) comboBox.setPopupVisible(true);
                        else moveListSelection(+1);
                    }
                    case KeyEvent.VK_UP    -> { e.consume(); moveListSelection(-1); }
                    case KeyEvent.VK_ENTER -> { e.consume(); commitSelected(); }
                    case KeyEvent.VK_ESCAPE -> comboBox.setPopupVisible(false);
                }
            }
        });

        // Commit on mouse click in the popup list
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < comboBox.getUI().getAccessibleChildrenCount(comboBox); i++) {
                Object child = comboBox.getUI().getAccessibleChild(comboBox, i);
                if (child instanceof BasicComboPopup popup) {
                    popup.getList().addMouseListener(new MouseAdapter() {
                        @Override public void mouseReleased(MouseEvent e) { commitSelected(); }
                    });
                }
            }
        });
    }

    public static void enable(JComboBox comboBox) {
        comboBox.setEditable(true);
        new AutoCompletion(comboBox, (JTextComponent) comboBox.getEditor().getEditorComponent());
    }

    /** Overload for when the combo uses a custom editor with a separate text field. */
    public static void enable(JComboBox comboBox, JTextComponent textField) {
        comboBox.setEditable(true);
        new AutoCompletion(comboBox, textField);
    }

    /** Overload with a callback invoked after every committed selection (Enter / mouse click). */
    public static void enable(JComboBox comboBox, JTextComponent textField, Runnable onCommit) {
        comboBox.setEditable(true);
        new AutoCompletion(comboBox, textField, onCommit);
    }

    // -------------------------------------------------------------------------

    private void onTextChanged() {
        if (selecting) return;
        String query = editor.getText();
        int generation = commitGeneration;
        // Defer model mutation until after the current document event finishes —
        // calling setModel/setText inside a document notification causes
        // IllegalStateException ("Attempt to mutate in notification").
        // Check generation before running: if a commit happened in the meantime, discard.
        SwingUtilities.invokeLater(() -> {
            if (commitGeneration != generation) return;
            updateFilter(query);
        });
    }

    /**
     * Rebuilds the combo model to only contain items whose name contains
     * {@code query} (case-insensitive). Opens or closes the popup accordingly.
     */
    private void updateFilter(String query) {
        // Capture caret before setModel resets the editor text
        int caretPos = editor.getCaretPosition();

        DefaultComboBoxModel<Object> filtered = new DefaultComboBoxModel<>();
        for (Object item : allItems) {
            if (containsIgnoreCase(item.toString(), query)) filtered.addElement(item);
        }

        selecting = true;
        comboBox.setModel(filtered);
        // setModel resets the editor text — restore what the user typed
        editor.setText(query);
        // Restore caret to where the user was, not unconditionally to the end
        editor.setCaretPosition(Math.min(caretPos, query.length()));
        selecting = false;

        comboBox.setPopupVisible(filtered.getSize() > 0);
    }

    /**
     * Navigates the popup list selection up or down without touching the text field.
     */
    private void moveListSelection(int delta) {
        for (int i = 0; i < comboBox.getUI().getAccessibleChildrenCount(comboBox); i++) {
            Object child = comboBox.getUI().getAccessibleChild(comboBox, i);
            if (child instanceof BasicComboPopup popup) {
                JList<?> list = popup.getList();
                int size = list.getModel().getSize();
                if (size == 0) return;
                int next = Math.max(0, Math.min(size - 1, list.getSelectedIndex() + delta));
                list.setSelectedIndex(next);
                list.ensureIndexIsVisible(next);
                // Keep combo's selected item in sync (for commitSelected)
                selecting = true;
                comboBox.setSelectedIndex(next);
                selecting = false;
                return;
            }
        }
    }

    /**
     * Writes the currently highlighted item into the text field and closes the popup.
     * Restores the full item model so the next search starts from scratch.
     */
    private void commitSelected() {
        Object selected = comboBox.getSelectedItem();
        if (selected == null && comboBox.getModel().getSize() > 0)
            selected = comboBox.getModel().getElementAt(0);
        if (selected == null) return;

        final String text = selected.toString();

        // Invalidate any pending invokeLater filter callbacks before writing
        commitGeneration++;

        // Restore full model and write committed text — selecting=true suppresses
        // the DocumentListener so no new filter callbacks are scheduled
        selecting = true;
        DefaultComboBoxModel<Object> full = new DefaultComboBoxModel<>(allItems);
        comboBox.setModel(full);
        comboBox.setSelectedItem(selected);
        // Also tell the editor to update (e.g. icon in IconComboBoxEditor)
        comboBox.getEditor().setItem(selected);
        editor.setText(text);
        editor.setCaretPosition(text.length());
        comboBox.setPopupVisible(false);
        selecting = false;

        if (onCommit != null) onCommit.run();
        editor.requestFocusInWindow();
    }

    // -------------------------------------------------------------------------

    private boolean containsIgnoreCase(String str, String pattern) {
        return str.toUpperCase().contains(pattern.toUpperCase());
    }

    /** Quick smoke-test: opens a frame with a searchable combo populated from BlockData.json. */
    public static void main(String[] args) throws Exception {
        String[] blocks = org.ironsight.schemEdit.BlockListUtil.loadBlockData().keySet()
                .stream().sorted().toArray(String[]::new);

        SwingUtilities.invokeLater(() -> {
            JComboBox<String> combo = new JComboBox<>(blocks);
            combo.setPreferredSize(new Dimension(400, combo.getPreferredSize().height));
            AutoCompletion.enable(combo);

            JFrame frame = new JFrame("AutoCompletion test");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.getContentPane().setLayout(new java.awt.FlowLayout());
            frame.getContentPane().add(new JLabel("Block:"));
            frame.getContentPane().add(combo);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
