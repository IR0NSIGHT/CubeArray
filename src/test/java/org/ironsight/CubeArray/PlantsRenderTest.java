package org.ironsight.CubeArray;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

/**
 * Builds a synthetic schematic containing a grid of "plant" blocks - flowers, a sapling, and
 * assorted greenery (grasses, ferns, bushes) - all of which render as the two-quad {@code
 * block/cross} model, each paired with an adjacent pink_wool reference cube, then renders it to a
 * PNG so the 45deg cross geometry can be visually verified against the axis-aligned cube.
 */
public class PlantsRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  // Plants with a trivial "" blockstate variant that maps directly to block/<name>, so each
  // placement expands into exactly the elements of that single cross model. Includes flowers, a
  // sapling, and assorted greenery (grasses, ferns, bushes) - all rendered as the two-quad cross.
  private static final String[] PLANTS = {
    "dandelion",
    "poppy",
    "blue_orchid",
    "allium",
    "azure_bluet",
    "red_tulip",
    "orange_tulip",
    "white_tulip",
    "pink_tulip",
    "oxeye_daisy",
    "cornflower",
    "lily_of_the_valley",
    "oak_sapling",
    "dead_bush",
    // greenery
    "short_grass",
    "fern",
    "short_dry_grass",
    "tall_dry_grass",
    "bush",
    "firefly_bush",
    "nether_sprouts",
  };

  /** Number of instances a single placement of the given model id expands into. */
  private static int instancesFor(Path vanillaRoot, String modelId) throws IOException {
    int elements = BlockModelParser.parseModel(vanillaRoot, modelId).subBlocks().size();
    return Math.max(1, elements);
  }

  private static Path buildPlantsSchematic() throws IOException {
    // arrange the plants in a near-square grid rather than one long row
    int cols = (int) Math.ceil(Math.sqrt(PLANTS.length));
    int rows = (int) Math.ceil((double) PLANTS.length / cols);
    int xSpacing = 2; // one plant column + one gap column
    int ySpacing = 3; // plant row + its pink_wool row + one gap row
    short width = (short) (1 + (cols - 1) * xSpacing + 2);
    short length = (short) (1 + (rows - 1) * ySpacing + 2);
    Sponge2Schematic schem = new Sponge2Schematic(width, (short) 1, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < length; y++) {
        schem.setBlockAt(x, y, 0, "minecraft:air");
      }
    }

    for (int i = 0; i < PLANTS.length; i++) {
      int x = 1 + (i % cols) * xSpacing;
      int y = 1 + (i / cols) * ySpacing;
      schem.setBlockAt(x, y, 0, "minecraft:" + PLANTS[i]);
      // an axis-aligned reference cube next to each plant, so the 45deg cross tilt is verifiable
      schem.setBlockAt(x, y + 1, 0, "minecraft:pink_wool");
    }

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("plants.schem");
    schem.save(schemPath.toString());
    return schemPath;
  }

  @Test
  public void renderPlants() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildPlantsSchematic();

    SchemReader.CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(
                List.of(schemPath), f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data for plants.schem", setup);

    // Each placement expands into one instance per element of its vanilla model (see
    // SchemReader.computePieces); derive the expected total from the parsed models so this stays
    // correct across vanilla versions.
    Path vanillaRoot =
        ResourceUtils.getInstallPath().resolve(ResourceUtils.VANILLA_ASSETS_RESOURCES);
    int expectedInstances = 0;
    for (String plant : PLANTS) {
      expectedInstances += instancesFor(vanillaRoot, "block/" + plant);
    }
    // plus one reference cube (pink_wool) placed next to each plant
    expectedInstances += PLANTS.length * instancesFor(vanillaRoot, "block/pink_wool");
    assertEquals("unexpected instance count", expectedInstances, setup.positions.length);

    Path outputPath = OUTPUT_DIR.resolve("plants.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered plants to: " + outputPath.toAbsolutePath());
  }
}
