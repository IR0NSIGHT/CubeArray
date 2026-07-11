package org.ironsight.cubearray.mcmodel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Parses vanilla-format blockstate JSON (assets/&lt;namespace&gt;/blockstates/*.json) into a form
 * that, given a block's blockstate properties, selects which model(s) to render and with what
 * rotation. This is the file type that maps e.g. {@code oak_stairs[shape=inner_left,half=top]} to
 * "use the block/oak_stairs_inner model, rotated x=180". It has no knowledge of any specific
 * block; every block is handled by the same two generic forms of the format:
 *
 * <ul>
 *   <li><b>variants</b>: a map of a mutually-exclusive property selector (e.g.
 *       {@code "facing=east,half=bottom,shape=straight"}, or {@code ""} to always match) to a
 *       model + rotation. Exactly one variant applies.
 *   <li><b>multipart</b>: a list of parts, each optionally guarded by a {@code when} condition;
 *       every part whose condition matches applies (so a fence emits a post plus one arm per
 *       connected side). Conditions support {@code {"key":"val"}} (AND of keys, value may be an
 *       {@code "a|b"} OR-list), {@code {"OR":[...]}} and {@code {"AND":[...]}}.
 * </ul>
 */
public class BlockStateParser {

  private static final Logger logger = Logger.getLogger(BlockStateParser.class.getName());
  private static final String DEFAULT_NAMESPACE = "minecraft";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private BlockStateParser() {}

  /** A model reference plus whole-model rotation (degrees) selected from a blockstate. */
  public record ModelPlacement(String model, int x, int y, boolean uvlock) {}

  /** A parsed blockstate: resolves a block's properties to the model placements to render. */
  public interface BlockState {
    /**
     * @param properties looks up a blockstate property's value by name (null if the block has no
     *     such property)
     * @return the placements to render (empty if nothing matched)
     */
    List<ModelPlacement> select(Function<String, String> properties);
  }

  /**
   * Parses every blockstate under {@code root/assets/<namespace>/blockstates}. Individual files
   * that fail to parse are logged and skipped rather than failing the whole load.
   *
   * @return fully-qualified block id (e.g. {@code "minecraft:oak_stairs"}) to parsed blockstate
   */
  public static Map<String, BlockState> parseAll(Path root) throws IOException {
    Map<String, BlockState> result = new HashMap<>();
    Path assetsDir = root.resolve("assets");
    if (!Files.isDirectory(assetsDir)) return result;

    try (Stream<Path> namespaceDirs = Files.list(assetsDir)) {
      for (Path namespaceDir : namespaceDirs.filter(Files::isDirectory).toList()) {
        String namespace = namespaceDir.getFileName().toString();
        Path blockstatesDir = namespaceDir.resolve("blockstates");
        if (!Files.isDirectory(blockstatesDir)) continue;

        try (Stream<Path> files = Files.walk(blockstatesDir)) {
          for (Path file :
              files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".json")).toList()) {
            String relative = blockstatesDir.relativize(file).toString().replace('\\', '/');
            String name = relative.substring(0, relative.length() - ".json".length());
            try {
              result.put(namespace + ":" + name, parse(MAPPER.readTree(file.toFile())));
            } catch (IOException | RuntimeException e) {
              logger.log(Level.WARNING, "failed to parse blockstate: " + namespace + ":" + name, e);
            }
          }
        }
      }
    }
    return result;
  }

  /** Interprets a parsed blockstate JSON tree. Package-private for testing. */
  static BlockState parse(JsonNode root) {
    if (root.has("multipart")) return new Multipart(root.get("multipart"));
    if (root.has("variants")) return new Variants(root.get("variants"));
    return properties -> List.of();
  }

  /**
   * Builds a placement from an {@code apply} node, which is either a single model object or a
   * (weighted-random) array of them - for a static render we always take the first.
   */
  private static ModelPlacement toPlacement(JsonNode applyNode) {
    if (applyNode == null) return null;
    JsonNode apply = applyNode.isArray() ? (applyNode.isEmpty() ? null : applyNode.get(0)) : applyNode;
    if (apply == null) return null;
    String model = apply.path("model").asText(null);
    if (model == null) return null;
    if (model.indexOf(':') < 0) model = DEFAULT_NAMESPACE + ":" + model;
    return new ModelPlacement(
        model, apply.path("x").asInt(0), apply.path("y").asInt(0), apply.path("uvlock").asBoolean(false));
  }

  /** Matches an actual property value against a spec value, which may be an "a|b" OR-list. */
  private static boolean valueMatches(String actual, String spec) {
    if (actual == null) return false;
    if (spec.indexOf('|') >= 0) {
      for (String option : spec.split("\\|")) {
        if (option.equals(actual)) return true;
      }
      return false;
    }
    return spec.equals(actual);
  }

  /** Evaluates a multipart {@code when} condition (supports nested OR / AND). */
  private static boolean conditionMatches(JsonNode when, Function<String, String> properties) {
    if (when.has("OR")) {
      for (JsonNode sub : when.get("OR")) {
        if (conditionMatches(sub, properties)) return true;
      }
      return false;
    }
    if (when.has("AND")) {
      for (JsonNode sub : when.get("AND")) {
        if (!conditionMatches(sub, properties)) return false;
      }
      return true;
    }
    var fields = when.fields();
    while (fields.hasNext()) {
      var field = fields.next();
      if (!valueMatches(properties.apply(field.getKey()), field.getValue().asText())) return false;
    }
    return true;
  }

  private static final class Variants implements BlockState {
    // preserves file order; variant selectors are mutually exclusive, so first match wins
    private final List<Map.Entry<String, ModelPlacement>> entries = new ArrayList<>();

    Variants(JsonNode variants) {
      var fields = variants.fields();
      while (fields.hasNext()) {
        var field = fields.next();
        ModelPlacement placement = toPlacement(field.getValue());
        if (placement != null) entries.add(Map.entry(field.getKey(), placement));
      }
    }

    @Override
    public List<ModelPlacement> select(Function<String, String> properties) {
      for (var entry : entries) {
        if (selectorMatches(entry.getKey(), properties)) return List.of(entry.getValue());
      }
      return List.of();
    }

    /** Selector is a comma-separated list of key=value conditions; all must match ("" = always). */
    private static boolean selectorMatches(String selector, Function<String, String> properties) {
      if (selector.isEmpty()) return true;
      for (String condition : selector.split(",")) {
        int eq = condition.indexOf('=');
        if (eq < 0) continue;
        String key = condition.substring(0, eq);
        String value = condition.substring(eq + 1);
        if (!valueMatches(properties.apply(key), value)) return false;
      }
      return true;
    }
  }

  private static final class Multipart implements BlockState {
    private record Part(JsonNode when, ModelPlacement apply) {}

    private final List<Part> parts = new ArrayList<>();

    Multipart(JsonNode multipart) {
      for (JsonNode part : multipart) {
        ModelPlacement placement = toPlacement(part.get("apply"));
        if (placement != null) parts.add(new Part(part.get("when"), placement));
      }
    }

    @Override
    public List<ModelPlacement> select(Function<String, String> properties) {
      List<ModelPlacement> applied = new ArrayList<>();
      for (Part part : parts) {
        if (part.when() == null || conditionMatches(part.when(), properties)) {
          applied.add(part.apply());
        }
      }
      return applied;
    }
  }
}
