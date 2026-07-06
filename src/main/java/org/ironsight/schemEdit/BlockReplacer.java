package org.ironsight.schemEdit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import pitheguy.schemconvert.converter.Schematic;
import pitheguy.schemconvert.converter.formats.SchematicFormats;

public class BlockReplacer {

  /**
   * Loads any supported schematic file ({@code .schem}, {@code .nbt}, {@code .litematic}, {@code
   * .bp}) into a {@link Schematic}. The format is detected automatically from the file extension.
   *
   * @param file the schematic file to load
   * @return the parsed {@link Schematic}
   * @throws IOException if the file cannot be read or parsed
   */
  public static Schematic load(File file) throws IOException {
    return Schematic.read(file);
  }

  /**
   * Returns a new {@link Schematic} with every block whose ID appears as a key in {@code
   * replacements} substituted with the corresponding value.
   *
   * <p>Block IDs must be fully-qualified namespaced strings, e.g. {@code "minecraft:stone"} →
   * {@code "minecraft:diamond_block"}. Blocks not present in {@code replacements} are copied
   * verbatim. Block entities and entities are forwarded unchanged from the source.
   *
   * @param source the schematic whose blocks should be replaced
   * @param replacements map of {@code oldBlockId → newBlockId}
   * @return a new schematic with the substitutions applied
   */
  public static Schematic replace(Schematic source, Map<String, String> replacements) {
    int[] size = source.getSize();
    int xSize = size[0];
    int ySize = size[1];
    int zSize = size[2];

    Schematic.Builder builder =
        new Schematic.Builder(source.getSourceFile(), source.getDataVersion(), xSize, ySize, zSize);

    for (int x = 0; x < xSize; x++) {
      for (int y = 0; y < ySize; y++) {
        for (int z = 0; z < zSize; z++) {
          String block = source.getBlock(x, y, z);
          String replaced = replacements.getOrDefault(block, block);
          builder.setBlockAt(x, y, z, replaced);
        }
      }
    }

    source
        .getBlockEntities()
        .forEach((pos, tag) -> builder.addBlockEntity(pos.x(), pos.y(), pos.z(), tag));

    source
        .getEntities()
        .forEach(
            entity ->
                builder.addEntity(entity.id(), entity.x(), entity.y(), entity.z(), entity.nbt()));

    return builder.build();
  }

  /**
   * Returns the ordered list of unique block IDs present in the schematic, i.e. the block palette.
   * Each entry is a fully-qualified namespaced string such as {@code "minecraft:stone"}.
   *
   * @param schematic the schematic to inspect
   * @return an unmodifiable set of block IDs
   */
  public static Set<String> getPalette(Schematic schematic) {
    return Set.copyOf(schematic.getPalette());
  }

  /**
   * Loads the built-in block list from {@code BlockData.json} and returns a palette of all known
   * Minecraft block IDs in {@code minecraft:<name>} form.
   *
   * <p>One entry is emitted per block defined in {@code BlockData.json}. Blocks with block-state
   * properties are represented as bare namespaced IDs (no {@code [...]}) since the dialog strips
   * states for display and {@link BlockListUtil#toLegalBlockState} validates any states that are
   * later transplanted from source blocks.
   *
   * @return an unmodifiable set of all known Minecraft block IDs
   * @throws IOException if {@code BlockData.json} cannot be found or read
   */
  public static Set<String> loadDefaultPalette() throws IOException {
    return BlockListUtil.loadBlockData().keySet().stream()
        .map(id -> "minecraft:" + id)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Strips the {@code minecraft:} namespace prefix and any {@code [...]} blockstate suffix from a
   * single block ID, returning only the bare name (e.g. {@code
   * "minecraft:sandstone_stairs[facing=north]"} → {@code "sandstone_stairs"}).
   *
   * @param blockId the full block ID to strip
   * @return the bare block name without namespace or blockstate
   */
  public static String stripBlockId(String blockId) {
    String s =
        blockId.startsWith("minecraft:") ? blockId.substring("minecraft:".length()) : blockId;
    int bracket = s.indexOf('[');
    return bracket >= 0 ? s.substring(0, bracket) : s;
  }

  /**
   * Returns a palette with all {@code [...]} blockstate suffixes stripped, yielding only the bare
   * block IDs (e.g. {@code "minecraft:sandstone_stairs"} instead of {@code
   * "minecraft:sandstone_stairs[facing=north,half=bottom]"}). Duplicate entries that arise from
   * stripping are collapsed into one.
   *
   * @param palette the full palette to strip
   * @return an unmodifiable set of bare block IDs
   */
  public static Set<String> blockOnlyPalette(Set<String> palette) {
    return palette.stream()
        .map(BlockReplacer::stripBlockId)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Loads a block ID palette from a plain-text file containing one {@code minecraft:*} ID per line
   * (e.g. {@code BlockIds.txt}). Blank lines and lines that do not start with {@code minecraft:}
   * are ignored.
   *
   * @param file the palette file to read
   * @return an unmodifiable set of block IDs
   * @throws IOException if the file cannot be read
   */
  public static Set<String> loadPalette(File file) throws IOException {
    try (var lines = Files.lines(file.toPath())) {
      return lines
          .map(String::strip)
          .filter(line -> line.startsWith("minecraft:"))
          .collect(Collectors.toUnmodifiableSet());
    }
  }

  /**
   * Loads a block ID palette from an {@link InputStream} containing one {@code minecraft:*} ID per
   * line. Blank lines and lines that do not start with {@code minecraft:} are ignored. The stream
   * is closed after reading.
   *
   * @param stream the input stream to read from
   * @return an unmodifiable set of block IDs
   * @throws IOException if the stream cannot be read
   */
  public static Set<String> loadPalette(InputStream stream) throws IOException {
    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream))) {
      return reader
          .lines()
          .map(String::strip)
          .filter(line -> line.startsWith("minecraft:"))
          .collect(Collectors.toUnmodifiableSet());
    }
  }

  /**
   * Writes a {@link Schematic} to a {@code .schem} (Sponge v3) file.
   *
   * @param schematic the schematic to write
   * @param output the destination file; will be created or overwritten
   * @throws IOException if the file cannot be written
   */
  public static void write(Schematic schematic, File output) throws IOException {
    schematic.write(output, SchematicFormats.SCHEM);
  }

  /**
   * Returns a new {@link Schematic} with blocks replaced according to category mappings.
   *
   * <p>Each entry in {@code categoryMapping} maps a source category (bare block ID, e.g. {@code
   * "cobblestone"}) to a target category (e.g. {@code "dark_oak"}). Every block in the schematic
   * whose category matches a source category key is remapped to the equivalent block in the target
   * category.
   *
   * <p>The equivalent target block is found by:
   *
   * <ol>
   *   <li>Stripping the source category prefix from the block ID to obtain a suffix (e.g. {@code
   *       cobblestone_stairs} − {@code cobblestone} = {@code _stairs}).
   *   <li>Appending that suffix to the target category prefix (e.g. {@code dark_oak} + {@code
   *       _stairs} = {@code dark_oak_stairs}).
   *   <li>Checking whether the resulting block ID exists in {@code BlockData.json}. If it does not
   *       exist, the source block is kept unchanged (no replacement).
   * </ol>
   *
   * <p>Block-state properties are transplanted and validated via {@link
   * BlockListUtil#toLegalBlockState} after the ID substitution.
   *
   * <p>Example: given {@code {"cobblestone" → "dark_oak"}}:
   *
   * <pre>
   *   cobblestone_stairs  → dark_oak_stairs   (exists)
   *   cobblestone_wall    → no replacement     (dark_oak_wall does not exist)
   *   cobblestone         → dark_oak           (bare category maps to bare target)
   * </pre>
   *
   * @param source the schematic to process
   * @param categoryMapping bare source category → bare target category
   * @return a new schematic with substitutions applied
   * @throws IOException if {@code BlockData.json} cannot be loaded
   */
  public static Schematic replaceByCategory(Schematic source, Map<String, String> categoryMapping)
      throws IOException {

    List<BlockListUtil.CategoryEntry> categoryEntries = BlockListUtil.loadCategories();
    Replacer replacer = new Replacer(categoryEntries);
    Map<String, String> bareReplacements = replacer.expandCategoryMapping(categoryMapping);

    Map<String, String> replacements = new LinkedHashMap<>();
    for (var entry : bareReplacements.entrySet()) {
      replacements.put("minecraft:" + entry.getKey(), "minecraft:" + entry.getValue());
    }

    // Expand bare-ID replacements to cover all full blockstate variants in the
    // schematic palette. e.g. "minecraft:red_sandstone_stairs[facing=east,...]"
    // needs its own entry pointing to "minecraft:sandstone_stairs[facing=east,...]".
    Map<String, String> expanded = new LinkedHashMap<>(replacements);
    for (String paletteEntry : getPalette(source)) {
      String bareSource = stripBlockId(paletteEntry); // strips namespace + [...]
      String namespacedBare = "minecraft:" + bareSource;
      if (!replacements.containsKey(namespacedBare)) continue;

      // Transplant blockstate from source variant onto target bare ID
      String namespacedTarget = replacements.get(namespacedBare);
      int bracket = paletteEntry.indexOf('[');
      if (bracket == -1) continue; // bare ID already covered above
      String blockState = paletteEntry.substring(bracket); // e.g. "[facing=east,...]"
      String targetWithState = BlockListUtil.toLegalBlockState(namespacedTarget + blockState);
      expanded.put(paletteEntry, targetWithState);
    }

    return replace(source, expanded);
  }
}
