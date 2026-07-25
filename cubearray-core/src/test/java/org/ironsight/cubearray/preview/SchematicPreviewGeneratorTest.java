package org.ironsight.cubearray.preview;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.preview.SchematicPreviewGenerator.PriorityTask;
import org.ironsight.cubearray.schematic.SchemReader;
import org.junit.Test;
import org.pepsoft.worldpainter.objects.WPObject;

public class SchematicPreviewGeneratorTest {

  private static boolean texturesCopied = false;

  private void ensureTextures() throws Exception {
    if (!texturesCopied) {
      ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
      texturesCopied = true;
    }
  }

  @Test
  public void priorityTaskEqualityByFilePath() {
    PriorityTask a1 = new PriorityTask(() -> {}, 1, "/path/file.schem");
    PriorityTask a2 = new PriorityTask(() -> {}, 2, "/path/file.schem");
    PriorityTask b  = new PriorityTask(() -> {}, 1, "/path/other.schem");

    assertEquals(a1, a2);
    assertEquals(a1.hashCode(), a2.hashCode());
    assertNotEquals(a1, b);
    assertNotEquals(a2, b);
  }

  @Test
  public void priorityTaskEqualityNullFilePath() {
    PriorityTask withNull  = new PriorityTask(() -> {}, 1);
    PriorityTask alsoNull  = new PriorityTask(() -> {}, 2, null);
    PriorityTask withPath  = new PriorityTask(() -> {}, 1, "/path/file.schem");

    assertNotNull(withNull);
    assertNotEquals(withNull, alsoNull);
    assertNotEquals(withNull, withPath);
    assertNotEquals(withPath, withNull);
  }

  @Test
  public void priorityBlockingQueueContainsWithCustomEquals() {
    PriorityBlockingQueue<PriorityTask> queue = new PriorityBlockingQueue<>();
    PriorityTask original = new PriorityTask(() -> {}, 1, "/path/file.schem");
    queue.add(original);

    PriorityTask duplicate = new PriorityTask(() -> {}, 2, "/path/file.schem");
    assertTrue("Queue should identify duplicate via equals", queue.contains(duplicate));

    PriorityTask different = new PriorityTask(() -> {}, 1, "/path/other.schem");
    assertFalse("Queue should not match different path", queue.contains(different));
  }

  @Test
  public void queueRenderRespectsPendingFilesDedup() throws Exception {
    ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);

    File schemFile = Path.of("src/test/resources/schematics/Dannypan/house_1.schem").toFile();
    WPObject obj = SchemReader.loadSchematics(List.of(schemFile.toPath()), f -> {}).get(0);

    SchematicPreviewGenerator gen = SchematicPreviewGenerator.getInstance();
    gen.invalidateIcon(schemFile);

    gen.queueRender(schemFile, obj, () -> {});
    int afterFirst = gen.getPendingRenderCount();

    gen.queueRender(schemFile, obj, () -> {});
    int afterSecond = gen.getPendingRenderCount();

    assertTrue("Expected at most 1 pending render after first queue, got " + afterFirst, afterFirst <= 1);
    assertEquals("Second queueRender should not increase pending count", afterFirst, afterSecond);
  }

  @Test
  public void queueRenderAlwaysProducesFullRendersAndThumbnails() throws Exception {
    ensureTextures();

    File schemFile = Path.of("src/test/resources/schematics/Dannypan/house_1.schem").toFile();
    assertTrue("Test schematic not found: " + schemFile, schemFile.exists());

    WPObject obj = SchemReader.loadSchematics(List.of(schemFile.toPath()), f -> {}).get(0);
    assertNotNull("Failed to load schematic", obj);

    Path renderPath = ResourceUtils.getRenderPathForFile(schemFile);
    Path thumbPath = ResourceUtils.getThumbPathForFile(schemFile);
    deleteFileTree(renderPath, thumbPath, 4);

    SchematicPreviewGenerator gen = SchematicPreviewGenerator.getInstance();
    gen.invalidateIcon(schemFile);

    CountDownLatch latch = new CountDownLatch(1);
    gen.queueRender(schemFile, obj, latch::countDown);

    assertTrue("Render did not complete in 120 seconds",
        latch.await(120, TimeUnit.SECONDS));

    for (int i = 0; i < 4; i++) {
      Path anglePath = renderPath.resolveSibling(
          insertSuffix(renderPath.getFileName().toString(), "_" + i));
      assertTrue("Missing render for angle " + i + ": " + anglePath, anglePath.toFile().exists());
      BufferedImage renderImg = ImageIO.read(anglePath.toFile());
      assertNotNull("Could not read render for angle " + i, renderImg);
      assertEquals("Render " + i + " width", 640, renderImg.getWidth());
      assertEquals("Render " + i + " height", 640, renderImg.getHeight());
    }

    for (int i = 0; i < 4; i++) {
      Path thumbAnglePath = thumbPath.resolveSibling(
          insertSuffix(thumbPath.getFileName().toString(), "_" + i));
      assertTrue("Missing thumbnail for angle " + i + ": " + thumbAnglePath, thumbAnglePath.toFile().exists());
      BufferedImage thumbImg = ImageIO.read(thumbAnglePath.toFile());
      assertNotNull("Could not read thumbnail for angle " + i, thumbImg);
      assertEquals("Thumbnail " + i + " width", 64, thumbImg.getWidth());
      assertEquals("Thumbnail " + i + " height", 64, thumbImg.getHeight());
    }

  }

  private static void deleteFileTree(Path renderPath, Path thumbPath, int angleCount) throws Exception {
    Files.deleteIfExists(renderPath);
    Files.deleteIfExists(thumbPath);
    for (int i = 0; i < angleCount; i++) {
      Files.deleteIfExists(renderPath.resolveSibling(
          insertSuffix(renderPath.getFileName().toString(), "_" + i)));
      Files.deleteIfExists(thumbPath.resolveSibling(
          insertSuffix(thumbPath.getFileName().toString(), "_" + i)));
    }
  }

  private static String insertSuffix(String filename, String suffix) {
    int dot = filename.lastIndexOf('.');
    return dot == -1 ? filename + suffix
                     : filename.substring(0, dot) + suffix + filename.substring(dot);
  }
}
