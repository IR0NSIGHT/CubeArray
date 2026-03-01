package org.ironsight.CubeArray.swing;

import org.ironsight.CubeArray.PeriodicChecker;
import org.ironsight.CubeArray.SchemReader;
import org.joml.Vector3f;
import org.pepsoft.worldpainter.layers.bo2.Schem;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class FileTableModel extends AbstractTableModel {
    private final static StringConverter dateRenderer = new StringConverter() {
        final  SimpleDateFormat sdf = new SimpleDateFormat("HH:mm, EEE dd MMM yyyy");
        @Override
        public String convertToString(Object o) {
            if (o instanceof Date date) {
                String formatted = sdf.format(date);
                return formatted;
            }
            return super.convertToString(o);
        }
    };

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

        IntStream.range(0, copyFiles.size())
                .mapToObj(i -> Map.entry(i, copyFiles.get(i)))
                .filter(entry -> !schematicObjects.containsKey(entry.getValue()))
                .sorted(Comparator.comparingLong(k -> k.getValue().length()))
                .forEach(entry -> {
                    int i = entry.getKey();
                    File f = entry.getValue();
                    System.out.println("Loading " + f.getName() + " size=" + f.length());

                    // check and load each files schematic if necessary.
                    try {
                        var schems = SchemReader.loadSchematics(List.of(f.toPath()));
                        for (WPObject schem : schems)
                            schematicObjects.put(f, schem);
                        final int ii = i;
                        SwingUtilities.invokeLater(() -> {
                            fireTableRowsUpdated(ii, ii);
                            if (remainingFileCountChangedCallback != null) {
                                int remainingCount = files.size() - schematicObjects.size();
                                remainingFileCountChangedCallback.accept(remainingCount);
                            }
                        });
                        Thread.sleep(1000);
                    } catch (IOException | InvalidPathException ex) {
                        System.err.println("unable to load schematic from file: " + f);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private Consumer<Integer> remainingFileCountChangedCallback; // int = remainingFiles
    public void setFileQueueSizeChangedCallback(Consumer<Integer> callback) {
        this.remainingFileCountChangedCallback = callback;
    }

    public void flagReloadFile(int modelRow) {
        schematicObjects.remove(getFile(modelRow));
        fireTableRowsUpdated(modelRow,modelRow);
        if (remainingFileCountChangedCallback != null) {
            int remainingCount = files.size() - schematicObjects.size();
            remainingFileCountChangedCallback.accept(remainingCount);
        }
    }

    public File getFile(int modelRow) {
        if (modelRow < 0 || modelRow >= files.size())
            return null;
        return files.get(modelRow);
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
        return CaColumn.values().length;
    }

    public CaColumn getColumn(int columnIdx) {
        return CaColumn.values()[columnIdx];
    }

    @Override
    public Object getValueAt(int row, int col) {
        assert row >= 0 : "row to small: " + row;
        assert row < files.size() : "row to big: " + row + ", " + files.size();

        if (col >= CaColumn.values().length)
            return null;

        File f = files.get(row);
        WPObject obj = getSchematicFor(f);
        return switch (CaColumn.values()[col]) {
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
    public boolean isFileLoaded(int modelRow) {
        return schematicObjects.containsKey(getFile(modelRow));
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
        if (column >= CaColumn.values().length)
            return "";
        return CaColumn.values()[column].displayName;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        if (column >= CaColumn.values().length)
            return Object.class;
        return CaColumn.values()[column].clazz;
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

    /**
     * hardcoded enum list of columns that the table (and model) can display.
     * comes with all info required to store across restarts and display to user
     */
    enum CaColumn {
        FILE("File", String.class, defaultRenderer, "Name of the file",240),
        LAST_CHANGED("Last Changed", Date.class, dateRenderer, "Date when the file was last modified",135),
        FILE_TYPE("File Type", String.class, defaultRenderer, "File extension",50),
        FILE_SIZE("File Size (MB)", Long.class, fileSizeRenderer, "Size of the file",60),
        DIMENSION_WIDTH("Width", Integer.class, dimensionRenderer, "Width of the schematic (meters)",40),
        DIMENSION_HEIGHT("Height", Integer.class, dimensionRenderer, "Height of the schematic (meters)",45),
        DIMENSION_DEPTH("Depth", Integer.class, dimensionRenderer, "Depth of the schematic (meters)",40),
        DIMENSION_DIAGONAL("Diagonal", Integer.class, dimensionRenderer, "Diagonal of the schematic from edge to edge (meters)",45),
        PATH("Path", String.class, defaultRenderer, "Filepath where the file lives",600),
        BLOCKS("Blocks", List.class, stringListRenderer, "Blocktypes that are used in the schematic",500),
        ENTITIES("Entities", List.class, stringListRenderer, "Entities in the schematic",200),
        TILE_ENTITIES("Tile Entities", List.class, stringListRenderer, "Tile Entities in the schematic",200),
        ATTRIBUTES("Attributes", HashMap.class, attributesRenderer, "NBT Attributes attached to the schematic",100),
        ;
        final String displayName;
        final Class<?> clazz;
        final String tooltip;
        final StringConverter renderer;
        final int defaultWidth;
        private CaColumn(String name, Class<?> clazz, StringConverter renderer, String tooltip, int defaultWidth) {
            this.tooltip = tooltip;
            this.displayName = name;
            this.clazz = clazz;
            this.defaultWidth = defaultWidth;
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

        private String searchText = "";

        public void setSearchText(String searchText) {
            this.searchText = searchText != null ? searchText : "";
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (searchText == null || searchText.isEmpty()) {
                setForeground(Color.BLACK);
                super.paintComponent(g);
                return;
            }

            String text = getText();
            if (text == null) {
                super.paintComponent(g);
                return;
            }

            String lowerText = text.toLowerCase();
            int index = lowerText.indexOf(searchText);

            if (index < 0) {
                setForeground(Color.GRAY);
                super.paintComponent(g);
                return;
            }
            setForeground(Color.BLACK);

            // draw a yellow filled rect into the background where the matchign string will be
            FontMetrics fm = g.getFontMetrics();
            Insets insets = getInsets();

            int textX = insets.left;
            int textY = insets.top + fm.getAscent();

            // Measure prefix width
            String prefix = text.substring(0, index);
            int prefixWidth = fm.stringWidth(prefix);

            // Measure match width
            String match = text.substring(index, index + searchText.length());
            int matchWidth = fm.stringWidth(match);

            int highlightX = textX + prefixWidth;
            int highlightY = insets.top;
            int highlightHeight = fm.getHeight();

            // Draw highlight background
            g.setColor(Color.YELLOW);
            g.fillRect(highlightX, highlightY, matchWidth, highlightHeight);

            // Draw the default text on top of the highlighted background
            g.setColor(getForeground());
            super.paintComponent(g);
        }
    }
}
