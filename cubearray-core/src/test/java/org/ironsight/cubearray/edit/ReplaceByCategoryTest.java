package org.ironsight.cubearray.edit;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import pitheguy.schemconvert.converter.Schematic;

/**
 * Tests for {@link BlockReplacer#replaceByCategory}.
 *
 * <p>Uses {@code jerusalem_tower_pretty_I.schem} which contains: - minecraft:red_sandstone (bare
 * block) - minecraft:red_sandstone_stairs[...] (multiple blockstate variants) -
 * minecraft:red_sandstone_wall[...] (multiple blockstate variants) -
 * minecraft:smooth_sandstone_slab[...] (different category: smooth_sandstone) -
 * minecraft:acacia_planks (different category: acacia) - minecraft:ladder[...] (no category — maps
 * to itself) - minecraft:cut_sandstone (category: cut_sandstone — no stairs/slab equivalent in
 * red_sandstone) - minecraft:air
 */
public class ReplaceByCategoryTest {

  private static final File TOWER =
      new File("src/test/resources/schematics/Ir0nsight/jerusalem_tower_pretty_I.schem");

  // -------------------------------------------------------------------------
  // Core behaviour
  // -------------------------------------------------------------------------

  /** All red_sandstone variants (bare, stairs, wall) map to their sandstone equivalents. */
  @Test
  public void redSandstone_to_sandstone_replacesAllVariants() throws Exception {
    Schematic source = BlockReplacer.load(TOWER);
    Schematic result =
        BlockReplacer.replaceByCategory(source, Map.of("red_sandstone", "sandstone"));

    Set<String> palette = BlockReplacer.getPalette(result);

    // Bare block replaced
    assertFalse(
        "red_sandstone bare should be gone",
        palette.stream().anyMatch(b -> b.equals("minecraft:red_sandstone")));
    assertTrue(
        "sandstone bare should appear",
        palette.stream().anyMatch(b -> b.equals("minecraft:sandstone")));

    // Stairs replaced (blockstate variants preserved)
    assertFalse(
        "red_sandstone_stairs should be gone",
        palette.stream().anyMatch(b -> b.contains("red_sandstone_stairs")));
    assertTrue(
        "sandstone_stairs should appear",
        palette.stream().anyMatch(b -> b.contains("sandstone_stairs")));

    // Wall replaced
    assertFalse(
        "red_sandstone_wall should be gone",
        palette.stream().anyMatch(b -> b.contains("red_sandstone_wall")));
    assertTrue(
        "sandstone_wall should appear",
        palette.stream().anyMatch(b -> b.contains("sandstone_wall")));
  }

  /** Blockstate properties on stairs are preserved after category replacement. */
  @Test
  public void blockstatePropertiesArePreserved() throws Exception {
    Schematic source = BlockReplacer.load(TOWER);
    Schematic result =
        BlockReplacer.replaceByCategory(source, Map.of("red_sandstone", "sandstone"));

    Set<String> palette = BlockReplacer.getPalette(result);

    // Every original red_sandstone_stairs variant should appear as sandstone_stairs
    // with the same blockstate string
    boolean hasStairsWithState =
        palette.stream()
            .anyMatch(b -> b.startsWith("minecraft:sandstone_stairs[") && b.contains("facing="));
    assertTrue("sandstone_stairs should carry blockstate properties", hasStairsWithState);
  }

  /** Dimensions are unchanged after category replacement. */
  @Test
  public void dimensionsUnchanged() throws Exception {
    Schematic source = BlockReplacer.load(TOWER);
    Schematic result =
        BlockReplacer.replaceByCategory(source, Map.of("red_sandstone", "sandstone"));
    assertArrayEquals(source.getSize(), result.getSize());
  }

  // -------------------------------------------------------------------------
  // Edge cases — no equivalent in target category
  // -------------------------------------------------------------------------

  /**
   * red_sandstone → oak: oak has no wall, so red_sandstone_wall should remain unchanged. oak_stairs
   * and oak_slab exist, so those should be replaced.
   */
  @Test
  public void noEquivalentInTarget_blockKeptUnchanged() throws Exception {
    Schematic source = BlockReplacer.load(TOWER);
    Schematic result = BlockReplacer.replaceByCategory(source, Map.of("red_sandstone", "oak_wood"));

    Set<String> palette = BlockReplacer.getPalette(result);

    // oak_wood has no wall → red_sandstone_wall must stay
    assertTrue(
        "red_sandstone_wall should remain (oak_wood has no wall)",
        palette.stream().anyMatch(b -> b.contains("red_sandstone_wall")));

    // oak_wood has stairs → red_sandstone_stairs should be replaced
    assertFalse(
        "red_sandstone_stairs should be replaced",
        palette.stream().anyMatch(b -> b.contains("red_sandstone_stairs")));
    assertTrue(
        "oak_stairs should appear", palette.stream().anyMatch(b -> b.contains("oak_stairs")));
  }

  // -------------------------------------------------------------------------
  // Edge cases — blocks outside the mapped category are untouched
  // -------------------------------------------------------------------------

  /** Blocks from unrelated categories are never modified. */
  @Test
  public void blocksOutsideMappedCategory_areUntouched() throws Exception {
    Schematic source = BlockReplacer.load(TOWER);
    Schematic result =
        BlockReplacer.replaceByCategory(source, Map.of("red_sandstone", "sandstone"));

    Set<String> palette = BlockReplacer.getPalette(result);

    // acacia_planks is in category "acacia", not "red_sandstone" → untouched
    assertTrue(
        "acacia_planks should be untouched",
        palette.stream().anyMatch(b -> b.equals("minecraft:acacia_planks")));

    // ladder has no category (maps to itself) → untouched
    assertTrue(
        "ladder should be untouched",
        palette.stream().anyMatch(b -> b.contains("minecraft:ladder")));
  }

  // -------------------------------------------------------------------------
  // Edge cases — empty / no-op mappings
  // -------------------------------------------------------------------------

  /** An empty category mapping produces a schematic identical to the source. */
  @Test
  public void emptyCategoryMapping_producesIdenticalPalette() throws Exception {
    Schematic source = BlockReplacer.load(TOWER);
    Schematic result = BlockReplacer.replaceByCategory(source, Map.of());

    assertEquals(BlockReplacer.getPalette(source), BlockReplacer.getPalette(result));
  }

  /** Mapping a category to itself is a no-op. */
  @Test
  public void mapCategoryToItself_producesIdenticalPalette() throws Exception {
    Schematic source = BlockReplacer.load(TOWER);
    Schematic result =
        BlockReplacer.replaceByCategory(source, Map.of("red_sandstone", "red_sandstone"));

    assertEquals(BlockReplacer.getPalette(source), BlockReplacer.getPalette(result));
  }

  /** Mapping a category that doesn't appear in the schematic is a no-op. */
  @Test
  public void mappingAbsentCategory_producesIdenticalPalette() throws Exception {
    Schematic source = BlockReplacer.load(TOWER);
    // "stone_brick" does not appear in the tower schematic
    Schematic result = BlockReplacer.replaceByCategory(source, Map.of("stone_brick", "oak"));

    assertEquals(BlockReplacer.getPalette(source), BlockReplacer.getPalette(result));
  }

  // -------------------------------------------------------------------------
  // bricks → cobblestone (programmatic schematic)
  // -------------------------------------------------------------------------

  /**
   * Replaces bricks, brick_slab, brick_stairs with their cobblestone equivalents using a
   * programmatically built schematic.
   */
  @Test
  public void bricks_to_cobblestone() throws IOException {
    var builder = new Schematic.Builder(new File("dummy.schem"), 3955, 3, 1, 1);
    builder.setBlockAt(0, 0, 0, "minecraft:bricks");
    builder.setBlockAt(1, 0, 0, "minecraft:brick_slab");
    builder.setBlockAt(2, 0, 0, "minecraft:brick_stairs");
    Schematic source = builder.build();

    Schematic result = BlockReplacer.replaceByCategory(source, Map.of("bricks", "cobblestone"));

    Set<String> palette = BlockReplacer.getPalette(result);

    assertTrue("bricks → cobblestone", palette.contains("minecraft:cobblestone"));
    assertTrue("brick_slab → cobblestone_slab", palette.contains("minecraft:cobblestone_slab"));
    assertTrue(
        "brick_stairs → cobblestone_stairs", palette.contains("minecraft:cobblestone_stairs"));
  }
}
