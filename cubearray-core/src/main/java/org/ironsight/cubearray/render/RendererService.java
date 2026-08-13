package org.ironsight.cubearray.render;

import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe access to the application's single OpenGL renderer.
 *
 * <p>Calls are serialized on the renderer thread. The native window remains alive while hidden, so
 * screenshots and interactive views reuse the same GLFW context.
 */
public final class RendererService {
  private static final RendererService INSTANCE = new RendererService();

  private final Object lifecycleLock = new Object();
  private final AtomicBoolean started = new AtomicBoolean();
  private volatile InstancedCubes renderer;
  private volatile Throwable startupFailure;

  private RendererService() {}

  public static RendererService getInstance() {
    return INSTANCE;
  }

  /** Shows the shared window with the supplied data. Safe from any thread. */
  public CompletableFuture<Void> show(CubeSetup setup) {
    if (setup == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("setup cannot be null"));
    }
    return submit(setup, () -> renderer.showSharedWindow(setup));
  }

  /** Shows the shared window if hidden, otherwise replaces its data without resetting the camera. */
  public CompletableFuture<Void> showOrReplace(CubeSetup setup) {
    if (setup == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("setup cannot be null"));
    }
    return submit(setup, () -> renderer.showOrReplaceSharedWindow(setup));
  }

  /** Replaces the displayed data on the next renderer iteration. Safe from any thread. */
  public CompletableFuture<Void> replaceData(CubeSetup setup) {
    if (setup == null) return CompletableFuture.completedFuture(null);
    return submit(setup, () -> renderer.replaceData(setup));
  }

  /** Hides the shared window without destroying its OpenGL resources. Safe from any thread. */
  public CompletableFuture<Void> hide() {
    if (!started.get()) return CompletableFuture.completedFuture(null);
    return submit(null, renderer::hideSharedWindow);
  }

  /** Renders a screenshot using the shared context. Safe from any thread. */
  public CompletableFuture<BufferedImage> renderScreenshot(
      CubeSetup setup, int width, int height, InstancedCubes.CameraState camera) {
    if (setup == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("setup cannot be null"));
    }
    CompletableFuture<BufferedImage> result = new CompletableFuture<>();
    ensureStarted(setup);
    Throwable failure = startupFailure;
    if (failure != null) return CompletableFuture.failedFuture(failure);
    renderer.enqueueSharedScreenshot(
        () -> {
          try {
            result.complete(renderer.renderSharedFrame(setup, width, height, camera));
          } catch (Throwable t) {
            result.completeExceptionally(t);
          }
        });
    return result;
  }

  /** Returns the most recently published camera state. Safe from any thread. */
  public CompletableFuture<InstancedCubes.CameraState> getCameraState() {
    InstancedCubes current = renderer;
    return CompletableFuture.completedFuture(current == null ? null : current.getCameraState());
  }

  /** Stops the renderer thread and destroys the shared native window. Safe from any thread. */
  public void shutdown() {
    if (!started.get()) return;
    renderer.requestSharedShutdown();
  }

  private CompletableFuture<Void> submit(CubeSetup initialSetup, Runnable command) {
    ensureStarted(initialSetup);
    Throwable failure = startupFailure;
    if (failure != null) return CompletableFuture.failedFuture(failure);
    CompletableFuture<Void> result = new CompletableFuture<>();
    renderer.enqueueSharedCommand(
        () -> {
          try {
            command.run();
            result.complete(null);
          } catch (Throwable t) {
            result.completeExceptionally(t);
          }
        });
    return result;
  }

  private void ensureStarted(CubeSetup initialSetup) {
    if (started.get()) return;
    synchronized (lifecycleLock) {
      if (started.get()) return;
      if (initialSetup == null) {
        throw new IllegalStateException("The renderer must be initialized with CubeSetup data");
      }
      renderer = new InstancedCubes(initialSetup);
      Thread thread =
          new Thread(
              () -> {
                try {
                  renderer.runSharedService();
                } catch (Throwable t) {
                  startupFailure = t;
                }
              },
              "shared-renderer");
      thread.setDaemon(true);
      started.set(true);
      thread.start();
    }
  }
}
