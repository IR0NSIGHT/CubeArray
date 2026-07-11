package org.ironsight.cubearray.schematic;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.ironsight.cubearray.edit.BlockReplacer;
import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.objects.WPObject;
import pitheguy.schemconvert.converter.Schematic;

/**
 * Round-trips a schematic through the exact I/O path the app uses: build with a SchemConvert {@link
 * Schematic.Builder}, write it with {@link BlockReplacer#write} (Sponge v3), then read it back with
 * {@link SchemReader#loadSchematics} (WorldPainter). A volume where width, length and height are all
 * different (and height &gt; 1) is used so a swapped axis or wrong multiplier anywhere in that chain
 * produces either an out-of-bounds crash or a block landing at the wrong coordinate.
 *
 * <p>Axis conventions in play: SchemConvert's {@code Builder.setBlockAt} is {@code (x=width,
 * y=height, z=length)}, whereas the loaded {@link WPObject} addresses cells as {@code (x=width,
 * y=length, z=height)} — so a block written at builder {@code (w, h, l)} reads back at WPObject
 * {@code (w, l, h)}.
 */
public class SchematicRoundTripTest {

  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  @Test
  public void roundTripsNonCubicVolume() throws Exception {
    final short width = 5;
    final short length = 9;
    final short height = 3;

    // SchemConvert Builder order is (xSize=width, ySize=height, zSize=length). dataVersion 1343 = MC
    // 1.12.2. Cells are addressed as setBlockAt(widthIdx, heightIdx, lengthIdx).
    Schematic.Builder schem = new Schematic.Builder(null, 1343, width, height, length);

    for (int x = 0; x < width; x++) {
      for (int l = 0; l < length; l++) {
        for (int h = 0; h < height; h++) {
          schem.setBlockAt(x, h, l, "minecraft:air");
        }
      }
    }

    // markers at each axis' extreme corner, so a swapped axis or wrong multiplier lands the
    // block at a different coordinate than expected (or throws ArrayIndexOutOfBounds).
    // setBlockAt args are (width, height, length).
    schem.setBlockAt(0, 0, 0, "minecraft:stone");
    schem.setBlockAt(width - 1, 0, 0, "minecraft:diamond_block");
    schem.setBlockAt(0, 0, length - 1, "minecraft:gold_block");
    schem.setBlockAt(0, height - 1, 0, "minecraft:emerald_block");
    schem.setBlockAt(width - 1, height - 1, length - 1, "minecraft:redstone_block");

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("roundtrip_noncubic.schem");
    BlockReplacer.write(schem.build(), schemPath.toFile());

    List<WPObject> loaded =
        SchemReader.loadSchematics(List.of(schemPath), f -> fail("load error: " + f.getName()));
    assertEquals(1, loaded.size());
    WPObject wp = loaded.get(0);

    // WPObject dimension convention: x=width, y=length, z=height (see SchemReader's main loop).
    assertEquals(width, wp.getDimensions().x);
    assertEquals(length, wp.getDimensions().y);
    assertEquals(height, wp.getDimensions().z);

    assertSimpleName("stone", wp.getMaterial(0, 0, 0));
    assertSimpleName("diamond_block", wp.getMaterial(width - 1, 0, 0));
    assertSimpleName("gold_block", wp.getMaterial(0, length - 1, 0));
    assertSimpleName("emerald_block", wp.getMaterial(0, 0, height - 1));
    assertSimpleName("redstone_block", wp.getMaterial(width - 1, length - 1, height - 1));

    // a handful of untouched cells should still be air
    assertSimpleName("air", wp.getMaterial(2, 4, 1));
    assertSimpleName("air", wp.getMaterial(width - 1, length - 1, 0));
    assertSimpleName("air", wp.getMaterial(0, 0, 1));
  }

  private static void assertSimpleName(String expected, Material actual) {
    assertNotNull("material was null", actual);
    assertEquals(expected, actual.simpleName);
  }
}
