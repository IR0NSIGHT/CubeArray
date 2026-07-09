package org.ironsight.CubeArray;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.objects.WPObject;

/**
 * Regression test for the setBlockAt flat-index formula. A schematic with width == length (or
 * height == 1) can't distinguish a correct x + y*width + z*width*length formula from a buggy one
 * that mixes up which axis is "length" and which is "height" - the terms happen to collide. This
 * uses a volume where width, length and height are all different (and height > 1) so a wrong
 * axis/multiplier produces either an out-of-bounds crash or a block landing at the wrong
 * coordinate.
 */
public class Sponge2SchematicRoundTripTest {

  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  @Test
  public void roundTripsNonCubicVolume() throws Exception {
    final short width = 5;
    final short length = 9;
    final short height = 3;

    Sponge2Schematic schem = new Sponge2Schematic(width, height, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < length; y++) {
        for (int z = 0; z < height; z++) {
          schem.setBlockAt(x, y, z, "minecraft:air");
        }
      }
    }

    // markers at each axis' extreme corner, so a swapped axis or wrong multiplier lands the
    // block at a different coordinate than expected (or throws ArrayIndexOutOfBounds).
    schem.setBlockAt(0, 0, 0, "minecraft:stone");
    schem.setBlockAt(width - 1, 0, 0, "minecraft:diamond_block");
    schem.setBlockAt(0, length - 1, 0, "minecraft:gold_block");
    schem.setBlockAt(0, 0, height - 1, "minecraft:emerald_block");
    schem.setBlockAt(width - 1, length - 1, height - 1, "minecraft:redstone_block");

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("roundtrip_noncubic.schem");
    schem.save(schemPath.toString());

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
