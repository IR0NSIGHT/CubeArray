package org.ironsight.cubearray.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.render.InstancedCubes;
import org.ironsight.cubearray.render.RendererService;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.schematic.SchemReader;
import org.ironsight.cubearray.edit.BlockReplacer;
import org.pepsoft.worldpainter.DefaultCustomObjectProvider;
import org.pepsoft.worldpainter.objects.WPObject;
import org.pepsoft.worldpainter.objects.WPObject;
import pitheguy.schemconvert.converter.Schematic;

public class ReplacerPreview {

  public interface Callback {
    //runs on the swing gui thread
    void onPreviewReady(File file);
  }

  private final int fullSize;
  private final int iconSize;
  private final Map<File, Schematic> loadedSchematics;
  private final Supplier<Map<String, String>> replacementsSupplier;
  private final ConcurrentMap<File, ImageIcon> afterPreviews = new ConcurrentHashMap<>();
  private final ScheduledExecutorService renderExecutor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "preview-renderer");
            t.setDaemon(true);
           return t;
         });
  private final ScheduledExecutorService interactiveScheduleExecutor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "replacement-interactive-scheduler");
            t.setDaemon(true);
            return t;
          });
  private final ExecutorService interactiveExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "replacement-interactive-preview");
            t.setDaemon(true);
            return t;
          });
  private final RendererService renderer = RendererService.getInstance();
  private final AtomicLong renderGeneration = new AtomicLong();
  private volatile CubeSetup latestInteractiveSetup;
  private boolean interactiveStarted;
  private ScheduledFuture<?> pendingRender;
  private Callback callback;

  public ReplacerPreview(
      int fullSize,
      int iconSize,
      Map<File, Schematic> loadedSchematics,
      Supplier<Map<String, String>> replacementsSupplier) {
    this.fullSize = fullSize;
    this.iconSize = iconSize;
    this.loadedSchematics = loadedSchematics;
    this.replacementsSupplier = replacementsSupplier;
  }

  public void setCallback(Callback callback) {
    this.callback = callback;
  }

  public ImageIcon getFullPreview(File file) {
    return afterPreviews.get(file);
  }

  public ImageIcon getIconPreview(File file) {
    ImageIcon full = afterPreviews.get(file);
    if (full == null) return null;
    return new ImageIcon(
        full.getImage().getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH));
  }

  public void clearCache() {
    afterPreviews.clear();
  }

  public void flagRerender() {
    long generation = renderGeneration.incrementAndGet();
    if (pendingRender != null) pendingRender.cancel(false);
    pendingRender =
        interactiveScheduleExecutor.schedule(() -> renderAll(generation), 1, TimeUnit.SECONDS);
    System.out.println("FLAGGED RE RENDER");

  }

  private void renderAll(long generation) {
    System.out.println("DO FULL RENDER");

    Map<String, String> replacements = safeGetReplacements();
    if (replacements == null) return;
    Map<String, String> snapshot = Map.copyOf(replacements);
    interactiveExecutor.execute(
        () -> {
          if (updateInteractivePreview(snapshot, generation)
              && generation == renderGeneration.get()) {
            renderExecutor.execute(() -> renderThumbnails(snapshot, generation));
          }
        });
    System.out.println(replacements.entrySet().stream()
            .map(e -> BlockReplacer.stripBlockId(e.getKey()) + " -> " +BlockReplacer.stripBlockId(e.getValue()) )
            .distinct()
            .toList())            ;
  }

  private boolean updateInteractivePreview(Map<String, String> replacements, long generation) {
    if (generation != renderGeneration.get()) return false;
    try {
      ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
      CubeSetup setup = prepareInteractiveSetup(replacements);
      if (setup == null || generation != renderGeneration.get()) return false;
      latestInteractiveSetup = setup;
      if (!interactiveStarted) {
        renderer.show(setup).join();
        interactiveStarted = true;
      } else {
        renderer.replaceData(setup).join();
      }
      return true;
    } catch (Exception e) {
      System.err.println("Interactive replacement preview failed: " + e.getMessage());
      return false;
    }
  }

  private void renderThumbnails(Map<String, String> replacements, long generation) {
    for (Map.Entry<File, Schematic> entry : loadedSchematics.entrySet()) {
      if (generation != renderGeneration.get()) return;
      renderOne(entry.getKey(), entry.getValue(), replacements);
    }
  }

  /** Reopens the shared renderer with the latest replacement setup without blocking the EDT. */
  public CompletableFuture<Void> openInteractive() {
    CubeSetup setup = latestInteractiveSetup;
    if (setup == null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("The replacement preview is not ready yet"));
    }
    CompletableFuture<Void> result = new CompletableFuture<>();
    try {
      interactiveExecutor.execute(
          () -> {
            try {
              renderer.show(setup).join();
              interactiveStarted = true;
              result.complete(null);
            } catch (Throwable error) {
              result.completeExceptionally(error);
            }
          });
    } catch (RejectedExecutionException error) {
      result.completeExceptionally(error);
    }
    return result;
  }

  private CubeSetup prepareInteractiveSetup(Map<String, String> replacements) throws Exception {
    List<WPObject> objects = new java.util.ArrayList<>();
    List<Path> temporarySchematics = new java.util.ArrayList<>();
    try {
      for (Schematic schematic : loadedSchematics.values()) {
        Schematic replaced = BlockReplacer.replace(schematic, replacements);
        Path temporary = Files.createTempFile("replace_interactive_", ".schem");
        temporarySchematics.add(temporary);
        BlockReplacer.write(replaced, temporary.toFile());
        objects.add(new DefaultCustomObjectProvider().loadObject(temporary.toFile()));
      }
      return objects.isEmpty() ? null : SchemReader.prepareData(objects);
    } finally {
      for (Path temporary : temporarySchematics) Files.deleteIfExists(temporary);
    }
  }

  private Map<String, String> safeGetReplacements() {
    var ref = new AtomicReference<Map<String, String>>();
    try {
      SwingUtilities.invokeAndWait(() -> ref.set(replacementsSupplier.get()));
    } catch (Exception e) {
      return null;
    }
    return ref.get();
  }

  private void renderOne(File file, Schematic schematic, Map<String, String> replacements) {
    try {
      Schematic replaced = BlockReplacer.replace(schematic, replacements);

      Path tmpSchem = Files.createTempFile("replace_preview_", ".schem");
      try {
        BlockReplacer.write(replaced, tmpSchem.toFile());

        ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
        WPObject wpObj = new DefaultCustomObjectProvider().loadObject(tmpSchem.toFile());
        CubeSetup setup = SchemReader.prepareData(List.of(wpObj));
        Path tmpRender = Files.createTempFile("render_preview_", ".png");
        try {
          InstancedCubes.renderToFile(setup, tmpRender, fullSize, fullSize);
          BufferedImage img = ImageIO.read(tmpRender.toFile());
          afterPreviews.put(file, new ImageIcon(img));
          if (callback != null) {
            SwingUtilities.invokeLater(() -> {
              System.out.println("PREVIEW READY FOR " + file.getName());
              callback.onPreviewReady(file);
            });
          }
        } finally {
          Files.deleteIfExists(tmpRender);
        }
      } finally {
        Files.deleteIfExists(tmpSchem);
      }
    } catch (Exception e) {
      String msg = e.getMessage();
      if (msg == null) msg = e.getClass().getSimpleName();
      System.err.println("Preview render failed for " + file.getName() + ": " + msg);
    }
  }

  public void dispose() {
    renderGeneration.incrementAndGet();
    if (pendingRender != null) pendingRender.cancel(false);
    renderExecutor.shutdown();
    interactiveScheduleExecutor.shutdownNow();
    interactiveExecutor.shutdownNow();
  }
}
