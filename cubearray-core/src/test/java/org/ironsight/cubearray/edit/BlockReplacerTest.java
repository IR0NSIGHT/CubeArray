package org.ironsight.cubearray.edit;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import pitheguy.schemconvert.converter.Schematic;

public class BlockReplacerTest {

  private static final File JERUSALEM_WALLS =
      new File("src/test/resources/schematics/Ir0nsight/jerusalem_walls_t.schem");

  @Test
  public void replaceRedSandstoneWithCobblestone() throws Exception {
    Schematic source = BlockReplacer.load(JERUSALEM_WALLS);

    assertTrue(
        "source must contain red_sandstone",
        BlockReplacer.getPalette(source).contains("minecraft:red_sandstone"));

    Schematic result =
        BlockReplacer.replace(source, Map.of("minecraft:red_sandstone", "minecraft:cobblestone"));

    Set<String> palette = BlockReplacer.getPalette(result);

    assertFalse("red_sandstone should be gone", palette.contains("minecraft:red_sandstone"));
    assertTrue("cobblestone should be present", palette.contains("minecraft:cobblestone"));
  }

  @Test
  public void getPaletteReturnsAllBlockTypes() throws Exception {
    Schematic source = BlockReplacer.load(JERUSALEM_WALLS);
    Set<String> palette = BlockReplacer.getPalette(source);

    // jerusalem_walls_t.schem contains exactly these two block types
    assertEquals(Set.of("minecraft:air", "minecraft:red_sandstone"), palette);
  }

  @Test
  public void getPaletteIsUnmodifiable() throws Exception {
    Schematic source = BlockReplacer.load(JERUSALEM_WALLS);
    Set<String> palette = BlockReplacer.getPalette(source);

    assertThrows(UnsupportedOperationException.class, () -> palette.add("minecraft:stone"));
  }

  @Test
  public void replacedSchematicHasSameDimensions() throws Exception {
    Schematic source = BlockReplacer.load(JERUSALEM_WALLS);
    Schematic result =
        BlockReplacer.replace(source, Map.of("minecraft:red_sandstone", "minecraft:cobblestone"));

    assertArrayEquals(source.getSize(), result.getSize());
  }

  @Test
  public void writeAndReloadProducesCorrectPalette() throws Exception {
    Schematic source = BlockReplacer.load(JERUSALEM_WALLS);
    Schematic result =
        BlockReplacer.replace(source, Map.of("minecraft:red_sandstone", "minecraft:cobblestone"));

    File tmp = File.createTempFile("jerusalem_walls_replaced", ".schem");
    tmp.deleteOnExit();
    BlockReplacer.write(result, tmp);

    Schematic reloaded = BlockReplacer.load(tmp);
    Set<String> palette = BlockReplacer.getPalette(reloaded);

    assertFalse(
        "red_sandstone should be gone after reload", palette.contains("minecraft:red_sandstone"));
    assertTrue("cobblestone should survive round-trip", palette.contains("minecraft:cobblestone"));
  }
}
