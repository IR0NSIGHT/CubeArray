package org.ironsight.schemEdit;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BlockListUtilTest {

  /** 1. No [...] at all — returned unchanged. */
  @Test
  public void noBlockState_returnedAsIs() {
    assertEquals("acacia_button", BlockListUtil.toLegalBlockState("acacia_button"));
  }

  /** 2. Unknown block ID — returned unchanged, including its [...] part. */
  @Test
  public void unknownBlockId_returnedAsIs() {
    assertEquals(
        "modded:fake_block[foo=bar]",
        BlockListUtil.toLegalBlockState("modded:fake_block[foo=bar]"));
  }

  /** 3. Property key is not defined for the block — pair is dropped. */
  @Test
  public void unknownPropertyKey_isDropped() {
    // "variant" is not a property of acacia_button; "face" is valid
    assertEquals(
        "acacia_button[face=floor]",
        BlockListUtil.toLegalBlockState("acacia_button[face=floor,variant=top]"));
  }

  /** 4. Property key is valid but the value is not among possible values — pair is dropped. */
  @Test
  public void invalidPropertyValue_isDropped() {
    // "face" is a valid property but "diagonal" is not a valid value for it
    assertEquals(
        "acacia_button[powered=false]",
        BlockListUtil.toLegalBlockState("acacia_button[face=diagonal,powered=false]"));
  }

  /** 5. All properties are fully valid — string is returned unchanged. */
  @Test
  public void allPropertiesValid_returnedUnchanged() {
    assertEquals(
        "acacia_button[face=floor,facing=north,powered=false]",
        BlockListUtil.toLegalBlockState("acacia_button[face=floor,facing=north,powered=false]"));
  }

  /**
   * 6. Double [...][...] from block-replacement concatenation bug — second group stripped,
   * namespace prefix handled, valid properties kept.
   */
  @Test
  public void doubleBracket_normalisedToSingleState() {
    assertEquals(
        "minecraft:stone_brick_slab[type=bottom,waterlogged=false]",
        BlockListUtil.toLegalBlockState(
            "minecraft:stone_brick_slab[type=bottom,waterlogged=false][type=bottom,waterlogged=false]"));
  }
}
