package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.CubeArrayMain;
import org.ironsight.CubeArray.ResourceUtils;
import org.ironsight.CubeArray.SchemReader;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.stream.Collectors;

import org.ironsight.CubeArray.InstancedCubes;

public class FileRenderApp {
    // main data structure
    private final AppContext context;
    //is context dirty and needs to be saved?
    private boolean contextDirtyFlag;

    // UI model
    private final FileTableModel tableModel = new FileTableModel();
    private final JTable fileTable = new JTable(tableModel);

    private final TableRowSorter<FileTableModel> rowSorter =
            new TableRowSorter<>(tableModel);


    public static void startApp(final AppContext context) {
        SwingUtilities.invokeLater(()-> new FileRenderApp(context));
    }
    final JFrame frame;
    public FileRenderApp(final AppContext context) {
        CubeArrayMain.periodicChecker.addCallback(this::onPeriodicCheck);

        this.context = context;
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


        JTextField searchField = new JTextField(20);
        searchField.setText("Search");
        searchField.putClientProperty("JTextField.placeholderText", "Search...");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
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

            public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });


        JScrollPane scrollPane = new JScrollPane(fileTable);

        JButton addBtn = new JButton("Add");
        JButton removeBtn = new JButton("Remove");
        JButton renderBtn = new JButton("Render");

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

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        context.filesAndTimestamps.keySet().forEach(tableModel::addFile);

        frame.setVisible(true);
    }

    private void addFiles(Component parent) {
        JFileChooser chooser = getFileChooser();
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                if (!context.filesAndTimestamps.containsKey(f)) {
                    tableModel.addFile(f);
                    context.filesAndTimestamps.put(f,System.currentTimeMillis());
                }
            }
        }
        context.lastSearchPath = chooser.getCurrentDirectory();
        contextDirtyFlag = true;
    }

    private JFileChooser getFileChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(context.lastSearchPath);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                for (String type : ResourceUtils.SUPPORTED_FILE_TYPES) {
                    if (f.getPath().endsWith(type))
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

    private void removeSelectedFiles() {
        int[] viewRows = fileTable.getSelectedRows();
        for (int viewRow : viewRows) {
            int modelRow = fileTable.convertRowIndexToModel(viewRow);
            File f = tableModel.getFileAt(modelRow);
            context.filesAndTimestamps.remove(f);
            tableModel.removeFile(f);
        }
        contextDirtyFlag = true;
    }

    private void renderSelectedFiles() {
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
                        SwingUtilities.invokeLater(()->{
                            JOptionPane.showMessageDialog(frame, "Error: unable to load schematics from selected files. Maybe the file type is not supported or does not exist anymore?");
                        });
                        return;
                    }
                    new InstancedCubes(setup).run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            glThread.start();

        } catch (Exception ex) {
            System.out.println("Error: " + ex.getMessage());
        }

    }

    void onPeriodicCheck() {
        // WARNING: this runs on the background thread NOT the gui thread!
        if (contextDirtyFlag) {
            contextDirtyFlag = false;
            System.out.println("WRITE CONTEXT TO FILE");
            AppContext.write(this.context);
        }
    }
}
