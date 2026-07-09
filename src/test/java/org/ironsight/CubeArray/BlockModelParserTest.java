package org.ironsight.CubeArray;

import static org.junit.Assert.*;

import java.nio.file.Path;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Verifies {@link BlockModelParser} against the real block model JSON shipped in the bundled
 * "Pixel Perfection Legacy" resource pack (src/main/resources/textures) - both a
 * self-contained model (acacia_fence_post, no "parent") and a model that only resolves through a
 * multi-level "parent" chain with "#key" texture indirection (cobbled_deepslate -> cube_all ->
 * cube).
 */
public class BlockModelParserTest {

  private static Path packRoot;

  @BeforeClass
  public static void extractResourcePack() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
    // Pixel Perfection Legacy's current-version content ships at the zip's top-level "assets/",
    // which lands directly under the extracted "textures/" folder (unlike Faithful, which wraps
    // itself in its own "Faithful_32x_1_21_7/" subfolder - see ResourceUtils.TEXTURE_PACK_ROOT).
    packRoot = ResourceUtils.getInstallPath().resolve(ResourceUtils.TEXTURE_RESOURCES);
  }

  @Test
  public void parsesSelfContainedFenceModel() throws Exception {
    BlockModel model = BlockModelParser.parseModel(packRoot, "block/acacia_fence_post");

    assertEquals(1, model.subBlocks().size());
    SubBlock post = model.subBlocks().get(0);
    assertEquals(new Vector3f(6, 0, 6), post.from());
    assertEquals(new Vector3f(10, 16, 10), post.to());

    assertEquals("block/acacia_fence_side", post.faces().get(Face.NORTH).texture());
    assertEquals("block/acacia_fence_side", post.faces().get(Face.EAST).texture());
    assertEquals("block/acacia_fence_side", post.faces().get(Face.SOUTH).texture());
    assertEquals("block/acacia_fence_side", post.faces().get(Face.WEST).texture());

    assertEquals("block/acacia_fence_top", post.faces().get(Face.UP).texture());
    assertEquals(Face.UP, post.faces().get(Face.UP).cullface());
    assertEquals("block/acacia_fence_top", post.faces().get(Face.DOWN).texture());
    assertEquals(Face.DOWN, post.faces().get(Face.DOWN).cullface());
  }

  @Test
  public void resolvesTextureThroughParentChainAndHashIndirection() throws Exception {
    // cobbled_deepslate.json{parent: cube_all, textures: {all: ".../cobbled_deepslate"}}
    //   -> cube_all.json{parent: cube, textures: {north: "#all", ...}}   (defines no elements)
    //     -> cube.json{parent: block/block (missing file), elements: [full 0-16 cube]}
    BlockModel model = BlockModelParser.parseModel(packRoot, "block/cobbled_deepslate");

    assertEquals(1, model.subBlocks().size());
    SubBlock cube = model.subBlocks().get(0);
    assertEquals(new Vector3f(0, 0, 0), cube.from());
    assertEquals(new Vector3f(16, 16, 16), cube.to());

    for (Face face : Face.values()) {
      FaceTexture faceTexture = cube.faces().get(face);
      assertNotNull("missing face: " + face, faceTexture);
      assertEquals(
          "face " + face + " should resolve through cube_all's \"#all\" indirection",
          "minecraft:block/cobbled_deepslate",
          faceTexture.texture());
      assertEquals(face, faceTexture.cullface());
    }
  }

  @Test
  public void parseAllFindsManyModels() throws Exception {
    Map<String, BlockModel> models = BlockModelParser.parseAll(packRoot);

    assertTrue("expected many block models, found: " + models.size(), models.size() > 100);
    assertTrue(models.containsKey("minecraft:block/acacia_fence_post"));
    assertTrue(models.containsKey("minecraft:block/cobbled_deepslate"));
  }
}
