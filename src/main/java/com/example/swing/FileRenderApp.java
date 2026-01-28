package com.example.swing;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class FileRenderApp {
    // main data structure
    private final AppContext context;

    // UI model
    private final DefaultListModel<File> listModel = new DefaultListModel<>();
    private final JList<File> fileList = new JList<>(listModel);

    public static void startApp(final AppContext context) {
        SwingUtilities.invokeLater(()-> new FileRenderApp(context));
    }

    public FileRenderApp(final AppContext context) {
        this.context = context;
        JFrame frame = new JFrame("File Renderer");
        frame.setSize(context.guiBounds.width, context.guiBounds.height);
        frame.setLocation(context.guiBounds.x, context.guiBounds.y);

        // Add listener
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Rectangle bounds = frame.getBounds();
                System.out.println("Resized: " + bounds.width + "x" + bounds.height);
                context.guiBounds = bounds;
                SwingUtilities.invokeLater(()->AppContext.write(context));
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                Rectangle bounds = frame.getBounds();
                System.out.println("Moved: " + bounds.x + "," + bounds.y);
                context.guiBounds = bounds;
                SwingUtilities.invokeLater(()->AppContext.write(context));

            }
        });

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Show only file names
        fileList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.getName());
            if (isSelected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });

        JScrollPane scrollPane = new JScrollPane(fileList);

        JButton addBtn = new JButton("Add");
        JButton removeBtn = new JButton("Remove");
        JButton renderBtn = new JButton("Render");

        addBtn.addActionListener(e -> addFiles(frame));
        removeBtn.addActionListener(e -> removeSelectedFiles());
        renderBtn.addActionListener(e -> renderSelectedFiles());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(addBtn);
        topPanel.add(removeBtn);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(renderBtn);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        listModel.addAll(context.filesAndTimestamps.keySet());

        frame.setVisible(true);
    }

    private void addFiles(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(context.lastSearchPath);
        chooser.setMultiSelectionEnabled(true);

        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                if (!context.filesAndTimestamps.containsKey(f)) {
                    listModel.addElement(f);
                    context.filesAndTimestamps.put(f,System.currentTimeMillis());
                }
            }
        }
        context.lastSearchPath = chooser.getCurrentDirectory();
        AppContext.write(context);
    }

    private void removeSelectedFiles() {
        List<File> selected = fileList.getSelectedValuesList();
        for (File f : selected) {
            context.filesAndTimestamps.remove(f);
            listModel.removeElement(f);
        }
        AppContext.write(context);
    }

    private void renderSelectedFiles() {
        List<File> selected = fileList.getSelectedValuesList();
        context.activeFiles.clear();
        context.activeFiles.addAll(selected);
        AppContext.write(context);

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
    }
}
