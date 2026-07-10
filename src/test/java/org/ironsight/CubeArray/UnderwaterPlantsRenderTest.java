package org.ironsight.CubeArray;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.ironsight.schemEdit.BlockReplacer;
import org.junit.Test;
import pitheguy.schemconvert.converter.Schematic;

/**
 * Builds a synthetic schematic housing all of Minecraft's underwater plants - the seagrasses, kelp,
 * sea pickle, and every living and dead coral, coral fan and coral wall fan - laid out in a
 * rectangular grid, then renders it to a PNG so their geometry can be visually verified.
 *
 * <p>Unlike {@link PlantsRenderTest}, this test does not assert an exact instance count: several of
 * these blocks expand unpredictably (seagrass/sea_pickle carry a waterlogged state and sea_pickle
 * uses a random-rotation variant list), so the model element count is not a reliable predictor.
 * Instead it asserts every placed block contributed at least one render instance, and that the load
 * itself did not error (the load-error callback fails the test).
 */
public class UnderwaterPlantsRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  private static final String[] CORAL_TYPES = {"tube", "brain", "bubble", "fire", "horn"};

  /** All underwater plant block ids (without the {@code minecraft:} namespace prefix). */
  private static final List<String> PLANTS = buildPlantList();

  private static List<String> buildPlantList() {
    List<String> plants = new ArrayList<>();
    // free-standing vegetation (tall_seagrass is two-tall, so include both halves)
    plants.add("seagrass");
    plants.add("tall_seagrass[half=lower]");
    plants.add("tall_seagrass[half=upper]");
    plants.add("kelp");
    plants.add("kelp_plant");
    plants.add("sea_pickle[pickles=4,waterlogged=true]");
    // living and dead corals, coral fans, and wall fans (facing=north) for each coral type
    for (String deadPrefix : new String[] {"", "dead_"}) {
      for (String type : CORAL_TYPES) {
        String coral = deadPrefix + type + "_coral";
        plants.add(coral);
        plants.add(coral + "_fan");
        plants.add(coral + "_wall_fan[facing=north]");
      }
    }
    return plants;
  }

  private static Path buildUnderwaterPlantsSchematic() throws Exception {
    // arrange the plants in a near-square grid
    int cols = (int) Math.ceil(Math.sqrt(PLANTS.size()));
    int rows = (int) Math.ceil((double) PLANTS.size() / cols);
    int spacing = 2; // one plant cell + one gap cell, in both axes
    short width = (short) (1 + (cols - 1) * spacing + 2);
    short length = (short) (1 + (rows - 1) * spacing + 2);
    // SchemConvert axis order is (x=width, y=height, z=length); height is always 1 here, so cells
    // are addressed as setBlockAt(widthIdx, 0, lengthIdx). dataVersion 1343 = MC 1.12.2.
    Schematic.Builder schem = new Schematic.Builder(null, 1343, width, 1, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < length; y++) {
        schem.setBlockAt(x, 0, y, "minecraft:air");
      }
    }

    for (int i = 0; i < PLANTS.size(); i++) {
      int x = 1 + (i % cols) * spacing;
      int y = 1 + (i / cols) * spacing;
      schem.setBlockAt(x, 0, y, "minecraft:" + PLANTS.get(i));
    }

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("underwater_plants.schem");
    BlockReplacer.write(schem.build(), schemPath.toFile());
    return schemPath;
  }

  @Test
  public void renderUnderwaterPlants() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildUnderwaterPlantsSchematic();

    SchemReader.CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(
                List.of(schemPath), f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data for underwater_plants.schem", setup);
    // every placed block expands into at least one render instance (see SchemReader.computePieces)
    assertTrue(
        "expected at least one instance per placed plant",
        setup.positions.length >= PLANTS.size());

    Path outputPath = OUTPUT_DIR.resolve("underwater_plants.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered underwater plants to: " + outputPath.toAbsolutePath());
  }
}
