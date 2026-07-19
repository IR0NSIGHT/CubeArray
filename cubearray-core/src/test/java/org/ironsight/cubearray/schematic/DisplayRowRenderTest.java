package org.ironsight.cubearray.schematic;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.ironsight.cubearray.edit.BlockReplacer;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.render.InstancedCubes;
import org.junit.Test;
import pitheguy.schemconvert.converter.Schematic;

public class DisplayRowRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  private static Path buildDisplayRowSchematic() throws IOException {
    int spacing = 3;
    int numSlots = 20;
    short width = (short) (1 + (numSlots - 1) * spacing + 2);
    short height = 2;
    short length = 1;

    Schematic.Builder schem = new Schematic.Builder(null, 1343, width, height, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        for (int z = 0; z < length; z++) {
          schem.setBlockAt(x, y, z, "minecraft:air");
        }
      }
    }

    int x = 1;

    schem.setBlockAt(x, 0, 0, "minecraft:air");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:azure_bluet");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:clay");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:dandelion");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:dirt");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:fern");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:grass");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:grass_block");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:gravel");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:kelp");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:large_fern[half=lower]");
    schem.setBlockAt(x, 1, 0, "minecraft:large_fern[half=upper]");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:peony[half=lower]");
    schem.setBlockAt(x, 1, 0, "minecraft:peony[half=upper]");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:poppy");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:rose_bush[half=lower]");
    schem.setBlockAt(x, 1, 0, "minecraft:rose_bush[half=upper]");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:sand");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:seagrass");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:sunflower[half=lower]");
    schem.setBlockAt(x, 1, 0, "minecraft:sunflower[half=upper]");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:tall_grass[half=lower]");
    schem.setBlockAt(x, 1, 0, "minecraft:tall_grass[half=upper]");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:water");
    x += spacing;

    schem.setBlockAt(x, 0, 0, "minecraft:white_tulip");

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("display_row.schem");
    BlockReplacer.write(schem.build(), schemPath.toFile());
    return schemPath;
  }

  @Test
  public void renderDisplayRow() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildDisplayRowSchematic();

    CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(
                List.of(schemPath), f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data for display_row.schem", setup);
    assertTrue("Expected at least one rendered instance", setup.positions.length > 0);

    Path outputPath = OUTPUT_DIR.resolve("display_row.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered display row to: " + outputPath.toAbsolutePath());
  }
}
