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

public class MiscBlocksRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  private static Path buildMiscBlocksSchematic() throws IOException {
    short width = 15;
    short height = 2;
    short length = 11;

    Schematic.Builder schem = new Schematic.Builder(null, 1343, width, height, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < height; y++) {
        for (int z = 0; z < length; z++) {
          schem.setBlockAt(x, y, z, "minecraft:air");
        }
      }
    }

    // (0,0) grass_block
    schem.setBlockAt(3, 0, 3, "minecraft:grass_block[snowy=false]");
    // (1,0) short_grass
    schem.setBlockAt(7, 0, 3, "minecraft:short_grass");
    // (2,0) furnace
    schem.setBlockAt(11, 0, 3, "minecraft:furnace[facing=north,lit=false]");
    // (0,1) tall_grass lower + upper
    schem.setBlockAt(3, 0, 7, "minecraft:tall_grass[half=lower]");
    schem.setBlockAt(3, 1, 7, "minecraft:tall_grass[half=upper]");
    // (1,1) chest
    schem.setBlockAt(7, 0, 7, "minecraft:chest");
    // (2,1) stone reference
    schem.setBlockAt(11, 0, 7, "minecraft:stone");

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("misc_blocks.schem");
    BlockReplacer.write(schem.build(), schemPath.toFile());
    return schemPath;
  }

  @Test
  public void renderMiscBlocks() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildMiscBlocksSchematic();

    CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(
                List.of(schemPath), f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data", setup);
    assertTrue("Expected at least one rendered instance", setup.positions.length > 0);

    Path outputPath = OUTPUT_DIR.resolve("misc_blocks.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered misc blocks to: " + outputPath.toAbsolutePath());
  }
}
