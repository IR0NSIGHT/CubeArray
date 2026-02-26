package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.CubeArrayMain;
import org.ironsight.CubeArray.InstancedCubes;
import org.ironsight.CubeArray.ResourceUtils;
import org.ironsight.CubeArray.SchemReader;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Set;

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
    private final HashMap<FileTableModel.Column, TableColumn> columToTableColumn = new HashMap<>();

    public FileRenderApp(final AppContext context) {
        this.context = context;
        if (context.neverBeforeUsed) {
            // add default schematics on very first use
            ResourceUtils.getDefaultSchematics().forEach(s -> context.filesAndTimestamps.put(s.toFile(),
                    System.currentTimeMillis()));
            context.neverBeforeUsed = false;
            contextDirtyFlag = true;
        }

        CubeArrayMain.periodicChecker.addCallback(this::checkContextSaving);
        CubeArrayMain.periodicChecker.addCallback(this::checkLoadingThreads);

        this.tableModel = new FileTableModel(CubeArrayMain.periodicChecker);
        this.fileTable = new JTable(tableModel);
        this.rowSorter = new TableRowSorter<>(tableModel);

        tableAddMouseClickListener(fileTable);

        fileTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {

            @Override
            public void columnAdded(TableColumnModelEvent e) {
                updateContextColumns(fileTable.getColumnModel());
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
                updateContextColumns(fileTable.getColumnModel());
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {
                updateContextColumns(fileTable.getColumnModel());
            }

            @Override
            public void columnMarginChanged(ChangeEvent e) {
                // Optional: track width changes
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
                // Usually ignore
            }
        });

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
                            for (FileTableModel.Column c: FileTableModel.Column.values()) {
                                if (c.renderer.convertToString(entry.getValue(c.ordinal())).toLowerCase().contains(text.toLowerCase()))
                                    return true;
                            }
                            return false;
                        }
                    });
                }
                for (FileTableModel.Column c: FileTableModel.Column.values()) {
                    c.renderer.setSearchText(text);
                }
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }
        });


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

        JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.LEFT);
        tabbedPane.add("File List", fileListPanel);
        tabbedPane.add("⚙\uFE0F", getSettingsComponent(context.displayColumnOrdinals)); // SETTINGS
        frame.add(tabbedPane);
        context.filesAndTimestamps.keySet().forEach(tableModel::addFile);

        initDisplayedColumns(context);

        frame.setVisible(true);
    }

    public static void startApp(final AppContext context) {
        SwingUtilities.invokeLater(() -> new FileRenderApp(context));
    }

    private JComponent getSettingsComponent(ArrayList<FileTableModel.Column> initialColumns) {
        // SELECT WHICH COLUMNS TO DISPLAY
        JComponent columnSettings = new JPanel(new GridLayout(0, 1));
        columnSettings.add(new JLabel("Show Columns:"));
        HashSet<FileTableModel.Column> columns = new HashSet<>(initialColumns);

        FileTableModel.Column[] columnSet = FileTableModel.Column.values();

        IntStream.range(0, columnSet.length).mapToObj(i -> new AbstractMap.SimpleEntry<>(i, columnSet[i])).sorted(Comparator.comparing(e -> e.getValue().displayName)).forEach(entry -> {
            var c = entry.getValue();
            JCheckBox checkBox = new JCheckBox(c.displayName);
            checkBox.setToolTipText(c.tooltip);
            checkBox.setSelected(columns.contains(c));
            checkBox.addActionListener(e -> {
                boolean show = checkBox.isSelected();
                if (show && !context.displayColumnOrdinals.contains(c)) {
                    context.displayColumnOrdinals.add(c);
                } else {
                    context.displayColumnOrdinals.remove(c);
                }
                updateDisplayColumns(new ArrayList<>(context.displayColumnOrdinals), fileTable.getColumnModel());
            });
            columnSettings.add(checkBox);
        });

        JScrollPane settingsPanel = new JScrollPane();
        {
            settingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel settingsContentPane = new JPanel();
            settingsContentPane.setLayout(new GridLayout(0, 1));
            settingsContentPane.add(columnSettings);
            settingsContentPane.add(new KeyBindingComponent());
            settingsPanel.setViewportView(settingsContentPane);
        }
        return settingsPanel;
    }

    private void initDisplayedColumns(AppContext context) {
        var columns = new ArrayList<>(context.displayColumnOrdinals);
        // init displayed columns
        TableColumnModel columnModel = fileTable.getColumnModel();
        for (int i = 0; i < columnModel.getColumnCount(); i++) {
            TableColumn tc = columnModel.getColumn(i);
            FileTableModel.Column c = FileTableModel.Column.values()[i];
            columToTableColumn.put(c, tc);
        }

        //display only columns from saved context
        updateDisplayColumns(columns, fileTable.getColumnModel());
    }

    void updateDisplayColumns(ArrayList<FileTableModel.Column> columns, TableColumnModel columnModel) {
        {    // rebuild column model to match active columns.
            // Remove all columns
            while (columnModel.getColumnCount() > 0) {
                columnModel.removeColumn(columnModel.getColumn(0));
            }
            for (var c : columns) {
                columnModel.addColumn(columToTableColumn.get(c));
            }
            System.out.println("set display columns to: " + context.displayColumnOrdinals.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
    }

    void showColumn(FileTableModel.Column column, int index, boolean show) {
        var columnModel = fileTable.getColumnModel();
        if (show && !context.displayColumnOrdinals.contains(column)) {
            context.displayColumnOrdinals.add(column);
        } else {
            context.displayColumnOrdinals.remove(column);
        }


        {    // rebuild column model to match active columns.
            // Remove all columns
            while (columnModel.getColumnCount() > 0) {
                columnModel.removeColumn(columnModel.getColumn(0));
            }
            for (var c : context.displayColumnOrdinals) {
                columnModel.addColumn(columToTableColumn.get(c));
            }
        }
    }

    void updateContextColumns(TableColumnModel columnModel) {

        Map<TableColumn, FileTableModel.Column> reverseMap = new HashMap<>();

        for (Map.Entry<FileTableModel.Column, TableColumn> e : columToTableColumn.entrySet()) {
            reverseMap.put(e.getValue(), e.getKey());
        }

        Enumeration<TableColumn> tableColumns = columnModel.getColumns();

        List<FileTableModel.Column> orderedColumns = new ArrayList<>();

        while (tableColumns.hasMoreElements()) {
            TableColumn tc = tableColumns.nextElement();
            orderedColumns.add(reverseMap.get(tc));
        }

        context.displayColumnOrdinals = new ArrayList<>(orderedColumns.stream().distinct().toList());
        System.out.println(context.displayColumnOrdinals.stream().map(c -> c.displayName).collect(Collectors.joining(
                ", ")));
        contextDirtyFlag = true;
    }

    void checkContextSaving() {
        // WARNING: this runs on the background thread NOT the gui thread!
        if (contextDirtyFlag) {
            contextDirtyFlag = false;
            System.out.println("WRITE CONTEXT TO FILE");
            AppContext.write(this.context);
        }
    }

    private void tableAddMouseClickListener(JTable table) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    int viewRow = table.rowAtPoint(e.getPoint());
                    int viewCol = table.columnAtPoint(e.getPoint());

                    if (viewRow == -1 || viewCol == -1) {
                        return; // click outside cells
                    }

                    int modelRow = table.convertRowIndexToModel(viewRow);
                    int modelCol = table.convertColumnIndexToModel(viewCol);

                    Object object = tableModel.getValueAt(modelRow, modelCol);

                    System.out.println("Model row=" + modelRow + ", model col=" + modelCol);

                    JPopupMenu menu = new JPopupMenu();
                    menu.setLightWeightPopupEnabled(false);
                    JTextArea textArea = new JTextArea(10, 50);
                    textArea.setLineWrap(true);
                    textArea.setWrapStyleWord(true);
                    textArea.setEditable(false);
                    String content = "EMPTY";
                    if (object instanceof List<?> list) {
                       content = list.stream().map(Object::toString).collect(Collectors.joining("\n"));
                    } else if (object instanceof Map<?,?> map) {
                        content = map.entrySet().stream().map(entry -> entry.getKey().toString() +": " + entry.getValue().toString()).collect(Collectors.joining("\n"));
                    } else if (object instanceof String s) {
                        content = s;
                    } else {
                        content = object.toString();
                    }

                    var longestLine = Arrays.stream(content.split("\n")).max(Comparator.comparing(String::length));
                    if (longestLine.isEmpty())
                        return; //

                    textArea.setText(content);

                    FontMetrics metrics = textArea.getFontMetrics(textArea.getFont());
                    float pxWidth = metrics.stringWidth(longestLine.get());
                    pxWidth /= metrics.stringWidth("m"); // what column size is based off
                    int columns = (int)Math.ceil(pxWidth);

                    textArea.setColumns(Math.max(Math.min(100,columns+5),20));
                    textArea.setRows(Math.max(7, Math.min(30, content.split("\n").length)));

                    textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN,12));

                    System.out.println("object string = " + textArea.getText());
                    JScrollPane scrollPane = new JScrollPane(textArea);
                    menu.add(new JLabel(tableModel.getColumn(modelCol).displayName));
                    menu.add(scrollPane);

                    SwingUtilities.invokeLater(() ->
                            menu.show(e.getComponent(), e.getX(), e.getY())
                    );
                }
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 3 && SwingUtilities.isLeftMouseButton(e)) {
                    int viewRow = table.rowAtPoint(e.getPoint());
                    if (viewRow >= 0) {
                        renderSelectedFiles();
                    }
                }
            }
        });
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
        List<File> selected =
                java.util.Arrays.stream(viewRows).map(fileTable::convertRowIndexToModel).mapToObj(tableModel::getFileAt).toList();

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
        File[] files =
                Arrays.stream(viewRows).map(fileTable::convertRowIndexToModel).mapToObj(tableModel::getFileAt).toArray(File[]::new);

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
                    SchemReader.CubeSetup setup =
                            SchemReader.prepareData(SchemReader.loadSchematics(selectedFiles.stream().map(File::toPath).toList()));
                    if (setup == null) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(frame, "Error: unable to load schematics from selected " +
                                    "files. Maybe the file type is not supported or does not exist " + "anymore?");
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
                    if (f.isDirectory() || f.getPath().endsWith(type)) return true;
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
}
