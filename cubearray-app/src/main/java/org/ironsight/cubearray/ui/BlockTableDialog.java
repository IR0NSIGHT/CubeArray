package org.ironsight.cubearray.ui;

import org.ironsight.cubearray.edit.BlockGradient;
import org.ironsight.cubearray.edit.BlockReplacer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class BlockTableDialog extends JDialog {

    private final List<TableRow> rows = new ArrayList<>();
    private final List<String> availableBlocks;
    private final BlockIconProvider iconProvider;
    private final BlockIconPanel iconPanel;
    private final BlockTableModel tableModel;

    private record TableRow(String block, int yPos, int chance) {
        TableRow withBlock(String b) { return new TableRow(b, yPos, chance); }
        TableRow withYPos(int y) { return new TableRow(block, y, chance); }
        TableRow withChance(int c) { return new TableRow(block, yPos, c); }
    }

    private static class BlockTableModel extends AbstractTableModel {
        private final List<TableRow> rows;
        private final List<String> availableBlocks;
        private final BlockIconProvider iconProvider;
        private final Runnable onChange;

        BlockTableModel(List<TableRow> rows, List<String> availableBlocks, BlockIconProvider iconProvider, Runnable onChange) {
            this.rows = rows;
            this.availableBlocks = availableBlocks;
            this.iconProvider = iconProvider;
            this.onChange = onChange;
        }

        void fireAllChanged() {
            fireTableDataChanged();
            if (onChange != null) onChange.run();
        }

        @Override
        public int getRowCount() { return rows.size(); }

        @Override
        public int getColumnCount() { return 3; }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Block";
                case 1 -> "Y Pos";
                case 2 -> "Chance";
                default -> "";
            };
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0 -> String.class;
                case 1 -> Integer.class;
                case 2 -> Integer.class;
                default -> Object.class;
            };
        }

        @Override
        public Object getValueAt(int row, int col) {
            TableRow r = rows.get(row);
            return switch (col) {
                case 0 -> r.block();
                case 1 -> r.yPos();
                case 2 -> r.chance();
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int row, int col) { return true; }

        @Override
        public void setValueAt(Object value, int row, int col) {
            TableRow r = rows.get(row);
            switch (col) {
                case 0 -> {
                    String newBlock = value.toString();
                    rows.set(row, r.withBlock(newBlock));
                }
                case 1 -> {
                    int yPos = value instanceof Integer ? (Integer) value : 0;
                    rows.set(row, r.withYPos(yPos));
                }
                case 2 -> {
                    int chance = value instanceof Integer ? (Integer) value : 0;
                    rows.set(row, r.withChance(chance));
                }
            }
            fireTableCellUpdated(row, col);
            if (onChange != null) onChange.run();
        }
    }

    private void rebuildGradient() {
        List<BlockGradient.GradientRow> gradientRows = new ArrayList<>();
        for (TableRow r : rows) {
            gradientRows.add(new BlockGradient.GradientRow(r.block(), r.yPos(), r.chance()));
        }
        iconPanel.setGradient(new BlockGradient(gradientRows));
    }

    private BlockTableDialog(Frame owner, List<String> availableBlocks) {
        super(owner, "Block Table Debug", true);
        this.availableBlocks = new ArrayList<>(availableBlocks);
        this.iconProvider = new BlockIconProvider(32, 20);

        rows.add(new TableRow("minecraft:stone", 0, 100));

        iconPanel = new BlockIconPanel(iconProvider);
        tableModel = new BlockTableModel(rows, availableBlocks, iconProvider, this::rebuildGradient);

        JTable table = new JTable(tableModel);
        table.setRowHeight(32);
        table.getColumnModel().getColumn(0).setCellRenderer(new BlockCellRenderer(iconProvider));
        table.getColumnModel().getColumn(0).setCellEditor(new SearchableBlockCellEditor(availableBlocks, iconProvider));
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);

        rebuildGradient();

        JButton addRowBtn = new JButton("Add Row");
        addRowBtn.addActionListener(e -> {
            rows.add(new TableRow(availableBlocks.isEmpty() ? "minecraft:stone" : availableBlocks.get(0), 0, 100));
            tableModel.fireAllChanged();
        });

        JButton removeRowBtn = new JButton("Remove Row");
        removeRowBtn.addActionListener(e -> {
            int[] selected = table.getSelectedRows();
            if (selected.length == 0) return;
            int[] modelRows = new int[selected.length];
            for (int i = 0; i < selected.length; i++) {
                modelRows[i] = table.convertRowIndexToModel(selected[i]);
            }
            Arrays.sort(modelRows);
            for (int i = modelRows.length - 1; i >= 0; i--) {
                rows.remove(modelRows[i]);
            }
            tableModel.fireAllChanged();
        });

        JPanel leftPanel = new JPanel(new BorderLayout());
        JPanel topBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topBtnPanel.add(addRowBtn);
        topBtnPanel.add(removeRowBtn);
        leftPanel.add(topBtnPanel, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, new JScrollPane(iconPanel));
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(0.5);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(splitPane, BorderLayout.CENTER);
        getContentPane().add(buildButtonPanel(), BorderLayout.SOUTH);

        pack();
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(owner);
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> { dispose(); });
        cancelBtn.addActionListener(e -> { dispose(); });
        getRootPane().setDefaultButton(okBtn);
        panel.add(okBtn);
        panel.add(cancelBtn);
        return panel;
    }

    public static void show(Component parent, List<String> availableBlocks) {
        Frame owner = parent == null ? null : (parent instanceof Frame f ? f : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent));
        var dialog = new BlockTableDialog(owner, availableBlocks);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                org.ironsight.cubearray.platform.ResourceUtils.copyResourcesToFile(
                    org.ironsight.cubearray.platform.ResourceUtils.TEXTURE_RESOURCES,
                    org.ironsight.cubearray.platform.ResourceUtils.VANILLA_ASSETS_RESOURCES
                );
                FileTableModel.blockIconProvider = new BlockIconProvider(32, 16);
                List<String> allBlocks = BlockReplacer.loadDefaultPalette().stream().sorted().toList();
                var dialog = new BlockTableDialog(null, allBlocks);
                dialog.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static class BlockCellRenderer extends DefaultTableCellRenderer {
        private final BlockIconProvider iconProvider;

        BlockCellRenderer(BlockIconProvider iconProvider) {
            this.iconProvider = iconProvider;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof String block) {
                String displayName = block.contains(":") ? block.substring(block.indexOf(":") + 1) : block;
                label.setText(displayName);
                label.setIcon(iconProvider.getIcon(block));
                label.setIconTextGap(6);
            }
            return label;
        }
    }

    private static class SearchableBlockCellEditor extends AbstractCellEditor implements javax.swing.table.TableCellEditor {
        private final SearchableTextField searchableTextField;

        SearchableBlockCellEditor(List<String> availableBlocks, BlockIconProvider iconProvider) {
            searchableTextField = new SearchableTextField(availableBlocks);
            searchableTextField.setIconProvider(iconProvider::getIcon);
            searchableTextField.setSyntaxValidator(BlockReplacer::isValidBlockSyntax);
            searchableTextField.addCommitListener(() -> fireEditingStopped());
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            if (value instanceof String block) {
                searchableTextField.getTextField().setText(block);
            }
            return searchableTextField;
        }

        @Override
        public Object getCellEditorValue() {
            return searchableTextField.getTextField().getText();
        }
    }
}
