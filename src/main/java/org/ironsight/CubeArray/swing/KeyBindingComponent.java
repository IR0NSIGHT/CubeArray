package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.OpenGl.KeyBinding;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class KeyBindingComponent extends JPanel {

    private JTable table;
    private DefaultTableModel model;

    public KeyBindingComponent() {
        setLayout(new BorderLayout());

        // Column names
        String[] columns = {"Name", "Key", "Description"};

        // Sample data (can be replaced dynamically)
        Object[][] data = {
        };

        // Table model
        model = new DefaultTableModel(data, columns) {
            // Make cells non-editable
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Table
        table = new JTable(model);
        table.setAutoCreateRowSorter(true); // enable sorting

        // Add table to scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        for (KeyBinding k: KeyBinding.values()) {
            this.addKeyBinding(k);
        }
    }

    public void addKeyBinding(KeyBinding keyBinding) {
        model.addRow(new Object[]{keyBinding.name().replace("_"," ").toLowerCase(), keyBinding.keyName, ""});
    }

    // Example usage
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("KeyBindings");
            KeyBindingComponent keyBindingComponent = new KeyBindingComponent();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(400, 300);
            frame.add(keyBindingComponent);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
