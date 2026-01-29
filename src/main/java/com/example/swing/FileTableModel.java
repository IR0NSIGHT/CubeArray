package com.example.swing;

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
        return 1;
    }

    @Override
    public String getColumnName(int column) {
        return "File";
    }

    @Override
    public Object getValueAt(int row, int col) {
        return files.get(row);
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
        if (i >= 0) {
            files.remove(i);
            fireTableRowsDeleted(i, i);
        }
    }

    public List<File> getFiles(int[] rows) {
        return java.util.Arrays.stream(rows)
                .mapToObj(this::getFileAt)
                .toList();
    }
}
