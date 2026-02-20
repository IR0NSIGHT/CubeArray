package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.CubeArrayMain;
import org.ironsight.CubeArray.ResourceUtils;
import org.ironsight.CubeArray.SchemReader;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import org.ironsight.CubeArray.InstancedCubes;

public class FileRenderApp {
    final JFrame frame;
    // main data structure
    private final AppContext context;
    // UI model
    private final FileTableModel tableModel;
    private final JTable fileTable;

    private final TableRowSorter<FileTableModel> rowSorter;

    private final JButton renderBtn;
    private final Set<Thread> loadingThreads = new HashSet<>();
    //is context dirty and needs to be saved?
    private boolean contextDirtyFlag;

    private HashSet<FileTableModel.Column> activeColumns = new HashSet<>();

    public FileRenderApp(final AppContext context) {
        activeColumns.addAll(Arrays.asList(FileTableModel.Column.values()));
        this.context = context;
        if (context.neverBeforeUsed) {
            // add default schematics on very first use
            ResourceUtils.getDefaultSchematics().forEach(s -> context.filesAndTimestamps.put(s.toFile(), System.currentTimeMillis()));
            context.neverBeforeUsed = false;
            contextDirtyFlag = true;
        }

        CubeArrayMain.periodicChecker.addCallback(this::checkContextSaving);
        CubeArrayMain.periodicChecker.addCallback(this::checkLoadingThreads);

        this.tableModel = new FileTableModel(CubeArrayMain.periodicChecker);
        this.fileTable = new JTable(tableModel);
        this.rowSorter = new TableRowSorter<>(tableModel);

        TableColumnModel columnModel = fileTable.getColumnModel();

        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn tc = columnModel.getColumn(i);
            FileTableModel.Column c = FileTableModel.Column.values()[i];
            columToTableColumn.put(c, tc);
        }



        // construct UI

        frame = new JFrame("File Renderer");
        frame.setSize(context.guiBounds.width, context.guiBounds.height);
        frame.setLocation(context.guiBounds.x, context.guiBounds.y);

        // Add listener
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Rectangle bounds = frame.getBounds();
                context.guiBounds = bounds;
                contextDirtyFlag = true;
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                Rectangle bounds = frame.getBounds();
                context.guiBounds = bounds;
                contextDirtyFlag = true;
            }
        });

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setRowSorter(rowSorter);

        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int viewRow = fileTable.rowAtPoint(e.getPoint());
                    if (viewRow >= 0) {
                        renderSelectedFiles();
                    }
                }
            }
        });
        fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        for (FileTableModel.Column c : FileTableModel.Column.values()) {
            fileTable.getColumnModel().getColumn(c.ordinal()).setCellRenderer(c.renderer);
        }

        JTextField searchField = new JTextField(20);
        searchField.setText("Search");
        searchField.putClientProperty("JTextField.placeholderText", "Search...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            private void update() {
                String text = searchField.getText().trim();
                if (text.isEmpty()) {
                    rowSorter.setRowFilter(null);
                } else {
                    rowSorter.setRowFilter(new RowFilter<>() {
                        @Override
                        public boolean include(Entry<? extends FileTableModel, ? extends Integer> entry) {
                            return (entry.getValue(0) instanceof String name && name.toLowerCase().contains(text.toLowerCase()) ||
                                    (entry.getValue(1) instanceof String fullPath && fullPath.toLowerCase().contains(text.toLowerCase())));
                        }
                    });
                }
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }
        });


        // SELECT WHICH COLUMNS TO DISPLAY
        JComponent columnSettings = new JPanel(new FlowLayout(FlowLayout.LEFT));
        columnSettings.add(new JLabel("Show Columns:"));
        for (FileTableModel.Column c : FileTableModel.Column.values()) {
            JCheckBox checkBox = new JCheckBox(c.name());
            checkBox.setSelected(activeColumns.contains(c));
            checkBox.addActionListener(e -> {
                showColumn(c, checkBox.isSelected());
            });
            columnSettings.add(checkBox);
        }

        fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // allows wide table + horizontal scrolling
        JScrollPane scrollPane = new JScrollPane(fileTable);

        JButton addBtn = new JButton("Add");
        JButton removeBtn = new JButton("Remove");
        renderBtn = new JButton("Render");

        addBtn.addActionListener(e -> addFiles(frame));
        removeBtn.addActionListener(e -> removeSelectedFiles());
        renderBtn.addActionListener(e -> renderSelectedFiles());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(addBtn);
        topPanel.add(removeBtn);
        topPanel.add(searchField);


        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(renderBtn);

        JPanel fileListPanel = new JPanel(new BorderLayout());
        {
            fileListPanel.add(topPanel, BorderLayout.NORTH);
            fileListPanel.add(scrollPane, BorderLayout.CENTER);
            fileListPanel.add(bottomPanel, BorderLayout.SOUTH);
        }

        JPanel keyBindingPanel = new KeyBindingComponent();

        JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.LEFT);
        tabbedPane.add("File List", fileListPanel);
        tabbedPane.add("Key Bindings", keyBindingPanel);
        tabbedPane.add("⚙\uFE0F", columnSettings); // SETTINGS
        frame.add(tabbedPane);
        context.filesAndTimestamps.keySet().forEach(tableModel::addFile);

        frame.setVisible(true);
    }

    private HashMap<FileTableModel.Column, TableColumn> columToTableColumn = new HashMap<>();

    void showColumn(FileTableModel.Column column, boolean show) {
        var columnModel = fileTable.getColumnModel();
        if (show) {
            activeColumns.add(column);
            columnModel.addColumn(columToTableColumn.get(column));
        }
        else {
            activeColumns.remove(column);
            columnModel.removeColumn(columToTableColumn.get(column));
        }
        System.out.println(activeColumns.stream().toList());

    }

    void checkContextSaving() {
        // WARNING: this runs on the background thread NOT the gui thread!
        if (contextDirtyFlag) {
            contextDirtyFlag = false;
            System.out.println("WRITE CONTEXT TO FILE");
            AppContext.write(this.context);
        }
    }

    private void checkLoadingThreads() {
        synchronized (loadingThreads) {
            loadingThreads.removeIf(t -> !t.isAlive());
        }
        SwingUtilities.invokeLater(() -> {
            renderBtn.setVisible(loadingThreads.isEmpty());
        });

    }

    private void renderSelectedFiles() {
        if (!loadingThreads.isEmpty()) {
            return;
        }

        int[] viewRows = fileTable.getSelectedRows();
        List<File> selected = java.util.Arrays.stream(viewRows)
                .map(fileTable::convertRowIndexToModel)
                .mapToObj(tableModel::getFileAt)
                .toList();

        context.activeFiles.clear();
        context.activeFiles.addAll(selected);
        contextDirtyFlag = true;

        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No files selected.");
        } else {
            // Your action goes here
            renderFiles(selected);
        }
    }



    private void addFiles(Component parent) {
        JFileChooser chooser = getFileChooser();
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                if (!context.filesAndTimestamps.containsKey(f)) {
                    tableModel.addFile(f);
                    context.filesAndTimestamps.put(f, System.currentTimeMillis());
                }
            }
        }
        context.lastSearchPath = chooser.getCurrentDirectory();
        contextDirtyFlag = true;
    }

    private void removeSelectedFiles() {
        int[] viewRows = fileTable.getSelectedRows();
        File[] files = Arrays.stream(viewRows).map(fileTable::convertRowIndexToModel).mapToObj(tableModel::getFileAt).toArray(File[]::new);

        Arrays.stream(files).forEach(context.filesAndTimestamps::remove);
        tableModel.removeFile(files);

        contextDirtyFlag = true;
    }

    private void renderFiles(List<File> selectedFiles) {
        // Placeholder logic
        System.out.println("Rendering files:");
        for (File f : selectedFiles) {
            System.out.println(" - " + f.getAbsolutePath());
        }

        try {
            Thread glThread = new Thread(() -> {
                try {
                    SchemReader.CubeSetup setup = SchemReader.prepareData(SchemReader.loadSchematics(selectedFiles.stream().map(File::toPath).toList()));
                    if (setup == null) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(frame, "Error: unable to load schematics from selected files. Maybe the file type is not supported or does not exist " +
                                    "anymore?");
                        });
                        return;
                    }
                    new InstancedCubes(setup).run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            glThread.start();

            // add to watchlist and update gui
            synchronized (loadingThreads) {
                loadingThreads.add(glThread);
            }
            checkLoadingThreads();

        } catch (Exception ex) {
            System.out.println("Error: " + ex.getMessage());
        }

    }

    private JFileChooser getFileChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(context.lastSearchPath);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                for (String type : ResourceUtils.SUPPORTED_FILE_TYPES) {
                    if (f.isDirectory() || f.getPath().endsWith(type))
                        return true;
                }
                return false;
            }

            @Override
            public String getDescription() {
                return ResourceUtils.SUPPORTED_FILE_TYPES.stream().sorted().collect(Collectors.joining(", "));
            }
        });
        return chooser;
    }

    public static void startApp(final AppContext context) {
        SwingUtilities.invokeLater(() -> new FileRenderApp(context));
    }
}
