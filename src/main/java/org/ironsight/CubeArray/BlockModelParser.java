package org.ironsight.CubeArray;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Parses vanilla-format block model JSON (see BlockModelFormat.md) out of any resource pack that
 * follows the standard {@code assets/<namespace>/models/block/*.json} layout - this class has no
 * knowledge of any specific resource pack's contents, only of that layout.
 *
 * <p>Handles the two indirections the format relies on: {@code "parent"} model inheritance
 * (textures maps are merged root-to-leaf, "elements" is inherited wholesale unless a model
 * defines its own) and {@code "#key"} texture references (resolved against the merged textures
 * map, chasing further "#" indirections until a literal sprite path is found).
 */
public class BlockModelParser {
  private static final Logger logger = AppLogger.get(BlockModelParser.class);

  /** Namespace assumed for a model reference with no explicit "namespace:" prefix. */
  private static final String DEFAULT_NAMESPACE = "minecraft";

  private BlockModelParser() {}

  /**
   * Parses every block model found under {@code resourcePackRoot/assets/<namespace>/models/block}
   * for every namespace present in the pack.
   *
   * @param resourcePackRoot directory containing the pack's {@code assets/} folder (i.e. an
   *     already-extracted resource pack, not a zip)
   * @return fully-qualified model id (e.g. {@code "minecraft:block/acacia_fence_post"}) to parsed
   *     model. Models that fail to parse are logged and omitted rather than failing the whole
   *     pack.
   */
  public static Map<String, BlockModel> parseAll(Path resourcePackRoot) throws IOException {
    Resolver resolver = new Resolver(resourcePackRoot);
    Map<String, BlockModel> result = new LinkedHashMap<>();
    for (String modelId : findAllBlockModelIds(resourcePackRoot)) {
      try {
        result.put(modelId, resolver.resolveModel(modelId));
      } catch (IOException e) {
        logger.log(Level.WARNING, "failed to parse block model: " + modelId, e);
      }
    }
    return result;
  }

  /**
   * Parses a single block model by id, e.g. {@code "block/acacia_fence_post"} (namespace defaults
   * to "minecraft") or {@code "dnt:block/some_block"}, following its "parent" chain as needed.
   */
  public static BlockModel parseModel(Path resourcePackRoot, String modelId) throws IOException {
    return new Resolver(resourcePackRoot).resolveModel(modelId);
  }

  /** Finds every {@code assets/<namespace>/models/block/**.json} file in the pack. */
  private static List<String> findAllBlockModelIds(Path resourcePackRoot) throws IOException {
    List<String> ids = new ArrayList<>();
    Path assetsDir = resourcePackRoot.resolve("assets");
    if (!Files.isDirectory(assetsDir)) return ids;

    try (Stream<Path> namespaceDirs = Files.list(assetsDir)) {
      for (Path namespaceDir : namespaceDirs.filter(Files::isDirectory).toList()) {
        String namespace = namespaceDir.getFileName().toString();
        Path blockModelsDir = namespaceDir.resolve("models").resolve("block");
        if (!Files.isDirectory(blockModelsDir)) continue;

        try (Stream<Path> files = Files.walk(blockModelsDir)) {
          files
              .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".json"))
              .forEach(
                  file -> {
                    String relative =
                        blockModelsDir.relativize(file).toString().replace('\\', '/');
                    String modelPath =
                        "block/" + relative.substring(0, relative.length() - ".json".length());
                    ids.add(namespace + ":" + modelPath);
                  });
        }
      }
    }
    return ids;
  }

  /** namespace:path -> fully-qualified reference, defaulting an unqualified ref's namespace. */
  private static String qualify(String reference, String defaultNamespace) {
    return reference.indexOf(':') >= 0 ? reference : defaultNamespace + ":" + reference;
  }

  private static String namespaceOf(String fullyQualifiedRef) {
    return fullyQualifiedRef.substring(0, fullyQualifiedRef.indexOf(':'));
  }

  private static Face parseFace(String name) {
    try {
      return Face.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      logger.warning("unknown face name in block model json: " + name);
      return null;
    }
  }

  private static Vector3f toVector3(float[] xyz) {
    return xyz != null && xyz.length == 3
        ? new Vector3f(xyz[0], xyz[1], xyz[2])
        : new Vector3f();
  }

  /** Resolves a face's "#key" (or already-literal) texture reference against a textures map. */
  private static String resolveTextureRef(String ref, Map<String, String> textures) {
    Set<String> seen = new HashSet<>();
    while (ref != null && ref.startsWith("#")) {
      if (!seen.add(ref)) {
        logger.warning("cyclic texture reference: " + ref);
        return null;
      }
      ref = textures.get(ref.substring(1));
    }
    return ref;
  }

  /** The merged, not-yet-converted state of a model after walking its "parent" chain. */
  private record RawModel(Map<String, String> textures, List<ElementJson> elements) {
    static final RawModel EMPTY = new RawModel(Map.of(), null);
  }

  /** Resolves and caches model files against one resource pack root. */
  private static final class Resolver {
    private final Path resourcePackRoot;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, RawModel> cache = new HashMap<>();

    Resolver(Path resourcePackRoot) {
      this.resourcePackRoot = resourcePackRoot;
    }

    BlockModel resolveModel(String modelId) throws IOException {
      RawModel raw = resolve(qualify(modelId, DEFAULT_NAMESPACE), new LinkedHashSet<>());
      return toBlockModel(raw);
    }

    private RawModel resolve(String fqName, Set<String> visiting) throws IOException {
      RawModel cached = cache.get(fqName);
      if (cached != null) return cached;

      if (!visiting.add(fqName)) {
        logger.warning("cyclic block model parent chain at: " + fqName);
        return RawModel.EMPTY;
      }

      Path file = locate(fqName);
      RawModel result;
      if (!Files.isRegularFile(file)) {
        // no JSON file for this reference, e.g. a builtin parent like "builtin/generated" that
        // the game generates procedurally instead of loading from disk
        result = RawModel.EMPTY;
      } else {
        ModelJson json = mapper.readValue(file.toFile(), ModelJson.class);
        RawModel parent =
            json.parent != null
                ? resolve(qualify(json.parent, namespaceOf(fqName)), visiting)
                : RawModel.EMPTY;

        Map<String, String> textures = new LinkedHashMap<>(parent.textures());
        if (json.textures != null) textures.putAll(json.textures);

        // elements are inherited wholesale from the parent unless this model defines its own
        List<ElementJson> elements = json.elements != null ? json.elements : parent.elements();

        result = new RawModel(textures, elements);
      }

      visiting.remove(fqName);
      cache.put(fqName, result);
      return result;
    }

    private Path locate(String fqName) {
      String namespace = namespaceOf(fqName);
      String path = fqName.substring(namespace.length() + 1);
      return resourcePackRoot
          .resolve("assets")
          .resolve(namespace)
          .resolve("models")
          .resolve(path + ".json");
    }

    private BlockModel toBlockModel(RawModel raw) {
      if (raw.elements() == null) return new BlockModel(List.of());
      List<SubBlock> subBlocks = new ArrayList<>(raw.elements().size());
      for (ElementJson element : raw.elements()) {
        subBlocks.add(toSubBlock(element, raw.textures()));
      }
      return new BlockModel(subBlocks);
    }

    private SubBlock toSubBlock(ElementJson element, Map<String, String> textures) {
      Map<Face, FaceTexture> faces = new LinkedHashMap<>();
      if (element.faces != null) {
        for (var entry : element.faces.entrySet()) {
          Face face = parseFace(entry.getKey());
          if (face == null) continue;
          faces.put(face, toFaceTexture(entry.getValue(), textures));
        }
      }
      Vector3f from = toVector3(element.from);
      Vector3f to = toVector3(element.to);
      if (element.rotation == null || element.rotation.angle == 0f) {
        return new SubBlock(from, to, faces);
      }
      return new SubBlock(
          from, to, faces, elementRotation(element.rotation), elementRescale(element.rotation));
    }

    /**
     * An element's {@code "rotation"} as Euler angles (radians). Vanilla element rotation is about a
     * single named axis; downstream only X and Y are applied (which is what {@code block/cross}
     * needs - a 45deg turn about Y), so a Z-axis rotation is parsed but warned about as unsupported.
     */
    private static Vector3f elementRotation(RotationJson rot) {
      float rad = (float) Math.toRadians(rot.angle);
      return switch (rot.axis == null ? "" : rot.axis.toLowerCase(Locale.ROOT)) {
        case "x" -> new Vector3f(rad, 0f, 0f);
        case "y" -> new Vector3f(0f, rad, 0f);
        case "z" -> {
          logger.warning("block model element rotation about Z is not rendered: angle=" + rot.angle);
          yield new Vector3f(0f, 0f, rad);
        }
        default -> {
          logger.warning("unknown/absent block model rotation axis: " + rot.axis);
          yield new Vector3f();
        }
      };
    }

    /**
     * The per-axis size multiplier implied by an element rotation's {@code "rescale"} flag: vanilla
     * scales the two axes perpendicular to the rotation axis by {@code 1/cos(angle)} so the rotated
     * element still reaches the block faces (e.g. the {@code block/cross} quads span corner to
     * corner). Because that scale is isotropic in the plane perpendicular to the rotation axis, it
     * commutes with the rotation and can be applied to the element's size before rotating - which is
     * exactly how {@code SchemReader.computePieces} consumes it. Unit (no-op) when rescale is off.
     */
    private static Vector3f elementRescale(RotationJson rot) {
      if (!rot.rescale) return new Vector3f(1f, 1f, 1f);
      float f = 1f / (float) Math.cos(Math.toRadians(rot.angle));
      return switch (rot.axis == null ? "" : rot.axis.toLowerCase(Locale.ROOT)) {
        case "x" -> new Vector3f(1f, f, f);
        case "y" -> new Vector3f(f, 1f, f);
        case "z" -> new Vector3f(f, f, 1f);
        default -> new Vector3f(1f, 1f, 1f);
      };
    }

    private FaceTexture toFaceTexture(FaceJson faceJson, Map<String, String> textures) {
      String resolvedTexture = resolveTextureRef(faceJson.texture, textures);
      Vector4f uv =
          faceJson.uv != null && faceJson.uv.length == 4
              ? new Vector4f(faceJson.uv[0], faceJson.uv[1], faceJson.uv[2], faceJson.uv[3])
              : null; // omitted uv means "auto-generate from element position", not modeled yet
      Face cullface = faceJson.cullface != null ? parseFace(faceJson.cullface) : null;
      return new FaceTexture(resolvedTexture, uv, cullface);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ModelJson {
    public String parent;
    public Map<String, String> textures;
    public List<ElementJson> elements;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ElementJson {
    public float[] from;
    public float[] to;
    public Map<String, FaceJson> faces;
    public RotationJson rotation;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class RotationJson {
    public float[] origin;
    public String axis;
    public float angle;
    public boolean rescale;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class FaceJson {
    public float[] uv;
    public String texture;
    public String cullface;
  }
}
