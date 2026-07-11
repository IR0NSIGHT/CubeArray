package org.ironsight.cubearray.ui;

import org.ironsight.cubearray.preview.SchematicPreviewHelper;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import org.ironsight.cubearray.platform.AppLogger;
import org.ironsight.cubearray.platform.PeriodicChecker;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.schematic.SchemReader;
import org.joml.Vector3f;
import org.pepsoft.worldpainter.layers.bo2.Schem;
import org.pepsoft.worldpainter.objects.WPObject;

class FileTableModel extends AbstractTableModel {
  private final SchematicPreviewHelper previewHelper;

  private static final Logger logger = AppLogger.get(FileTableModel.class);
  public static final StringConverter dateRenderer =
      new StringConverter() {
        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm, EEE dd MMM yyyy");

        @Override
        public String convertToString(Object o) {
          if (o instanceof Date date) {
            String formatted = sdf.format(date);
            return formatted;
          }
          return super.convertToString(o);
        }
      };

  public static final StringConverter defaultRenderer =
      new StringConverter() {
        @Override
        public String convertToString(Object o) {
          return o.toString();
        }
      };
  public static final StringConverter attributesRenderer =
      new StringConverter() {
        @Override
        public String convertToString(Object value) {
          if (value instanceof HashMap<?, ?> map) {
            return map.entrySet().stream()
                .filter(Objects::nonNull)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .sorted()
                .collect(Collectors.joining(", "));
          }
          return super.convertToString(value);
        }
      };
  public static final StringConverter fileSizeRenderer =
      new StringConverter() {

        @Override
        public String convertToString(Object value) {
          if (value instanceof Long bytes) {
            return formatSize(bytes);
          } else {
            return super.convertToString(value);
          }
        }
      };
  public static final StringConverter stringListRenderer =
      new StringConverter() {
        @Override
        public String convertToString(Object value) {
          if (value instanceof List<?> list) {
            return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.joining(", "));
          } else {
            return super.convertToString(value);
          }
        }
      };
  public static int NO_VALUE = -1;
  public static final StringConverter iconRenderer =
      new StringConverter() {
        @Override
        protected void setValue(Object value) {
          if (value instanceof Icon icon) {
            setIcon(icon);
            setText("");
          } else {
            setIcon(null);
            setText("");
          }
        }

        @Override
        public String convertToString(Object o) {
          return "";
        }
      };
  public static final StringConverter dimensionRenderer =
      new StringConverter() {
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
  private final HashSet<File> loadingFiles = new HashSet<>();

  public FileTableModel(PeriodicChecker checker, SchematicPreviewHelper previewHelper) {
    this.previewHelper = previewHelper;
    if (checker != null) checker.addCallback(this::tryLoadSchematics);
  }

  private final HashSet<File> errorFiles = new HashSet<>();

  private void flagAsError(File file) {
    errorFiles.add(file);
    loadingFiles.remove(file);
  }

  /** this method will be called from another thread */
  public void tryLoadSchematics() {
    List<File> copyFiles;
    synchronized (files) {
      copyFiles = List.copyOf(files);
    }

    IntStream.range(0, copyFiles.size())
        .mapToObj(i -> Map.entry(i, copyFiles.get(i)))
        .filter(entry -> !errorFiles.contains(entry.getValue()))
        .filter(entry -> !schematicObjects.containsKey(entry.getValue()))
        .sorted(Comparator.comparingLong(k -> k.getValue().length()))
        .forEach(
            entry -> {
              int i = entry.getKey();
              File f = entry.getValue();
              logger.info("Loading " + f.getName() + " size=" + f.length());

              loadingFiles.add(f);
              final int loadingIdx = i;
              SwingUtilities.invokeLater(
                  () -> {
                    if (loadingIdx < files.size()) fireTableRowsUpdated(loadingIdx, loadingIdx);
                  });

              // check and load each files schematic if necessary.
              try {
                var schems = SchemReader.loadSchematics(List.of(f.toPath()), this::flagAsError);
                loadingFiles.remove(f);
                for (WPObject schem : schems) {
                  schematicObjects.put(f, schem);
                  if (onSchematicLoadedCallback != null) onSchematicLoadedCallback.accept(f);
                }
                final int ii = i;
                SwingUtilities.invokeLater(
                    () -> {
                      if (ii >= files.size()) return;
                      fireTableRowsUpdated(
                          ii, ii); // FIXME the index might have changed, get current index of file
                      if (remainingFileCountChangedCallback != null) {
                        int remainingCount = files.size() - schematicObjects.size();
                        remainingFileCountChangedCallback.accept(remainingCount);
                      }
                    });
              } catch (IOException | InvalidPathException ex) {
                logger.log(Level.SEVERE, "unable to load schematic from file: " + f, ex);
                loadingFiles.remove(f);
                errorFiles.add(f);
                final int ii = i;
                SwingUtilities.invokeLater(
                    () -> {
                      if (ii < files.size()) fireTableRowsUpdated(ii, ii);
                    });
              }
            });
  }

  private Consumer<Integer> remainingFileCountChangedCallback; // int = remainingFiles
  private Consumer<File> onSchematicLoadedCallback;

  public void setFileQueueSizeChangedCallback(Consumer<Integer> callback) {
    this.remainingFileCountChangedCallback = callback;
  }

  public void setOnSchematicLoadedCallback(Consumer<File> callback) {
    this.onSchematicLoadedCallback = callback;
  }

  public void flagReloadFile(int modelRow) {
    File file = getFile(modelRow);
    schematicObjects.remove(file);
    errorFiles.remove(file);
    loadingFiles.remove(file);
    fireTableRowsUpdated(modelRow, modelRow);
    if (remainingFileCountChangedCallback != null) {
      int remainingCount = files.size() - schematicObjects.size();
      remainingFileCountChangedCallback.accept(remainingCount);
    }
  }

  public File getFile(int modelRow) {
    if (modelRow < 0 || modelRow >= files.size()) return null;
    return files.get(modelRow);
  }

  private static String formatPos(int x, int y, int z) {
    return x + ", " + y + ", " + z;
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

    if (col >= CaColumn.values().length) return null;

    File f = files.get(row);
    WPObject obj = getSchematicFor(f);
    return switch (CaColumn.values()[col]) {
      case ICON -> previewHelper.getIcon(f);
      case FILE -> f.getName();
      case PATH -> f.getAbsolutePath();
      case FILE_SIZE -> getSizeBytes(f);
      case FILE_TYPE -> ResourceUtils.detectSchematicType(f);
      case LAST_CHANGED -> new Date(f.lastModified());
      case DIMENSION_WIDTH -> obj == null ? NO_VALUE : obj.getDimensions().x;
      case DIMENSION_HEIGHT -> obj == null ? NO_VALUE : obj.getDimensions().z;
      case DIMENSION_DEPTH -> obj == null ? NO_VALUE : obj.getDimensions().y;
      case DIMENSION_DIAGONAL -> {
        if (obj == null) yield NO_VALUE;
        yield Math.round(
            new Vector3f(obj.getDimensions().x, obj.getDimensions().y, obj.getDimensions().z)
                .length());
      }
      case OFFSET -> {
        if (obj == null) yield "";
        var o = obj.getOffset();
        // Point3i axes are (x=width, y=depth, z=height); present in Minecraft order (x, y-up, z)
        yield formatPos(o.x, o.z, o.y);
      }
      case MIN_POS -> {
        if (obj == null) yield "";
        var o = obj.getOffset();
        yield formatPos(o.x, o.z, o.y);
      }
      case MAX_POS -> {
        if (obj == null) yield "";
        var o = obj.getOffset();
        var d = obj.getDimensions();
        yield formatPos(o.x + d.x - 1, o.z + d.z - 1, o.y + d.y - 1);
      }
      case BLOCKS -> {
        if (obj == null) yield List.of();
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
              .map(
                  e ->
                      e.getId()
                          + " ["
                          + Arrays.stream(e.getPos())
                              .mapToLong(Math::round)
                              .mapToObj(Long::toString)
                              .collect(Collectors.joining(", "))
                          + "]")
              .distinct()
              .sorted()
              .toList();
        } else yield List.of();
      }

      case TILE_ENTITIES -> {
        if (obj != null && obj.getTileEntities() != null) {
          yield obj.getTileEntities().stream()
              .map(te -> te.getId() + "[" + te.getX() + "," + te.getY() + "," + te.getZ() + "]")
              .distinct()
              .sorted()
              .toList();
        } else yield List.of();
      }

      case LOADING_STATE -> {
        if (errorFiles.contains(f)) yield "failed";
        if (schematicObjects.containsKey(f)) yield "loaded";
        if (loadingFiles.contains(f)) yield "loading";
        yield "pending";
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
    return (dot > 0 && dot < name.length() - 1) ? name.substring(dot + 1).toLowerCase() : "";
  }

  @Override
  public String getColumnName(int column) {
    if (column >= CaColumn.values().length) return "";
    return CaColumn.values()[column].displayName;
  }

  @Override
  public Class<?> getColumnClass(int column) {
    if (column >= CaColumn.values().length) return Object.class;
    return CaColumn.values()[column].clazz;
  }

  public File getFileAt(int row) {
    return files.get(row);
  }

  public int indexOfFile(File f) {
    return files.indexOf(f);
  }

  public void invalidateIconCache(File f) {
    previewHelper.invalidateIcon(f);
  }

  public void addFile(File f) {
    if (f != null && !files.contains(f)) {
      files.add(f);
      fireTableRowsInserted(files.size() - 1, files.size() - 1);
    }
  }

  public void removeFile(File... files) {
    for (File file : files) {
      int i = this.files.indexOf(file);
      this.files.remove(file);
      this.schematicObjects.remove(file);
      this.errorFiles.remove(file);
      this.loadingFiles.remove(file);

      if (i >= 0) fireTableRowsDeleted(i, i);
      assert !this.files.contains(file);
    }
  }

  abstract static class StringConverter extends DefaultTableCellRenderer {
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

      // Measure prefix width
      String prefix = text.substring(0, index);
      int prefixWidth = fm.stringWidth(prefix);

      // Measure match width
      String match = text.substring(index, index + searchText.length());
      int matchWidth = fm.stringWidth(match);

      int highlightX = textX + prefixWidth;
      int highlightY = insets.top + (getHeight() - insets.top - insets.bottom - fm.getHeight()) / 2;
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
