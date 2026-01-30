package org.ironsight.CubeArray.swing;

import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

class FileTableModel extends AbstractTableModel {
    private final List<File> files = new java.util.ArrayList<>();

    @Override
    public int getRowCount() {
        return files.size();
    }

    @Override
    public int getColumnCount() {
        return 5;
    }

    @Override
    public Object getValueAt(int row, int col) {
        assert row >= 0 : "row to small: " + row;
        assert row < files.size() : "row to big: " + row + ", " + files.size();
        File f = files.get(row);

        return switch (col) {
            case 0 -> f.getName();
            case 1 -> f.getAbsolutePath();
            case 2 -> new Date(f.lastModified());   // or format later
            case 3 -> getFileExtension(f);
            case 4 -> getSizeBytes(f);
            default -> null;
        };
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

    private static double bytesToMB(long bytes) {
        return Math.round((bytes / (1024.0 * 1024.0)) * 100.0) / 100.0;
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case 0 -> "File";
            case 1 -> "Path";
            case 2 -> "Last Changed";
            case 3 -> "Type";
            case 4 -> "Size (MB)";
            default -> "";
        };
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
            if (i >= 0) fireTableRowsDeleted(i, i);
            this.files.remove(file);
            assert !this.files.contains(file);
        }
    }
}
