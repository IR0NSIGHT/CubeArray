package org.ironsight.CubeArray;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

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

  private static Path buildTorchesAndLanternsSchematic() throws IOException {
    int spacing = 1;
    // 1 standing torch + one wall torch per facing + lantern/soul_lantern, each hanging or not
    int numVariants = 1 + FACINGS.length + 4;
    short width = (short) (1 + (numVariants - 1) * spacing + 2);
    short length = 3;
    Sponge2Schematic schem = new Sponge2Schematic(width, (short) 1, length);

    for (int x = 0; x < width; x++) {
      for (int y = 0; y < length; y++) {
        schem.setBlockAt(x, y, 0, "minecraft:air");
      }
    }

    // a pink_wool reference cube sits behind (y+1) every variant, so orientation - and, for the
    // wall torches, that they hang off the right face - is verifiable against an axis-aligned block
    int col = 0;
    int x = 1 + col++ * spacing;
    schem.setBlockAt(x, 1, 0, "minecraft:torch");
    schem.setBlockAt(x, 2, 0, "minecraft:pink_wool");
    for (String facing : FACINGS) {
      x = 1 + col++ * spacing;
      schem.setBlockAt(x, 1, 0, "minecraft:wall_torch[facing=" + facing + "]");
      schem.setBlockAt(x, 2, 0, "minecraft:pink_wool");
    }
    for (String lanternType : new String[] {"lantern", "soul_lantern"}) {
      for (boolean hanging : new boolean[] {false, true}) {
        x = 1 + col++ * spacing;
        String block =
            String.format(
                "minecraft:%s[hanging=%b,waterlogged=false]", lanternType, hanging);
        schem.setBlockAt(x, 1, 0, block);
        schem.setBlockAt(x, 2, 0, "minecraft:pink_wool");
      }
    }

    Files.createDirectories(SCHEMATIC_DIR);
    Path schemPath = SCHEMATIC_DIR.resolve("torches_and_lanterns.schem");
    schem.save(schemPath.toString());
    return schemPath;
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
