package org.ironsight.CubeArray.swing;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.ironsight.CubeArray.InstancedCubes;
import org.ironsight.CubeArray.ResourceUtils;
import org.ironsight.CubeArray.SchemReader;
import org.ironsight.schemEdit.BlockListUtil;
import org.ironsight.schemEdit.BlockListUtil.CategoryEntry;
import org.ironsight.schemEdit.BlockReplacer;
import org.ironsight.schemEdit.NotFoundExc;
import org.pepsoft.worldpainter.DefaultCustomObjectProvider;
import org.pepsoft.worldpainter.objects.WPObject;
import pitheguy.schemconvert.converter.Schematic;
import org.ironsight.schemEdit.Replacer;

/**
 * Modal dialog that lets the user map each block in a palette to a replacement.
 *
 * <p>Two modes, cycled by the button above the list:
 *
 * <ul>
 *   <li><b>Blocks</b> — one row per unique block type. Blockstate properties are transplanted
 *       automatically via {@link BlockListUtil#toLegalBlockState}.
 *   <li><b>Details</b> — one row per exact block-state variant. No transplanting.
 * </ul>
 *
 * <p>Use {@link #show(Component, Set, Set)} to open the dialog and retrieve the result.
 */
public class BlockReplacerDialog extends JDialog {

  // -------------------------------------------------------------------------
  // Mode
  // -------------------------------------------------------------------------

  private enum Mode {
    BLOCKS,
    DETAILS
  }

  private Mode mode = Mode.BLOCKS;

  // -------------------------------------------------------------------------
  // Result type
  // -------------------------------------------------------------------------

  /**
   * Result returned by {@link #show}: a direct {@code fullId → fullId} map for {@link
   * BlockReplacer#replace}.
   */
  public record ReplaceResult(Map<String, String> replacements) {}

  // -------------------------------------------------------------------------
  // Dialog state
  // -------------------------------------------------------------------------

  private final Set<String> palette;
  private final Set<String> availableBlocks;
  private final Map<File, Schematic> loadedSchematics;

  // Blocks-mode data
  private final List<Map.Entry<String, List<String>>> groups;
  private final String[] choices; // full IDs from availableBlocks
  private final String[] displayChoices; // stripped names for Blocks-mode combos

  // Details-mode data
  private final List<String> variants; // sorted full IDs from palette
  private final String[] detailChoices; // full IDs for Details-mode combos

  // Category data (used by Blocks-mode header combos)
  private final String[] categoryChoices; // all known category prefixes (sorted)
  private final Map<String, List<String>>
      categoryToBlocks; // category → distinct stripped block IDs in palette
  private final Map<String, List<String>>
      allCategoryBlocks; // category → all blocks in that category (full data)

  // Block → category map for grouping blocks-mode rows
  private final Map<String, String> blockToCategory;
  private final Set<String> realCategories;

  private final Replacer replacer;
  private Map<String, int[]> categoryComboRanges;

  // Active combo list — rebuilt on each mode switch
  private final List<SearchableTextField> combos = new ArrayList<>();

  // Maps combo index → source entry index for result building
  private int[] comboToGroup;
  private int[] comboToVariant;

  private String searchText = "";

  // User-set replacements that survive scroll-pane rebuilds (e.g. search filtering).
  // Keyed by source block identifier: display name (Blocks mode) or variant ID (Details mode).
  private final Map<String, String> overrides = new LinkedHashMap<>();

  private boolean confirmed = false;

  // UI references held for mode switching
  private final JButton modeBtn = new JButton("Blocks");
  private JScrollPane mappingScroll;
  private JPanel centerWrapper;

  // Right-column icon labels keyed by file, updated after each background render
  private final Map<File, JLabel> rightIconLabels = new LinkedHashMap<>();

  // Cached 640×640 after-replacement previews for the right column
  private final Map<File, ImageIcon> afterPreviews = new HashMap<>();

  private final BlockIconProvider iconProvider = new BlockIconProvider(32, 20);

  // Background render executor with 1-second debounce
  private final ScheduledExecutorService renderExecutor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "preview-renderer");
            t.setDaemon(true);
            return t;
          });
  private ScheduledFuture<?> pendingRender;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  private BlockReplacerDialog(Frame owner, Set<String> palette, Set<String> availableBlocks, Map<File, Schematic> loaded) {
    super(owner, "Replace blocks", true);
    this.palette = palette;
    this.availableBlocks = availableBlocks;
    this.loadedSchematics = loaded;

    // --- Load categories (needed by all modes) ---
    List<CategoryEntry> categoryEntries;
    try {
      categoryEntries = BlockListUtil.loadCategories();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load BlockCategories.json", e);
    }
    Map<String, String> catMap = new LinkedHashMap<>();
    for (var entry : categoryEntries) {
      for (String block : entry.blocks()) {
        catMap.put(block, entry.id());
      }
    }
    this.blockToCategory = catMap;
    this.realCategories =
        categoryEntries.stream().map(CategoryEntry::id).collect(Collectors.toSet());

    this.replacer = new Replacer(categoryEntries);

    // --- Blocks-mode data ---
    Map<String, List<String>> unsorted =
        palette.stream()
            .sorted()
            .collect(
                Collectors.groupingBy(
                    BlockReplacer::stripBlockId, LinkedHashMap::new, Collectors.toList()));
    this.groups =
        unsorted.entrySet().stream()
            .sorted(
                java.util.Comparator.<Map.Entry<String, List<String>>, String>comparing(
                        e -> blockToCategory.getOrDefault(e.getKey(), e.getKey()))
                    .thenComparing(Map.Entry::getKey))
            .toList();
    this.choices = availableBlocks.stream().sorted().toArray(String[]::new);
    this.displayChoices =
        java.util.stream.Stream.concat(
                Arrays.stream(this.choices).map(BlockReplacer::stripBlockId),
                groups.stream().map(Map.Entry::getKey))
            .distinct()
            .sorted()
            .toArray(String[]::new);

    // --- Details-mode data ---
    this.variants = palette.stream().sorted().collect(Collectors.toList());
    this.detailChoices =
        java.util.stream.Stream.concat(availableBlocks.stream().sorted(), palette.stream().sorted())
            .distinct()
            .sorted()
            .toArray(String[]::new);

    // All known category IDs from JSON
    this.categoryChoices =
        categoryEntries.stream().map(CategoryEntry::id).distinct().sorted().toArray(String[]::new);
    // Map each real category to the distinct stripped block IDs from the palette that belong to it
    this.categoryToBlocks = new LinkedHashMap<>();
    for (String cat : realCategories) categoryToBlocks.put(cat, new ArrayList<>());
    palette.stream()
        .map(BlockReplacer::stripBlockId)
        .distinct()
        .sorted()
        .forEach(
            bare -> {
              String cat = catMap.getOrDefault(bare, bare);
              if (categoryToBlocks.containsKey(cat)) categoryToBlocks.get(cat).add(bare);
            });
    // Build the full category → blocks map from JSON entries
    this.allCategoryBlocks = new LinkedHashMap<>();
    for (var entry : categoryEntries) {
      allCategoryBlocks.put(entry.id(), new ArrayList<>(entry.blocks()));
    }
    allCategoryBlocks.values().forEach(list -> list.sort(null));
    setLayout(new BorderLayout(8, 8));
    getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

    add(buildTopBar(), BorderLayout.NORTH);
    mappingScroll = buildMappingPanel();
    centerWrapper = buildCenterWrapper();
    add(centerWrapper, BorderLayout.CENTER);
    add(buildButtonPanel(), BorderLayout.SOUTH);

    pack();
    setMinimumSize(new Dimension(680, 200));
    setLocationRelativeTo(owner);
    scheduleAfterPreviews();
  }

  // -------------------------------------------------------------------------
  // UI construction
  // -------------------------------------------------------------------------

  private JPanel buildTopBar() {
    modeBtn.addActionListener(e -> toggleMode());

    JTextField searchField = new JTextField();
    searchField.putClientProperty("JTextField.placeholderText", "Search blocks…");
    searchField.setPreferredSize(new Dimension(200, searchField.getPreferredSize().height));
    searchField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                onSearchChanged();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                onSearchChanged();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {}

              private void onSearchChanged() {
                searchText = searchField.getText().toLowerCase();
                SwingUtilities.invokeLater(BlockReplacerDialog.this::rebuildScrollPane);
              }
            });

    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    bar.add(modeBtn);
    bar.add(searchField);
    return bar;
  }

  /** Tears down and rebuilds the center wrapper (file columns + mapping scroll pane). */
  private void rebuildScrollPane() {
    remove(centerWrapper);
    combos.clear();
    rightIconLabels.clear();
    // afterPreviews deliberately NOT cleared — only the UI is rebuilt;
    // the underlying replacement data hasn't changed.
    // We restore cached previews onto the new labels below.
    mappingScroll = buildMappingPanel();
    centerWrapper = buildCenterWrapper();
    add(centerWrapper, BorderLayout.CENTER);
    revalidate();
    repaint();
    pack();
    // Restore any cached after-preview icons onto the fresh labels
    for (Map.Entry<File, ImageIcon> entry : afterPreviews.entrySet()) {
      JLabel label = rightIconLabels.get(entry.getKey());
      if (label != null) {
        label.setIcon(
            new ImageIcon(
                entry.getValue().getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH)));
      }
    }
  }

  private void toggleMode() {
    mode =
        switch (mode) {
          case BLOCKS -> Mode.DETAILS;
          case DETAILS -> Mode.BLOCKS;
        };
    modeBtn.setText(
        switch (mode) {
          case BLOCKS -> "Blocks";
          case DETAILS -> "Details";
        });
    searchText = "";
    overrides.clear();
    afterPreviews.clear();
    rebuildScrollPane();
    scheduleAfterPreviews();
  }

  private JScrollPane buildMappingPanel() {
    JPanel rows = new JPanel(new GridBagLayout());
    rows.setBorder(new EmptyBorder(0, 0, 6, 0));

    GridBagConstraints srcC = new GridBagConstraints();
    srcC.gridx = 0;
    srcC.gridy = 0;
    srcC.anchor = GridBagConstraints.WEST;
    srcC.insets = new Insets(2, 4, 2, 8);
    srcC.weightx = 0.5;
    srcC.fill = GridBagConstraints.HORIZONTAL;

    GridBagConstraints destC = new GridBagConstraints();
    destC.gridx = 1;
    destC.gridy = 0;
    destC.anchor = GridBagConstraints.WEST;
    destC.insets = new Insets(2, 0, 2, 4);
    destC.weightx = 0.5;
    destC.fill = GridBagConstraints.HORIZONTAL;

    rows.add(bold("Source block"), srcC);
    rows.add(bold("Replace with"), destC);
    srcC.gridy++;
    destC.gridy++;

    if (mode == Mode.BLOCKS) {
      buildBlocksRows(rows, srcC, destC);
    } else {
      buildDetailsRows(rows, srcC, destC);
    }

    int rowCount =
        switch (mode) {
          case BLOCKS -> {
            boolean filtering = !searchText.isEmpty();
            long matchingGroups =
                groups.stream()
                    .filter(g -> !filtering || g.getKey().toLowerCase().contains(searchText))
                    .count();
            long visibleCats =
                groups.stream()
                    .filter(g -> !filtering || g.getKey().toLowerCase().contains(searchText))
                    .map(g -> blockToCategory.getOrDefault(g.getKey(), g.getKey()))
                    .filter(realCategories::contains)
                    .distinct()
                    .count();
            yield (int) (matchingGroups + visibleCats);
          }
          case DETAILS -> (int)
              variants.stream()
                  .filter(v -> searchText.isEmpty() || v.toLowerCase().contains(searchText))
                  .count();
        };
    JScrollPane scroll = new JScrollPane(rows);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setPreferredSize(new Dimension(480, Math.min(400, rowCount * 40 + 48)));
    return scroll;
  }

  /** Builds one row per unique block type (grouped, blockstate auto-transplanted). */
  /** Adds a full-width separator row spanning both columns. */
  private void addCategorySeparator(JPanel rows, GridBagConstraints srcC) {
    GridBagConstraints sepC = new GridBagConstraints();
    sepC.gridx = 0;
    sepC.gridwidth = 2;
    sepC.gridy = srcC.gridy;
    sepC.fill = GridBagConstraints.HORIZONTAL;
    sepC.insets = new Insets(4, 4, 4, 4);
    rows.add(new JSeparator(), sepC);
    srcC.gridy++;
  }

  private void buildBlocksRows(JPanel rows, GridBagConstraints srcC, GridBagConstraints destC) {
    // Determine which blocks match the search filter
    boolean filtering = !searchText.isEmpty();
    Set<String> matchingGroups = new HashSet<>();
    for (var group : groups) {
      if (!filtering || group.getKey().toLowerCase().contains(searchText))
        matchingGroups.add(group.getKey());
    }
    // Real categories with ≥2 matching bare blocks get a header group
    Map<String, Long> catCount =
        groups.stream()
            .filter(g -> matchingGroups.contains(g.getKey()))
            .map(g -> blockToCategory.getOrDefault(g.getKey(), g.getKey()))
            .filter(realCategories::contains)
            .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
    Set<String> groupedCategories =
        catCount.entrySet().stream()
            .filter(e -> e.getValue() >= 2)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    // Categories that have at least one matching block
    Set<String> visibleCategories = new HashSet<>(groupedCategories);
    for (var group : groups) {
      if (!matchingGroups.contains(group.getKey())) continue;
      String cat = blockToCategory.getOrDefault(group.getKey(), group.getKey());
      if (realCategories.contains(cat)) visibleCategories.add(cat);
    }

    List<Integer> groupIndexMapping = new ArrayList<>();
    String currentCategory = null;
    int comboStart = -1;
    Map<String, int[]> ranges = new LinkedHashMap<>();
    for (int gi = 0; gi < groups.size(); gi++) {
      var group = groups.get(gi);
      String displayName = group.getKey();

      // Apply search filter
      if (filtering && !displayName.toLowerCase().contains(searchText)) continue;

      String category = blockToCategory.getOrDefault(displayName, displayName);

      if (!Objects.equals(category, currentCategory)) {
        // Close previous grouped-category range when leaving it
        if (currentCategory != null && groupedCategories.contains(currentCategory)) {
          ranges.put(currentCategory, new int[] {comboStart, combos.size()});
          addCategorySeparator(rows, srcC);
          destC.gridy = srcC.gridy;
        }
        currentCategory = null;
        // Open new grouped-category range when entering it
        if (groupedCategories.contains(category) && visibleCategories.contains(category)) {
          currentCategory = category;
          comboStart = combos.size();
          addCategorySeparator(rows, srcC);
          destC.gridy = srcC.gridy;

          SearchableTextField catField =
              new SearchableTextField(Arrays.asList(categoryChoices));
          catField.setSuggestionRenderer(createCategoryRenderer());
          catField.addCommitListener(
              () -> {
                String tgt = catField.getTextField().getText();
                if (tgt != null && !tgt.isEmpty()) autoReplaceCategory(category, tgt);
                scheduleAfterPreviews();
              });
          rows.add(buildCategoryLabel(category), srcC);
          rows.add(catField, destC);
          srcC.gridy++;
          destC.gridy++;
        }
      }

      List<String> varList = group.getValue();
      String label =
          varList.size() > 1 ? displayName + "  (" + varList.size() + " variants)" : displayName;
      JLabel srcLabel = new JLabel(label, iconProvider.getIcon(displayName), JLabel.LEADING);
      srcLabel.setIconTextGap(6);
      rows.add(srcLabel, srcC);

      rows.add(makeCombo(displayChoices, displayName), destC);
      srcC.gridy++;
      destC.gridy++;
      groupIndexMapping.add(gi);
    }
    // Close last grouped-category range
    if (currentCategory != null && groupedCategories.contains(currentCategory)) {
      ranges.put(currentCategory, new int[] {comboStart, combos.size()});
      addCategorySeparator(rows, srcC);
      destC.gridy = srcC.gridy;
    }
    this.categoryComboRanges = ranges;
    this.comboToGroup = groupIndexMapping.stream().mapToInt(Integer::intValue).toArray();
  }

  /** Auto-replaces all blocks in a category when the header combo changes. */
  private void autoReplaceCategory(String srcCategory, String tgtCategory) {
    if (tgtCategory == null || tgtCategory.isEmpty()) return;
    int[] range = categoryComboRanges.get(srcCategory);
    if (range == null) return;
    for (int i = range[0]; i < range[1]; i++) {
      SearchableTextField stf = combos.get(i);
      String srcBlock = stf.getTextField().getText();
      if (srcBlock == null || srcBlock.isEmpty()) continue;
      try {
        String replaced = replacer.replaceBlockByCategory(srcBlock, tgtCategory);
        stf.getTextField().setText(replaced);
        overrides.put(srcBlock, replaced);
      } catch (NotFoundExc ignored) {
        // no equivalent in target category — leave unchanged
      }
    }
  }

  /** Builds one row per exact block-state variant — no grouping, no transplanting. */
  private void buildDetailsRows(JPanel rows, GridBagConstraints srcC, GridBagConstraints destC) {
    boolean filtering = !searchText.isEmpty();
    List<Integer> variantIndexMapping = new ArrayList<>();
    for (int vi = 0; vi < variants.size(); vi++) {
      String variant = variants.get(vi);
      if (filtering && !variant.toLowerCase().contains(searchText)) continue;

      JLabel srcLabel =
          new JLabel(variant, iconProvider.getIcon(variant), JLabel.LEADING);
      srcLabel.setIconTextGap(6);
      rows.add(srcLabel, srcC);

      rows.add(makeCombo(detailChoices, variant), destC);
      srcC.gridy++;
      destC.gridy++;
      variantIndexMapping.add(vi);
    }
    this.comboToVariant = variantIndexMapping.stream().mapToInt(Integer::intValue).toArray();
  }

  private static final int MAX_CATEGORY_ICONS = 8;

  @SuppressWarnings("rawtypes")
  private ListCellRenderer createCategoryRenderer() {
    return (list, value, index, isSelected, cellHasFocus) -> {
      String category = value == null ? "" : (String) value;

      JPanel cell = new JPanel(new BorderLayout(6, 0));
      cell.setOpaque(true);
      cell.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
      cell.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

      JLabel nameLabel = new JLabel(category);
      nameLabel.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
      nameLabel.setFont(list.getFont());
      cell.add(nameLabel, BorderLayout.WEST);

      List<String> blocks = allCategoryBlocks.getOrDefault(category, List.of());
      JPanel iconStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
      iconStrip.setOpaque(false);
      blocks.stream()
          .limit(MAX_CATEGORY_ICONS)
          .forEach(
              blockId -> {
                Icon icon = iconProvider.getIconSmall(blockId);
                if (icon != null) {
                  JLabel iconLabel = new JLabel(icon);
                  iconLabel.setToolTipText(blockId);
                  iconStrip.add(iconLabel);
                }
              });
      cell.add(iconStrip, BorderLayout.CENTER);

      return cell;
    };
  }

  /** Builds the source-cell component for a category row: name label + icon strip. */
  private JPanel buildCategoryLabel(String category) {
    JPanel cell = new JPanel(new BorderLayout(6, 0));
    cell.setOpaque(false);

    // Left: category name label
    JLabel nameLabel = new JLabel(category);
    nameLabel.setIconTextGap(0);
    cell.add(nameLabel, BorderLayout.WEST);

    // Right: horizontal strip of small icons for blocks in this category
    List<String> blocks = categoryToBlocks.getOrDefault(category, List.of());
    JPanel iconStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    iconStrip.setOpaque(false);

    blocks.stream()
        .limit(MAX_CATEGORY_ICONS)
        .forEach(
            blockId -> {
              Icon icon = iconProvider.getIconSmall(blockId);
              if (icon != null) {
                JLabel iconLabel = new JLabel(icon);
                iconLabel.setToolTipText(blockId);
                iconStrip.add(iconLabel);
              }
            });

    cell.add(iconStrip, BorderLayout.CENTER);
    return cell;
  }

  private SearchableTextField makeCombo(String[] choices, String sourceKey) {
    SearchableTextField stf = new SearchableTextField(Arrays.asList(choices));
    stf.getTextField().setText(sourceKey);
    stf.setIconProvider(iconProvider::getIcon);
    stf.addCommitListener(
        () -> {
          String sel = stf.getTextField().getText();
          if (sel != null) {
            if (sel.equals(sourceKey)) {
              overrides.remove(sourceKey);
            } else {
              overrides.put(sourceKey, sel);
            }
          }
          scheduleAfterPreviews();
        });
    // Restore any previously stored override
    String saved = overrides.get(sourceKey);
    if (saved != null) {
      stf.getTextField().setText(saved);
    }
    combos.add(stf);
    return stf;
  }

  // -------------------------------------------------------------------------
  // File icon columns
  // -------------------------------------------------------------------------

  private JPanel buildCenterWrapper() {
    JPanel wrapper = new JPanel(new BorderLayout(8, 0));
    wrapper.add(buildFileColumn(false), BorderLayout.WEST);
    wrapper.add(mappingScroll, BorderLayout.CENTER);
    wrapper.add(buildFileColumn(true), BorderLayout.EAST);
    return wrapper;
  }

  private JScrollPane buildFileColumn(boolean isAfter) {
    JPanel column = new JPanel();
    column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
    column.setBorder(new EmptyBorder(4, 4, 4, 4));

    for (File file : loadedSchematics.keySet()) {
      JPanel entry = new JPanel();
      entry.setLayout(new BoxLayout(entry, BoxLayout.Y_AXIS));
      entry.setOpaque(false);

      JLabel iconLabel = new JLabel(loadFileIcon(file));
      iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
      iconLabel.setToolTipText(file.getName());
      iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      Runnable clickAction = isAfter ? () -> showAfterPreview(file) : () -> showFilePreview(file);
      iconLabel.addMouseListener(
          new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              if (SwingUtilities.isLeftMouseButton(e)) {
                clickAction.run();
              }
            }
          });
      entry.add(iconLabel);

      if (isAfter) {
        rightIconLabels.put(file, iconLabel);
      }

      JLabel nameLabel = new JLabel(file.getName(), SwingConstants.CENTER);
      nameLabel.setFont(nameLabel.getFont().deriveFont(10f));
      nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
      Dimension iconDim = new Dimension(64, nameLabel.getPreferredSize().height);
      nameLabel.setPreferredSize(iconDim);
      nameLabel.setMaximumSize(iconDim);
      entry.add(nameLabel);

      column.add(entry);
      column.add(Box.createVerticalStrut(6));
    }

    JScrollPane scroll = new JScrollPane(column);
    scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setPreferredSize(new Dimension(88, 1));
    scroll.setMinimumSize(new Dimension(88, 1));
    return scroll;
  }

  private Icon loadFileIcon(File file) {
    Path renderPath = ResourceUtils.getRenderPathForFile(file);
    if (Files.exists(renderPath)) {
      return new ImageIcon(
          new ImageIcon(renderPath.toString())
              .getImage()
              .getScaledInstance(64, 64, Image.SCALE_SMOOTH));
    }
    return generatePlaceholderIcon(file);
  }

  private static Icon generatePlaceholderIcon(File f) {
    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(0x33, 0x33, 0x33));
    g.fillRect(0, 0, 64, 64);
    g.setColor(new Color(0x88, 0x88, 0x88));
    g.setFont(g.getFont().deriveFont(Font.BOLD, 24f));
    FontMetrics fm = g.getFontMetrics();
    String letter = f.getName().substring(0, 1).toUpperCase();
    int x = (64 - fm.stringWidth(letter)) / 2;
    int y = (64 - fm.getHeight()) / 2 + fm.getAscent();
    g.drawString(letter, x, y);
    g.dispose();
    return new ImageIcon(image);
  }

  private void showFilePreview(File file) {
    Path renderPath = ResourceUtils.getRenderPathForFile(file);
    if (!Files.exists(renderPath)) {
      JOptionPane.showMessageDialog(this, "No render available yet.", file.getName(), JOptionPane.PLAIN_MESSAGE);
      return;
    }
    ImageIcon icon =
        new ImageIcon(
            new ImageIcon(renderPath.toString())
                .getImage()
                .getScaledInstance(640, 640, Image.SCALE_SMOOTH));
    JOptionPane.showMessageDialog(this, icon, file.getName(), JOptionPane.PLAIN_MESSAGE);
  }

  private void showAfterPreview(File file) {
    ImageIcon preview = afterPreviews.get(file);
    if (preview == null) {
      JOptionPane.showMessageDialog(this, "Preview not yet available.", file.getName(), JOptionPane.PLAIN_MESSAGE);
      return;
    }
    JOptionPane.showMessageDialog(this, preview, file.getName(), JOptionPane.PLAIN_MESSAGE);
  }

  // -------------------------------------------------------------------------
  // After-replacement preview rendering
  // -------------------------------------------------------------------------

  private void scheduleAfterPreviews() {
    if (pendingRender != null) pendingRender.cancel(false);
    pendingRender = renderExecutor.schedule(this::updateAfterPreviews, 1, TimeUnit.SECONDS);
  }

  private void updateAfterPreviews() {
    var ref = new java.util.concurrent.atomic.AtomicReference<Map<String, String>>();
    try {
      SwingUtilities.invokeAndWait(() -> ref.set(buildResult().replacements()));
    } catch (Exception e) {
      return;
    }
    Map<String, String> replacements = ref.get();

    for (Map.Entry<File, Schematic> entry : loadedSchematics.entrySet()) {
      File file = entry.getKey();
      Schematic schematic = entry.getValue();

      try {
        Schematic replaced = BlockReplacer.replace(schematic, replacements);

        Path tmpSchem = Files.createTempFile("replace_preview_", ".schem");
        try {
          BlockReplacer.write(replaced, tmpSchem.toFile());

          ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
          WPObject wpObj = new DefaultCustomObjectProvider().loadObject(tmpSchem.toFile());
          SchemReader.CubeSetup setup = SchemReader.prepareData(List.of(wpObj));
          Path tmpRender = Files.createTempFile("render_preview_", ".png");
          try {
            InstancedCubes.renderToFile(setup, tmpRender, 640, 640);
            ImageIcon fullPreview = new ImageIcon(tmpRender.toString());
            ImageIcon icon =
                new ImageIcon(
                    fullPreview.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH));
            afterPreviews.put(file, fullPreview);
            SwingUtilities.invokeLater(
                () -> {
                  JLabel label = rightIconLabels.get(file);
                  if (label != null) label.setIcon(icon);
                });
          } finally {
            Files.deleteIfExists(tmpRender);
          }
        } finally {
          Files.deleteIfExists(tmpSchem);
        }
      } catch (Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = e.getClass().getSimpleName();
        System.err.println("Preview render failed for " + file.getName() + ": " + msg);
      }
    }
  }

  @Override
  public void dispose() {
    if (pendingRender != null) pendingRender.cancel(false);
    super.dispose();
  }

  private JPanel buildButtonPanel() {
    JButton okBtn = new JButton("OK");
    JButton cancelBtn = new JButton("Cancel");

    okBtn.addActionListener(
        e -> {
          confirmed = true;
          dispose();
        });
    cancelBtn.addActionListener(e -> dispose());

    getRootPane().setDefaultButton(okBtn);

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    panel.add(okBtn);
    panel.add(cancelBtn);
    return panel;
  }

  private static JLabel bold(String text) {
    JLabel lbl = new JLabel(text);
    lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
    return lbl;
  }

  // -------------------------------------------------------------------------
  // Result building
  // -------------------------------------------------------------------------

  private ReplaceResult buildResult() {
    Map<String, String> result =
        switch (mode) {
          case BLOCKS -> buildResultBlocks();
          case DETAILS -> buildResultDetails();
        };
    return new ReplaceResult(result);
  }

  /**
   * Blocks mode: transplant each source variant's {@code [...]} onto the chosen replacement, then
   * validate via {@link BlockListUtil#toLegalBlockState}.
   */
  private Map<String, String> buildResultBlocks() {
    Map<String, String> result = new LinkedHashMap<>();
    for (int ci = 0; ci < combos.size(); ci++) {
      int gi = comboToGroup[ci];
      String destDisplay = combos.get(ci).getTextField().getText();
      if (destDisplay == null || destDisplay.isEmpty()) continue;
      String srcStripped = groups.get(gi).getKey();
      if (destDisplay.equals(srcStripped)) continue;

      String dest =
          Arrays.stream(choices)
              .filter(c -> BlockReplacer.stripBlockId(c).equals(destDisplay))
              .findFirst()
              .orElseGet(
                  () ->
                      groups.stream()
                          .filter(g -> g.getKey().equals(destDisplay))
                          .flatMap(g -> g.getValue().stream())
                          .map(
                              id -> {
                                int b = id.indexOf('[');
                                return b >= 0 ? id.substring(0, b) : id;
                              })
                          .findFirst()
                          .orElse(destDisplay));

      for (String variant : groups.get(gi).getValue()) {
        int bracket = variant.indexOf('[');
        String blockState = bracket >= 0 ? variant.substring(bracket) : "";
        result.put(variant, BlockListUtil.toLegalBlockState(dest + blockState));
      }
    }
    return result;
  }

  /**
   * Details mode: map each exact source variant verbatim to the chosen replacement. No blockstate
   * transplanting — what the user picks is what goes in.
   */
  private Map<String, String> buildResultDetails() {
    Map<String, String> result = new LinkedHashMap<>();
    for (int ci = 0; ci < combos.size(); ci++) {
      int vi = comboToVariant[ci];
      String src = variants.get(vi);
      String dest = combos.get(ci).getTextField().getText();
      if (dest == null || dest.isEmpty() || dest.equals(src)) continue;
      result.put(src, dest);
    }
    return result;
  }

  // -------------------------------------------------------------------------
  // Public API
  // -------------------------------------------------------------------------

  public static Optional<ReplaceResult> show(
      Component parent, Set<String> palette, Set<String> availableBlocks, Map<File, Schematic> loaded) {

    Frame owner =
        parent == null
            ? null
            : (parent instanceof Frame f
                ? f
                : (Frame) SwingUtilities.getAncestorOfClass(Frame.class, parent));

    var dialog = new BlockReplacerDialog(owner, palette, availableBlocks, loaded);
    dialog.setVisible(true);

    if (!dialog.confirmed) return Optional.empty();
    ReplaceResult result = dialog.buildResult();
    return result.replacements().isEmpty() ? Optional.empty() : Optional.of(result);
  }
}
