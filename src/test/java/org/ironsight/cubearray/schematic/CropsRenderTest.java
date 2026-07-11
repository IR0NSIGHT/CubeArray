package org.ironsight.cubearray.schematic;
import org.ironsight.cubearray.render.InstancedCubes;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.platform.ResourceUtils;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.ironsight.cubearray.edit.BlockReplacer;
import org.junit.Test;
import pitheguy.schemconvert.converter.Schematic;

/**
 * Builds a synthetic schematic laying out the growable vegetable crops in a rectangular grid - one
 * row per crop, one column per growth stage - then renders it to a PNG so each stage's texture can
 * be visually verified. Wheat and carrots grow through {@code age=0..7} (the two-quad {@code
 * block/crop} model, one distinct sprite per stage); pumpkin, melon and carved_pumpkin are the
 * mature full blocks (they have no block-level growth stages - the plant grows via a separate stem
 * block), so those rows hold a single block.
 */
public class CropsRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  /** Each row is one crop's growth-stage block strings, laid out left-to-right by stage. */
  private static List<List<String>> cropRows() {
    List<List<String>> rows = new ArrayList<>();
    rows.add(ages("minecraft:wheat", 8)); // age 0..7
    rows.add(ages("minecraft:carrots", 8)); // age 0..7
    rows.add(List.of("minecraft:pumpkin"));
    rows.add(List.of("minecraft:melon"));
    rows.add(List.of("minecraft:carved_pumpkin[facing=south]"));
    return rows;
  }

  private static List<String> ages(String block, int count) {
    List<String> stages = new ArrayList<>(count);
    for (int age = 0; age < count; age++) stages.add(block + "[age=" + age + "]");
    return stages;
  }

  private static Path buildCropsSchematic() throws IOException {
    List<List<String>> rows = cropRows();
    int numCols = rows.stream().mapToInt(List::size).max().orElse(1);
    int numRows = rows.size();
    int spacing = 2; // one crop column/row + one gap, so neighbours dont visually merge
    short width = (short) (1 + (numCols - 1) * spacing + 2);
    short length = (short) (1 + (numRows - 1) * spacing + 2);
    // SchemConvert axis order is (x=width, y=height, z=length); height is always 1 here, so cells
    // are addressed as setBlockAt(widthIdx, 0, lengthIdx). dataVersion 1343 = MC 1.12.2.
    Schematic.Builder schem = new Schematic.Builder(null, 1343, width, 1, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < length; y++) {
        schem.setBlockAt(x, 0, y, "minecraft:air");
      }
    }

    for (int r = 0; r < numRows; r++) {
      List<String> stages = rows.get(r);
      for (int c = 0; c < stages.size(); c++) {
        int x = 1 + c * spacing;
        int y = 1 + r * spacing;
        schem.setBlockAt(x, 0, y, stages.get(c));
      }
    }

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("crops.schem");
    BlockReplacer.write(schem.build(), schemPath.toFile());
    return schemPath;
  }

  @Test
  public void renderCrops() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildCropsSchematic();

    CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(List.of(schemPath), f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data for crops.schem", setup);
    assertTrue("no cubes were generated for the crops schematic", setup.positions.length > 0);

    Path outputPath = OUTPUT_DIR.resolve("crops.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered crops to: " + outputPath.toAbsolutePath());
  }
}
