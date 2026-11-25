package org.ironsight.cubearray.render;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.Test;

public class RendererServiceTest {
  static CubeSetup minimalSetup() {
    BufferedImage atlas = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    atlas.setRGB(0, 0, 0xFFFFFFFF);
    Vector4f[] uv = new Vector4f[6];
    for (int i = 0; i < uv.length; i++) uv[i] = new Vector4f(0, 0, 1, 1);
    return new CubeSetup(
        new Vector3f[] {new Vector3f()},
        new int[] {0},
        new Vector3f[] {new Vector3f(1)},
        new Vector3f[] {new Vector3f(1)},
        new Vector3f[] {new Vector3f()},
        new Vector3f[] {new Vector3f()},
        uv,
        atlas,
        new Vector3f(-1),
        new Vector3f(1),
        "lifecycle test",
        false);
  }

  @Test(timeout = 10000)
  public void concurrentWarmUpWaitsForStartupAndCreatesOneOwner() throws Exception {
    CubeSetup setup = minimalSetup();
    StubRenderer stub = new StubRenderer(setup);
    AtomicInteger creations = new AtomicInteger();
    RendererService service =
        new RendererService(
            data -> {
              creations.incrementAndGet();
              return stub;
            });
    try {
      List<CompletableFuture<Void>> requests = new ArrayList<>();
      requests.add(service.warmUp(setup));
      for (int i = 1; i < 20; i++) {
        requests.add(
            CompletableFuture.supplyAsync(() -> service.warmUp(setup)).thenCompose(f -> f));
      }
      assertTrue(stub.entered.await(2, TimeUnit.SECONDS));
      assertFalse(requests.get(0).isDone());
      stub.startAllowed.countDown();
      CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(3, TimeUnit.SECONDS);
      assertEquals(1, creations.get());
      assertEquals(20, stub.warmUps);
      assertEquals(0, stub.shows);
    } finally {
      stub.startAllowed.countDown();
      service.shutdownAsync().get(3, TimeUnit.SECONDS);
    }
    assertFalse(stub.owner.isAlive());
    assertEquals(1, stub.cleanups);
  }

  @Test(timeout = 10000)
  public void startupFailureFailsEveryQueueAndTermination() throws Exception {
    CubeSetup setup = minimalSetup();
    StubRenderer stub = new StubRenderer(setup);
    RuntimeException failure = new IllegalStateException("startup failed");
    stub.startupFailure = failure;
    RendererService service = new RendererService(data -> stub);
    CompletableFuture<Void> warm = service.warmUp(setup);
    CompletableFuture<Void> show = service.show(setup);
    CompletableFuture<Void> replace = service.replaceData(setup);
    CompletableFuture<BufferedImage> screenshot = service.renderScreenshot(setup, 8, 8, null);
    stub.startAllowed.countDown();

    for (CompletableFuture<?> request : List.of(warm, show, replace, screenshot)) {
      assertSame(failure, failureOf(request));
    }
    assertSame(failure, failureOf(service.shutdownAsync()));
    assertSame(failure, failureOf(service.warmUp(setup)));
    assertEquals(1, stub.cleanups);
    assertFalse(stub.owner.isAlive());
  }

  @Test(timeout = 10000)
  public void shutdownWaitsForCleanupAndFailsUnexecutedRequests() throws Exception {
    CubeSetup setup = minimalSetup();
    StubRenderer stub = new StubRenderer(setup);
    stub.cleanupAllowed = new CountDownLatch(1);
    RendererService service = new RendererService(data -> stub);
    CompletableFuture<Void> warm = service.warmUp(setup);
    CompletableFuture<BufferedImage> screenshot = service.renderScreenshot(setup, 8, 8, null);
    CompletableFuture<Void> shutdown = service.shutdownAsync();
    CompletableFuture<Void> anotherShutdown = service.shutdownAsync();
    try {
      assertTrue(failureOf(service.show(setup)) instanceof IllegalStateException);
      stub.startAllowed.countDown();
      warm.get(3, TimeUnit.SECONDS);
      assertTrue(stub.cleaning.await(2, TimeUnit.SECONDS));
      assertFalse(shutdown.isDone());
      assertFalse(anotherShutdown.isDone());
    } finally {
      stub.startAllowed.countDown();
      stub.cleanupAllowed.countDown();
      shutdown.get(3, TimeUnit.SECONDS);
    }
    anotherShutdown.get(3, TimeUnit.SECONDS);
    assertTrue(failureOf(screenshot) instanceof IllegalStateException);
    assertEquals(1, stub.cleanups);
    assertFalse(stub.owner.isAlive());
    assertTrue(failureOf(service.hide()) instanceof IllegalStateException);
    assertTrue(failureOf(service.replaceData(null)) instanceof IllegalStateException);
  }

  @Test(timeout = 10000)
  public void unexpectedLoopFailureFailsDeferredWorkAndRejectsLaterRequests() throws Exception {
    CubeSetup setup = minimalSetup();
    StubRenderer stub = new StubRenderer(setup);
    RendererService service = new RendererService(data -> stub);
    CompletableFuture<Void> warm = service.warmUp(setup);
    stub.startAllowed.countDown();
    warm.get(3, TimeUnit.SECONDS);
    CompletableFuture<BufferedImage> deferred = service.renderScreenshot(setup, 8, 8, null);
    RuntimeException failure = new IllegalStateException("render loop failed");
    stub.commands.add(
        () -> {
          throw failure;
        });

    assertSame(failure, failureOf(deferred));
    assertSame(failure, failureOf(service.shutdownAsync()));
    assertSame(failure, failureOf(service.show(setup)));
    assertEquals(1, stub.cleanups);
    assertFalse(stub.owner.isAlive());
  }

  @Test(timeout = 10000)
  public void shutdownBeforeFirstUseIsTerminalAndCancellationDoesNotCancelCleanup()
      throws Exception {
    AtomicInteger creations = new AtomicInteger();
    RendererService unused =
        new RendererService(
            setup -> {
              creations.incrementAndGet();
              return new StubRenderer(setup);
            });
    unused.hide().get(2, TimeUnit.SECONDS);
    unused.shutdown();
    unused.shutdownAsync().get(2, TimeUnit.SECONDS);
    assertTrue(failureOf(unused.warmUp(minimalSetup())) instanceof IllegalStateException);
    assertEquals(0, creations.get());

    StubRenderer stub = new StubRenderer(minimalSetup());
    RendererService service = new RendererService(setup -> stub);
    service.warmUp(minimalSetup());
    CompletableFuture<Void> cancelled = service.shutdownAsync();
    assertTrue(cancelled.cancel(false));
    stub.startAllowed.countDown();
    service.shutdownAsync().get(3, TimeUnit.SECONDS);
    assertEquals(1, stub.cleanups);
    assertFalse(stub.owner.isAlive());
  }

  @Test(timeout = 10000)
  public void invalidArgumentsDoNotStartRendererAndCommandFailureDoesNotStopIt() throws Exception {
    CubeSetup setup = minimalSetup();
    StubRenderer stub = new StubRenderer(setup);
    AtomicInteger creations = new AtomicInteger();
    RendererService service =
        new RendererService(
            data -> {
              creations.incrementAndGet();
              return stub;
            });
    assertTrue(failureOf(service.warmUp(null)) instanceof IllegalArgumentException);
    assertTrue(failureOf(service.show(null)) instanceof IllegalArgumentException);
    assertTrue(failureOf(service.showOrReplace(null)) instanceof IllegalArgumentException);
    assertTrue(
        failureOf(service.renderScreenshot(null, 8, 8, null)) instanceof IllegalArgumentException);
    assertTrue(
        failureOf(service.renderScreenshot(setup, 0, 8, null)) instanceof IllegalArgumentException);
    assertEquals(0, creations.get());
    stub.commandFailure = new IllegalArgumentException("bad warm-up");
    try {
      CompletableFuture<Void> warm = service.warmUp(setup);
      stub.startAllowed.countDown();
      assertSame(stub.commandFailure, failureOf(warm));
      service.show(setup).get(3, TimeUnit.SECONDS);
      assertEquals(1, stub.shows);
      assertTrue(failureOf(service.show(null)) instanceof IllegalArgumentException);
    } finally {
      service.shutdownAsync().get(3, TimeUnit.SECONDS);
    }
  }

  private static Throwable failureOf(CompletableFuture<?> future) throws Exception {
    try {
      future.get(3, TimeUnit.SECONDS);
      fail("Expected exceptional completion");
      return null;
    } catch (ExecutionException e) {
      return e.getCause();
    }
  }

  private static class StubRenderer extends InstancedCubes {
    final BlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch startAllowed = new CountDownLatch(1);
    final CountDownLatch cleaning = new CountDownLatch(1);
    CountDownLatch cleanupAllowed = new CountDownLatch(0);
    volatile Thread owner;
    RuntimeException startupFailure;
    RuntimeException commandFailure;
    int warmUps;
    int shows;
    int cleanups;
    boolean stopped;

    StubRenderer(CubeSetup setup) {
      super(setup);
    }

    @Override
    void runSharedService() throws Exception {
      owner = Thread.currentThread();
      entered.countDown();
      try {
        startAllowed.await();
        if (startupFailure != null) throw startupFailure;
        while (!stopped) commands.take().run();
      } finally {
        cleaning.countDown();
        cleanupAllowed.await();
        cleanups++;
      }
    }

    @Override
    void enqueueSharedCommand(Runnable command) {
      commands.add(command);
    }

    @Override
    void enqueueSharedPriorityCommand(Runnable command) {
      commands.add(command);
    }

    @Override
    void enqueueSharedScreenshot(Runnable command) {
      // Screenshots can remain deferred while the interactive viewer is visible.
    }

    @Override
    void requestSharedShutdown() {
      commands.add(() -> stopped = true);
    }

    @Override
    void warmUpSharedRenderer(CubeSetup setup) {
      assertSame(owner, Thread.currentThread());
      if (commandFailure != null) throw commandFailure;
      warmUps++;
    }

    @Override
    void showSharedWindow(CubeSetup setup) {
      assertSame(owner, Thread.currentThread());
      shows++;
    }

    @Override
    public void replaceData(CubeSetup setup) {
      assertSame(owner, Thread.currentThread());
    }
  }
}
