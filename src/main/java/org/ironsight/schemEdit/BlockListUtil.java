package org.ironsight.schemEdit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class BlockListUtil {

  /**
   * URL pointing to the Minecraft block data summary maintained by misode. The JSON at this URL
   * contains a complete list of all Minecraft block IDs and their associated properties for the
   * current game version.
   */
  public static final String BLOCK_RESOURCE_URL =
      "https://github.com/misode/mcmeta/blob/summary/blocks/data.json";

  /**
   * Holds the parsed data for a single Minecraft block: the set of valid values for each
   * block-state property, and the default value of each property.
   *
   * <p>Example for {@code acacia_button}:
   *
   * <pre>
   *   possibleValues = { "face" -> ["floor","wall","ceiling"],
   *                       "facing" -> ["north","south","west","east"],
   *                       "powered" -> ["true","false"] }
   *   defaultValues  = { "face" -> "wall", "facing" -> "north", "powered" -> "false" }
   * </pre>
   *
   * Blocks with no block-state properties (e.g. {@code air}) will have empty maps.
   *
   * @param blockId the namespaced block ID, e.g. {@code "acacia_button"}
   * @param possibleValues map of property name → list of all valid string values
   * @param defaultValues map of property name → default string value
   */
  public record BlockData(
      String blockId,
      Map<String, List<String>> possibleValues,
      Map<String, String> defaultValues) {}

  /**
   * Parses a {@code BlockData.json} resource stream into a map of block ID → {@link BlockData}.
   *
   * <p>The expected JSON structure is a top-level object whose keys are block IDs. Each value is a
   * two-element array:
   *
   * <ol>
   *   <li>An object mapping each property name to an array of all valid string values.
   *   <li>An object mapping each property name to its default string value.
   * </ol>
   *
   * Blocks with no block-state properties have empty objects {@code {}} for both elements.
   *
   * <p>The returned map preserves insertion order (i.e. the order blocks appear in the file). It is
   * unmodifiable.
   *
   * @param in an {@link InputStream} for the JSON file; the caller is responsible for closing it
   * @return an unmodifiable map of block ID → {@link BlockData}
   * @throws IOException if the stream cannot be read or the JSON is malformed
   */
  public static Map<String, BlockData> loadBlockData(InputStream in) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(in);

    Map<String, BlockData> result = new LinkedHashMap<>();

    root.fields()
        .forEachRemaining(
            entry -> {
              String blockId = entry.getKey();
              JsonNode pair = entry.getValue();

              JsonNode possibleNode = pair.get(0);
              JsonNode defaultNode = pair.get(1);

              Map<String, List<String>> possibleValues = new LinkedHashMap<>();
              if (possibleNode != null) {
                possibleNode
                    .fields()
                    .forEachRemaining(
                        prop -> {
                          List<String> values = new ArrayList<>();
                          prop.getValue().forEach(v -> values.add(v.asText()));
                          possibleValues.put(prop.getKey(), Collections.unmodifiableList(values));
                        });
              }

              Map<String, String> defaultValues = new LinkedHashMap<>();
              if (defaultNode != null) {
                defaultNode
                    .fields()
                    .forEachRemaining(
                        prop -> defaultValues.put(prop.getKey(), prop.getValue().asText()));
              }

              result.put(
                  blockId,
                  new BlockData(
                      blockId,
                      Collections.unmodifiableMap(possibleValues),
                      Collections.unmodifiableMap(defaultValues)));
            });

    return Collections.unmodifiableMap(result);
  }

  /**
   * Convenience overload that loads {@code BlockData.json} directly from the classpath (i.e. from
   * {@code src/main/resources/BlockData.json}).
   *
   * @return an unmodifiable map of block ID → {@link BlockData}
   * @throws IOException if the resource cannot be found or read
   */
  public static Map<String, BlockData> loadBlockData() throws IOException {
    try (InputStream in =
        BlockListUtil.class.getClassLoader().getResourceAsStream("BlockData.json")) {
      if (in == null) {
        throw new IOException("BlockData.json not found on classpath");
      }
      return loadBlockData(in);
    }
  }

  // -------------------------------------------------------------------------
  // Block-state normalisation
  // -------------------------------------------------------------------------

  /** Lazily-loaded, immutable cache of the full block data map. */
  private static volatile Map<String, BlockData> blockDataCache;

  /**
   * Returns the shared block-data map, loading it from the classpath on the first call and caching
   * it for all subsequent calls.
   *
   * @throws IllegalStateException if {@code BlockData.json} cannot be loaded
   */
  private static Map<String, BlockData> getBlockDataCache() {
    if (blockDataCache == null) {
      synchronized (BlockListUtil.class) {
        if (blockDataCache == null) {
          try {
            blockDataCache = loadBlockData();
          } catch (IOException e) {
            throw new IllegalStateException("Failed to load BlockData.json", e);
          }
        }
      }
    }
    return blockDataCache;
  }

  /**
   * Strips all block-state properties that are not valid for the given block, returning a
   * normalised block-state string.
   *
   * <p>The input format is the standard Minecraft block-state notation:
   *
   * <pre>
   *   blockId[key1=value1,key2=value2,...]
   * </pre>
   *
   * The {@code [...]} part is optional. A block ID with no {@code [} is returned as-is.
   *
   * <p>Filtering rules (applied per {@code key=value} pair in the brackets):
   *
   * <ul>
   *   <li>If the block ID is not found in the data file the entire input is returned unchanged.
   *   <li>If {@code key} is not a known property for the block, the pair is dropped.
   *   <li>If {@code key} is known but {@code value} is not among its possible values, the pair is
   *       dropped.
   * </ul>
   *
   * <p>When all pairs are dropped the trailing {@code []} is omitted, so the result is a bare block
   * ID.
   *
   * <p>Example:
   *
   * <pre>
   *   toLegalBlockState("acacia_button[face=floor,facing=north,variant=top]")
   *   // → "acacia_button[face=floor,facing=north]"
   *   //   "variant" is not a property of acacia_button, so it is dropped.
   * </pre>
   *
   * @param blockState a block-state string such as {@code
   *     "acacia_button[face=floor,powered=false]"}
   * @return the block-state string with illegal properties removed
   */
  public static String toLegalBlockState(String blockState) {
    int bracketStart = blockState.indexOf('[');

    // No block-state properties — nothing to filter.
    if (bracketStart == -1) {
      return blockState;
    }

    String blockId = blockState.substring(0, bracketStart);

    // Only parse the first [...] group; ignore any trailing [...][...] from concatenation bugs.
    int bracketEnd = blockState.indexOf(']', bracketStart);
    String statesPart =
        blockState.substring(bracketStart + 1, bracketEnd >= 0 ? bracketEnd : blockState.length());

    // Strip namespace prefix for lookup — BlockData.json keys are un-namespaced.
    int colon = blockId.indexOf(':');
    String lookupKey = colon >= 0 ? blockId.substring(colon + 1) : blockId;

    Map<String, BlockData> cache = getBlockDataCache();
    BlockData data = cache.get(lookupKey);

    // Unknown block — return input unchanged.
    if (data == null) {
      return blockState;
    }

    Map<String, List<String>> possible = data.possibleValues();
    StringJoiner kept = new StringJoiner(",");

    for (String pair : statesPart.split(",")) {
      int eq = pair.indexOf('=');
      if (eq == -1) continue; // malformed pair, skip

      String key = pair.substring(0, eq);
      String value = pair.substring(eq + 1);

      List<String> validValues = possible.get(key);
      if (validValues == null) continue; // unknown property, drop
      if (!validValues.contains(value)) continue; // invalid value, drop

      kept.add(pair);
    }

    String keptStr = kept.toString();
    return keptStr.isEmpty() ? blockId : blockId + "[" + keptStr + "]";
  }

  // -------------------------------------------------------------------------
  // Category data types
  // -------------------------------------------------------------------------

  /**
   * A single category entry from {@code BlockCategories.json}.
   *
   * @param id the category identifier (e.g. {@code "oak_wood"})
   * @param icon the representative block ID for the icon
   * @param blocks the bare block IDs belonging to this category
   */
  public record CategoryEntry(String id, String icon, List<String> blocks) {}

  /**
   * Functional suffix strings that distinguish block variants within a material family (e.g. {@code
   * _stairs}, {@code _slab}, {@code _door}). Ordered longest-first so longer suffixes match before
   * shorter ones.
   */
  static final List<String> FUNCTIONAL_SUFFIXES =
      List.of(
          "_wall_hanging_sign",
          "_wall_sign",
          "_hanging_sign",
          "_fence_gate",
          "_pressure_plate",
          "_stairs",
          "_slab",
          "_wall",
          "_button",
          "_fence",
          "_sign",
          "_trapdoor",
          "_door",
          "_sapling",
          "_leaves",
          "_planks",
          "_shelf",
          "_pillar",
          "_grate",
          "_bulb",
          "_stem",
          "_hyphae",
          "_roots",
          "_block",
          "_wood",
          "_log",
          "_bricks",
          "_tiles");

  /**
   * Loads the category list from {@code BlockCategories.json} on the classpath.
   *
   * @return an unmodifiable list of {@link CategoryEntry}
   * @throws IOException if the resource cannot be found or read
   */
  public static List<CategoryEntry> loadCategories() throws IOException {
    try (InputStream in =
        BlockListUtil.class.getClassLoader().getResourceAsStream("BlockCategories.json")) {
      if (in == null) {
        throw new IOException("BlockCategories.json not found on classpath");
      }
      return loadCategories(in);
    }
  }

  /** Parses a {@code BlockCategories.json} stream into a list of {@link CategoryEntry}. */
  public static List<CategoryEntry> loadCategories(InputStream in) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(in);

    List<CategoryEntry> result = new ArrayList<>();
    if (root.isArray()) {
      for (JsonNode node : root) {
        String id = node.get("id").asText();
        String icon = node.get("icon").asText();
        List<String> blocks = new ArrayList<>();
        for (JsonNode b : node.get("blocks")) {
          blocks.add(b.asText());
        }
        result.add(new CategoryEntry(id, icon, Collections.unmodifiableList(blocks)));
      }
    }
    return Collections.unmodifiableList(result);
  }

  static final List<String> DECORATING_PREFIXES =
      List.of(
          "waxed_exposed_",
          "waxed_oxidized_",
          "waxed_weathered_",
          "waxed_",
          "exposed_",
          "oxidized_",
          "weathered_",
          "stripped_",
          "potted_",
          "infested_",
          "cracked_",
          "mossy_",
          "chiseled_",
          "smooth_",
          "cobbled_",
          "cut_",
          "polished_",
          "dead_",
          "gilded_");
}
