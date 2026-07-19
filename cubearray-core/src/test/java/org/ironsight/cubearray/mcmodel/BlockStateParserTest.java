package org.ironsight.cubearray.mcmodel;
import org.ironsight.cubearray.platform.ResourceUtils;

import static org.junit.Assert.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.ironsight.cubearray.mcmodel.BlockStateParser.BlockState;
import org.ironsight.cubearray.mcmodel.BlockStateParser.ModelPlacement;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Verifies {@link BlockStateParser} selection against the real bundled vanilla blockstates:
 * stairs (variants, incl. the inner/outer-L shapes and upside-down x-rotation), fences (multipart
 * with per-side rotation), slabs (variants), and redstone wire (multipart with an OR condition).
 */
public class BlockStateParserTest {

  private static Map<String, BlockState> states;

  @BeforeClass
  public static void parse() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.VANILLA_ASSETS_RESOURCES);
    Path root = ResourceUtils.getInstallPath().resolve(ResourceUtils.VANILLA_ASSETS_RESOURCES);
    states = BlockStateParser.parseAll(root);
  }

  /** Builds a property lookup from "key=value,key=value" like a schematic block's state. */
  private static Function<String, String> props(String kv) {
    Map<String, String> map = new HashMap<>();
    if (!kv.isEmpty()) {
      for (String pair : kv.split(",")) {
        int eq = pair.indexOf('=');
        map.put(pair.substring(0, eq), pair.substring(eq + 1));
      }
    }
    return map::get;
  }

  private static ModelPlacement only(BlockState state, String propsKv) {
    List<ModelPlacement> placements = state.select(props(propsKv));
    assertEquals("expected exactly one variant placement for [" + propsKv + "]", 1, placements.size());
    return placements.get(0);
  }

  @Test
  public void stairsSelectsShapeModelAndRotation() {
    BlockState stairs = states.get("minecraft:oak_stairs");
    assertNotNull("oak_stairs blockstate missing", stairs);

    // straight -> base model, no rotation
    ModelPlacement straight = only(stairs, "facing=east,half=bottom,shape=straight");
    assertEquals("minecraft:block/oak_stairs", straight.model());
    assertEquals(0, straight.x());
    assertEquals(0, straight.y());

    // inner-L corner -> inner model, y-rotated
    ModelPlacement innerLeft = only(stairs, "facing=east,half=bottom,shape=inner_left");
    assertEquals("minecraft:block/oak_stairs_inner", innerLeft.model());
    assertEquals(0, innerLeft.x());
    assertEquals(270, innerLeft.y());

    // outer-L corner -> outer model
    ModelPlacement outerRight = only(stairs, "facing=east,half=bottom,shape=outer_right");
    assertEquals("minecraft:block/oak_stairs_outer", outerRight.model());

    // upside-down (half=top) -> x flipped 180
    ModelPlacement innerLeftTop = only(stairs, "facing=east,half=top,shape=inner_left");
    assertEquals("minecraft:block/oak_stairs_inner", innerLeftTop.model());
    assertEquals(180, innerLeftTop.x());
    assertEquals(0, innerLeftTop.y());

    ModelPlacement innerRightTop = only(stairs, "facing=east,half=top,shape=inner_right");
    assertEquals("minecraft:block/oak_stairs_inner", innerRightTop.model());
    assertEquals(180, innerRightTop.x());
    assertEquals(90, innerRightTop.y());

    // every facing/half/shape combination must resolve to exactly one placement
    for (String facing : new String[] {"north", "east", "south", "west"}) {
      for (String half : new String[] {"bottom", "top"}) {
        for (String shape :
            new String[] {"straight", "inner_left", "inner_right", "outer_left", "outer_right"}) {
          String state = "facing=" + facing + ",half=" + half + ",shape=" + shape;
          assertEquals(
              "no unique variant for [" + state + "]", 1, stairs.select(props(state)).size());
        }
      }
    }
  }

  @Test
  public void fenceSelectsPostPlusConnectedSides() {
    BlockState fence = states.get("minecraft:oak_fence");
    assertNotNull("oak_fence blockstate missing", fence);

    // post always present; a side (rotated per direction) per connected side
    List<ModelPlacement> ne =
        fence.select(props("north=true,east=true,south=false,west=false"));
    assertTrue(
        "post should always apply",
        ne.stream().anyMatch(p -> p.model().equals("minecraft:block/oak_fence_post")));
    assertTrue(
        "north side at y=0",
        ne.stream()
            .anyMatch(p -> p.model().equals("minecraft:block/oak_fence_side") && p.y() == 0));
    assertTrue(
        "east side at y=90",
        ne.stream()
            .anyMatch(p -> p.model().equals("minecraft:block/oak_fence_side") && p.y() == 90));
    // 1 post + 2 connected sides
    assertEquals(3, ne.size());

    // no connections -> post only
    assertEquals(
        1, fence.select(props("north=false,east=false,south=false,west=false")).size());
  }

  @Test
  public void slabSelectsPerType() {
    BlockState slab = states.get("minecraft:oak_slab");
    assertNotNull(slab);
    assertEquals("minecraft:block/oak_slab", only(slab, "type=bottom").model());
    assertEquals("minecraft:block/oak_slab_top", only(slab, "type=top").model());
    // a double slab is a full block -> the plain planks model
    assertEquals("minecraft:block/oak_planks", only(slab, "type=double").model());
  }

  @Test
  public void redstoneWireMultipartOrCondition() {
    BlockState wire = states.get("minecraft:redstone_wire");
    assertNotNull(wire);
    // all sides "none" matches the dot's OR branch and none of the side parts
    List<ModelPlacement> dot =
        wire.select(props("north=none,east=none,south=none,west=none,power=0"));
    assertTrue("expected a redstone dot placement", !dot.isEmpty());
    assertTrue(
        "all placements should be the dot model when unconnected",
        dot.stream().allMatch(p -> p.model().contains("dot")));
  }
}
