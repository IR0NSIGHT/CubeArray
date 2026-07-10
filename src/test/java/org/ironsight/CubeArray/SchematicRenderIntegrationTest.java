package org.ironsight.CubeArray;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SchematicRenderIntegrationTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders");
  private static boolean texturesCopied = false;

  private void ensureTextures() throws IOException {
    if (!texturesCopied) {
      ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
      texturesCopied = true;
    }
  }

  @Test
  public void renderDannypanSchematics() throws Exception {
    ensureTextures();
    File dir = new File("src/main/resources/schematics/Dannypan");
    assertTrue("Dannypan directory not found: " + dir.getAbsolutePath(), dir.isDirectory());

    File[] schemFiles = dir.listFiles((d, name) -> name.endsWith(".schem"));
    assertNotNull("No .schem files in Dannypan", schemFiles);

    List<String> failures = new ArrayList<>();
    for (File schemFile : schemFiles) {
      try {
        Path outputPath =
            OUTPUT_DIR.resolve("Dannypan/" + schemFile.getName().replace(".schem", ".png"));
        Files.createDirectories(outputPath.getParent());

        SchemReader.CubeSetup setup =
            SchemReader.prepareData(
                SchemReader.loadSchematics(
                    List.of(schemFile.toPath()), f -> failures.add("load error: " + f.getName())));

        assertNotNull("Failed to prepare data for: " + schemFile.getName(), setup);
        InstancedCubes.renderToFile(setup, outputPath, 640, 640);

        assertTrue("Output file missing for " + schemFile.getName(), outputPath.toFile().exists());
        assertTrue(
            "Output file empty for " + schemFile.getName(), outputPath.toFile().length() > 0);
        System.out.println("OK: " + schemFile.getName() + " -> " + outputPath);
      } catch (Exception e) {
        failures.add(schemFile.getName() + ": " + e.getMessage());
        e.printStackTrace();
      }
    }
    assertTrue("Failures: " + String.join(", ", failures), failures.isEmpty());
  }

  @Test
  public void renderPaleozoeySchematics() throws Exception {
    ensureTextures();
    File dir = new File("src/main/resources/schematics/Paleozoey");
    assertTrue("Paleozoey directory not found: " + dir.getAbsolutePath(), dir.isDirectory());

    File[] schemFiles = dir.listFiles((d, name) -> name.endsWith(".schem"));
    assertNotNull("No .schem files in Paleozoey", schemFiles);

    List<String> failures = new ArrayList<>();
    for (File schemFile : schemFiles) {
      try {
        Path outputPath =
            OUTPUT_DIR.resolve("Paleozoey/" + schemFile.getName().replace(".schem", ".png"));
        Files.createDirectories(outputPath.getParent());

        SchemReader.CubeSetup setup =
            SchemReader.prepareData(
                SchemReader.loadSchematics(
                    List.of(schemFile.toPath()), f -> failures.add("load error: " + f.getName())));

        assertNotNull("Failed to prepare data for: " + schemFile.getName(), setup);
        InstancedCubes.renderToFile(setup, outputPath, 640, 640);

        assertTrue("Output file missing for " + schemFile.getName(), outputPath.toFile().exists());
        assertTrue(
            "Output file empty for " + schemFile.getName(), outputPath.toFile().length() > 0);
        System.out.println("OK: " + schemFile.getName() + " -> " + outputPath);
      } catch (Exception e) {
        failures.add(schemFile.getName() + ": " + e.getMessage());
        e.printStackTrace();
      }
    }
    assertTrue("Failures: " + String.join(", ", failures), failures.isEmpty());
  }
}
