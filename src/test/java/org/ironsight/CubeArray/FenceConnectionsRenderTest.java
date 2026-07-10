package org.ironsight.CubeArray;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

/**
 * Builds a synthetic schematic containing all 16 north/east/south/west connection combinations of
 * a fence block, then renders it to a PNG so the fence post + arm geometry (see
 * SchemReader#addFenceArmInstances) can be visually verified.
 */
public class FenceConnectionsRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  private static Path buildFenceConnectionsSchematic() throws IOException {
    // width (x) and length (y-param of setBlockAt) form the horizontal grid; height stays 1
    // (z-param of setBlockAt is always 0, the only valid height index).
    short size = 12;
    Sponge2Schematic schem = new Sponge2Schematic(size, (short) 1, size);

    for (int x = 0; x < size; x++) {
      for (int lengthIdx = 0; lengthIdx < size; lengthIdx++) {
        schem.setBlockAt(x, lengthIdx, 0, "minecraft:air");
      }
    }

    for (int i = 0; i < 16; i++) {
      boolean north = (i & 1) != 0;
      boolean east = (i & 2) != 0;
      boolean south = (i & 4) != 0;
      boolean west = (i & 8) != 0;
      String block =
          String.format(
              "minecraft:oak_fence[north=%b,east=%b,south=%b,west=%b,waterlogged=false]",
              north, east, south, west);
      int x = 1 + (i % 4) * 3;
      int lengthIdx = 1 + (i / 4) * 3;
      schem.setBlockAt(x, lengthIdx, 0, block);
    }

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("fence_connections.schem");
    schem.save(schemPath.toString());
    return schemPath;
  }

  @Test
  public void renderAllFenceConnections() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildFenceConnectionsSchematic();

    SchemReader.CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(
                List.of(schemPath),
                f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data for fence_connections.schem", setup);

    // 16 posts + one arm-top/arm-bottom pair per connected side; across all 16 combinations of
    // 4 boolean sides, each side is set in exactly half (8) of them, so 4 sides * 8 * 2 bars = 64
    // arm instances, plus 16 posts = 80 instances total.
    assertEquals("unexpected instance count", 80, setup.positions.length);

    Path outputPath = OUTPUT_DIR.resolve("fence_connections.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered fence connections to: " + outputPath.toAbsolutePath());
  }
}
