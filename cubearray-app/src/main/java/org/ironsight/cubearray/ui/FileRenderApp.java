package org.ironsight.cubearray.ui;

import static org.ironsight.cubearray.platform.ResourceUtils.isSupportedSchematicType;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import org.ironsight.cubearray.platform.AppLogger;
import org.ironsight.cubearray.platform.PeriodicChecker;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.render.InstancedCubes;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.schematic.SchemReader;
import org.ironsight.cubearray.edit.BatchConverter;
import org.ironsight.cubearray.edit.BlockReplacer;
import org.ironsight.cubearray.preview.SchematicPreviewHelper;
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
  private final BlockIconProvider blockIconProvider = new BlockIconProvider(32, 16);
  // is context dirty and needs to be saved?
  private boolean contextDirtyFlag;
  private final HashMap<CaColumn, TableColumn> columToTableColumn = new HashMap<>();
  private final HashMap<CaColumn, TableColumn> folderColumToTableColumn = new HashMap<>();


  private void flagContextDirty(AppContext context) {
    this.context = context;
    contextDirtyFlag = true;
  }

  private ChipSearchManager chipSearchManager;
  private DebouncedDocumentListener debouncer;

  private JTextField searchField;
  private JPanel chipRow;
  private JProgressBar searchSpinner;

  private final List<SearchFilter> searchFilters = new ArrayList<>();
  private final Map<String, ChipSearchFilter> chipFilters = new HashMap<>();

  // Folder view components
  private JTable folderTable;
  private TableRowSorter<FileTableModel> folderRowSorter;
  private JTextField folderSearchField;
  private JPanel folderChipRow;
  private JProgressBar folderSearchSpinner;
  private final List<SearchFilter> folderSearchFilters = new ArrayList<>();
  private final Map<String, ChipSearchFilter> folderChipFilters = new HashMap<>();
  private ChipSearchManager folderChipManager;
  private DebouncedDocumentListener folderDebouncer;
  private File folderViewPath;
  private JTextField folderPathField;
  private JLabel folderErrorIcon;
  private int folderRefreshCounter;

  private static final long DEBUG_SEARCH_DELAY_MS = 0;

  private final JLabel topInfoLabel = new JLabel();
  private final JLabel renderInfoLabel = new JLabel();

  private record FileListPanel(
      JPanel panel,
      JTable table,
      TableRowSorter<FileTableModel> sorter,
      JTextField searchField,
      ChipSearchManager chipManager,
      JPanel chipRow,
      JProgressBar searchSpinner,
      List<SearchFilter> searchFilters,
      Map<String, ChipSearchFilter> chipFilters,
      DebouncedDocumentListener debouncer
  ) {}

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
              context.columnContext(),
              context.folderViewPath());
      flagContextDirty(newContext);
    }

    PeriodicChecker.INSTANCE.addCallback(this::checkContextSaving);
    PeriodicChecker.INSTANCE.addCallback(this::checkLoadingThreads);
    PeriodicChecker.INSTANCE.addCallback(this::autoRefreshFolderView);

    this.tableModel = new FileTableModel(PeriodicChecker.INSTANCE, SchematicPreviewHelper.getInstance());
    FileTableModel.blockIconProvider = blockIconProvider;
    tableModel.setFileQueueSizeChangedCallback(
        count -> {
          if (count == 0) this.setTextRemainingFiles("");
          else this.setTextRemainingFiles("Loading " + count + " file(s)");
        });
    tableModel.setOnSchematicLoadedCallback(this::onSchematicLoaded);
    SchematicPreviewHelper.getInstance().setPendingRenderCountChangedCallback(
        count -> {
          if (count == 0) this.setTextRenderingSchematics("");
          else this.setTextRenderingSchematics("Rendering " + count + " schematic(s)");
        });

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
                    context.columnContext(),
                    context.folderViewPath());
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
                    context.columnContext(),
                    context.folderViewPath());
            flagContextDirty(newContext);
          }
        });

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    // --- Global view ---
    FileListPanel globalPanel = createFileListPanel(tableModel, null, frame, null);
    this.fileTable = globalPanel.table();
    this.rowSorter = globalPanel.sorter();
    this.searchField = globalPanel.searchField();
    this.chipRow = globalPanel.chipRow();
    this.searchSpinner = globalPanel.searchSpinner();
    this.searchFilters.clear();
    this.searchFilters.addAll(globalPanel.searchFilters());
    this.chipFilters.clear();
    this.chipFilters.putAll(globalPanel.chipFilters());
    this.chipSearchManager = globalPanel.chipManager();
    this.debouncer = globalPanel.debouncer();

    // --- Folder view ---
    RowFilter<FileTableModel, Integer> folderRowFilterInstance =
        new RowFilter<>() {
          @Override
          public boolean include(Entry<? extends FileTableModel, ? extends Integer> entry) {
            return folderRowFilter(entry);
          }
        };
    FileListPanel folderPanel =
        createFileListPanel(
            tableModel, folderRowFilterInstance, frame, dir -> setFolderViewPath(dir));
    this.folderTable = folderPanel.table();
    this.folderRowSorter = folderPanel.sorter();
    this.folderRowSorter.setComparator(
        CaColumn.FILE_TYPE.ordinal(),
        (Comparator<Object>)
            (a, b) -> {
              boolean aUp = "Parent folder".equals(a);
              boolean bUp = "Parent folder".equals(b);
              if (aUp && bUp) return 0;
              if (aUp) return -1;
              if (bUp) return 1;
              return ((Comparable<Object>) a).compareTo(b);
            });
    this.folderSearchField = folderPanel.searchField();
    this.folderChipRow = folderPanel.chipRow();
    this.folderSearchSpinner = folderPanel.searchSpinner();
    this.folderSearchFilters.clear();
    this.folderSearchFilters.addAll(folderPanel.searchFilters());
    this.folderChipFilters.clear();
    this.folderChipFilters.putAll(folderPanel.chipFilters());
    this.folderChipManager = folderPanel.chipManager();
    this.folderDebouncer = folderPanel.debouncer();

    // Wrap folder panel with path bar
    JPanel folderViewPanel = new JPanel(new BorderLayout());
    folderViewPanel.add(createFolderPathBar(), BorderLayout.NORTH);
    folderViewPanel.add(folderPanel.panel(), BorderLayout.CENTER);

    JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.LEFT);
    tabbedPane.addTab(null, Icons.get("folder"), globalPanel.panel());
    tabbedPane.addTab(null, Icons.get("tree"), folderViewPanel);
    tabbedPane.addTab(null, Icons.get("settings"),
        getSettingsComponent(context.columnContext().displayedColumns()));
    JPanel topStatusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
    topStatusBar.add(topInfoLabel);
    topStatusBar.add(Box.createHorizontalStrut(16));
    topStatusBar.add(renderInfoLabel);
    frame.add(topStatusBar, BorderLayout.NORTH);
    frame.add(tabbedPane, BorderLayout.CENTER);
    context.filesAndTimestamps().keySet().forEach(tableModel::addFile);

    initDisplayedColumns(context);

    rowSorter.addRowSorterListener(e -> {
      if (e.getType() != RowSorterEvent.Type.SORT_ORDER_CHANGED) return;
      List<? extends RowSorter.SortKey> sortKeys = rowSorter.getSortKeys();
      var oldColumnContext = context.columnContext();
      ColumnContext newColumnContext;
      if (!sortKeys.isEmpty()) {
        RowSorter.SortKey key = sortKeys.get(0);
        int modelColumn = key.getColumn();
        SortOrder order = key.getSortOrder();
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
              newColumnContext,
              context.folderViewPath()));
    });
    folderRowSorter.addRowSorterListener(e -> {
      if (e.getType() != RowSorterEvent.Type.SORT_ORDER_CHANGED) return;
      List<? extends RowSorter.SortKey> sortKeys = folderRowSorter.getSortKeys();
      var oldColumnContext = context.columnContext();
      ColumnContext newColumnContext;
      if (!sortKeys.isEmpty()) {
        RowSorter.SortKey key = sortKeys.get(0);
        int modelColumn = key.getColumn();
        SortOrder order = key.getSortOrder();
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
              newColumnContext,
              context.folderViewPath()));
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
            context.columnContext(),
            context.folderViewPath());
    flagContextDirty(newContext);
  }

  private FileListPanel createFileListPanel(
      FileTableModel model,
      RowFilter<FileTableModel, Integer> extraFilter,
      JFrame parentFrame,
      Consumer<File> onDirDoubleClick) {
    JTable table = new JTable(model);
    TableRowSorter<FileTableModel> sorter = new TableRowSorter<>(model);
    sorter.setComparator(CaColumn.ICON.ordinal(), (a, b) -> 0);

    List<SearchFilter> searchFilters = new ArrayList<>();
    Map<String, ChipSearchFilter> chipFilters = new HashMap<>();

    sorter.setRowFilter(
        new RowFilter<>() {
          @Override
          public boolean include(Entry<? extends FileTableModel, ? extends Integer> entry) {
            int row = entry.getIdentifier();
            if (model.isDirectoryRow(row)) return extraFilter != null;
            if (extraFilter != null && !extraFilter.include(entry)) return false;
            synchronized (searchFilters) {
              for (SearchFilter f : searchFilters) {
                if (!f.contains(row)) return false;
              }
            }
            return true;
          }
        });

    table.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            int viewRow = table.rowAtPoint(e.getPoint());
            int viewCol = table.columnAtPoint(e.getPoint());
            if (viewRow == -1 || viewCol == -1) {
              return;
            }
            int modelRow = table.convertRowIndexToModel(viewRow);
            int modelCol = table.convertColumnIndexToModel(viewCol);
            Object object = model.getValueAt(modelRow, modelCol);

            if (e.getClickCount() == 1 && SwingUtilities.isRightMouseButton(e)) {
              JPopupMenu rightMenu = createTableContextMenu(table);
              long fileCount =
                  Arrays.stream(table.getSelectedRows())
                      .map(table::convertRowIndexToModel)
                      .filter(m -> !tableModel.isDirectoryRow(m))
                      .count();
              long dirCount = table.getSelectedRowCount() - fileCount;
              String title = fileCount + " file(s)";
              if (dirCount > 0) title += ", " + dirCount + " folder(s)";
              ((JLabel) rightMenu.getComponent(0)).setText(title);
              SwingUtilities.invokeLater(
                  () -> rightMenu.show(e.getComponent(), e.getX(), e.getY()));
            }
            if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
              if (model.isDirectoryRow(modelRow)) {
                if (onDirDoubleClick != null) {
                  onDirDoubleClick.accept(model.getFileAt(modelRow));
                }
                return;
              }
              if (modelCol == CaColumn.ICON.ordinal()) {
                File file = model.getFileAt(modelRow);
                SchematicPreviewHelper.getInstance().showPreviewDialog(file, frame);
                return;
              }
              if (modelCol == CaColumn.BLOCKS.ordinal() && object instanceof List<?> list) {
                showBlocksPopup(e, list.stream().map(Object::toString).toList());
                return;
              }
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
                        .map(entry -> entry.getKey().toString() + ": " + entry.getValue().toString())
                        .collect(Collectors.joining("\n"));
              } else if (object instanceof String s) {
                content = s;
              } else {
                content = object.toString();
              }
              var longestLine =
                  Arrays.stream(content.split("\n")).max(Comparator.comparing(String::length));
              if (longestLine.isEmpty()) return;
              textArea.setText(content);
              FontMetrics metrics = textArea.getFontMetrics(textArea.getFont());
              float pxWidth = metrics.stringWidth(longestLine.get());
              pxWidth /= metrics.stringWidth("m");
              int columns = (int) Math.ceil(pxWidth);
              textArea.setColumns(Math.max(Math.min(100, columns + 5), 20));
              textArea.setRows(Math.max(7, Math.min(30, content.split("\n").length)));
              textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
              JScrollPane scrollPane = new JScrollPane(textArea);
              menu.add(new JLabel(model.getColumn(modelCol).displayName));
              menu.add(scrollPane);
              SwingUtilities.invokeLater(() -> menu.show(e.getComponent(), e.getX(), e.getY()));
            }
          }
        });

    table
        .getColumnModel()
        .addColumnModelListener(
            new TableColumnModelListener() {
              @Override
              public void columnAdded(TableColumnModelEvent e) {
                updateContextColumns(table.getColumnModel());
              }

              @Override
              public void columnRemoved(TableColumnModelEvent e) {
                updateContextColumns(table.getColumnModel());
              }

              @Override
              public void columnMoved(TableColumnModelEvent e) {
                updateContextColumns(table.getColumnModel());
              }

              @Override
              public void columnMarginChanged(ChangeEvent e) {
                updateContextColumns(table.getColumnModel());
              }

              @Override
              public void columnSelectionChanged(ListSelectionEvent e) {}
            });

    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setRowSorter(sorter);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

    for (CaColumn c : CaColumn.values()) {
      table.getColumnModel().getColumn(c.ordinal()).setCellRenderer(c.renderer);
    }
    {
      Function<CaColumn, List<String>> highlightFn = col -> {
        synchronized (searchFilters) {
          List<String> result = new ArrayList<>();
          for (SearchFilter f : searchFilters) {
            String s = f.getHighlightString(col);
            if (s != null && !s.isEmpty()) result.add(s.toLowerCase());
          }
          return result;
        }
      };
      for (CaColumn c : CaColumn.values()) {
        c.renderer.setHighlightLookup(highlightFn);
      }
    }
    table.setRowHeight(64);

    JTextField searchField = new JTextField(40);
    searchField.setText("Search");
    searchField.putClientProperty("JTextField.placeholderText", "Search...");

    FreeTextSearchFilter freeTextFilter = new FreeTextSearchFilter(model, context);
    synchronized (searchFilters) {
      searchFilters.add(freeTextFilter);
    }
    freeTextFilter.setOnProgressCallback(
        progress -> SwingUtilities.invokeLater(() -> tableModel.fireTableDataChanged()));
    freeTextFilter.startThread();

    DebouncedDocumentListener debouncer =
        DebouncedDocumentListener.create(
            200, () -> freeTextFilter.setSearchText(searchField.getText()));
    searchField.getDocument().addDocumentListener(debouncer);

    JProgressBar searchSpinner = new JProgressBar();
    searchSpinner.setIndeterminate(true);
    searchSpinner.setPreferredSize(new Dimension(16, 16));
    searchSpinner.setMaximumSize(new Dimension(16, 16));
    searchSpinner.setVisible(false);
    debouncer.addPropertyChangeListener(
        DebouncedDocumentListener.PROP_SEARCHING,
        e -> searchSpinner.setVisible((boolean) e.getNewValue()));

    JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    chipRow.setVisible(false);

    ChipSearchManager[] chipManagerRef = new ChipSearchManager[1];
    chipManagerRef[0] =
        new ChipSearchManager(
            searchField,
            chipRow,
            () -> syncChipFilters(chipFilters, searchFilters, chipManagerRef[0]),
            parentFrame);
    ChipSearchManager chipManager = chipManagerRef[0];
    JButton addConditionBtn = chipManager.createAddConditionButton();

    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    JScrollPane scrollPane = new JScrollPane(table);

    JPanel topPanel = new JPanel();
    topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
    {
      JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
      {
        JButton addBtn = new JButton(Icons.get("menu"));
        final JPopupMenu filesMenu =
            new JPopupMenu() {
              @Override
              public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (d.width < addBtn.getWidth()) d.width = addBtn.getWidth();
                return d;
              }
            };
        filesMenu.setLayout(new GridLayout(0, 1));
        {
          JButton importSingleFile = new JButton("Import file");
          importSingleFile.addActionListener(a -> this.importFile());
          filesMenu.add(importSingleFile);

          JButton importFolder = new JButton("Import folder");
          importFolder.addActionListener(a -> this.importFolder());
          filesMenu.add(importFolder);

          JButton reloadAll = new JButton("Reload all");
          reloadAll.addActionListener(a -> this.reloadAllFiles());
          filesMenu.add(reloadAll);

          JButton removeAll = new JButton("Remove all");
          removeAll.addActionListener(a -> this.removeAllFiles());
          filesMenu.add(removeAll);
        }

        addBtn.addActionListener(
            e ->
                SwingUtilities.invokeLater(
                    () -> filesMenu.show(addBtn, 0, addBtn.getHeight())));
        searchRow.add(addBtn);
        searchRow.add(searchField);
        searchRow.add(addConditionBtn);
        searchRow.add(searchSpinner);
      }
      topPanel.add(searchRow);

      topPanel.add(chipRow);
    }

    JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(topPanel, BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(bottomPanel, BorderLayout.SOUTH);

    return new FileListPanel(
        panel, table, sorter, searchField, chipManager, chipRow, searchSpinner,
        searchFilters, chipFilters, debouncer);
  }

  private JPopupMenu createTableContextMenu(JTable table) {
    JPopupMenu rightMenu = new JPopupMenu();
    rightMenu.setLayout(new GridLayout(0, 1));

    JLabel menuTitleLbl = new JLabel("");
    rightMenu.add(menuTitleLbl);

    JButton reloadFileBtn = new JButton("Reload");
    reloadFileBtn.addActionListener(
        a -> {
          for (int viewRow : table.getSelectedRows()) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (tableModel.isDirectoryRow(modelRow)) continue;
            File file = tableModel.getFileAt(modelRow);
            tableModel.flagReloadFile(modelRow);
            tableModel.invalidateIconCache(file);
            try {
              Files.deleteIfExists(ResourceUtils.getRenderPathForFile(file));
              Files.deleteIfExists(ResourceUtils.getThumbPathForFile(file));
            } catch (IOException ex) {
              // ignore
            }
          }
        });
    rightMenu.add(reloadFileBtn);

    JButton renderFilesBtn = new JButton("Render");
    renderFilesBtn.addActionListener(
        a -> {
          List<File> files = Arrays.stream(table.getSelectedRows())
              .map(table::convertRowIndexToModel)
              .filter(m -> !tableModel.isDirectoryRow(m))
              .mapToObj(tableModel::getFileAt)
              .toList();
          if (!files.isEmpty()) renderFiles(files);
        });
    rightMenu.add(renderFilesBtn);

    JButton removeFilesBtn = new JButton("Remove");
    removeFilesBtn.addActionListener(
        a -> {
          File[] files = Arrays.stream(table.getSelectedRows())
              .map(table::convertRowIndexToModel)
              .filter(m -> !tableModel.isDirectoryRow(m))
              .mapToObj(tableModel::getFileAt)
              .toArray(File[]::new);
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
                  context.columnContext(),
                  context.folderViewPath()));
        });
    rightMenu.add(removeFilesBtn);

    JButton deleteFilesBtn = new JButton("Delete from disk");
    deleteFilesBtn.addActionListener(
        a -> {
          File[] selected = Arrays.stream(table.getSelectedRows())
              .map(table::convertRowIndexToModel)
              .filter(m -> !tableModel.isDirectoryRow(m))
              .mapToObj(tableModel::getFileAt)
              .toArray(File[]::new);
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
            } catch (IOException ex) {
              failed.add(file);
            }
          }
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
                  context.columnContext(),
                  context.folderViewPath()));
          if (!failed.isEmpty()) {
            JOptionPane.showMessageDialog(
                frame,
                "Could not delete:\n" + failed.stream().map(File::getName).collect(Collectors.joining("\n")),
                "Delete from disk",
                JOptionPane.ERROR_MESSAGE);
          }
        });
    rightMenu.add(deleteFilesBtn);

    JButton openFolderBtn = new JButton("Open folder");
    openFolderBtn.addActionListener(
        a -> {
          File[] files = Arrays.stream(table.getSelectedRows())
              .map(table::convertRowIndexToModel)
              .mapToObj(tableModel::getFileAt)
              .toArray(File[]::new);
          Desktop desktop = Desktop.getDesktop();
          if (desktop == null) return;
          List<File> folders =
              Arrays.stream(files)
                  .filter(f -> !"..".equals(f.getName()))
                  .map(f -> f.isDirectory() ? f : f.getParentFile())
                  .distinct()
                  .toList();
          if (folders.size() > 2) {
            int reply2 =
                JOptionPane.showConfirmDialog(
                    frame,
                    "You are trying to open " + folders.size() + " folders at once. Do you want to continue?",
                    "Open folders",
                    JOptionPane.YES_NO_OPTION);
            if (reply2 != JOptionPane.YES_OPTION) return;
          }
          folders.forEach(
              folder -> {
                try {
                  desktop.open(folder);
                } catch (IOException ex) {
                  throw new RuntimeException(ex);
                }
              });
        });
    rightMenu.add(openFolderBtn);

    JButton convertToSponge3Btn = new JButton("Convert to Sponge3");
    convertToSponge3Btn.addActionListener(a -> convertSelectedToSponge3(table));
    rightMenu.add(convertToSponge3Btn);

    JButton replaceSandstoneBtn = new JButton("Replace blocks");
    replaceSandstoneBtn.addActionListener(a -> replaceSandstoneWithCobblestone(table));
    rightMenu.add(replaceSandstoneBtn);

    return rightMenu;
  }

  private void convertSelectedToSponge3(JTable table) {
    List<File> selected = Arrays.stream(table.getSelectedRows())
        .map(table::convertRowIndexToModel)
        .filter(m -> !tableModel.isDirectoryRow(m))
        .mapToObj(tableModel::getFileAt)
        .toList();
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
    File outputDir = chooser.getSelectedFile();
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
                            context.columnContext(),
                            context.folderViewPath()));
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
                          "Converted " + result.convertedFiles().size() + " of " + selected.size()
                              + " file(s).\n\nFailed:\n" + failedNames,
                          "Convert to Sponge3",
                          JOptionPane.WARNING_MESSAGE);
                    }
                  });
            },
            "sponge3-converter")
        .start();
  }

  private void replaceSandstoneWithCobblestone(JTable table) {
    File[] selected = Arrays.stream(table.getSelectedRows())
        .map(table::convertRowIndexToModel)
        .filter(m -> !tableModel.isDirectoryRow(m))
        .mapToObj(tableModel::getFileAt)
        .toArray(File[]::new);
    if (selected.length == 0) {
      JOptionPane.showMessageDialog(
          frame, "No files selected.", "Replace blocks", JOptionPane.WARNING_MESSAGE);
      return;
    }
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
    var mapping = BlockReplacerDialog.show(frame, palette, availableBlocks, loaded);
    if (mapping.isEmpty()) return;
    var replaceResult = mapping.get();
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
            context.columnContext(),
            context.folderViewPath()));
    String msg = written.size() + " file(s) written.";
    if (!failed.isEmpty())
      msg += "\nFailed: " + failed.stream().map(File::getName).collect(Collectors.joining(", "));
    JOptionPane.showMessageDialog(
        frame,
        msg,
        "Replace blocks",
        failed.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
  }

  private JPanel createFolderPathBar() {
    JPanel pathBar = new JPanel(new BorderLayout(4, 2));
    pathBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

    folderPathField = new JTextField();
    folderPathField.setEditable(false);
    folderPathField.setFont(folderPathField.getFont().deriveFont(Font.PLAIN, 12f));
    folderPathField.setColumns(30);

    folderErrorIcon = new JLabel();
    folderErrorIcon.setIcon(UIManager.getIcon("OptionPane.errorIcon"));
    folderErrorIcon.setToolTipText("Folder no longer exists");
    folderErrorIcon.setVisible(false);

    JButton upBtn = new JButton("\u2191 Up");
    upBtn.setToolTipText("Go to parent folder");
    upBtn.addActionListener(
        e -> {
          if (folderViewPath == null) return;
          File parent = folderViewPath.getParentFile();
          if (parent != null) {
            setFolderViewPath(parent);
          }
        });

    JButton browseBtn = new JButton("Browse...");
    browseBtn.setToolTipText("Select a folder");
    browseBtn.addActionListener(
        e -> {
          JFileChooser chooser = new JFileChooser();
          chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
          chooser.setDialogTitle("Select folder to display");
          if (folderViewPath != null) {
            chooser.setCurrentDirectory(folderViewPath);
          }
          if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            setFolderViewPath(chooser.getSelectedFile());
          }
        });

    JPanel leftGroup = new JPanel(new BorderLayout(4, 0));
    leftGroup.add(new JLabel("Folder: "), BorderLayout.WEST);
    leftGroup.add(folderPathField, BorderLayout.CENTER);
    leftGroup.add(folderErrorIcon, BorderLayout.EAST);

    JPanel rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
    rightGroup.add(upBtn);
    rightGroup.add(browseBtn);

    pathBar.add(leftGroup, BorderLayout.CENTER);
    pathBar.add(rightGroup, BorderLayout.EAST);

    // Restore saved folder if any, else default to home
    if (context.folderViewPath() != null) {
      setFolderViewPath(context.folderViewPath());
    } else {
      setFolderViewPath(new File(System.getProperty("user.home")));
    }

    return pathBar;
  }

  private void setFolderViewPath(File folder) {
    if ("..".equals(folder.getName())) {
      folder = folderViewPath.getParentFile();
      if (folder == null) return;
    }
    this.folderViewPath = folder;
    folderPathField.setText(folder.getAbsolutePath());
    List<File> dirs = new ArrayList<>();
    if (folder.getParentFile() != null) {
      dirs.add(new File(".."));
      tableModel.setParentDirPath(folder.getParentFile().getAbsolutePath());
    }
    File[] subdirs = folder.listFiles(File::isDirectory);
    if (subdirs != null) {
      Arrays.stream(subdirs).sorted().forEach(dirs::add);
    }
    tableModel.setDirectories(dirs);
    flagContextDirty(
        new AppContext(
            context.filesAndTimestamps(),
            context.lastSearchPath(),
            context.guiBounds(),
            context.neverBeforeUsed(),
            context.columnContext(),
            folder));
    folderRowSorter.sort();
    autoImportFolderView();
    updateFolderErrorIcon();
  }

  private boolean folderRowFilter(RowFilter.Entry<? extends FileTableModel, ? extends Integer> entry) {
    if (folderViewPath == null) return false;
    int row = (int) entry.getIdentifier();
    File file = tableModel.getFileAt(row);
    if (file == null) return false;
    return file.getParentFile().equals(folderViewPath);
  }

  private void autoImportFolderView() {
    File folder = folderViewPath;
    if (folder == null) return;

    Map<File, Long> currentTimestamps = context.filesAndTimestamps();

    new Thread(
            () -> {
              try {
                List<File> discovered = getAllFiles(folder, 1);
                if (discovered.isEmpty()) return;

                var newTimestamps = new HashMap<>(currentTimestamps);
                for (File f : discovered) {
                  newTimestamps.putIfAbsent(f, System.currentTimeMillis());
                }
                SwingUtilities.invokeLater(
                    () -> {
                      tableModel.addFiles(discovered);
                      flagContextDirty(
                          new AppContext(
                              newTimestamps,
                              context.lastSearchPath(),
                              context.guiBounds(),
                              context.neverBeforeUsed(),
                              context.columnContext(),
                              context.folderViewPath()));
                    });
              } catch (IOException e) {
                logger.log(Level.WARNING, "Auto-import failed for " + folder, e);
              }
            },
            "folder-auto-import")
        .start();
  }

  private void autoRefreshFolderView() {
    folderRefreshCounter++;
    if (folderRefreshCounter % 3 != 0) return;
    File folder = folderViewPath;
    if (folder == null) return;
    updateFolder(folder.toPath(), false);
    SwingUtilities.invokeLater(this::updateFolderErrorIcon);
  }

  private void syncChipFilters(
      Map<String, ChipSearchFilter> chipFilters,
      List<SearchFilter> searchFilters,
      ChipSearchManager chipManager) {
    Set<String> activeKeys =
        chipManager.getConditions().stream()
            .map(c -> c.column().name() + ":" + c.searchTerm().toLowerCase())
            .collect(Collectors.toSet());

    var it = chipFilters.entrySet().iterator();
    while (it.hasNext()) {
      var e = it.next();
      if (!activeKeys.contains(e.getKey())) {
        removeFilter(e.getValue(), searchFilters);
        it.remove();
      }
    }

    for (ChipSearchManager.SearchCondition cond : chipManager.getConditions()) {
      String key = cond.column().name() + ":" + cond.searchTerm().toLowerCase();
      if (!chipFilters.containsKey(key)) {
        ChipSearchFilter f = new ChipSearchFilter(tableModel, cond.column(), cond.searchTerm());
        chipFilters.put(key, f);
        addFilter(f, searchFilters);
      }
    }
  }

  private void addFilter(SearchFilter filter, List<SearchFilter> searchFilters) {
    synchronized (searchFilters) {
      searchFilters.add(filter);
    }
    filter.setOnProgressCallback(
        progress ->
            SwingUtilities.invokeLater(
                () -> {
                  int n = tableModel.getRowCount();
                  if (n > 0) {
                    tableModel.fireTableDataChanged();
                  }
                }));
    filter.startThread();
  }

  private void removeFilter(SearchFilter filter, List<SearchFilter> searchFilters) {
    filter.stop();
    filter.setOnProgressCallback(null);
    synchronized (searchFilters) {
      searchFilters.remove(filter);
    }
    int n = tableModel.getRowCount();
    if (n > 0) {
      tableModel.fireTableDataChanged();
    }
  }

  private void importFolder() {
    JFileChooser chooser = getFileChooser(true);
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
    File[] folders = chooser.getSelectedFiles();
    new Thread(() -> {
      for (File f : folders) updateFolder(f.toPath(), true);
    }, "folder-importer").start();
  }

  /**
   * Recursively collects all files in a folder and its subfolders.
   *
   * @param folder   the root folder to start from
   * @param maxDepth maximum recursion depth
   * @return list of files found
   * @throws IOException if an I/O error occurs
   */
  public static List<File> getAllFiles(File folder, int maxDepth) throws IOException {
    if (folder == null || !folder.isDirectory()) {
      throw new IllegalArgumentException("Input must be a valid directory");
    }

    List<File> fileList = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(folder.toPath(), maxDepth)) {
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
                Files.deleteIfExists(ResourceUtils.getThumbPathForFile(file));
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
            context.columnContext(),
            context.folderViewPath()));
  }

  private void updateFolderErrorIcon() {
    folderErrorIcon.setVisible(folderViewPath == null || !folderViewPath.exists());
  }

  private void updateFolder(Path folder, boolean recursive) {
    File folderFile = folder.toFile();
    if (!folderFile.isDirectory()) return;
    int depth = recursive ? Integer.MAX_VALUE : 1;

    Map<File, Long> timestamps = context.filesAndTimestamps();

    List<File> underFolder = new ArrayList<>();
    int count = tableModel.getRowCount();
    for (int i = 0; i < count; i++) {
      File f = tableModel.getFileAt(i);
      if (f == null) continue;
      boolean matches =
          recursive
              ? f.toPath().startsWith(folder)
              : folderFile.equals(f.getParentFile());
      if (matches) {
        underFolder.add(f);
      }
    }
    Set<File> underSet = new HashSet<>(underFolder);

    List<File> currentFiles;
    try {
      currentFiles = getAllFiles(folderFile, depth);
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to scan folder: " + folder, e);
      return;
    }
    Set<File> currentSet = new HashSet<>(currentFiles);

    List<File> toRemove = new ArrayList<>();
    for (File f : underFolder) {
      if (!currentSet.contains(f) && !f.exists()) {
        toRemove.add(f);
      }
    }

    List<File> toAdd = new ArrayList<>();
    for (File f : currentFiles) {
      if (!underSet.contains(f)) {
        toAdd.add(f);
      }
    }

    List<File> toReload = new ArrayList<>();
    for (File f : currentFiles) {
      if (underSet.contains(f)) {
        Long stored = timestamps.get(f);
        if (stored == null || f.lastModified() > stored) {
          toReload.add(f);
        }
      }
    }

    for (File f : toReload) {
      tableModel.invalidateIconCache(f);
      try {
        Files.deleteIfExists(ResourceUtils.getRenderPathForFile(f));
        Files.deleteIfExists(ResourceUtils.getThumbPathForFile(f));
      } catch (IOException e) {
        // ignore
      }
    }

    if (toRemove.isEmpty() && toReload.isEmpty() && toAdd.isEmpty()) return;

    var newTimestamps = new HashMap<>(timestamps);
    for (File f : toRemove) newTimestamps.remove(f);
    for (File f : toReload) newTimestamps.put(f, f.lastModified());
    for (File f : toAdd) newTimestamps.putIfAbsent(f, f.lastModified());

    SwingUtilities.invokeLater(() -> {
      if (!toRemove.isEmpty()) tableModel.removeFile(toRemove.toArray(File[]::new));
      for (File f : toReload) {
        int row = tableModel.indexOfFile(f);
        if (row >= 0) tableModel.flagReloadFile(row);
      }
      if (!toAdd.isEmpty()) tableModel.addFiles(toAdd);
      flagContextDirty(
          new AppContext(
              newTimestamps,
              context.lastSearchPath(),
              context.guiBounds(),
              context.neverBeforeUsed(),
              context.columnContext(),
              context.folderViewPath()));
    });
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
                            newColumnContext,
                            context.folderViewPath()));
                    updateDisplayColumns(
                        new ArrayList<>(newColumnContext.displayedColumns()),
                        new ArrayList<>(newColumnContext.columnWidths()),
                        new HashSet<>(),
                        fileTable.getColumnModel(),
                        columToTableColumn);
                    updateDisplayColumns(
                        new ArrayList<>(newColumnContext.displayedColumns()),
                        new ArrayList<>(newColumnContext.columnWidths()),
                        new HashSet<>(),
                        folderTable.getColumnModel(),
                        folderColumToTableColumn);
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

    // construct hashmaps to lookup column -> tableColumn for both tables
    TableColumnModel globalColumnModel = fileTable.getColumnModel();
    for (int i = 0; i < globalColumnModel.getColumnCount(); i++) {
      TableColumn tc = globalColumnModel.getColumn(i);
      CaColumn c = CaColumn.values()[i];
      columToTableColumn.put(c, tc);
    }
    TableColumnModel folderColumnModel = folderTable.getColumnModel();
    for (int i = 0; i < folderColumnModel.getColumnCount(); i++) {
      TableColumn tc = folderColumnModel.getColumn(i);
      CaColumn c = CaColumn.values()[i];
      folderColumToTableColumn.put(c, tc);
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

    // display only columns from saved context on both tables
    updateDisplayColumns(
        displayed,
        widths,
        new HashSet<>(),
        fileTable.getColumnModel(),
        columToTableColumn);
    updateDisplayColumns(
        displayed,
        widths,
        new HashSet<>(),
        folderTable.getColumnModel(),
        folderColumToTableColumn);

    // apply sorting to the global table (folder table uses default PATH sort)
    if (contextClone.orderedColumn() != null) {
      List<RowSorter.SortKey> keys =
          List.of(
              new RowSorter.SortKey(
                  contextClone.orderedColumn().ordinal(),
                  contextClone.orderAscending() ? SortOrder.ASCENDING : SortOrder.DESCENDING));
      rowSorter.setSortKeys(keys);
      rowSorter.sort();
    }
    // folder table default: sort by PATH ascending
    folderRowSorter.setSortKeys(
        List.of(new RowSorter.SortKey(CaColumn.PATH.ordinal(), SortOrder.ASCENDING)));
    folderRowSorter.sort();
  }

  void updateDisplayColumns(
      List<CaColumn> caColumns,
      List<Integer> columnWidths,
      HashSet<CaColumn> hiddenColumns,
      TableColumnModel columnModel,
      HashMap<CaColumn, TableColumn> columnMap) {
    { // rebuild column model to match active columns.
      // Remove all columns
      while (columnModel.getColumnCount() > 0) {
        columnModel.removeColumn(columnModel.getColumn(0));
      }
      for (int i = 0; i < caColumns.size(); i++) {
        CaColumn column = caColumns.get(i);
        if (hiddenColumns.contains(column)) continue;
        TableColumn tc = columnMap.get(column);
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
            newColumnContext,
            context.folderViewPath()));
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

  private void setTextRenderingSchematics(String text) {
    renderInfoLabel.setText(text);
  }

  private void checkLoadingThreads() {
    synchronized (loadingThreads) {
      loadingThreads.removeIf(t -> !t.isAlive());
    }
  }

  private void showBlocksPopup(MouseEvent e, List<String> blocks) {
    JPopupMenu menu = new JPopupMenu();
    menu.setLightWeightPopupEnabled(false);
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    for (String block : blocks) {
      JLabel label = new JLabel(block, blockIconProvider.getIcon(block), JLabel.LEADING);
      label.setIconTextGap(6);
      label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
      panel.add(label);
    }
    JScrollPane scroll = new JScrollPane(panel);
    scroll.setPreferredSize(new Dimension(300, Math.min(400, blocks.size() * 28 + 10)));
    menu.add(new JLabel("Blocks"));
    menu.add(scroll);
    SwingUtilities.invokeLater(() -> menu.show(e.getComponent(), e.getX(), e.getY()));
  }


  private void renderFiles(List<File> selectedFiles) {
    logger.info("Rendering files:");
    for (File f : selectedFiles) {
      logger.info(" - " + f.getAbsolutePath());
    }
    var paths = selectedFiles.stream().map(File::toPath).toList();
    SchematicPreviewHelper.getInstance().queueInteractiveRender(paths);
  }

  private void showRenderPreview(int modelRow) {
    File file = tableModel.getFileAt(modelRow);
    SchematicPreviewHelper.getInstance().showPreviewDialog(file, frame);
  }

  private void renderSchematicIcon(File file) {
    if (file == null) return;
    WPObject obj = tableModel.getSchematicFor(file);
    if (obj == null) return;
    SchematicPreviewHelper.getInstance().render(
        file,
        obj,
        () -> {
          tableModel.invalidateIconCache(file);
          int idx = tableModel.indexOfFile(file);
          if (idx >= 0) tableModel.fireTableRowsUpdated(idx, idx);
        });
  }

  private void onSchematicLoaded(File file) {
    int row = tableModel.indexOfFile(file);
    System.out.println("[FileRenderApp] onSchematicLoaded file=" + file.getName() + " row=" + row);
    if (row >= 0) {
      synchronized (searchFilters) {
        for (SearchFilter f : searchFilters) {
          f.markDirty(row);
        }
      }
      synchronized (folderSearchFilters) {
        for (SearchFilter f : folderSearchFilters) {
          f.markDirty(row);
        }
      }
    }
    renderSchematicIcon(file);
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
