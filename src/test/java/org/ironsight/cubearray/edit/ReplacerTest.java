package org.ironsight.cubearray.edit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import org.junit.BeforeClass;
import org.junit.Test;

public class ReplacerTest {

  private static Replacer replacer;

  @BeforeClass
  public static void setUp() throws IOException {
    replacer = new Replacer(BlockListUtil.loadCategories());
  }

  // Wood families — same-kinded blocks map across

  @Test
  public void oakPlanks_toSpruceWood() throws NotFoundExc {
    assertEquals("spruce_planks", replacer.replaceBlockByCategory("oak_planks", "spruce_wood"));
  }

  @Test
  public void oakStairs_toSpruceWood() throws NotFoundExc {
    assertEquals("spruce_stairs", replacer.replaceBlockByCategory("oak_stairs", "spruce_wood"));
  }

  @Test
  public void oakHangingSign_toSpruceWood() throws NotFoundExc {
    assertEquals(
        "spruce_hanging_sign", replacer.replaceBlockByCategory("oak_hanging_sign", "spruce_wood"));
  }

  @Test
  public void oakLog_toOakTree() throws NotFoundExc {
    // Same category — should map to itself
    assertEquals("oak_log", replacer.replaceBlockByCategory("oak_log", "oak_tree"));
  }

  @Test
  public void oakLog_toSpruceTree() throws NotFoundExc {
    assertEquals("spruce_log", replacer.replaceBlockByCategory("oak_log", "spruce_tree"));
  }

  @Test
  public void strippedOakLog_toSpruceTree() throws NotFoundExc {
    assertEquals(
        "stripped_spruce_log", replacer.replaceBlockByCategory("stripped_oak_log", "spruce_tree"));
  }

  @Test
  public void oakDoor_toSpruceDoor() throws NotFoundExc {
    assertEquals("spruce_door", replacer.replaceBlockByCategory("oak_door", "spruce_door"));
  }

  @Test
  public void oakTrapdoor_toSpruceDoor() throws NotFoundExc {
    assertEquals("spruce_trapdoor", replacer.replaceBlockByCategory("oak_trapdoor", "spruce_door"));
  }

  // Stone families

  @Test
  public void cobblestone_toStone() throws NotFoundExc {
    assertEquals("stone", replacer.replaceBlockByCategory("cobblestone", "stone"));
  }

  @Test
  public void cobblestoneSlab_toStone() throws NotFoundExc {
    assertEquals("stone_slab", replacer.replaceBlockByCategory("cobblestone_slab", "stone"));
  }

  @Test
  public void cobblestoneStairs_toStone() throws NotFoundExc {
    assertEquals("stone_stairs", replacer.replaceBlockByCategory("cobblestone_stairs", "stone"));
  }

  @Test
  public void infestedCobblestone_toStone() throws NotFoundExc {
    assertEquals(
        "infested_stone", replacer.replaceBlockByCategory("infested_cobblestone", "stone"));
  }

  @Test
  public void stoneBrickSlab_toStone() throws NotFoundExc {
    assertEquals("stone_slab", replacer.replaceBlockByCategory("stone_brick_slab", "stone"));
  }

  @Test
  public void stoneBrickStairs_toStone() throws NotFoundExc {
    assertEquals("stone_stairs", replacer.replaceBlockByCategory("stone_brick_stairs", "stone"));
  }

  @Test
  public void chiseledSandstone_toCutSandstone() throws NotFoundExc {
    assertEquals(
        "cut_sandstone", replacer.replaceBlockByCategory("chiseled_sandstone", "cut_sandstone"));
  }

  @Test
  public void smoothSandstoneSlab_toCutSandstone() throws NotFoundExc {
    assertEquals(
        "cut_sandstone_slab",
        replacer.replaceBlockByCategory("smooth_sandstone_slab", "cut_sandstone"));
  }

  // Copper variants

  @Test
  public void copperBlock_toExposedCopper() throws NotFoundExc {
    assertEquals(
        "exposed_copper", replacer.replaceBlockByCategory("copper_block", "exposed_copper"));
  }

  @Test
  public void copperGrate_toExposedCopper() throws NotFoundExc {
    assertEquals(
        "exposed_copper_grate", replacer.replaceBlockByCategory("copper_grate", "exposed_copper"));
  }

  // Blocks with no equivalent should throw

  @Test
  public void blockNotInAnyCategory_throws() {
    assertThrows(NotFoundExc.class, () -> replacer.replaceBlockByCategory("air", "stone"));
  }

  @Test
  public void unknownCategory_throws() {
    assertThrows(
        NotFoundExc.class, () -> replacer.replaceBlockByCategory("oak_planks", "nonexistent"));
  }

  @Test
  public void noEquivalentBlockInTarget_throws() {
    // cobblestone_wall has no equivalent in stone_button category
    assertThrows(
        NotFoundExc.class,
        () -> replacer.replaceBlockByCategory("cobblestone_wall", "smooth_stone"));
  }
}
