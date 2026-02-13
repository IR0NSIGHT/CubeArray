package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.PeriodicChecker;
import org.ironsight.CubeArray.SchemReader;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.pepsoft.worldpainter.layers.bo2.Schem;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.util.*;
import java.util.stream.Collectors;

class FileTableModel extends AbstractTableModel {

    private final static StringConverter defaultRenderer = new StringConverter() {
        @Override
        public String convertToString(Object o) {
            return o.toString();
        }
    };
    private final static StringConverter attributesRenderer = new StringConverter() {
        @Override
        public String convertToString(Object value) {
            if (value instanceof HashMap<?, ?> map) {
                return
                        map.entrySet().stream()
                                .filter(Objects::nonNull)
                                .map(entry -> entry.getKey() + "=" + entry.getValue())
                                .sorted()
                                .collect(Collectors.joining(", "))
                        ;
            }
            return super.convertToString(value);
        }
    };
    private static final StringConverter fileSizeRenderer = new StringConverter() {

        @Override
        public String convertToString(Object value) {
            if (value instanceof Long bytes) {
                return formatSize(bytes);
            } else {
                return super.convertToString(value);
            }
        }
    };
    private static final StringConverter stringListRenderer = new StringConverter() {
        @Override
        public String convertToString(Object value) {
            if (value instanceof List<?> list) {
                return list.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining(", "));
            } else {
                return super.convertToString(value);
            }
        }
    };
    public static int NO_VALUE = -1;
    private static final StringConverter dimensionRenderer = new StringConverter() {
        @Override
        public String convertToString(Object value) {
            if (value instanceof Integer dim && dim != NO_VALUE) {
                return dim.toString();
            } else {
                return super.convertToString(value);
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
            case DIMENSION_WIDTH -> obj == null ? NO_VALUE : obj.getDimensions().x;
            case DIMENSION_HEIGHT -> obj == null ? NO_VALUE : obj.getDimensions().z;
            case DIMENSION_DEPTH -> obj == null ? NO_VALUE : obj.getDimensions().y;
            case DIMENSION_DIAGONAL -> {
                if (obj == null)
                    yield NO_VALUE;
                yield Math.round(new Vector3f(obj.getDimensions().x, obj.getDimensions().y, obj.getDimensions().z).length());
            }
            case BLOCKS -> {
                if (obj == null)
                    yield List.of();
                yield obj.getAllMaterials().stream()
                        .filter(Objects::nonNull)
                        .map(m -> m.simpleName)
                        .distinct()
                        .sorted()
                        .toList();
            }

            case ATTRIBUTES -> {
                if (obj instanceof Schem schematic) {
                    yield schematic.getAttributes();
                } else {
                    yield List.of();
                }
            }

            case ENTITIES -> {
                if (obj != null && obj.getEntities() != null) {
                    yield obj.getEntities().stream()
                            .map(e -> e.getId() + " [" +
                                    Arrays.stream(e.getPos())
                                            .mapToLong(Math::round)
                                            .mapToObj(Long::toString).collect(Collectors.joining(", "))
                                    + "]"
                            ).distinct().sorted().toList();
                } else
                    yield List.of();
            }

            case TILE_ENTITIES -> {
                if (obj != null && obj.getTileEntities() != null) {
                    yield obj.getTileEntities().stream().map(te ->
                            te.getId() + "[" + te.getX() + "," + te.getY() + "," + te.getZ() + "]"
                    ).distinct().sorted().toList();
                } else
                    yield List.of();
            }

            default -> {
                assert false : "incomplete enum";
                yield null;
            }
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
        FILE("File", String.class, defaultRenderer, "Name of the file"),
        LAST_CHANGED("Last Changed", Date.class, defaultRenderer, "Date when the file was last modified"),
        FILE_TYPE("File Type", String.class, defaultRenderer, "File extension"),
        FILE_SIZE("File Size (MB)", Long.class, fileSizeRenderer, "Size of the file"),
        DIMENSION_WIDTH("Width", Integer.class, dimensionRenderer, "Width of the schematic (meters)"),
        DIMENSION_HEIGHT("Height", Integer.class, dimensionRenderer, "Height of the schematic (meters)"),
        DIMENSION_DEPTH("Depth", Integer.class, dimensionRenderer, "Depth of the schematic (meters)"),
        DIMENSION_DIAGONAL("Diagonal", Integer.class, dimensionRenderer, "Diagonal of the schematic from edge to edge (meters)"),
        PATH("Path", String.class, defaultRenderer, "Filepath where the file lives"),
        BLOCKS("Blocks", List.class, stringListRenderer, "Blocktypes that are used in the schematic"),
        ENTITIES("Entities", List.class, stringListRenderer, "Entities in the schematic"),
        TILE_ENTITIES("Tile Entities", List.class, stringListRenderer, "Tile Entities in the schematic"),
        ATTRIBUTES("Attributes", HashMap.class, defaultRenderer, "NBT Attributes attached to the schematic"),
        ;
        final String displayName;
        final Class<?> clazz;
        final String tooltip;
        final StringConverter renderer;

        private Column(String name, Class<?> clazz, StringConverter renderer, String tooltip) {
            this.tooltip = tooltip;
            this.displayName = name;
            this.clazz = clazz;

            this.renderer = renderer;

        }
    }

    static abstract class StringConverter extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            setText(convertToString(value));
        }

        ;

        public String convertToString(Object o) {
            return "?";
        }
    }
}
