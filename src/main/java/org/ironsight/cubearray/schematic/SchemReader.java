package org.ironsight.cubearray.schematic;

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
import org.ironsight.cubearray.mcmodel.BlockModel;
import org.ironsight.cubearray.mcmodel.BlockModelParser;
import org.ironsight.cubearray.mcmodel.BlockStateParser;
import org.ironsight.cubearray.mcmodel.Face;
import org.ironsight.cubearray.mcmodel.FaceTexture;
import org.ironsight.cubearray.mcmodel.SubBlock;
import org.ironsight.cubearray.platform.AppLogger;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.render.CubeSetup;
import org.joml.Matrix3f;
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

  /**
   * One renderable cuboid: a model element positioned by a blockstate placement's rotation, plus
   * the per-face textures ({@link SubBlock#faces()}) so each cube face can sample its own sprite.
   */
  private record Piece(
      Vector3f size, Vector3f offset, Vector3f rotation, Map<Face, FaceTexture> faces) {}

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
      // blockstate rotation about the block centre (the renderer applies it as Ry * Rx)
      Vector3f blockRotation =
          new Vector3f(
              (float) Math.toRadians(placement.x()), (float) Math.toRadians(placement.y()), 0f);
      for (SubBlock sub : model.subBlocks()) {
        // stretch the element's size by its rescale factor (e.g. block/cross's corner-to-corner
        // quads for grass)
        Vector3f size = sub.size().mul(sub.rescale());
        if (sub.rotation().z != 0f) {
          // a Z-axis element tilt (e.g. a wall torch) can't be written as the renderer's Ry * Rx;
          // re-express it for that rotation without touching the shader (see tiltedPiece)
          pieces.add(tiltedPiece(blockRotation, sub, size));
        } else {
          // add the element's own X/Y rotation to the blockstate rotation (both share the block
          // centre and the renderer applies Ry * Rx), reproducing e.g. block/cross's 45deg X
          Vector3f rotation = new Vector3f(blockRotation).add(sub.rotation());
          pieces.add(new Piece(size, sub.offset(), rotation, sub.faces()));
        }
      }
    }
    if (pieces.isEmpty()) {
      // no resolvable geometry (missing/empty model) -> render as a default full cube (flat colour)
      pieces.add(new Piece(new Vector3f(1, 1, 1), new Vector3f(), new Vector3f(), Map.of()));
    }
    return pieces;
  }

  /**
   * Builds a render piece for an element whose rotation includes a Z-axis tilt (e.g. a wall torch
   * leaning off its wall), which the renderer's {@code Ry * Rx} rotation cannot express directly -
   * without any shader change. It computes the true target orientation {@code Rblock * Relem}, then
   * re-expresses it as the {@code (rx, ry)} whose {@code Ry * Rx} reproduces the same long axis (the
   * leftover twist about that axis is invisible on the near-square, uniformly side-textured torch),
   * and pre-rotates the offset so the cuboid still lands where the model's rotation origin puts it.
   */
  private static Piece tiltedPiece(Vector3f blockRotation, SubBlock sub, Vector3f size) {
    // orientation the renderer applies for this blockstate, in the shader's own (angle-negating)
    // convention, composed with the element's own rotation of the geometry
    Matrix3f rblock = shaderRotation(blockRotation.x, blockRotation.y);
    Vector3f er = sub.rotation();
    Matrix3f relem = new Matrix3f().rotateXYZ(er.x, er.y, er.z);
    Matrix3f target = new Matrix3f(rblock).mul(relem); // full orientation the renderer must match

    // stored (ax, ay) such that the shader's Ry*Rx sends +Y onto the target's up axis (its Ry/Rx
    // negate the angle, so solve against shaderRotation, not the standard matrices)
    Vector3f up = target.transform(new Vector3f(0, 1, 0));
    float ax = (float) Math.acos(Math.max(-1f, Math.min(1f, up.y)));
    float ay =
        (Math.abs(up.x) < 1e-4f && Math.abs(up.z) < 1e-4f) ? 0f : (float) Math.atan2(up.x, -up.z);
    Matrix3f applied = shaderRotation(ax, ay);

    // where the cuboid centre should end up: rotate the element about its own origin, then apply
    // the blockstate rotation about the block centre
    Vector3f center = new Vector3f(sub.offset()).sub(sub.originOffset());
    relem.transform(center).add(sub.originOffset());
    rblock.transform(center);
    // undo the rotation the renderer will apply to the offset, so it lands the centre where we want
    Vector3f offset = new Matrix3f(applied).transpose().transform(new Vector3f(center));

    return new Piece(size, offset, new Vector3f(ax, ay, 0f), sub.faces());
  }

  /**
   * The exact orientation the vertex shader produces for stored palette angles {@code (ax, ay)}. The
   * shader's rotationX/rotationY (see {@link VertexShaderSource}) are built so they rotate by the
   * negative of their argument, i.e. {@code Ry(-ay) * Rx(-ax)} in standard terms; matching that here
   * lets {@link #tiltedPiece} invert it exactly.
   */
  private static Matrix3f shaderRotation(float ax, float ay) {
    return new Matrix3f().rotateY(-ay).rotateX(-ax);
  }

  // model face "uv" is in 0..16 texel space regardless of the pack's texture resolution
  private static final float MODEL_UV_SPAN = 16f;

  /**
   * Crops a sprite's full atlas cell {@code (u1,v1,u2,v2)} to the sub-rect a model face's {@code
   * "uv"} selects. Model uv is 0..16 with a top-left origin, matching the atlas' UV space, so this
   * is a straight linear remap into the cell. Small elements (torches, lanterns, fence arms) draw
   * only a strip of their sprite and rely on this; a null uv or an untextured cell uses the whole
   * cell.
   */
  private static Vector4f cropToFaceUv(Vector4f cell, Vector4f uv) {
    if (uv == null || (cell.x == 0 && cell.y == 0 && cell.z == 0 && cell.w == 0)) return cell;
    float cw = cell.z - cell.x;
    float ch = cell.w - cell.y;
    return new Vector4f(
        cell.x + (uv.x / MODEL_UV_SPAN) * cw,
        cell.y + (uv.y / MODEL_UV_SPAN) * ch,
        cell.x + (uv.z / MODEL_UV_SPAN) * cw,
        cell.y + (uv.w / MODEL_UV_SPAN) * ch);
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
    float maxDim = 0;
    for (WPObject object : schematics) {

      if (index % 20 == 0) {
        gridOffset.y += maxDepth;
        gridOffset.x = 0;
        maxDepth = 0;
      }
      var dimensions = object.getDimensions();
      maxDim = Math.max(maxDim, Math.max(dimensions.x, Math.max(dimensions.y, dimensions.z)));
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

    Material markerX = WOOL_WHITE.withProperty("cubearray_axis", "x");
    Material markerY = WOOL_WHITE.withProperty("cubearray_axis", "y");
    Material markerZ = WOOL_WHITE.withProperty("cubearray_axis", "z");
    for (Material m : new Material[]{markerX, markerY, markerZ}) {
      maxColorIdx = addInstance(m, new Vector3f(0, 0, 0), positions, blockTypeIndicesList, mat_to_palette_idx, maxColorIdx);
    }
    int xIdx = mat_to_palette_idx.get(markerX);
    int yIdx = mat_to_palette_idx.get(markerY);
    int zIdx = mat_to_palette_idx.get(markerZ);

    Vector3f[] colorPalette = new Vector3f[mat_to_palette_idx.size()];
    Arrays.fill(colorPalette, new Vector3f(1, 1, 1));

    Vector3f[] sizePalette = new Vector3f[mat_to_palette_idx.size()];
    Arrays.fill(sizePalette, new Vector3f(1, 1, 1));

    Vector3f[] offsetPalette = new Vector3f[mat_to_palette_idx.size()];
    Arrays.fill(offsetPalette, new Vector3f(0, 0, 0));

    Vector3f[] rotationPalette = new Vector3f[mat_to_palette_idx.size()];
    Arrays.fill(rotationPalette, new Vector3f(0, 0, 0));

    // the render piece each palette entry resolves to; reused below to build the per-face uv palette
    Piece[] pieceByIdx = new Piece[mat_to_palette_idx.size()];
    // per material, the sprite names its faces reference - the atlas allocates one cell per sprite
    Map<Material, Set<String>> materialSprites = new HashMap<>();

    for (var entry : mat_to_palette_idx.entrySet()) {
      Material keyed = entry.getKey();
      int matIdx = entry.getValue();

      // color from the material; the synthetic piece tag doesn't affect its colour
      Color color = new Color(keyed.colour);
      colorPalette[matIdx] =
          new Vector3f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f);

      // size/offset/rotation/faces come entirely from the material's selected render piece
      // (blockstate -> model placement -> element); see computePieces
      String pieceTag = keyed.getProperty(PIECE_PROPERTY);
      int pieceIdx = pieceTag != null ? Integer.parseInt(pieceTag) : 0;
      List<Piece> pieces = piecesFor(keyed);
      Piece piece = pieces.get(Math.min(pieceIdx, pieces.size() - 1));
      pieceByIdx[matIdx] = piece;
      sizePalette[matIdx] = piece.size();
      offsetPalette[matIdx] = piece.offset();
      rotationPalette[matIdx] = piece.rotation();

      Set<String> sprites = new HashSet<>();
      for (FaceTexture face : piece.faces().values()) {
        if (face.texture() != null) sprites.add(face.texture());
      }
      materialSprites.put(keyed, sprites);
    }

    float rodLen = maxDim * 2;
    colorPalette[xIdx] = new Vector3f(1, 0, 0);
    sizePalette[xIdx] = new Vector3f(rodLen, 0.1f, 0.1f);
    offsetPalette[xIdx] = new Vector3f(0, 0, 0);
    rotationPalette[xIdx] = new Vector3f(0, 0, 0);

    colorPalette[yIdx] = new Vector3f(0, 1, 0);
    sizePalette[yIdx] = new Vector3f(0.1f, rodLen, 0.1f);
    offsetPalette[yIdx] = new Vector3f(0, 0, 0);
    rotationPalette[yIdx] = new Vector3f(0, 0, 0);

    colorPalette[zIdx] = new Vector3f(0, 0, 1);
    sizePalette[zIdx] = new Vector3f(0.1f, 0.1f, rodLen);
    offsetPalette[zIdx] = new Vector3f(0, 0, 0);
    rotationPalette[zIdx] = new Vector3f(0, 0, 0);

    SpriteSheet spriteSheet =
        new SpriteSheet(
            ResourceUtils.getInstallPath().resolve(ResourceUtils.TEXTURE_PACK_ROOT).toFile(),
            materialSprites);

    // per-face uv palette, laid out for the shader's 2D lookup: row per face (Face ordinal), column
    // per block type -> uvCoordsPalette[face.ordinal() * numTypes + matIdx]. A face with no texture
    // stays (0,0,0,0), which the fragment shader renders as flat block colour.
    int numTypes = mat_to_palette_idx.size();
    Vector4f[] uvCoordsPalette = new Vector4f[numTypes * Face.values().length];
    Arrays.fill(uvCoordsPalette, new Vector4f(0, 0, 0, 0));
    for (var entry : mat_to_palette_idx.entrySet()) {
      Material keyed = entry.getKey();
      int matIdx = entry.getValue();
      for (Map.Entry<Face, FaceTexture> faceEntry : pieceByIdx[matIdx].faces().entrySet()) {
        FaceTexture face = faceEntry.getValue();
        if (face.texture() == null) continue;
        Vector4f cell = spriteSheet.uvFor(keyed, face.texture());
        uvCoordsPalette[faceEntry.getKey().ordinal() * numTypes + matIdx] =
            cropToFaceUv(cell, face.uv());
      }
    }

    for (int f = 0; f < Face.values().length; f++) {
      uvCoordsPalette[f * numTypes + xIdx] = new Vector4f(0, 0, 0, 0);
      uvCoordsPalette[f * numTypes + yIdx] = new Vector4f(0, 0, 0, 0);
      uvCoordsPalette[f * numTypes + zIdx] = new Vector4f(0, 0, 0, 0);
    }

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
        uvCoordsPalette,
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
}
