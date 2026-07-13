package org.ironsight.cubearray.schematic;
import org.ironsight.cubearray.render.InstancedCubes;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.platform.ResourceUtils;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
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

        CubeSetup setup =
            SchemReader.prepareData(
                SchemReader.loadSchematics(
                    List.of(schemFile.toPath()), f -> failures.add("load error: " + f.getName())));

        assertNotNull("Failed to prepare data for: " + schemFile.getName(), setup);
        InstancedCubes.renderToFile(setup, outputPath, 640, 640);

        assertTrue("Output file missing for " + schemFile.getName(), outputPath.toFile().exists());
        assertTrue(
            "Output file empty for " + schemFile.getName(), outputPath.toFile().length() > 0);
        assertNonBlank(outputPath, schemFile.getName());
        System.out.println("OK: " + schemFile.getName() + " -> " + outputPath);
      } catch (Exception e) {
        failures.add(schemFile.getName() + ": " + e.getMessage());
        e.printStackTrace();
      }
    }
    assertTrue("Failures: " + String.join(", ", failures), failures.isEmpty());
  }

  /**
   * Guards against the "blank render" regression: an off-main-thread render into a hidden window's
   * default framebuffer used to read back as an all-transparent image that still passed the mere
   * {@code length > 0} check. A real render is fully opaque (opaque sky-blue background) and
   * contains more than one distinct color.
   */
  private static void assertNonBlank(Path png, String name) throws IOException {
    BufferedImage img = ImageIO.read(png.toFile());
    assertNotNull("Could not read rendered PNG for " + name, img);
    int w = img.getWidth();
    int h = img.getHeight();
    long opaque = 0;
    Set<Integer> colors = new HashSet<>();
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int argb = img.getRGB(x, y);
        if ((argb >>> 24) >= 250) opaque++;
        colors.add(argb & 0x00FFFFFF);
      }
    }
    double opaqueFraction = opaque / (double) (w * h);
    assertTrue(
        "Render for " + name + " is (near) fully transparent (opaqueFraction=" + opaqueFraction
            + ") -> blank",
        opaqueFraction > 0.9);
    assertTrue(
        "Render for " + name + " is a single flat color -> blank", colors.size() > 1);
  }

  /**
   * Reproduces the actual in-app failure condition: the app renders list icons and previews on
   * background daemon threads (e.g. {@code render-worker}, {@code preview-renderer}), not the JVM
   * main thread. Reading back the hidden window's default framebuffer off the main thread produced
   * an all-transparent (blank) image. This asserts an off-main-thread render is non-blank.
   */
  @Test
  public void renderOffMainThreadIsNotBlank() throws Exception {
    ensureTextures();
    File dir = new File("src/main/resources/schematics/Dannypan");
    File[] schemFiles = dir.listFiles((d, name) -> name.endsWith(".schem"));
    assertNotNull("No .schem files in Dannypan", schemFiles);
    assertTrue("No .schem files in Dannypan", schemFiles.length > 0);
    File schemFile = schemFiles[0];

    Path outputPath =
        OUTPUT_DIR.resolve("offthread/" + schemFile.getName().replace(".schem", ".png"));
    Files.createDirectories(outputPath.getParent());

    CubeSetup setup =
        SchemReader.prepareData(SchemReader.loadSchematics(List.of(schemFile.toPath()), f -> {}));
    assertNotNull("Failed to prepare data for: " + schemFile.getName(), setup);

    final Throwable[] error = new Throwable[1];
    Thread renderThread =
        new Thread(
            () -> {
              try {
                InstancedCubes.renderToFile(setup, outputPath, 640, 640);
              } catch (Throwable t) {
                error[0] = t;
              }
            },
            "render-worker-test");
    renderThread.setDaemon(true);
    renderThread.start();
    renderThread.join(60_000);

    assertNull(
        "Off-thread render threw: " + (error[0] == null ? "" : error[0]), error[0]);
    assertFalse("Off-thread render did not finish in time", renderThread.isAlive());
    assertTrue("Output file missing", outputPath.toFile().exists());
    assertNonBlank(outputPath, schemFile.getName() + " (off-thread)");
  }

  @Test
  public void renderWithGridEnabled() throws Exception {
    ensureTextures();
    File dir = new File("src/main/resources/schematics/Dannypan");
    File[] schemFiles = dir.listFiles((d, name) -> name.endsWith(".schem"));
    assertNotNull("No .schem files in Dannypan", schemFiles);
    assertTrue("No .schem files in Dannypan", schemFiles.length > 0);
    File schemFile = schemFiles[0];

    Path outputPath =
        OUTPUT_DIR.resolve("withgrid/" + schemFile.getName().replace(".schem", ".png"));
    Files.createDirectories(outputPath.getParent());

    CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(List.of(schemFile.toPath()), f -> {}),
            true);

    assertNotNull("Failed to prepare data with grid", setup);
    assertTrue("useGrid should be true", setup.useGrid);
    InstancedCubes.renderToFile(setup, outputPath, 640, 640);

    assertTrue("Output file missing", outputPath.toFile().exists());
    assertNonBlank(outputPath, schemFile.getName() + " (grid)");
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

        CubeSetup setup =
            SchemReader.prepareData(
                SchemReader.loadSchematics(
                    List.of(schemFile.toPath()), f -> failures.add("load error: " + f.getName())));

        assertNotNull("Failed to prepare data for: " + schemFile.getName(), setup);
        InstancedCubes.renderToFile(setup, outputPath, 640, 640);

        assertTrue("Output file missing for " + schemFile.getName(), outputPath.toFile().exists());
        assertTrue(
            "Output file empty for " + schemFile.getName(), outputPath.toFile().length() > 0);
        assertNonBlank(outputPath, schemFile.getName());
        System.out.println("OK: " + schemFile.getName() + " -> " + outputPath);
      } catch (Exception e) {
        failures.add(schemFile.getName() + ": " + e.getMessage());
        e.printStackTrace();
      }
    }
    assertTrue("Failures: " + String.join(", ", failures), failures.isEmpty());
  }
}
