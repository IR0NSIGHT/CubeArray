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
 * Builds a synthetic schematic containing a standing torch, one wall torch per facing, and both
 * lantern types (hanging and standing), then renders it to a PNG so torch/lantern geometry can be
 * visually verified.
 */
public class TorchesAndLanternsRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  private static final String[] FACINGS = {"north", "east", "south", "west"};

  /** Instances a single placement of a block with the given model id expands into. */
  private static int instancesFor(Path vanillaRoot, String modelId) throws IOException {
    int elements = BlockModelParser.parseModel(vanillaRoot, modelId).subBlocks().size();
    return Math.max(1, elements);
  }

  /**
   * Where each wall_torch's supporting wall sits relative to the torch, as a {dx, dy} schematic
   * offset. A wall torch mounts to the block opposite its facing (facing=east hangs off a wall to
   * its west), so its base should touch a block placed here.
   */
  private static int[] wallDelta(String facing) {
    return switch (facing) {
      case "north" -> new int[] {0, 1};
      case "south" -> new int[] {0, -1};
      case "east" -> new int[] {-1, 0};
      case "west" -> new int[] {1, 0};
      default -> new int[] {0, 1};
    };
  }

  private static Path buildTorchesAndLanternsSchematic() throws IOException {
    // Lay the variants out on a near-square 3-column grid (rather than one long row) so the camera
    // frames them large enough to inspect. Each cell is 3x3 so a wall torch's supporting wall block
    // can sit on any side without colliding with a neighbour.
    int cols = 3;
    int cell = 3;
    // 1 standing torch + one wall torch per facing + lantern/soul_lantern, each hanging or not
    int numVariants = 1 + FACINGS.length + 4;
    int rows = (numVariants + cols - 1) / cols;
    short width = (short) (3 + (cols - 1) * cell + 3);
    short length = (short) (3 + (rows - 1) * cell + 3);
    // SchemConvert axis order is (x=width, y=height, z=length); height is always 1 here, so cells
    // are addressed as setBlockAt(widthIdx, 0, lengthIdx). dataVersion 1343 = MC 1.12.2.
    Schematic.Builder schem = new Schematic.Builder(null, 1343, width, 1, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < length; y++) {
        schem.setBlockAt(x, 0, y, "minecraft:air");
      }
    }

    int idx = 0;
    // standing torch, with a pink_wool size reference beside it
    placeStandingOrLantern(schem, idx++, cols, cell, "minecraft:torch");
    // each wall torch, with a pink_wool "wall" on the exact side it mounts to, so a correctly
    // placed torch has its base touching that wool and tilts away from it
    for (String facing : FACINGS) {
      int x = 3 + (idx % cols) * cell;
      int y = 3 + (idx / cols) * cell;
      idx++;
      schem.setBlockAt(x, 0, y, "minecraft:wall_torch[facing=" + facing + "]");
      int[] d = wallDelta(facing);
      schem.setBlockAt(x + d[0], 0, y + d[1], "minecraft:pink_wool");
    }
    for (String lanternType : new String[] {"lantern", "soul_lantern"}) {
      for (boolean hanging : new boolean[] {false, true}) {
        String block =
            String.format("minecraft:%s[hanging=%b,waterlogged=false]", lanternType, hanging);
        placeStandingOrLantern(schem, idx++, cols, cell, block);
      }
    }

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("torches_and_lanterns.schem");
    BlockReplacer.write(schem.build(), schemPath.toFile());
    return schemPath;
  }

  /** Places a non-wall variant at its grid cell with a pink_wool size reference beside it. */
  private static void placeStandingOrLantern(
      Schematic.Builder schem, int idx, int cols, int cell, String block) {
    int x = 3 + (idx % cols) * cell;
    int y = 3 + (idx / cols) * cell;
    schem.setBlockAt(x, 0, y, block);
    schem.setBlockAt(x, 0, y + 1, "minecraft:pink_wool");
  }

  @Test
  public void renderTorchesAndLanterns() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildTorchesAndLanternsSchematic();

    SchemReader.CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(
                List.of(schemPath), f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data for torches_and_lanterns.schem", setup);
    // each placement expands into one instance per element of its vanilla model (see
    // SchemReader.addModelInstances); derive the expected total from the parsed models so this
    // stays correct across vanilla versions.
    // hanging=true selects a different model (…_hanging) than hanging=false, so count each
    // separately - the blockstate picks the model per state.
    Path vanillaRoot =
        ResourceUtils.getInstallPath().resolve(ResourceUtils.VANILLA_ASSETS_RESOURCES);
    int expectedInstances =
        instancesFor(vanillaRoot, "block/torch") // 1 standing torch
            + FACINGS.length * instancesFor(vanillaRoot, "block/wall_torch")
            + instancesFor(vanillaRoot, "block/lantern") // hanging=false
            + instancesFor(vanillaRoot, "block/lantern_hanging") // hanging=true
            + instancesFor(vanillaRoot, "block/soul_lantern")
            + instancesFor(vanillaRoot, "block/soul_lantern_hanging");
    // plus one reference cube (pink_wool) behind each variant placement
    int variantCount = 1 + FACINGS.length + 4; // standing torch + wall torches + lanterns
    expectedInstances += variantCount * instancesFor(vanillaRoot, "block/pink_wool");
    assertEquals("unexpected instance count", expectedInstances, setup.positions.length);

    Path outputPath = OUTPUT_DIR.resolve("torches_and_lanterns.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered torches and lanterns to: " + outputPath.toAbsolutePath());
  }
}
