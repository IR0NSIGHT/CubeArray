package org.ironsight.CubeArray.swing;

import static org.ironsight.CubeArray.ResourceUtils.isSupportedSchematicType;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import org.ironsight.CubeArray.AppLogger;
import org.ironsight.CubeArray.CubeArrayMain;
import org.ironsight.CubeArray.InstancedCubes;
import org.ironsight.CubeArray.ResourceUtils;
import org.ironsight.CubeArray.SchemReader;
import org.ironsight.schemEdit.BatchConverter;
import org.ironsight.schemEdit.BlockReplacer;
import org.pepsoft.worldpainter.objects.WPObject;

public class FileRenderApp {
  private static final Logger logger = AppLogger.get(FileRenderApp.class);
  final JFrame frame;
  // main data structure
  private AppContext context;
  // UI model
  private final FileTableModel tableModel;
  private final JTable fileTable;

  private final TableRowSorter<FileTableModel> rowSorter;

  private final Set<Thread> loadingThreads = new HashSet<>();
  // is context dirty and needs to be saved?
  private boolean contextDirtyFlag;
  private final HashMap<CaColumn, TableColumn> columToTableColumn = new HashMap<>();
  private final ExecutorService renderExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "render-worker");
            t.setDaemon(true);
            return t;
          });

  private void flagContextDirty(AppContext context) {
    this.context = context;
    contextDirtyFlag = true;
  }

  private record TextSearch(
      String searchString, List<CaColumn> searchCaColumns, boolean excludeMatches) {
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

      rowSorter.setRowFilter(
          new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends FileTableModel, ? extends Integer> entry) {
              for (CaColumn c : context.columnContext().displayedColumns()) {
                if (c.renderer
                    .convertToString(entry.getValue(c.ordinal()))
                    .toLowerCase()
                    .contains(currentSearch.searchString)) return true;
              }
              return false;
            }
          });
    }
    for (CaColumn c : CaColumn.values()) {
      c.renderer.setSearchText(currentSearch.searchString);
    }
  }

  private final JLabel topInfoLabel;

  public FileRenderApp(final AppContext initialContext) {
    this.context = initialContext;
    if (context.neverBeforeUsed()) {
      // add default schematics on very first use
      var newFilesAndTimestamps = new HashMap<File, Long>();
      ResourceUtils.getDefaultSchematics()
          .forEach(s -> newFilesAndTimestamps.put(s.toFile(), System.currentTimeMillis()));
      AppContext newContext =
          new AppContext(
              newFilesAndTimestamps,
              context.lastSearchPath(),
              context.guiBounds(),
              false,
              context.columnContext());
      flagContextDirty(newContext);
    }

    CubeArrayMain.periodicChecker.addCallback(this::checkContextSaving);
    CubeArrayMain.periodicChecker.addCallback(this::checkLoadingThreads);

    this.tableModel = new FileTableModel(CubeArrayMain.periodicChecker);
    tableModel.setFileQueueSizeChangedCallback(
        count -> {
          if (count == 0) this.setTextRemainingFiles("");
          else this.setTextRemainingFiles("Loading " + count + " file(s)");
        });
    tableModel.setOnSchematicLoadedCallback(this::renderSchematicIcon);

    this.fileTable = new JTable(tableModel);
    this.rowSorter = new TableRowSorter<>(tableModel);
    rowSorter.setComparator(CaColumn.ICON.ordinal(), (a, b) -> 0);

    tableAddMouseClickListener(fileTable);

    fileTable
        .getColumnModel()
        .addColumnModelListener(
            new TableColumnModelListener() {

              @Override
              public void columnAdded(TableColumnModelEvent e) {
                updateContextColumns(fileTable.getColumnModel());
              }

              @Override
              public void columnRemoved(TableColumnModelEvent e) {
                updateContextColumns(fileTable.getColumnModel());
              }

              @Override
              public void columnMoved(TableColumnModelEvent e) {
                updateContextColumns(fileTable.getColumnModel());
              }

              @Override
              public void columnMarginChanged(ChangeEvent e) {
                updateContextColumns(fileTable.getColumnModel());
              }

              @Override
              public void columnSelectionChanged(ListSelectionEvent e) {
                // Usually ignore
              }
            });

    // construct UI

    frame = new JFrame("File Renderer");
    frame.setSize(context.guiBounds().width, context.guiBounds().height);
    frame.setLocation(context.guiBounds().x, context.guiBounds().y);

    // Add listener
    frame.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            Rectangle bounds = frame.getBounds();
            var newContext =
                new AppContext(
                    context.filesAndTimestamps(),
                    context.lastSearchPath(),
                    bounds,
                    context.neverBeforeUsed(),
                    context.columnContext());
            flagContextDirty(newContext);
          }

          @Override
          public void componentMoved(ComponentEvent e) {
            Rectangle bounds = frame.getBounds();
            var newContext =
                new AppContext(
                    context.filesAndTimestamps(),
                    context.lastSearchPath(),
                    bounds,
                    context.neverBeforeUsed(),
                    context.columnContext());
            flagContextDirty(newContext);
          }
        });

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    fileTable.setRowSorter(rowSorter);

    fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

    for (CaColumn c : CaColumn.values()) {
      fileTable.getColumnModel().getColumn(c.ordinal()).setCellRenderer(c.renderer);
    }
    fileTable.setRowHeight(64);

    JTextField searchField = new JTextField(20);
    searchField.setText("Search");
    searchField.putClientProperty("JTextField.placeholderText", "Search...");
    searchField
        .getDocument()
        .addDocumentListener(
            new javax.swing.event.DocumentListener() {
              public void insertUpdate(javax.swing.event.DocumentEvent e) {
                update();
              }

              private void update() {
                String text = searchField.getText().trim().toLowerCase();
                updateTextSearch(
                    new TextSearch(
                        text, currentSearch.searchCaColumns, currentSearch.excludeMatches));
              }

              public void removeUpdate(javax.swing.event.DocumentEvent e) {
                update();
              }

              public void changedUpdate(javax.swing.event.DocumentEvent e) {
                update();
              }
            });

    fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // allows wide table + horizontal scrolling
    JScrollPane scrollPane = new JScrollPane(fileTable);

    JPanel topPanel = new JPanel(new GridLayout(2, 0));
    {
      JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
      {
        JButton addBtn = new JButton("Files");
        final JPopupMenu filesMenu =
            new JPopupMenu() {
              @Override
              public Dimension getPreferredSize() { // snugly assume width of button
                Dimension d = super.getPreferredSize();
                if (d.width < addBtn.getWidth()) d.width = addBtn.getWidth();
                return d;
              }
            };
        filesMenu.setLayout(new GridLayout(0, 1));
        {
          {
            JButton importSingleFile = new JButton("Import file");
            importSingleFile.addActionListener(a -> this.importFile());
            filesMenu.add(importSingleFile);
          }

          {
            JButton importFolder = new JButton("Import folder");
            importFolder.addActionListener(a -> this.importFolder());
            filesMenu.add(importFolder);
          }

          {
            JButton reloadAll = new JButton("Reload all");
            reloadAll.addActionListener(a -> this.reloadAllFiles());
            filesMenu.add(reloadAll);
          }

          {
            JButton reloadAll = new JButton("Remove all");
            reloadAll.addActionListener(a -> this.removeAllFiles());
            filesMenu.add(reloadAll);
          }
        }

        addBtn.addActionListener(
            e -> {
              SwingUtilities.invokeLater(
                  () -> {
                    filesMenu.show(addBtn, 0, addBtn.getHeight());
                  });
            });
        topPanel.add(addBtn);

        topPanel.add(searchField);
      }
      JPanel topInfo = new JPanel(new FlowLayout(FlowLayout.LEFT));
      {
        JLabel topInfoLabel = new JLabel();
        topInfo.add(topInfoLabel);
        this.topInfoLabel = topInfoLabel;
      }
      topPanel.add(topButtons);
      topPanel.add(topInfo);
    }

    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

    JPanel fileListPanel = new JPanel(new BorderLayout());
    {
      fileListPanel.add(topPanel, BorderLayout.NORTH);
      fileListPanel.add(scrollPane, BorderLayout.CENTER);
      fileListPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.LEFT);
    tabbedPane.add("File List", fileListPanel);
    tabbedPane.add(
        "⚙\uFE0F", getSettingsComponent(context.columnContext().displayedColumns())); // SETTINGS
    frame.add(tabbedPane);
    context.filesAndTimestamps().keySet().forEach(tableModel::addFile);

    initDisplayedColumns(context);

    rowSorter.addRowSorterListener(
        e -> {
          if (e.getType() == RowSorterEvent.Type.SORT_ORDER_CHANGED) {

            List<? extends RowSorter.SortKey> sortKeys = fileTable.getRowSorter().getSortKeys();
            var oldColumnContext = this.context.columnContext();
            ColumnContext newColumnContext;
            if (!sortKeys.isEmpty()) {
              RowSorter.SortKey key = sortKeys.get(0);

              int modelColumn = key.getColumn(); // model index
              SortOrder order = key.getSortOrder(); // ASCENDING / DESCENDING
              newColumnContext =
                  new ColumnContext(
                      oldColumnContext.displayedColumns(),
                      oldColumnContext.columnWidths(),
                      tableModel.getColumn(modelColumn),
                      SortOrder.ASCENDING.equals(order));
            } else {
              newColumnContext =
                  new ColumnContext(
                      oldColumnContext.displayedColumns(),
                      oldColumnContext.columnWidths(),
                      null,
                      false);
            }
            flagContextDirty(
                new AppContext(
                    context.filesAndTimestamps(),
                    context.lastSearchPath(),
                    context.guiBounds(),
                    context.neverBeforeUsed(),
                    newColumnContext));
          }
        });

    frame.setVisible(true);
  }

  private void importFile() {
    JFileChooser chooser = getFileChooser(false);
    var newFilesAndTimeStamps = new HashMap<>(context.filesAndTimestamps());
    if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {

      for (File f : chooser.getSelectedFiles()) {
        if (!context.filesAndTimestamps().containsKey(f)) {
          tableModel.addFile(f);
          newFilesAndTimeStamps.put(f, System.currentTimeMillis());
        }
      }
    }
    var newContext =
        new AppContext(
            newFilesAndTimeStamps,
            chooser.getCurrentDirectory(),
            context.guiBounds(),
            context.neverBeforeUsed(),
            context.columnContext());
    flagContextDirty(newContext);
  }

  private void importFolder() {
    JFileChooser chooser = getFileChooser(true);
    var newFilesAndTimeStamps = new HashMap<>(context.filesAndTimestamps());
    if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
      for (File folder : chooser.getSelectedFiles()) {
        try {
          var filesInFolderRecursive = getAllFiles(folder);
          filesInFolderRecursive.forEach(
              f -> {
                newFilesAndTimeStamps.put(f, System.currentTimeMillis());
                tableModel.addFile(f);
              });
        } catch (IOException e) {
          JOptionPane.showMessageDialog(
              frame,
              "Importing of folder '"
                  + folder.getAbsolutePath()
                  + "' has failed: "
                  + e.getMessage(),
              "Error",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    }
    var newContext =
        new AppContext(
            newFilesAndTimeStamps,
            chooser.getCurrentDirectory(),
            context.guiBounds(),
            context.neverBeforeUsed(),
            context.columnContext());
    flagContextDirty(newContext);
  }

  /**
   * Recursively collects all files in a folder and its subfolders.
   *
   * @param folder the root folder to start from
   * @return list of files found
   * @throws IOException if an I/O error occurs
   */
  public static List<File> getAllFiles(File folder) throws IOException {
    if (folder == null || !folder.isDirectory()) {
      throw new IllegalArgumentException("Input must be a valid directory");
    }

    List<File> fileList = new ArrayList<>();

    // Using java.nio.file.Files.walk
    try (Stream<Path> paths = Files.walk(folder.toPath())) {
      paths
          .filter(Files::isRegularFile) // only files, ignore directories
          .filter(isSupportedSchematicType)
          .forEach(path -> fileList.add(path.toFile()));
    }

    return fileList;
  }

  private void reloadAllFiles() {
    IntStream.range(0, tableModel.getRowCount())
        .mapToObj(i -> Map.entry(i, tableModel.getFileAt(i)))
        .sorted(Comparator.comparingLong(e -> e.getValue().length()))
        .forEach(
            entry -> {
              int row = entry.getKey();
              File file = entry.getValue();
              tableModel.flagReloadFile(row);
              tableModel.invalidateIconCache(file);
              try {
                Files.deleteIfExists(ResourceUtils.getRenderPathForFile(file));
              } catch (IOException e) {
                // ignore
              }
            });
  }

  private void removeAllFiles() {
    var allFiles =
        IntStream.range(0, tableModel.getRowCount())
            .mapToObj(tableModel::getFileAt)
            .toArray(File[]::new);
    tableModel.removeFile(allFiles);

    flagContextDirty(
        new AppContext(
            new HashMap<>(),
            context.lastSearchPath(),
            context.guiBounds(),
            context.neverBeforeUsed(),
            context.columnContext()));
  }

  public static void startApp(final AppContext context) {
    SwingUtilities.invokeLater(() -> new FileRenderApp(context));
  }

  private JComponent getSettingsComponent(List<CaColumn> initialCaColumns) {
    // SELECT WHICH COLUMNS TO DISPLAY
    JComponent columnSettings = new JPanel(new GridLayout(0, 1));
    columnSettings.add(new JLabel("Show Columns:"));
    HashSet<CaColumn> caColumns = new HashSet<>(initialCaColumns);

    CaColumn[] caColumnSet = CaColumn.values();

    IntStream.range(0, caColumnSet.length)
        .mapToObj(i -> new AbstractMap.SimpleEntry<>(i, caColumnSet[i]))
        .filter(e -> e.getValue() != CaColumn.ICON)
        .sorted(Comparator.comparing(e -> e.getValue().displayName))
        .forEach(
            entry -> {
              var c = entry.getValue();
              JCheckBox checkBox = new JCheckBox(c.displayName);
              checkBox.setToolTipText(c.tooltip);
              checkBox.setSelected(caColumns.contains(c));
              checkBox.addActionListener(
                  e -> {
                    boolean show = checkBox.isSelected();
                    var oldColumnContext = context.columnContext();
                    List<CaColumn> newDisplayed =
                        new ArrayList<>(oldColumnContext.displayedColumns());
                    List<Integer> newWidths = new ArrayList<>(oldColumnContext.columnWidths());
                    int idx = newDisplayed.indexOf(c);
                    if (show) {
                      if (idx < 0) {
                        newDisplayed.add(c);
                        newWidths.add(c.defaultWidth);
                      }
                    } else if (idx >= 0) {
                      newDisplayed.remove(idx);
                      if (idx < newWidths.size()) newWidths.remove(idx);
                    }
                    var newColumnContext =
                        new ColumnContext(
                            newDisplayed,
                            newWidths,
                            oldColumnContext.orderedColumn(),
                            oldColumnContext.orderAscending());
                    flagContextDirty(
                        new AppContext(
                            context.filesAndTimestamps(),
                            context.lastSearchPath(),
                            context.guiBounds(),
                            context.neverBeforeUsed(),
                            newColumnContext));
                    updateDisplayColumns(
                        new ArrayList<>(newColumnContext.displayedColumns()),
                        new ArrayList<>(newColumnContext.columnWidths()),
                        new HashSet<>(),
                        fileTable.getColumnModel());
                  });
              columnSettings.add(checkBox);
            });

    JScrollPane settingsPanel = new JScrollPane();
    {
      settingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

      JPanel appPanel = new JPanel(new GridLayout(0, 1));
      appPanel.add(new JLabel("Application:"));
      JButton openInstallPathBtn = new JButton("Open install folder");
      openInstallPathBtn.addActionListener(
          e -> {
            try {
              Desktop.getDesktop().open(ResourceUtils.getInstallPath().toFile());
            } catch (IOException ex) {
              logger.log(Level.WARNING, "Could not open install folder", ex);
            }
          });
      appPanel.add(openInstallPathBtn);

      JButton openLogFileBtn = new JButton("Open log file");
      openLogFileBtn.addActionListener(
          e -> {
            try {
              java.nio.file.Path logDir = ResourceUtils.getInstallPath().resolve("logs");
              java.io.File[] logs =
                  logDir
                      .toFile()
                      .listFiles((d, n) -> n.startsWith("cubearray") && n.endsWith(".log"));
              if (logs == null || logs.length == 0) {
                JOptionPane.showMessageDialog(
                    frame,
                    "No log file found in:\n" + logDir,
                    "Open log file",
                    JOptionPane.INFORMATION_MESSAGE);
                return;
              }
              java.io.File latest =
                  Arrays.stream(logs)
                      .max(Comparator.comparingLong(java.io.File::lastModified))
                      .get();
              Desktop.getDesktop().open(latest);
            } catch (IOException ex) {
              logger.log(Level.WARNING, "Could not open log file", ex);
            }
          });
      appPanel.add(openLogFileBtn);

      JPanel settingsContentPane = new JPanel();
      settingsContentPane.setLayout(new GridLayout(0, 1));
      settingsContentPane.add(appPanel);
      settingsContentPane.add(columnSettings);
      settingsContentPane.add(new KeyBindingComponent());
      settingsPanel.setViewportView(settingsContentPane);
    }
    return settingsPanel;
  }

  private void initDisplayedColumns(AppContext context) {
    var contextClone = context.columnContext().copy();

    // construct hashmap to lookup column -> tableColumn
    TableColumnModel columnModel = fileTable.getColumnModel();
    for (int i = 0; i < columnModel.getColumnCount(); i++) {
      TableColumn tc = columnModel.getColumn(i);
      CaColumn c = CaColumn.values()[i];
      columToTableColumn.put(c, tc);
    }

    // ensure ICON column is always visible and at the leftmost position
    List<CaColumn> displayed = new ArrayList<>(contextClone.displayedColumns());
    List<Integer> widths = new ArrayList<>(contextClone.columnWidths());
    int iconIdx = displayed.indexOf(CaColumn.ICON);
    if (iconIdx < 0) {
      displayed.addFirst(CaColumn.ICON);
      widths.addFirst(CaColumn.ICON.defaultWidth);
    } else if (iconIdx > 0) {
      displayed.remove(iconIdx);
      widths.remove(iconIdx);
      displayed.addFirst(CaColumn.ICON);
      widths.addFirst(CaColumn.ICON.defaultWidth);
    }

    // display only columns from saved context
    updateDisplayColumns(
        displayed,
        widths,
        new HashSet<>(),
        fileTable.getColumnModel());

    // apply sorting
    if (contextClone.orderedColumn() != null) {
      List<RowSorter.SortKey> keys =
          List.of(
              new RowSorter.SortKey(
                  contextClone.orderedColumn().ordinal(),
                  contextClone.orderAscending() ? SortOrder.ASCENDING : SortOrder.DESCENDING));
      rowSorter.setSortKeys(keys);
      rowSorter.sort();
    }
  }

  void updateDisplayColumns(
      List<CaColumn> caColumns,
      List<Integer> columnWidths,
      HashSet<CaColumn> hiddenColumns,
      TableColumnModel columnModel) {
    { // rebuild column model to match active columns.
      // Remove all columns
      while (columnModel.getColumnCount() > 0) {
        columnModel.removeColumn(columnModel.getColumn(0));
      }
      for (int i = 0; i < caColumns.size(); i++) {
        CaColumn column = caColumns.get(i);
        if (hiddenColumns.contains(column)) continue;
        TableColumn tc = columToTableColumn.get(column);
        int width = (i < columnWidths.size()) ? columnWidths.get(i) : caColumns.get(i).defaultWidth;
        tc.setPreferredWidth(width);
        tc.setWidth(width);
        columnModel.addColumn(tc);
      }
      logger.fine("DISPLAY COLUMN WIDTHS " + columnWidths);
    }
  }

  void updateContextColumns(TableColumnModel columnModel) {
    List<TableColumn> columns = Collections.list(columnModel.getColumns());

    ArrayList<CaColumn> orderedCaColumns = new ArrayList<>();
    ArrayList<Integer> columnWidths = new ArrayList<>();

    CaColumn[] enumColums = CaColumn.values();
    for (TableColumn tc : columns) {
      int modelIdx = tc.getModelIndex();
      // model columns are equal to the enum
      if (modelIdx >= 0 && modelIdx < enumColums.length) {
        CaColumn caColumn = enumColums[modelIdx];
        orderedCaColumns.add(caColumn);
        columnWidths.add(tc.getWidth());
      }
    }

    if (!(orderedCaColumns.size() == orderedCaColumns.stream().distinct().toList().size())) {
      assert false : "Columns have different size";
      ;
    }
    var oldColumnContext = this.context.columnContext();
    var newColumnContext =
        new ColumnContext(
            orderedCaColumns,
            columnWidths,
            oldColumnContext.orderedColumn(),
            oldColumnContext.orderAscending());
    logger.fine("SET COLUMN WIDTHS TO " + columnWidths);
    assert orderedCaColumns.size() == orderedCaColumns.stream().distinct().toList().size()
        : "ordered columns not distinct:" + orderedCaColumns;
    assert oldColumnContext.displayedColumns().size() == oldColumnContext.columnWidths().size();

    flagContextDirty(
        new AppContext(
            context.filesAndTimestamps(),
            context.lastSearchPath(),
            context.guiBounds(),
            context.neverBeforeUsed(),
            newColumnContext));
  }

  void checkContextSaving() {
    // WARNING: this runs on the background thread NOT the gui thread!
    if (contextDirtyFlag) {
      contextDirtyFlag = false;
      logger.fine("WRITE CONTEXT TO FILE");
      AppContext.write(this.context);
    }
  }

  private void setTextRemainingFiles(String text) {
    topInfoLabel.setText(text);
  }

  private void reloadSelectedFiles() {
    var rows = getSelectedModelRows();
    for (int row : rows) {
      File file = tableModel.getFileAt(row);
      tableModel.flagReloadFile(row);
      tableModel.invalidateIconCache(file);
      try {
        Files.deleteIfExists(ResourceUtils.getRenderPathForFile(file));
      } catch (IOException e) {
        // ignore
      }
    }
  }

  private void openFolderSelectedFiles() {
    var files = getSelectedFiles();
    Desktop desktop = Desktop.getDesktop();
    if (desktop == null) {
      assert false : "desktop is null";
      return;
    }
    List<File> folders = Arrays.stream(files).map(File::getParentFile).distinct().toList();
    if (folders.size() > 2) {
      int reply =
          JOptionPane.showConfirmDialog(
              frame,
              "You are trying to open "
                  + folders.size()
                  + " folders at once. Do you want to continue?",
              "Open folders",
              JOptionPane.YES_NO_OPTION);
      if (reply != JOptionPane.YES_OPTION) {
        return;
      }
    }
    folders.forEach(
        folder -> {
          try {
            desktop.open(folder);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private void tableAddMouseClickListener(JTable table) {
    final JPopupMenu rightMenu = new JPopupMenu();
    final JButton reloadFileBtn;
    final JButton renderFilesBtn;
    final JLabel menuTitleLbl;
    {
      rightMenu.setLayout(new GridLayout(0, 1));

      menuTitleLbl = new JLabel("");
      rightMenu.add(menuTitleLbl);

      reloadFileBtn = new JButton("Reload");
      reloadFileBtn.addActionListener(a -> this.reloadSelectedFiles());
      rightMenu.add(reloadFileBtn);

      renderFilesBtn = new JButton("Render");
      renderFilesBtn.addActionListener(a -> this.renderSelectedFiles());
      rightMenu.add(renderFilesBtn);



      JButton removeFilesBtn = new JButton("Remove");
      removeFilesBtn.addActionListener(a -> this.removeSelectedFiles());
      rightMenu.add(removeFilesBtn);

      JButton deleteFilesBtn = new JButton("Delete from disk");
      deleteFilesBtn.addActionListener(a -> this.deleteSelectedFiles());
      rightMenu.add(deleteFilesBtn);

      JButton openFolderBtn = new JButton("Open folder");
      openFolderBtn.addActionListener(a -> this.openFolderSelectedFiles());
      rightMenu.add(openFolderBtn);

      JButton convertToSponge3Btn = new JButton("Convert to Sponge3");
      convertToSponge3Btn.addActionListener(a -> this.convertSelectedToSponge3());
      rightMenu.add(convertToSponge3Btn);

      JButton replaceSandstoneBtn = new JButton("Replace sandstone with cobblestone");
      replaceSandstoneBtn.addActionListener(a -> this.replaceSandstoneWithCobblestone());
      rightMenu.add(replaceSandstoneBtn);
    }

    Consumer<Integer> updateRightMenu =
        modelRow -> {
          reloadFileBtn.setEnabled(
              Arrays.stream(getSelectedModelRows()).anyMatch(tableModel::isFileLoaded));
          renderFilesBtn.setEnabled(
              Arrays.stream(getSelectedModelRows()).allMatch(tableModel::isFileLoaded));
          menuTitleLbl.setText(getSelectedFiles().length + " file(s) selected");
        };

    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            int viewRow = table.rowAtPoint(e.getPoint());
            int viewCol = table.columnAtPoint(e.getPoint());
            if (viewRow == -1 || viewCol == -1) {
              return; // click outside cells
            }
            int modelRow = table.convertRowIndexToModel(viewRow);
            int modelCol = table.convertColumnIndexToModel(viewCol);
            Object object = tableModel.getValueAt(modelRow, modelCol);

            if (e.getClickCount() == 1 && SwingUtilities.isRightMouseButton(e)) {
              updateRightMenu.accept(modelRow);
              SwingUtilities.invokeLater(
                  () -> rightMenu.show(e.getComponent(), e.getX(), e.getY()));
            }
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
              if (modelCol == CaColumn.ICON.ordinal()) {
                showRenderPreview(modelRow);
                return;
              }

              logger.fine("Model row=" + modelRow + ", model col=" + modelCol);

              JPopupMenu menu = new JPopupMenu();
              menu.setLightWeightPopupEnabled(false);
              JTextArea textArea = new JTextArea(10, 50);
              textArea.setLineWrap(true);
              textArea.setWrapStyleWord(true);
              textArea.setEditable(false);
              String content = "EMPTY";
              if (object instanceof List<?> list) {
                content = list.stream().map(Object::toString).collect(Collectors.joining("\n"));
              } else if (object instanceof Map<?, ?> map) {
                content =
                    map.entrySet().stream()
                        .map(
                            entry -> entry.getKey().toString() + ": " + entry.getValue().toString())
                        .collect(Collectors.joining("\n"));
              } else if (object instanceof String s) {
                content = s;
              } else {
                content = object.toString();
              }

              var longestLine =
                  Arrays.stream(content.split("\n")).max(Comparator.comparing(String::length));
              if (longestLine.isEmpty()) return; //

              textArea.setText(content);

              FontMetrics metrics = textArea.getFontMetrics(textArea.getFont());
              float pxWidth = metrics.stringWidth(longestLine.get());
              pxWidth /= metrics.stringWidth("m"); // what column size is based off
              int columns = (int) Math.ceil(pxWidth);

              textArea.setColumns(Math.max(Math.min(100, columns + 5), 20));
              textArea.setRows(Math.max(7, Math.min(30, content.split("\n").length)));

              textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

              logger.fine("object string = " + textArea.getText());
              JScrollPane scrollPane = new JScrollPane(textArea);
              menu.add(new JLabel(tableModel.getColumn(modelCol).displayName));
              menu.add(scrollPane);

              SwingUtilities.invokeLater(() -> menu.show(e.getComponent(), e.getX(), e.getY()));
            }
          }
        });

    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 3 && SwingUtilities.isLeftMouseButton(e)) {
              int viewRow = table.rowAtPoint(e.getPoint());
              if (viewRow >= 0) {
                renderSelectedFiles();
              }
            }
          }
        });
  }

  private void checkLoadingThreads() {
    synchronized (loadingThreads) {
      loadingThreads.removeIf(t -> !t.isAlive());
    }
  }

  private void renderSelectedFiles() {
    if (!loadingThreads.isEmpty()) {
      return;
    }

    int[] viewRows = fileTable.getSelectedRows();
    List<File> selected =
        java.util.Arrays.stream(viewRows)
            .map(fileTable::convertRowIndexToModel)
            .mapToObj(tableModel::getFileAt)
            .toList();

    if (selected.isEmpty()) {
      JOptionPane.showMessageDialog(null, "No files selected.");
    } else {
      // Your action goes here
      renderFiles(selected);
    }
  }

  private File[] getSelectedFiles() {
    int[] viewRows = fileTable.getSelectedRows();
    File[] files =
        Arrays.stream(viewRows)
            .map(fileTable::convertRowIndexToModel)
            .mapToObj(tableModel::getFileAt)
            .toArray(File[]::new);

    return files;
  }

  private int[] getSelectedModelRows() {
    int[] viewRows = fileTable.getSelectedRows();
    int[] rows = Arrays.stream(viewRows).map(fileTable::convertRowIndexToModel).toArray();
    return rows;
  }

  private void removeSelectedFiles() {
    var files = getSelectedFiles();
    tableModel.removeFile(files);

    var newFilesAndTimestamps = new HashMap<>(context.filesAndTimestamps());
    for (File file : files) {
      newFilesAndTimestamps.remove(file);
    }
    flagContextDirty(
        new AppContext(
            newFilesAndTimestamps,
            context.lastSearchPath(),
            context.guiBounds(),
            context.neverBeforeUsed(),
            context.columnContext()));
  }

  private void deleteSelectedFiles() {
    File[] selected = getSelectedFiles();
    if (selected.length == 0) return;

    String fileList = Arrays.stream(selected).map(File::getName).collect(Collectors.joining("\n"));
    int reply =
        JOptionPane.showConfirmDialog(
            frame,
            "Permanently delete " + selected.length + " file(s) from disk?\n\n" + fileList,
            "Delete from disk",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (reply != JOptionPane.YES_OPTION) return;

    List<File> failed = new ArrayList<>();
    for (File file : selected) {
      try {
        Files.delete(file.toPath());
        logger.info("Deleted file: " + file.getAbsolutePath());
      } catch (IOException e) {
        failed.add(file);
        logger.log(Level.WARNING, "Could not delete file: " + file.getAbsolutePath(), e);
      }
    }

    // Remove all selected from the table regardless of delete success
    tableModel.removeFile(selected);
    var newFilesAndTimestamps = new HashMap<>(context.filesAndTimestamps());
    for (File file : selected) {
      newFilesAndTimestamps.remove(file);
    }
    flagContextDirty(
        new AppContext(
            newFilesAndTimestamps,
            context.lastSearchPath(),
            context.guiBounds(),
            context.neverBeforeUsed(),
            context.columnContext()));

    if (!failed.isEmpty()) {
      JOptionPane.showMessageDialog(
          frame,
          "Could not delete:\n"
              + failed.stream().map(File::getName).collect(Collectors.joining("\n")),
          "Delete from disk",
          JOptionPane.ERROR_MESSAGE);
    }
  }

  private void replaceSandstoneWithCobblestone() {
    File[] selected = getSelectedFiles();
    if (selected.length == 0) {
      JOptionPane.showMessageDialog(
          frame, "No files selected.", "Replace blocks", JOptionPane.WARNING_MESSAGE);
      return;
    }

    // Collect the union palette across all selected files
    Set<String> palette = new LinkedHashSet<>();
    Map<File, pitheguy.schemconvert.converter.Schematic> loaded = new LinkedHashMap<>();
    for (File file : selected) {
      try {
        var schematic = BlockReplacer.load(file);
        loaded.put(file, schematic);
        palette.addAll(BlockReplacer.getPalette(schematic));
      } catch (IOException e) {
        JOptionPane.showMessageDialog(
            frame,
            "Could not load: " + file.getName() + "\n" + e.getMessage(),
            "Replace blocks",
            JOptionPane.ERROR_MESSAGE);
        return;
      }
    }

    // Load the full block ID list from the bundled resource
    Set<String> availableBlocks;
    try {
      availableBlocks = BlockReplacer.loadDefaultPalette();
    } catch (IOException e) {
      JOptionPane.showMessageDialog(
          frame,
          "Could not load block list: " + e.getMessage(),
          "Replace blocks",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    // Show the mapping dialog — palette = rows, full block list = available choices
    var mapping = BlockReplacerDialog.show(frame, palette, availableBlocks, loaded.keySet());
    if (mapping.isEmpty()) return;
    var replaceResult = mapping.get();

    // Ask for output options (postfix + optional target folder)
    File firstFile = loaded.keySet().iterator().next();
    var outputOptions = OutputOptionsDialog.show(frame, firstFile);
    if (outputOptions.isEmpty()) return;
    var options = outputOptions.get();

    List<File> failed = new ArrayList<>();
    List<File> written = new ArrayList<>();

    for (var entry : loaded.entrySet()) {
      File file = entry.getKey();
      try {
        var replaced = BlockReplacer.replace(entry.getValue(), replaceResult.replacements());
        File output = options.outputFileFor(file);
        if (output.getParentFile() != null) output.getParentFile().mkdirs();
        BlockReplacer.write(replaced, output);
        written.add(output);
        tableModel.addFile(output);
      } catch (IOException e) {
        failed.add(file);
        logger.log(Level.SEVERE, "failed to write replaced schematic for: " + file.getName(), e);
      }
    }

    var newFilesAndTimestamps = new HashMap<>(context.filesAndTimestamps());
    for (File output : written) {
      newFilesAndTimestamps.put(output, System.currentTimeMillis());
    }
    flagContextDirty(
        new AppContext(
            newFilesAndTimestamps,
            context.lastSearchPath(),
            context.guiBounds(),
            context.neverBeforeUsed(),
            context.columnContext()));

    String msg = written.size() + " file(s) written.";
    if (!failed.isEmpty())
      msg += "\nFailed: " + failed.stream().map(File::getName).collect(Collectors.joining(", "));
    JOptionPane.showMessageDialog(
        frame,
        msg,
        "Replace blocks",
        failed.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
  }

  private void convertSelectedToSponge3() {
    List<File> selected = Arrays.asList(getSelectedFiles());
    if (selected.isEmpty()) {
      JOptionPane.showMessageDialog(
          frame, "No files selected.", "Convert to Sponge3", JOptionPane.WARNING_MESSAGE);
      return;
    }

    JFileChooser chooser = new JFileChooser();
    chooser.setCurrentDirectory(selected.get(0).getParentFile());
    chooser.setDialogTitle("Select output folder for converted .schem files");
    chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    chooser.setAcceptAllFileFilterUsed(false);
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
    final File outputDir = chooser.getSelectedFile();

    new Thread(
            () -> {
              List<Path> paths = selected.stream().map(File::toPath).toList();
              BatchConverter.ConversionResult result;
              try {
                result = BatchConverter.convertToSponge3(paths, outputDir);
              } catch (IOException e) {
                SwingUtilities.invokeLater(
                    () ->
                        JOptionPane.showMessageDialog(
                            frame,
                            "Conversion failed: " + e.getMessage(),
                            "Convert to Sponge3",
                            JOptionPane.ERROR_MESSAGE));
                return;
              }

              SwingUtilities.invokeLater(
                  () -> {
                    // add each newly produced .schem file to the table
                    var newFilesAndTimestamps = new HashMap<>(context.filesAndTimestamps());
                    for (File produced : result.convertedFiles()) {
                      tableModel.addFile(produced);
                      newFilesAndTimestamps.put(produced, System.currentTimeMillis());
                    }
                    flagContextDirty(
                        new AppContext(
                            newFilesAndTimestamps,
                            context.lastSearchPath(),
                            context.guiBounds(),
                            context.neverBeforeUsed(),
                            context.columnContext()));

                    List<File> failed = result.failedFiles();
                    if (failed.isEmpty()) {
                      JOptionPane.showMessageDialog(
                          frame,
                          "Converted " + result.convertedFiles().size() + " file(s) to Sponge v3.",
                          "Convert to Sponge3",
                          JOptionPane.INFORMATION_MESSAGE);
                    } else {
                      String failedNames =
                          failed.stream().map(File::getName).collect(Collectors.joining("\n"));
                      JOptionPane.showMessageDialog(
                          frame,
                          "Converted "
                              + result.convertedFiles().size()
                              + " of "
                              + selected.size()
                              + " file(s).\n\nFailed:\n"
                              + failedNames,
                          "Convert to Sponge3",
                          JOptionPane.WARNING_MESSAGE);
                    }
                  });
            },
            "sponge3-converter")
        .start();
  }

  private void renderFiles(List<File> selectedFiles) {
    // Placeholder logic
    logger.info("Rendering files:");
    for (File f : selectedFiles) {
      logger.info(" - " + f.getAbsolutePath());
    }

    try {
      Thread glThread =
          new Thread(
              () -> {
                try {
                  SchemReader.CubeSetup setup =
                      SchemReader.prepareData(
                          SchemReader.loadSchematics(
                              selectedFiles.stream().map(File::toPath).toList(),
                              f -> logger.warning("can not render " + f.getAbsolutePath())));
                  if (setup == null) {
                    SwingUtilities.invokeLater(
                        () -> {
                          JOptionPane.showMessageDialog(
                              frame,
                              "Error: unable to load schematics from selected "
                                  + "files. Maybe the file type is not supported or does not exist "
                                  + "anymore?");
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
      logger.log(Level.SEVERE, "Error starting render", ex);
    }
  }

  private void showRenderPreview(int modelRow) {
    File file = tableModel.getFileAt(modelRow);
    Path renderPath = ResourceUtils.getRenderPathForFile(file);
    if (!Files.exists(renderPath)) {
      JOptionPane.showMessageDialog(frame, "No render available yet.");
      return;
    }
    ImageIcon icon =
        new ImageIcon(
            new ImageIcon(renderPath.toString())
                .getImage()
                .getScaledInstance(640, 640, Image.SCALE_SMOOTH));
    JOptionPane.showMessageDialog(frame, icon);
  }

  private void renderSchematicIcon(File file) {
    if (file == null) return;
    if (!ResourceUtils.needsNewRender(file)) return;
    Future<?> future =
        renderExecutor.submit(
            () -> {
              try {
                WPObject obj = tableModel.getSchematicFor(file);
                if (obj == null) return;
                ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
                SchemReader.CubeSetup setup = SchemReader.prepareData(List.of(obj));
                if (setup == null) return;
                Path renderPath = ResourceUtils.getRenderPathForFile(file);
                Files.createDirectories(renderPath.getParent());
                InstancedCubes.renderToFile(setup, renderPath, 640, 640);
                SwingUtilities.invokeLater(
                    () -> {
                      tableModel.invalidateIconCache(file);
                      int idx = tableModel.indexOfFile(file);
                      if (idx >= 0) tableModel.fireTableRowsUpdated(idx, idx);
                    });
              } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to render icon for " + file.getName(), e);
              }
            });
    try {
      future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      logger.log(Level.WARNING, "Render failed for " + file.getName(), e);
    }
  }

  private JFileChooser getFileChooser(boolean folder) {
    JFileChooser chooser = new JFileChooser();
    chooser.setCurrentDirectory(context.lastSearchPath());
    chooser.setMultiSelectionEnabled(true);

    if (!folder) {
      // IMPORT FILES
      chooser.setFileFilter(
          new FileFilter() {
            @Override
            public boolean accept(File f) {
              for (String type : ResourceUtils.SUPPORTED_FILE_TYPES) {
                if (f.isDirectory() || f.getPath().endsWith(type)) return true;
              }
              return false;
            }

            @Override
            public String getDescription() {
              return ResourceUtils.SUPPORTED_FILE_TYPES.stream()
                  .sorted()
                  .collect(Collectors.joining(", "));
            }
          });
    } else {
      // IMPORT FOLDER
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      chooser.setAcceptAllFileFilterUsed(false); // optional, hide files
    }
    return chooser;
  }
}
