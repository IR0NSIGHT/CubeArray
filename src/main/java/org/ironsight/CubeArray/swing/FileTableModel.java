package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.PeriodicChecker;
import org.ironsight.CubeArray.SchemReader;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.vecmath.Point3i;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

class FileTableModel extends AbstractTableModel {
    private final static DefaultTableCellRenderer fileSizeRenderer = new DefaultTableCellRenderer() {
        @Override
        protected void setValue(Object value) {
            if (value instanceof Long bytes) {
                setText(formatSize(bytes));
            } else {
                setText("");
            }
        }
    };
    private final static DefaultTableCellRenderer dimensionRenderer = new DefaultTableCellRenderer() {
        @Override
        protected void setValue(Object value) {
            if (value instanceof Point3i dimension) {
                setText(dimension.x + " x " + dimension.y + " x " + dimension.z);
            } else {
                setText("");
            }
        }
    };
    private final List<File> files = new java.util.ArrayList<>();
    private final HashMap<File, WPObject> schematicObjects = new HashMap<>();

    public FileTableModel(PeriodicChecker checker) {
        if (checker != null)
            checker.addCallback(this::tryLoadSchematics);
    }

    /**
     * this method will be called from another thread
     */
    public void tryLoadSchematics() {
        List<File> copyFiles;
        synchronized (files) {
            copyFiles = List.copyOf(files);
        }

        // check and load each files schematic if necessary.
        for (int i = 0; i < copyFiles.size(); i++) {
            File f = copyFiles.get(i);
            if (schematicObjects.containsKey(f))
                continue;

            try {
                var schems = SchemReader.loadSchematics(List.of(f.toPath()));
                for (WPObject schem : schems)
                    schematicObjects.put(f, schem);
                final int ii = i;
                SwingUtilities.invokeLater(() -> {
                    fireTableRowsUpdated(ii, ii);
                });
            } catch (IOException | InvalidPathException ex) {
                System.err.println("unable to load schematic from file: " + f);
            }
        }

    }

    private static String formatSize(long bytes) {
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);

        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);

        double gb = mb / 1024.0;
        return String.format("%.1f GB", gb);
    }

    @Override
    public int getRowCount() {
        return files.size();
    }

    @Override
    public int getColumnCount() {
        return Column.values().length;
    }

    @Override
    public Object getValueAt(int row, int col) {
        assert row >= 0 : "row to small: " + row;
        assert row < files.size() : "row to big: " + row + ", " + files.size();

        if (col >= Column.values().length)
            return null;

        File f = files.get(row);
        WPObject obj = getSchematicFor(f);
        return switch (Column.values()[col]) {
            case FILE -> f.getName();
            case PATH -> f.getAbsolutePath();
            case FILE_SIZE -> getSizeBytes(f);
            case FILE_TYPE -> getFileExtension(f);
            case LAST_CHANGED -> new Date(f.lastModified());
            case DIMENSION -> obj == null ? new Vector3f(0, 0, 0) : obj.getDimensions();

            default -> null;
        };
    }

    public WPObject getSchematicFor(File f) {
        return schematicObjects.get(f);
    }

    private static long getSizeBytes(File f) {
        try {
            if (!f.isFile()) {
                return 0;
            }
            return java.nio.file.Files.size(f.toPath());
        } catch (IOException e) {
            return 0;
        }
    }

    private static String getFileExtension(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return (dot > 0 && dot < name.length() - 1)
                ? name.substring(dot + 1).toLowerCase()
                : "";
    }

    @Override
    public String getColumnName(int column) {
        if (column >= Column.values().length)
            return "";
        return Column.values()[column].displayName;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        if (column >= Column.values().length)
            return Object.class;
        return Column.values()[column].clazz;
    }

    public File getFileAt(int row) {
        return files.get(row);
    }

    public void addFile(File f) {
        if (!files.contains(f)) {
            files.add(f);
            fireTableRowsInserted(files.size() - 1, files.size() - 1);
        }
    }

    public void removeFile(File... files) {
        for (File file : files) {
            int i = this.files.indexOf(file);
            this.files.remove(file);
            this.schematicObjects.remove(file);

            if (i >= 0) fireTableRowsDeleted(i, i);
            assert !this.files.contains(file);
        }
    }

    enum Column {
        FILE("File", String.class, null),
        LAST_CHANGED("Last Changed", Date.class, null),
        FILE_TYPE("File Type", String.class, null),
        FILE_SIZE("File Size (MB)", Long.class, fileSizeRenderer),
        DIMENSION("Dimension", Point3i.class, dimensionRenderer),
        PATH("Path", String.class, null),
        ;
        final String displayName;
        final Class<?> clazz;
        final DefaultTableCellRenderer renderer;

        private Column(String name, Class<?> clazz, @Nullable DefaultTableCellRenderer renderer) {
            this.displayName = name;
            this.clazz = clazz;
            if (renderer == null) {
                this.renderer = new DefaultTableCellRenderer();
            } else {
                this.renderer = renderer;
            }
        }
    }
}
