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

/**
 * Builds a synthetic schematic containing a gray stained glass pane in several connection states
 * and renders it to a PNG. This exercises the MC 26.1 texture object format ({"sprite": …,
 * "force_translucent": true}) used by all glass and glass pane block models.
 */
public class GlassPaneRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  private static Path buildGlassPaneSchematic() throws IOException {
    int cell = 4;
    int cols = 3;
    int rows = 2;
    short width = (short) (3 + cols * cell);
    short length = (short) (3 + rows * cell);

    Schematic.Builder schem = new Schematic.Builder(null, 1343, width, 1, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < length; y++) {
        schem.setBlockAt(x, 0, y, "minecraft:air");
      }
    }

    // gray_stained_glass_pane with all connections (post + 4 sides)
    schem.setBlockAt(
        3, 0, 3,
        "minecraft:gray_stained_glass_pane[north=true,east=true,south=true,west=true,waterlogged=false]");

    // gray_stained_glass_pane with no connections (post + 4 noside models)
    schem.setBlockAt(
        3 + cell, 0, 3,
        "minecraft:gray_stained_glass_pane[north=false,east=false,south=false,west=false,waterlogged=false]");

    // gray_stained_glass block as a solid-glass reference
    schem.setBlockAt(3 + 2 * cell, 0, 3, "minecraft:gray_stained_glass");

    // plain glass pane with all connections
    schem.setBlockAt(
        3, 0, 3 + cell,
        "minecraft:glass_pane[north=true,east=true,south=true,west=true,waterlogged=false]");

    // stone block as solid reference
    schem.setBlockAt(3 + cell, 0, 3 + cell, "minecraft:stone");

    // regular glass block
    schem.setBlockAt(3 + 2 * cell, 0, 3 + cell, "minecraft:glass");

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("gray_stained_glass_pane.schem");
    BlockReplacer.write(schem.build(), schemPath.toFile());
    return schemPath;
  }

  @Test
  public void renderGlassPane() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildGlassPaneSchematic();

    CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(
                List.of(schemPath), f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data", setup);
    assertTrue("Expected at least one rendered instance", setup.positions.length > 0);

    Path outputPath = OUTPUT_DIR.resolve("gray_stained_glass_pane.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered glass pane to: " + outputPath.toAbsolutePath());
  }
}
