package org.ironsight.CubeArray;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.ironsight.CubeArray.BlockStateParser.BlockState;
import org.ironsight.CubeArray.BlockStateParser.ModelPlacement;
import org.junit.Test;

/**
 * Builds a synthetic schematic containing every facing/half/shape combination of a stairs block,
 * then renders it to a PNG so stairs geometry can be visually verified.
 */
public class StairsVariantsRenderTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static final Path SCHEMATIC_DIR = Path.of("target", "test-schematics");

  private static final String[] FACINGS = {"north", "east", "south", "west"};
  private static final String[] HALVES = {"bottom", "top"};
  private static final String[] SHAPES = {
    "straight", "inner_left", "inner_right", "outer_left", "outer_right"
  };

  /**
   * Mirrors {@link org.ironsight.CubeArray.SchemReader}'s piece computation: number of render
   * instances a block+state expands into = sum over its selected model placements of that model's
   * element count (a full-cube fallback when nothing resolves).
   */
  private static int pieceCount(
      Path root, Map<String, BlockState> states, String block, String propsKv) throws IOException {
    Map<String, String> map = new HashMap<>();
    for (String pair : propsKv.split(",")) {
      int eq = pair.indexOf('=');
      map.put(pair.substring(0, eq), pair.substring(eq + 1));
    }
    Function<String, String> props = map::get;

    BlockState state = states.get("minecraft:" + block);
    List<ModelPlacement> placements = state != null ? state.select(props) : List.of();
    if (placements.isEmpty()) {
      placements = List.of(new ModelPlacement("minecraft:block/" + block, 0, 0, false));
    }
    int total = 0;
    for (ModelPlacement p : placements) {
      total += BlockModelParser.parseModel(root, p.model()).subBlocks().size();
    }
    return Math.max(1, total);
  }

  private static Path buildStairsVariantsSchematic() throws IOException {
    // columns = shapes, rows = facing/half combinations
    int spacing = 3;
    int numCols = SHAPES.length;
    int numRows = FACINGS.length * HALVES.length;
    short width = (short) (1 + (numCols - 1) * spacing + 2);
    short length = (short) (1 + (numRows - 1) * spacing + 2);
    Sponge2Schematic schem = new Sponge2Schematic(width, (short) 1, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < length; y++) {
        schem.setBlockAt(x, y, 0, "minecraft:air");
      }
    }

    int row = 0;
    for (String facing : FACINGS) {
      for (String half : HALVES) {
        int col = 0;
        for (String shape : SHAPES) {
          String block =
              String.format(
                  "minecraft:oak_stairs[facing=%s,half=%s,shape=%s,waterlogged=false]",
                  facing, half, shape);
          int x = 1 + col * spacing;
          int y = 1 + row * spacing;
          schem.setBlockAt(x, y, 0, block);
          col++;
        }
        row++;
      }
    }

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("stairs_variants.schem");
    schem.save(schemPath.toString());
    return schemPath;
  }

  @Test
  public void renderAllStairsVariants() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    Path schemPath = buildStairsVariantsSchematic();

    SchemReader.CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(
                List.of(schemPath), f -> fail("load error: " + f.getName())));

    assertNotNull("Failed to prepare data for stairs_variants.schem", setup);

    // Each variant is now resolved through the blockstate: shape picks the straight/inner/outer
    // model and half/facing pick the rotation. Derive the expected instance total by mirroring
    // that same blockstate -> model -> element chain, so this validates selection end-to-end
    // (and stays correct across vanilla versions).
    Path vanillaRoot =
        ResourceUtils.getInstallPath().resolve(ResourceUtils.VANILLA_ASSETS_RESOURCES);
    Map<String, BlockState> states = BlockStateParser.parseAll(vanillaRoot);
    int expected = 0;
    for (String facing : FACINGS) {
      for (String half : HALVES) {
        for (String shape : SHAPES) {
          expected +=
              pieceCount(
                  vanillaRoot,
                  states,
                  "oak_stairs",
                  "facing=" + facing + ",half=" + half + ",shape=" + shape);
        }
      }
    }
    assertEquals("unexpected instance count", expected, setup.positions.length);

    Path outputPath = OUTPUT_DIR.resolve("stairs_variants.png");
    Files.createDirectories(outputPath.getParent());
    InstancedCubes.renderToFile(setup, outputPath, 1920, 1200);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertTrue("Output file empty", outputPath.toFile().length() > 0);
    System.out.println("Rendered stairs variants to: " + outputPath.toAbsolutePath());
  }
}
