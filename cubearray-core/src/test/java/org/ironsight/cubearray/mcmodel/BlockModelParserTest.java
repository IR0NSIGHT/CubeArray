package org.ironsight.cubearray.mcmodel;
import org.ironsight.cubearray.platform.ResourceUtils;

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
  private static Path vanillaPackRoot;

  @BeforeClass
  public static void extractResourcePack() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
    ResourceUtils.copyResourcesToFile(ResourceUtils.VANILLA_ASSETS_RESOURCES);
    // Pixel Perfection Legacy's current-version content ships at the zip's top-level "assets/",
    // which lands directly under the extracted "textures/" folder (unlike Faithful, which wraps
    // itself in its own "Faithful_32x_1_21_7/" subfolder - see ResourceUtils.TEXTURE_PACK_ROOT).
    packRoot = ResourceUtils.getInstallPath().resolve(ResourceUtils.TEXTURE_RESOURCES);
    vanillaPackRoot = ResourceUtils.getInstallPath().resolve(ResourceUtils.VANILLA_ASSETS_RESOURCES);
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
  public void parsesTextureObjectFormat() throws Exception {
    // gray_stained_glass_pane_noside uses the new MC 26.1 texture object format where "pane" is
    // an object with "sprite" and "force_translucent" fields instead of a plain string.
    BlockModel model =
        BlockModelParser.parseModel(vanillaPackRoot, "block/gray_stained_glass_pane_noside");

    assertNotNull(model);
    // The model inherits from template_glass_pane_noside which defines a "particle" ref "#pane"
    // and one element with a north face using texture "#pane".  Our parser extracts the sprite
    // from the {"force_translucent": true, "sprite": "..."} object so it resolves correctly.
    assertFalse("expected at least one sub-block", model.subBlocks().isEmpty());
    SubBlock block = model.subBlocks().get(0);
    FaceTexture northFace = block.faces().get(Face.NORTH);
    assertNotNull("expected north face", northFace);
    assertEquals(
        "minecraft:block/gray_stained_glass",
        northFace.texture());
  }

  @Test
  public void parseAllFindsManyModels() throws Exception {
    Map<String, BlockModel> models = BlockModelParser.parseAll(packRoot);

    assertTrue("expected many block models, found: " + models.size(), models.size() > 100);
    assertTrue(models.containsKey("minecraft:block/acacia_fence_post"));
    assertTrue(models.containsKey("minecraft:block/cobbled_deepslate"));
  }
}
