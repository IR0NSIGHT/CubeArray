package org.ironsight.cubearray.render;

import static org.junit.Assert.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33.*;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Requires desktop OpenGL support. Run separately with {@code mvn -pl cubearray-core
 * -Dtest=RendererServiceNativeIT test}: other render tests retain the singleton's process-global
 * GLFW lock for their JVM's lifetime.
 */
public class RendererServiceNativeIT {
  @Test(timeout = 30000)
  public void warmShowHideAndShowReuseOneWindowContextAndUploads() throws Exception {
    CubeSetup setup = RendererServiceTest.minimalSetup();
    TrackingRenderer renderer = new TrackingRenderer(setup);
    AtomicInteger creations = new AtomicInteger();
    RendererService service =
        new RendererService(
            data -> {
              creations.incrementAndGet();
              return renderer;
            });
    try {
      service.warmUp(setup).get(10, TimeUnit.SECONDS);
      Snapshot warm = snapshot(renderer);
      assertTrue(warm.window != 0);
      assertEquals(GLFW_FALSE, warm.visible);
      assertEquals(GL_NO_ERROR, warm.glError);
      assertEquals(1, renderer.instanceUploads);
      assertEquals(1, renderer.paletteUploads);

      service.warmUp(setup).get(5, TimeUnit.SECONDS);
      assertEquals(1, renderer.instanceUploads);
      assertEquals(1, renderer.paletteUploads);

      service.show(setup).get(5, TimeUnit.SECONDS);
      Snapshot visible = snapshot(renderer);
      assertEquals(GLFW_TRUE, visible.visible);
      assertEquals(warm.window, visible.window);
      assertSame(warm.capabilities, visible.capabilities);

      // Warming a different setup while visible must not replace the interactive scene.
      service.warmUp(RendererServiceTest.minimalSetup()).get(5, TimeUnit.SECONDS);
      assertEquals(GLFW_TRUE, snapshot(renderer).visible);
      assertEquals(1, renderer.instanceUploads);
      assertEquals(1, renderer.paletteUploads);

      service.hide().get(5, TimeUnit.SECONDS);
      assertEquals(GLFW_FALSE, snapshot(renderer).visible);
      BufferedImage screenshot =
          service.renderScreenshot(setup, 64, 48, null).get(5, TimeUnit.SECONDS);
      assertEquals(64, screenshot.getWidth());
      assertEquals(48, screenshot.getHeight());
      assertNotEquals(screenshot.getRGB(0, 0), screenshot.getRGB(32, 24));
      Snapshot afterScreenshot = snapshot(renderer);
      assertEquals(warm.viewportWidth, afterScreenshot.viewportWidth);
      assertEquals(warm.viewportHeight, afterScreenshot.viewportHeight);

      service.showOrReplace(setup).get(5, TimeUnit.SECONDS);
      Snapshot reopened = snapshot(renderer);
      assertEquals(GLFW_TRUE, reopened.visible);
      assertEquals(warm.window, reopened.window);
      assertSame(warm.capabilities, reopened.capabilities);
      assertEquals(GL_NO_ERROR, reopened.glError);
      assertEquals(1, creations.get());
      assertEquals(1, renderer.instanceUploads);
      assertEquals(1, renderer.paletteUploads);
    } finally {
      service.shutdownAsync().get(10, TimeUnit.SECONDS);
    }
    assertEquals(0L, renderer.contextAfterCleanup);
    assertFalse(renderer.owner.isAlive());
  }

  @Test(timeout = 30000)
  public void changingHiddenDataUploadsOnceWithoutRecreatingContext() throws Exception {
    CubeSetup initial = RendererServiceTest.minimalSetup();
    TrackingRenderer renderer = new TrackingRenderer(initial);
    RendererService service = new RendererService(data -> renderer);
    try {
      service.warmUp(initial).get(10, TimeUnit.SECONDS);
      Snapshot first = snapshot(renderer);
      CubeSetup replacement = RendererServiceTest.minimalSetup();
      service.warmUp(replacement).get(5, TimeUnit.SECONDS);
      Snapshot second = snapshot(renderer);
      assertEquals(GLFW_FALSE, second.visible);
      assertEquals(first.window, second.window);
      assertSame(first.capabilities, second.capabilities);
      assertEquals(GL_NO_ERROR, second.glError);
      assertEquals(2, renderer.instanceUploads);
      assertEquals(2, renderer.paletteUploads);
      service.show(replacement).get(5, TimeUnit.SECONDS);
      assertEquals(GLFW_TRUE, snapshot(renderer).visible);
      assertEquals(2, renderer.instanceUploads);
      assertEquals(2, renderer.paletteUploads);
    } finally {
      service.shutdownAsync().get(10, TimeUnit.SECONDS);
    }
  }

  @Test(timeout = 30000)
  public void partialInitializationFailureCleansContextAndRestoresHostCallback() throws Exception {
    GLFWErrorCallback hostCallback = GLFWErrorCallback.createPrint(System.err);
    GLFWErrorCallback previous = glfwSetErrorCallback(hostCallback);
    CubeSetup setup = RendererServiceTest.minimalSetup();
    TrackingRenderer renderer = new TrackingRenderer(setup);
    renderer.initializationFailure = new IllegalStateException("failure after GPU allocation");
    RendererService service = new RendererService(data -> renderer);
    try {
      try {
        service.warmUp(setup).get(10, TimeUnit.SECONDS);
        fail("Expected initialization failure");
      } catch (ExecutionException e) {
        assertSame(renderer.initializationFailure, e.getCause());
      }
      try {
        service.shutdownAsync().get(5, TimeUnit.SECONDS);
        fail("Expected failed termination");
      } catch (ExecutionException e) {
        assertSame(renderer.initializationFailure, e.getCause());
      }
      assertEquals(1, renderer.instanceUploads);
      assertEquals(1, renderer.paletteUploads);
      assertEquals(0L, renderer.contextAfterCleanup);
      assertFalse(renderer.owner.isAlive());
      GLFWErrorCallback restored = glfwSetErrorCallback(hostCallback);
      assertEquals(hostCallback.address(), restored.address());
    } finally {
      try {
        service.shutdownAsync().get(5, TimeUnit.SECONDS);
      } catch (ExecutionException expected) {
        assertSame(renderer.initializationFailure, expected.getCause());
      } finally {
        glfwSetErrorCallback(previous);
        hostCallback.free();
      }
    }
  }

  private static Snapshot snapshot(InstancedCubes renderer) throws Exception {
    CompletableFuture<Snapshot> result = new CompletableFuture<>();
    renderer.enqueueSharedCommand(() -> captureSnapshot(result));
    return result.get(5, TimeUnit.SECONDS);
  }

  private static void captureSnapshot(CompletableFuture<Snapshot> result) {
    try {
      long window = glfwGetCurrentContext();
      int[] viewport = new int[4];
      glGetIntegerv(GL_VIEWPORT, viewport);
      result.complete(
          new Snapshot(
              window,
              glfwGetWindowAttrib(window, GLFW_VISIBLE),
              GL.getCapabilities(),
              glGetError(),
              viewport[2],
              viewport[3]));
    } catch (Throwable t) {
      result.completeExceptionally(t);
    }
  }

  private record Snapshot(
      long window,
      int visible,
      GLCapabilities capabilities,
      int glError,
      int viewportWidth,
      int viewportHeight) {}

  private static class TrackingRenderer extends InstancedCubes {
    int instanceUploads;
    int paletteUploads;
    long contextAfterCleanup = -1;
    Thread owner;
    RuntimeException initializationFailure;

    TrackingRenderer(CubeSetup setup) {
      super(setup);
    }

    @Override
    void runSharedService() throws Exception {
      owner = Thread.currentThread();
      try {
        super.runSharedService();
      } finally {
        contextAfterCleanup = glfwGetCurrentContext();
      }
    }

    @Override
    void uploadInstanceData() {
      super.uploadInstanceData();
      instanceUploads++;
    }

    @Override
    void uploadPaletteTextures() {
      super.uploadPaletteTextures();
      paletteUploads++;
      if (initializationFailure != null) throw initializationFailure;
    }
  }
}
