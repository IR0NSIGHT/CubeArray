package org.ironsight.CubeArray;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.ironsight.schemEdit.BlockReplacer;
import org.junit.Test;
import pitheguy.schemconvert.converter.Schematic;

/**
 * Builds a synthetic schematic containing every "type" variant of a slab block (bottom, top,
 * double), then renders it to a PNG so slab geometry can be visually verified.
 */
public class SlabsVariantsRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  private static final String[] TYPES = {"bottom", "top", "double"};

  private static Path buildSlabsVariantsSchematic() throws IOException {
    int spacing = 3;
    short width = (short) (1 + (TYPES.length - 1) * spacing + 2);
    short length = 3;
    // SchemConvert axis order is (x=width, y=height, z=length); height is always 1 here, so cells
    // are addressed as setBlockAt(widthIdx, 0, lengthIdx). dataVersion 1343 = MC 1.12.2.
    Schematic.Builder schem = new Schematic.Builder(null, 1343, width, 1, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < length; y++) {
        schem.setBlockAt(x, 0, y, "minecraft:air");
      }
    }

    int col = 0;
    for (String type : TYPES) {
      String block = String.format("minecraft:oak_slab[type=%s,waterlogged=false]", type);
      int x = 1 + col * spacing;
      schem.setBlockAt(x, 0, 1, block);
      col++;
    }

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("slabs_variants.schem");
    BlockReplacer.write(schem.build(), schemPath.toFile());
    return schemPath;
  }

  @Test
  public void renderAllSlabVariants() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildSlabsVariantsSchematic();

    SchemReader.CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(
                List.of(schemPath), f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data for slabs_variants.schem", setup);
    assertEquals("unexpected instance count", TYPES.length, setup.positions.length);

    Path outputPath = OUTPUT_DIR.resolve("slabs_variants.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered slab variants to: " + outputPath.toAbsolutePath());
  }
}
