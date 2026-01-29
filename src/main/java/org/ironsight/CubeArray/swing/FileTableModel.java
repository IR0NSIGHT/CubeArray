package org.ironsight.CubeArray.swing;

import javax.swing.table.AbstractTableModel;
import java.io.File;
import java.util.List;

class FileTableModel extends AbstractTableModel {
    private final List<File> files = new java.util.ArrayList<>();

    @Override
    public int getRowCount() {
        return files.size();
    }

    @Override
    public int getColumnCount() {
        return 2;  // now 2 columns: "File" + "Path"
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case 0 -> "File";        // just name
            case 1 -> "Path";        // full path
            default -> "";
        };
    }

    @Override
    public Object getValueAt(int row, int col) {
        File f = files.get(row);
        return switch (col) {
            case 0 -> f.getName();   // display name
            case 1 -> f.getAbsolutePath(); // full path
            default -> null;
        };
    }

    public File getFileAt(int row) {
        return files.get(row);
    }

    public void addFile(File f) {
        files.add(f);
        fireTableRowsInserted(files.size() - 1, files.size() - 1);
    }

    public void removeFile(File f) {
        int i = files.indexOf(f);
        if (i >= 0) fireTableRowsDeleted(i, i);
        files.remove(f);
    }

    public List<File> getFiles(int[] rows) {
        return java.util.Arrays.stream(rows)
                .mapToObj(this::getFileAt)
                .toList();
    }
}
