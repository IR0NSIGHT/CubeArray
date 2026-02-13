package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.CubeArrayMain;
import org.ironsight.CubeArray.InstancedCubes;
import org.ironsight.CubeArray.ResourceUtils;
import org.ironsight.CubeArray.SchemReader;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    private record TextSearch(
            String searchString,
            List<FileTableModel.Column> searchColumns,
            boolean excludeMatches
    ) {
        // provide default values via a compact constructor
        public TextSearch() {
            this("", List.of(), false);
        }
    }
    private TextSearch currentSearch = new TextSearch();

    private void updateTextSearch(TextSearch newSearch) {
        this.currentSearch = newSearch;
        if (currentSearch.searchString.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {

            rowSorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends FileTableModel, ? extends Integer> entry) {
                    for (FileTableModel.Column c: FileTableModel.Column.values()) {
                        if (c.renderer.convertToString(entry.getValue(c.ordinal())).toLowerCase().contains(currentSearch.searchString))
                            return true;
                    }
                    return false;
                }
            });
        }
        for (FileTableModel.Column c: FileTableModel.Column.values()) {
            c.renderer.setSearchText(currentSearch.searchString);
        }
    }
    public FileRenderApp(final AppContext context) {
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
                String text = searchField.getText().trim().toLowerCase();
                updateTextSearch(new TextSearch(text, currentSearch.searchColumns, currentSearch.excludeMatches));
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update();
            }
        });


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
        //topPanel.add(new JLabel("Search:"));
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

        frame.add(tabbedPane);
        context.filesAndTimestamps.keySet().forEach(tableModel::addFile);

        frame.setVisible(true);
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
