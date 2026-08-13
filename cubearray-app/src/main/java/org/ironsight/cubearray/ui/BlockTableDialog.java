package org.ironsight.cubearray.ui;

import org.ironsight.cubearray.edit.BlockGradient;
import org.ironsight.cubearray.edit.BlockGradient.GradientRow;
import org.ironsight.cubearray.edit.BlockReplacer;
import org.ironsight.cubearray.platform.AppLogger;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.render.RendererService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BlockTableDialog extends JDialog {

    private static final Logger logger = AppLogger.get(BlockTableDialog.class);
    private static final Path GRADIENT_FILE = ResourceUtils.getInstallPath().resolve("gradient.json");

    private static final File[] DEBUG_SCHEM_FILES = {
        new File("D:\\Repos\\worldpainter_related\\CastlePacks\\Poland\\xl_wall_1.schem"),
        new File("D:\\Repos\\worldpainter_related\\CastlePacks\\Poland\\xl_wall_2_gate.schem"),
        new File("D:\\Repos\\worldpainter_related\\CastlePacks\\Poland\\xl_wall_3_stairs.schem"),
        new File("D:\\Repos\\worldpainter_related\\CastlePacks\\Poland\\poland_tower_round_large_1.schem"),
        new File("D:\\Repos\\worldpainter_related\\CastlePacks\\Poland\\wall_1_tower_square.schem")
    };
    private static final Set<String> TARGET_BLOCKS = Set.of("minecraft:stone_bricks");

    private final List<TableRow> rows = new ArrayList<>();
    private final List<String> availableBlocks;
    private final BlockIconProvider iconProvider;
    private final BlockIconPanel iconPanel;
    private final BlockTableModel tableModel;
    private final RendererService renderer = RendererService.getInstance();
    private final List<File> previewFiles;
    private final Set<String> targetBlocks;
    private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService previewExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "gradient-preview");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong previewGeneration = new AtomicLong();
    private ScheduledFuture<?> pendingSave;
    private boolean confirmed = false;

    private record TableRow(String block, int yPos, int chance) {
        TableRow withBlock(String b) { return new TableRow(b, yPos, chance); }
        TableRow withYPos(int y) { return new TableRow(block, y, chance); }
        TableRow withChance(int c) { return new TableRow(block, yPos, c); }
    }

    private static class BlockTableModel extends AbstractTableModel {
        private final List<TableRow> rows;
        private final Runnable onTableChanged;

        BlockTableModel(List<TableRow> rows, Runnable onTableChanged) {
            this.rows = rows;
            this.onTableChanged = onTableChanged;
        }

        void fireAllChanged() {
            fireTableDataChanged();
            if (onTableChanged != null) onTableChanged.run();
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
            if (onTableChanged != null) onTableChanged.run();
        }
    }

    private void rebuildGradient() {
        List<GradientRow> gradientRows = new ArrayList<>();
        for (TableRow r : rows) {
            gradientRows.add(new GradientRow(r.block(), r.yPos(), r.chance()));
        }
        iconPanel.setGradient(new BlockGradient(gradientRows));
    }

    private void scheduleSave() {
        if (pendingSave != null) pendingSave.cancel(false);
        pendingSave = saveScheduler.schedule(() -> {
            List<GradientRow> gradientRows = new ArrayList<>();
            for (TableRow r : rows) {
                gradientRows.add(new GradientRow(r.block(), r.yPos(), r.chance()));
            }
            BlockGradient.save(GRADIENT_FILE, gradientRows);
        }, 500, TimeUnit.MILLISECONDS);
    }

    private void applyGradientToSchematics() {
        long generation = previewGeneration.incrementAndGet();
        List<GradientRow> gradientRows = rows.stream()
            .map(r -> new GradientRow(r.block(), r.yPos(), r.chance()))
            .toList();
        BlockGradient gradient = new BlockGradient(gradientRows);

        if (previewFiles.isEmpty()) {
            logger.warning("No schematic files available for gradient preview");
            return;
        }

        previewExecutor.execute(() -> {
            try {
                CubeSetup setup = GradientSchematicApplier.apply(previewFiles, gradient, targetBlocks);
                if (setup != null && generation == previewGeneration.get()) {
                    renderer.showOrReplace(setup)
                        .exceptionally(error -> {
                            logger.log(Level.WARNING, "Failed to update gradient preview", error);
                            return null;
                        });
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to apply gradient", e);
            }
        });
    }

    private BlockTableDialog(
        Frame owner, List<String> availableBlocks, List<File> previewFiles, Set<String> targetBlocks) {
        super(owner, "Block Gradient", true);
        this.availableBlocks = new ArrayList<>(availableBlocks);
        this.previewFiles = List.copyOf(previewFiles);
        this.targetBlocks = Set.copyOf(targetBlocks);
        this.iconProvider = new BlockIconProvider(32, 20);

        List<GradientRow> saved = BlockGradient.load(GRADIENT_FILE);
        if (saved.isEmpty()) {
            rows.add(new TableRow("minecraft:stone", 0, 100));
        } else {
            for (GradientRow r : saved) {
                rows.add(new TableRow(r.block(), r.yPos(), r.chance()));
            }
        }

        iconPanel = new BlockIconPanel(iconProvider);
        Runnable onChange = () -> { rebuildGradient(); scheduleSave(); applyGradientToSchematics(); };
        tableModel = new BlockTableModel(rows, onChange);

        JTable table = new JTable(tableModel);
        table.setRowHeight(32);
        table.getColumnModel().getColumn(0).setCellRenderer(new BlockCellRenderer(iconProvider));
        table.getColumnModel().getColumn(0).setCellEditor(new SearchableBlockCellEditor(availableBlocks, iconProvider));
        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);

        TableRowSorter<BlockTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        sorter.setSortsOnUpdates(true);
        sorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(1, javax.swing.SortOrder.ASCENDING)));

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
        applyGradientToSchematics();
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        okBtn.addActionListener(e -> { confirmed = true; dispose(); });
        cancelBtn.addActionListener(e -> { dispose(); });
        getRootPane().setDefaultButton(okBtn);
        panel.add(okBtn);
        panel.add(cancelBtn);
        return panel;
    }

    public static Optional<BlockGradient> show(
        Component parent, List<String> availableBlocks, List<File> previewFiles, Set<String> targetBlocks) {
        Frame owner = parent == null ? null : (parent instanceof Frame f ? f : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent));
        var dialog = new BlockTableDialog(owner, availableBlocks, previewFiles, targetBlocks);
        dialog.setVisible(true);
        if (!dialog.confirmed) return Optional.empty();
        List<GradientRow> rows = dialog.rows.stream()
            .map(r -> new GradientRow(r.block(), r.yPos(), r.chance()))
            .toList();
        return rows.isEmpty() ? Optional.empty() : Optional.of(new BlockGradient(rows));
    }

    public static void main(String[] args) {
        try {
            ResourceUtils.copyResourcesToFile(
                ResourceUtils.TEXTURE_RESOURCES,
                ResourceUtils.VANILLA_ASSETS_RESOURCES
            );
            FileTableModel.blockIconProvider = new BlockIconProvider(32, 16);

            List<File> debugFiles = Arrays.stream(DEBUG_SCHEM_FILES)
                .filter(File::exists)
                .toList();

            SwingUtilities.invokeLater(() -> {
                try {
                    List<String> allBlocks = BlockReplacer.loadDefaultPalette().stream().sorted().toList();
                    var dialog = new BlockTableDialog(null, allBlocks, debugFiles, TARGET_BLOCKS);
                    dialog.setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void dispose() {
        previewGeneration.incrementAndGet();
        previewExecutor.shutdownNow();
        saveScheduler.shutdownNow();
        super.dispose();
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
