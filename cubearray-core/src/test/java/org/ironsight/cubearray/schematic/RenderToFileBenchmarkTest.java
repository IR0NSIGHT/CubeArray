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

public class RenderToFileBenchmarkTest {

  private static final Path OUTPUT_DIR = Path.of("target", "test-renders", "perf-bench");
  private static boolean texturesCopied = false;

  private void ensureTextures() throws IOException {
    if (!texturesCopied) {
      ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
      texturesCopied = true;
    }
  }

  @Test
  public void benchmark100RenderToFile() throws Exception {
    ensureTextures();
    File dir = new File("src/test/resources/schematics/Dannypan");
    File[] schemFiles = dir.listFiles((d, n) -> n.endsWith(".schem"));
    assertNotNull("No .schem files in Dannypan", schemFiles);
    assertTrue("Need at least one schematic", schemFiles.length > 0);
    File schemFile = schemFiles[0];
    System.out.println("Using schematic: " + schemFile.getName());

    CubeSetup setup =
        SchemReader.prepareData(
            SchemReader.loadSchematics(List.of(schemFile.toPath()), f -> {}));
    assertNotNull("Failed to prepare CubeSetup", setup);

    int iterations = 100;
    List<Path> outputs = new ArrayList<>();
    Files.createDirectories(OUTPUT_DIR);

    long start = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      Path out = OUTPUT_DIR.resolve(i + ".png");
      InstancedCubes.renderToFile(setup, out, 640, 640);
      outputs.add(out);
    }
    long elapsedNs = System.nanoTime() - start;
    long elapsedMs = elapsedNs / 1_000_000;

    for (Path p : outputs) {
      assertTrue("Missing output: " + p, p.toFile().exists());
      assertTrue("Empty output: " + p, p.toFile().length() > 0);
    }
    assertNonBlank(outputs.get(0), schemFile.getName() + " (first)");
    assertNonBlank(outputs.get(outputs.size() - 1), schemFile.getName() + " (last)");

    System.out.println("===== renderToFile Benchmark =====");
    System.out.println("Iterations: " + iterations);
    System.out.println("Total time: " + elapsedMs + " ms");
    System.out.println("Average:    " + String.format("%.2f", elapsedMs / (double) iterations) + " ms per render");
    System.out.println("Output dir: " + OUTPUT_DIR.toAbsolutePath());
  }

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
}
