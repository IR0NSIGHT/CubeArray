package org.ironsight.CubeArray;

import static org.pepsoft.minecraft.Material.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import javax.vecmath.Point3i;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.mdc.MDCCapturingRuntimeException;
import org.pepsoft.worldpainter.DefaultCustomObjectProvider;
import org.pepsoft.worldpainter.objects.WPObject;

public class SchemReader {

  private static final Logger logger = AppLogger.get(SchemReader.class);

  // synthetic material property tagging which piece (one element of one blockstate-selected model
  // placement) a render instance represents, since one schematic block can expand into several
  // pieces (e.g. a fence post + arms, or a stair's slab + step) that each need their own
  // size/offset/rotation palette entry; see addPieces and the palette loop in prepareData.
  private static final String PIECE_PROPERTY = "cubearray_piece";

  // bundled vanilla assets, parsed once by ensureAssetsLoaded(); see BlockModelParser /
  // BlockStateParser and BlockModelFormat.md
  private static Map<String, BlockModel> blockModels = Map.of();
  private static Map<String, BlockStateParser.BlockState> blockStates = Map.of();
  private static boolean assetsLoaded = false;

  // cache of the render pieces a material expands into (deterministic per material)
  private static final Map<Material, List<Piece>> pieceCache = new ConcurrentHashMap<>();

  /** One renderable cuboid: a model element positioned by a blockstate placement's rotation. */
  private record Piece(Vector3f size, Vector3f offset, Vector3f rotation) {}

  /**
   * Parses the bundled vanilla block models and blockstates (see {@link BlockModelParser} and
   * {@link BlockStateParser}), once. Called at the top of {@link #prepareData} so the first render
   * (or icon render) triggers the parse; later calls are no-ops. Geometry AND orientation come
   * entirely from these vanilla assets (see src/main/resources/vanilla_assets) - a texture pack
   * only ships texture-stub models, so the real cuboids and the state->model mapping live in
   * vanilla.
   */
  private static synchronized void ensureAssetsLoaded() {
    if (assetsLoaded) return;
    assetsLoaded = true;
    try {
      ResourceUtils.copyResourcesToFile(ResourceUtils.VANILLA_ASSETS_RESOURCES);
      Path root = ResourceUtils.getInstallPath().resolve(ResourceUtils.VANILLA_ASSETS_RESOURCES);
      blockModels = BlockModelParser.parseAll(root);
      blockStates = BlockStateParser.parseAll(root);
      logger.info(
          "parsed "
              + blockModels.size()
              + " vanilla block models and "
              + blockStates.size()
              + " blockstates");
    } catch (IOException e) {
      logger.log(
          Level.WARNING, "failed to parse vanilla assets; blocks will render as default cubes", e);
    }
  }

  /**
   * Selects which model(s) + rotation to render for a material, from its blockstate (see {@link
   * BlockStateParser}). Falls back to the conventional "block/&lt;name&gt;" model with no rotation
   * when the block has no blockstate, or none of its variants matched.
   */
  private static List<BlockStateParser.ModelPlacement> selectPlacements(Material mat) {
    BlockStateParser.BlockState state = blockStates.get(mat.namespace + ":" + mat.simpleName);
    if (state != null) {
      List<BlockStateParser.ModelPlacement> placements = state.select(key -> mat.getProperty(key));
      if (!placements.isEmpty()) return placements;
    }
    return List.of(
        new BlockStateParser.ModelPlacement(
            mat.namespace + ":block/" + mat.simpleName, 0, 0, false));
  }

  /** Render pieces a material expands into: one per element of each selected model placement. */
  private static List<Piece> piecesFor(Material mat) {
    return pieceCache.computeIfAbsent(mat, SchemReader::computePieces);
  }

  private static List<Piece> computePieces(Material mat) {
    List<Piece> pieces = new ArrayList<>();
    for (BlockStateParser.ModelPlacement placement : selectPlacements(mat)) {
      BlockModel model = blockModels.get(placement.model());
      if (model == null) continue;
      Vector3f rotation =
          new Vector3f(
              (float) Math.toRadians(placement.x()), (float) Math.toRadians(placement.y()), 0f);
      for (SubBlock sub : model.subBlocks()) {
        pieces.add(new Piece(sub.size(), sub.offset(), rotation));
      }
    }
    if (pieces.isEmpty()) {
      // no resolvable geometry (missing/empty model) -> render as a default full cube
      pieces.add(new Piece(new Vector3f(1, 1, 1), new Vector3f(), new Vector3f()));
    }
    return pieces;
  }

  // TEST
  public static void main(String[] args) {
    Material mat = Material.COBBLESTONE_STAIRS;
    logger.fine(mat.toString());
  }

  public static List<WPObject> loadSchematics(List<Path> pathList, Consumer<File> onLoadError)
      throws IOException {
    ArrayList<WPObject> schematics = new ArrayList<>();
    for (Path path : pathList) {
      File file = path.toFile();
      if (file.isFile()) {
        assert file.exists();
        try {
          WPObject schematic = new DefaultCustomObjectProvider().loadObject(file);
          schematics.add(schematic);
        } catch (IllegalArgumentException ex) {
          logger.fine("ignore non-schem: " + file.getName());
        } catch (ArrayIndexOutOfBoundsException
            | ZipException
            | MDCCapturingRuntimeException
            | IllegalStateException ex) {
          logger.log(Level.SEVERE, "cant load file: " + file.getAbsolutePath(), ex);
          onLoadError.accept(file);
        }
      }
    }
    return schematics;
  }

  public static CubeSetup prepareData(List<WPObject> schematics) throws Exception {
    ensureAssetsLoaded();
    if (schematics.isEmpty()) return null;
    final String name = schematics.size() == 1 ? schematics.get(0).getName() : "schematics";
    List<Vector3f> positions = new ArrayList<>();
    List<Integer> blockTypeIndicesList = new ArrayList<>();

    HashMap<Material, Integer> mat_to_palette_idx = new HashMap<>();
    int maxColorIdx = 0;
    Point3i gridOffset = new Point3i(0, 0, 0);
    int spacer = 10;
    int maxDepth = 0;
    int index = 0;
    for (WPObject object : schematics) {

      if (index % 20 == 0) {
        gridOffset.y += maxDepth;
        gridOffset.x = 0;
        maxDepth = 0;
      }
      var dimensions = object.getDimensions();
      var offset = object.getOffset();
      for (int x = 0; x < object.getDimensions().x; x++) {
        for (int y = 0; y < object.getDimensions().y; y++) {
          for (int z = object.getDimensions().z - 1; z >= 0; z--) {
            Material mat = object.getMaterial(x, y, z);
            if (mat != null && mat != Material.AIR) {

              // test neighbours
              boolean hasNonSolidNeighbour = false;
              for (int xN = -1; xN <= 1 && !hasNonSolidNeighbour; xN++)
                for (int yN = -1; yN <= 1 && !hasNonSolidNeighbour; yN++)
                  for (int zN = -1; zN <= 1 && !hasNonSolidNeighbour; zN++) {
                    if (xN == 0 && yN == 0 && zN == 0) continue;

                    int xNN = x + xN, yNN = y + yN, zNN = z + zN;

                    // always render edge blocks
                    if (xNN < 0 || yNN < 0 || zNN < 0) {
                      hasNonSolidNeighbour = true;
                      continue;
                    }
                    if (xNN >= dimensions.x || yNN >= dimensions.y || zNN >= dimensions.z) {
                      hasNonSolidNeighbour = true;
                      continue;
                    }

                    Material neighbour = object.getMaterial(xNN, yNN, zNN);
                    if (neighbour != null && !neighbour.solid) hasNonSolidNeighbour = true;
                  }
              if (!hasNonSolidNeighbour) continue;

              Vector3f pos =
                  new Vector3f(
                      x + offset.x + gridOffset.x, z + offset.z, y + offset.y + gridOffset.y);

              maxColorIdx =
                  addPieces(
                      mat, pos, positions, blockTypeIndicesList, mat_to_palette_idx, maxColorIdx);

              if (mat.is(WATERLOGGED) || mat.watery) {
                maxColorIdx =
                    addPieces(
                        WATER, pos, positions, blockTypeIndicesList, mat_to_palette_idx, maxColorIdx);
              }
            }
          }
        }
      }

      maxDepth = Math.max(maxDepth, object.getDimensions().y);
      gridOffset.x += object.getDimensions().x + spacer;
      index++;
    }

    Vector3f[] colorPalette = new Vector3f[mat_to_palette_idx.size()];
    Arrays.fill(colorPalette, new Vector3f(1, 1, 1));

    Vector3f[] sizePalette = new Vector3f[mat_to_palette_idx.size()];
    Arrays.fill(sizePalette, new Vector3f(1, 1, 1));

    Vector3f[] offsetPalette = new Vector3f[mat_to_palette_idx.size()];
    Arrays.fill(offsetPalette, new Vector3f(0, 0, 0));

    Vector3f[] rotationPalette = new Vector3f[mat_to_palette_idx.size()];
    Arrays.fill(rotationPalette, new Vector3f(0, 0, 0));

    for (var entry : mat_to_palette_idx.entrySet()) {
      Material keyed = entry.getKey();
      int matIdx = entry.getValue();

      // color from the material; the synthetic piece tag doesn't affect its colour
      Color color = new Color(keyed.colour);
      colorPalette[matIdx] =
          new Vector3f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);

      // size/offset/rotation come entirely from the material's selected render piece
      // (blockstate -> model placement -> element); see computePieces
      String pieceTag = keyed.getProperty(PIECE_PROPERTY);
      int pieceIdx = pieceTag != null ? Integer.parseInt(pieceTag) : 0;
      List<Piece> pieces = piecesFor(keyed);
      Piece piece = pieces.get(Math.min(pieceIdx, pieces.size() - 1));
      sizePalette[matIdx] = piece.size();
      offsetPalette[matIdx] = piece.offset();
      rotationPalette[matIdx] = piece.rotation();
    }

    SpriteSheet spriteSheet =
        new SpriteSheet(
            ResourceUtils.getInstallPath().resolve(ResourceUtils.TEXTURE_PACK_ROOT).toFile(),
            mat_to_palette_idx.keySet());

    int[] blockTypeIndices = blockTypeIndicesList.stream().mapToInt(i -> i).toArray();

    Vector3f min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
    Vector3f max = new Vector3f(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE);
    positions.stream()
        .forEach(
            p -> {
              min.x = Math.min(p.x, min.x);
              min.y = Math.min(p.y, min.y);
              min.z = Math.min(p.z, min.z);
              max.x = Math.max(p.x, max.x);
              max.y = Math.max(p.y, max.y);
              max.z = Math.max(p.z, max.z);
            });

    return new CubeSetup(
        positions.toArray(new Vector3f[0]),
        blockTypeIndices,
        colorPalette,
        sizePalette,
        offsetPalette,
        rotationPalette,
        spriteSheet.getUvCoords(mat_to_palette_idx),
        spriteSheet.getTextureAtlas(),
        min,
        max,
        name);
  }

  /**
   * Adds one render instance for {@code mat} at {@code pos} to the given palette-index-keyed
   * material, creating a new palette entry if needed.
   */
  private static int addInstance(
      Material mat,
      Vector3f pos,
      List<Vector3f> positions,
      List<Integer> blockTypeIndicesList,
      HashMap<Material, Integer> mat_to_palette_idx,
      int maxColorIdx) {
    int idx;
    if (mat_to_palette_idx.containsKey(mat)) {
      idx = mat_to_palette_idx.get(mat);
    } else {
      idx = maxColorIdx++;
      mat_to_palette_idx.put(mat, idx);
    }
    positions.add(pos);
    blockTypeIndicesList.add(idx);
    return maxColorIdx;
  }

  /**
   * Emits one render instance per {@link Piece} the material expands into (see {@link
   * #piecesFor}). A single-piece material is emitted untagged, so identical blocks dedup to one
   * palette entry and textures resolve by simple name; a multi-piece material tags each instance
   * with its piece index via {@link #PIECE_PROPERTY} so the palette loop can give each piece its
   * own size/offset/rotation.
   */
  private static int addPieces(
      Material mat,
      Vector3f pos,
      List<Vector3f> positions,
      List<Integer> blockTypeIndicesList,
      HashMap<Material, Integer> mat_to_palette_idx,
      int maxColorIdx) {
    List<Piece> pieces = piecesFor(mat);
    boolean single = pieces.size() == 1;
    for (int i = 0; i < pieces.size(); i++) {
      Material keyed = single ? mat : mat.withProperty(PIECE_PROPERTY, String.valueOf(i));
      maxColorIdx =
          addInstance(keyed, pos, positions, blockTypeIndicesList, mat_to_palette_idx, maxColorIdx);
    }
    return maxColorIdx;
  }

  public static List<Path> findAllFiles(Path dir) throws IOException {
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream
          .filter(Files::isRegularFile) // only files, no directories
          .collect(Collectors.toList());
    }
  }

  public static class CubeSetup {
    final String name;
    final Vector3f[] positions;
    final int[] colorIndices;
    // block colors by type
    final Vector3f[] colorPalette;
    // block dimensions by type
    final Vector3f[] sizePalette;
    // how to shift blocks from their origin at 0.5 0.5 0.5 (width height depth)
    final Vector3f[] offsetPalette;
    final Vector3f[] rotationPalette;
    final Vector4f[] uvCoordsPalette; // uv1 uv2 for each block type
    final BufferedImage textureAtlas;
    final Vector3f min;
    final Vector3f max;

    public CubeSetup(
        Vector3f[] positions,
        int[] colorIndices,
        Vector3f[] colorPalette,
        Vector3f[] sizePalette,
        Vector3f[] offsetPalette,
        Vector3f[] rotationPalette,
        Vector4f[] uvCoordsPalette,
        BufferedImage textureAtlas,
        Vector3f min,
        Vector3f max,
        String name) {
      this.positions = positions;
      this.colorIndices = colorIndices;
      this.colorPalette = colorPalette;
      this.sizePalette = sizePalette;
      this.offsetPalette = offsetPalette;
      this.rotationPalette = rotationPalette;
      this.uvCoordsPalette = uvCoordsPalette;
      this.textureAtlas = textureAtlas;
      this.min = min;
      this.max = max;
      this.name = name;
    }
  }
}
