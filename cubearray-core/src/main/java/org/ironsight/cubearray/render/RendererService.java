package org.ironsight.cubearray.render;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Thread-safe access to the application's single OpenGL renderer.
 *
 * <p>Calls are serialized on the renderer thread. The native window remains alive while hidden, so
 * screenshots and interactive views reuse the same GLFW context. Do not block the Swing event
 * thread or a renderer completion callback waiting for another renderer operation.
 *
 * <pre>{@code
 * RendererService renderer = RendererService.getInstance();
 * renderer.warmUp(setup)                 // during application startup, no visible window
 *     .thenCompose(ready -> renderer.show(setup)); // later, reuse the warmed renderer
 * // At application exit: renderer.shutdownAsync()
 * }</pre>
 */
public final class RendererService {
  private static final RendererService INSTANCE = new RendererService(InstancedCubes::new);

  private final Object lifecycleLock = new Object();
  private final Function<CubeSetup, InstancedCubes> rendererFactory;
  private final Set<CompletableFuture<?>> pending = new HashSet<>();
  private final CompletableFuture<Void> terminated = new CompletableFuture<>();
  private volatile InstancedCubes renderer;
  private Thread rendererThread;
  private boolean stopping;
  private Throwable failure;

  // Allows lifecycle tests to substitute a renderer without loading native libraries.
  RendererService(Function<CubeSetup, InstancedCubes> rendererFactory) {
    this.rendererFactory = rendererFactory;
  }

  public static RendererService getInstance() {
    return INSTANCE;
  }

  /**
   * Initializes the shared renderer and draws one frame without showing a window. Completes after
   * shaders, data uploads, and the hidden frame are ready. Does not disturb an already-visible
   * view. Repeated calls reuse the same renderer and context; no screenshot or file is produced.
   */
  public CompletableFuture<Void> warmUp(CubeSetup setup) {
    return submit(setup, Queue.COMMAND, () -> renderer.warmUpSharedRenderer(setup));
  }

  /** Shows the shared window with the supplied data, resetting the camera. Safe from any thread. */
  public CompletableFuture<Void> show(CubeSetup setup) {
    return submit(setup, Queue.COMMAND, () -> renderer.showSharedWindow(setup));
  }

  /**
   * Shows the shared window if hidden, otherwise replaces its data without resetting the camera.
   */
  public CompletableFuture<Void> showOrReplace(CubeSetup setup) {
    return submit(setup, Queue.COMMAND, () -> renderer.showOrReplaceSharedWindow(setup));
  }

  /** Replaces the displayed data on the next renderer iteration. Safe from any thread. */
  public CompletableFuture<Void> replaceData(CubeSetup setup) {
    if (setup == null) {
      synchronized (lifecycleLock) {
        return stopping
            ? CompletableFuture.failedFuture(
                new IllegalStateException("The renderer has shut down"))
            : CompletableFuture.completedFuture(null);
      }
    }
    return submit(setup, Queue.PRIORITY, () -> renderer.replaceData(setup));
  }

  /** Hides the shared window without destroying its OpenGL resources. Safe from any thread. */
  public CompletableFuture<Void> hide() {
    synchronized (lifecycleLock) {
      if (renderer == null && !stopping) return CompletableFuture.completedFuture(null);
      return submitResult(null, Queue.COMMAND, this::hideWindow);
    }
  }

  private Void hideWindow() {
    renderer.hideSharedWindow();
    return null;
  }

  /** Renders a screenshot using the shared context. Safe from any thread. */
  public CompletableFuture<BufferedImage> renderScreenshot(
      CubeSetup setup, int width, int height, InstancedCubes.CameraState camera) {
    if (setup == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("setup cannot be null"));
    }
    if (width <= 0 || height <= 0) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Screenshot dimensions must be positive"));
    }
    return submitResult(
        setup, Queue.SCREENSHOT, () -> renderer.renderSharedFrame(setup, width, height, camera));
  }

  /** Returns the most recently published camera state. Safe from any thread. */
  public CompletableFuture<InstancedCubes.CameraState> getCameraState() {
    InstancedCubes current = renderer;
    return CompletableFuture.completedFuture(current == null ? null : current.getCameraState());
  }

  /**
   * Requests terminal shutdown without waiting. Use {@link #shutdownAsync()} to observe cleanup.
   */
  public void shutdown() {
    shutdownAsync();
  }

  /**
   * Stops the renderer and completes after native cleanup and renderer-thread termination. Shutdown
   * is terminal, even before first use; subsequent work is rejected. Accepted but unexecuted
   * requests fail rather than hanging. Repeated calls observe the same outcome.
   */
  public CompletableFuture<Void> shutdownAsync() {
    boolean neverStarted;
    synchronized (lifecycleLock) {
      if (stopping) return terminated.copy();
      stopping = true;
      neverStarted = renderer == null;
      if (!neverStarted) renderer.requestSharedShutdown();
    }
    if (neverStarted) terminated.complete(null);
    // A caller cancelling its observation must not cancel the service's termination signal.
    return terminated.copy();
  }

  private CompletableFuture<Void> submit(CubeSetup setup, Queue queue, Runnable command) {
    if (setup == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("setup cannot be null"));
    }
    return submitResult(setup, queue, () -> runCommand(command));
  }

  private static Void runCommand(Runnable command) {
    command.run();
    return null;
  }

  private <T> CompletableFuture<T> submitResult(CubeSetup setup, Queue queue, Supplier<T> command) {
    synchronized (lifecycleLock) {
      if (stopping) {
        return CompletableFuture.failedFuture(
            failure != null ? failure : new IllegalStateException("The renderer has shut down"));
      }
      if (setup == null && renderer == null) {
        return CompletableFuture.failedFuture(new IllegalArgumentException("setup cannot be null"));
      }
      CompletableFuture<T> result = new CompletableFuture<>();
      pending.add(result);
      result.whenComplete((value, error) -> forget(result));
      try {
        ensureStarted(setup);
        Runnable task = () -> execute(command, result);
        switch (queue) {
          case COMMAND -> renderer.enqueueSharedCommand(task);
          case PRIORITY -> renderer.enqueueSharedPriorityCommand(task);
          case SCREENSHOT -> renderer.enqueueSharedScreenshot(task);
        }
      } catch (Throwable t) {
        pending.remove(result);
        result.completeExceptionally(t);
      }
      return result;
    }
  }

  private void forget(CompletableFuture<?> result) {
    synchronized (lifecycleLock) {
      pending.remove(result);
    }
  }

  private static <T> void execute(Supplier<T> command, CompletableFuture<T> result) {
    if (result.isDone()) return;
    try {
      result.complete(command.get());
    } catch (Throwable t) {
      result.completeExceptionally(t);
    }
  }

  // Called with lifecycleLock held, so concurrent first use creates exactly one owner thread.
  private void ensureStarted(CubeSetup initialSetup) {
    if (renderer != null) return;
    try {
      renderer = rendererFactory.apply(initialSetup);
      rendererThread = new Thread(this::runRenderer, "shared-renderer");
      rendererThread.setDaemon(true);
      // Start the observer first, but let it acquire lifecycleLock before joining the owner. This
      // also covers a failure to start the owner thread without leaving requests unresolved.
      Thread.ofVirtual().name("shared-renderer-termination").start(this::awaitTermination);
      rendererThread.start();
    } catch (Throwable t) {
      stopping = true;
      failure = t;
      terminated.completeExceptionally(t);
      throw t;
    }
  }

  private void runRenderer() {
    try {
      renderer.runSharedService();
    } catch (Throwable t) {
      synchronized (lifecycleLock) {
        failure = t;
      }
    }
  }

  private void awaitTermination() {
    Thread thread;
    synchronized (lifecycleLock) {
      // ensureStarted has either started the owner or recorded its startup failure.
      thread = rendererThread;
    }
    boolean interrupted = false;
    while (thread.isAlive()) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    List<CompletableFuture<?>> unfinished;
    Throwable cause;
    synchronized (lifecycleLock) {
      stopping = true;
      cause = failure;
      unfinished = List.copyOf(pending);
      pending.clear();
    }
    Throwable rejection =
        cause != null
            ? cause
            : new IllegalStateException("The renderer stopped before executing the request");
    for (CompletableFuture<?> result : unfinished) result.completeExceptionally(rejection);
    if (cause == null) terminated.complete(null);
    else terminated.completeExceptionally(cause);
    if (interrupted) Thread.currentThread().interrupt();
  }

  private enum Queue {
    COMMAND,
    PRIORITY,
    SCREENSHOT
  }
}
