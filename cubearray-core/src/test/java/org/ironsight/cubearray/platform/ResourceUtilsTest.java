package org.ironsight.cubearray.platform;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ResourceUtilsTest {

  private File tempSchemFile;
  private final List<Path> createdFiles = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    tempSchemFile = File.createTempFile("test_schematic_", ".schem");
    tempSchemFile.deleteOnExit();
  }

  @After
  public void tearDown() throws IOException {
    for (Path p : createdFiles) {
      Files.deleteIfExists(p);
    }
    if (tempSchemFile != null && tempSchemFile.exists()) {
      tempSchemFile.delete();
    }
  }

  @Test
  public void deleteRenderFiles_removesAllAngleVariantsAndThumbnails() throws IOException {
    Path renderPath = ResourceUtils.getRenderPathForFile(tempSchemFile);
    Path thumbPath = ResourceUtils.getThumbPathForFile(tempSchemFile);
    Files.createDirectories(renderPath.getParent());

    String rName = renderPath.getFileName().toString();
    int rDot = rName.lastIndexOf('.');
    String rBase = rName.substring(0, rDot);
    String rExt = rName.substring(rDot);

    String tName = thumbPath.getFileName().toString();
    int tDot = tName.lastIndexOf('.');
    String tBase = tName.substring(0, tDot);
    String tExt = tName.substring(tDot);

    Files.writeString(renderPath, "dummy render");
    createdFiles.add(renderPath);
    for (int i = 0; i < 4; i++) {
      Path angle = renderPath.resolveSibling(rBase + "_" + i + rExt);
      Files.writeString(angle, "dummy render angle " + i);
      createdFiles.add(angle);
    }
    Files.writeString(thumbPath, "dummy thumb");
    createdFiles.add(thumbPath);
    for (int i = 0; i < 4; i++) {
      Path angleThumb = thumbPath.resolveSibling(tBase + "_" + i + tExt);
      Files.writeString(angleThumb, "dummy thumb angle " + i);
      createdFiles.add(angleThumb);
    }

    assertTrue("base render should exist before delete", renderPath.toFile().exists());
    assertTrue("thumb should exist before delete", thumbPath.toFile().exists());

    ResourceUtils.deleteRenderFiles(tempSchemFile);

    assertFalse("base render should be deleted", renderPath.toFile().exists());
    assertFalse("thumb should be deleted", thumbPath.toFile().exists());
    for (int i = 0; i < 4; i++) {
      Path angle = renderPath.resolveSibling(rBase + "_" + i + rExt);
      assertFalse("angle render " + i + " should be deleted", angle.toFile().exists());
      Path angleThumb = thumbPath.resolveSibling(tBase + "_" + i + tExt);
      assertFalse("angle thumb " + i + " should be deleted", angleThumb.toFile().exists());
    }
    assertTrue("schematic file should not be deleted", tempSchemFile.exists());
  }

  @Test
  public void deleteRenderFiles_doesNotDeleteUnrelatedFiles() throws IOException {
    Path renderPath = ResourceUtils.getRenderPathForFile(tempSchemFile);
    Files.createDirectories(renderPath.getParent());
    Files.writeString(renderPath, "dummy render");
    createdFiles.add(renderPath);

    Path otherFile = renderPath.getParent().resolve("other_file.png");
    Files.writeString(otherFile, "other");
    createdFiles.add(otherFile);

    ResourceUtils.deleteRenderFiles(tempSchemFile);

    assertFalse("render should be deleted", renderPath.toFile().exists());
    assertTrue("unrelated file should remain", otherFile.toFile().exists());
  }

  @Test
  public void deleteRenderFiles_handlesMissingFilesGracefully() throws IOException {
    Path renderPath = ResourceUtils.getRenderPathForFile(tempSchemFile);
    assertFalse("render should not exist", renderPath.toFile().exists());
    ResourceUtils.deleteRenderFiles(tempSchemFile);
  }

  @Test
  public void deleteRenderFiles_worksWithSingleRenderNoAngles() throws IOException {
    Path renderPath = ResourceUtils.getRenderPathForFile(tempSchemFile);
    Path thumbPath = ResourceUtils.getThumbPathForFile(tempSchemFile);
    Files.createDirectories(renderPath.getParent());

    Files.writeString(renderPath, "single render");
    createdFiles.add(renderPath);
    Files.writeString(thumbPath, "single thumb");
    createdFiles.add(thumbPath);

    assertTrue("render should exist before delete", renderPath.toFile().exists());
    ResourceUtils.deleteRenderFiles(tempSchemFile);
    assertFalse("render should be deleted", renderPath.toFile().exists());
    assertFalse("thumb should be deleted", thumbPath.toFile().exists());
    assertTrue("schematic should remain", tempSchemFile.exists());
  }

  @Test
  public void needsNewRender_returnsTrueAfterDeleteRenderFiles() throws IOException {
    Path renderPath = ResourceUtils.getRenderPathForFile(tempSchemFile);
    Path thumbPath = ResourceUtils.getThumbPathForFile(tempSchemFile);
    Files.createDirectories(renderPath.getParent());
    Files.writeString(renderPath, "stale render");
    createdFiles.add(renderPath);
    Files.writeString(thumbPath, "stale thumb");
    createdFiles.add(thumbPath);

    assertFalse("needsNewRender should be false before delete (render + thumb exist)",
        ResourceUtils.needsNewRender(tempSchemFile));

    ResourceUtils.deleteRenderFiles(tempSchemFile);

    assertTrue("needsNewRender should be true after delete (render gone)",
        ResourceUtils.needsNewRender(tempSchemFile));
  }
}
