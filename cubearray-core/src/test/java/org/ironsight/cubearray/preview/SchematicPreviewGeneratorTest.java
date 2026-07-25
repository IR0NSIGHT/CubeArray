package org.ironsight.cubearray.preview;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.preview.SchematicPreviewGenerator.PriorityTask;
import org.ironsight.cubearray.schematic.SchemReader;
import org.junit.Test;
import org.pepsoft.worldpainter.objects.WPObject;

public class SchematicPreviewGeneratorTest {

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
}
