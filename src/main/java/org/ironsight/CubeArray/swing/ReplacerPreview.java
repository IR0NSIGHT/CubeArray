package org.ironsight.CubeArray.swing;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import org.ironsight.CubeArray.InstancedCubes;
import org.ironsight.CubeArray.ResourceUtils;
import org.ironsight.CubeArray.SchemReader;
import org.ironsight.schemEdit.BlockReplacer;
import org.pepsoft.worldpainter.DefaultCustomObjectProvider;
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
    if (pendingRender != null) pendingRender.cancel(false);
    pendingRender = renderExecutor.schedule(this::renderAll, 1, TimeUnit.SECONDS);
    System.out.println("FLAGGED RE RENDER");

  }

  private void renderAll() {
    System.out.println("DO FULL RENDER");

    Map<String, String> replacements = safeGetReplacements();
    if (replacements == null) return;
    System.out.println(replacements.entrySet().stream()
            .map(e -> BlockReplacer.stripBlockId(e.getKey()) + " -> " +BlockReplacer.stripBlockId(e.getValue()) )
            .distinct()
            .toList())            ;
    for (Map.Entry<File, Schematic> entry : loadedSchematics.entrySet()) {
      renderOne(entry.getKey(), entry.getValue(), replacements);

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
        SchemReader.CubeSetup setup = SchemReader.prepareData(List.of(wpObj));
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
    if (pendingRender != null) pendingRender.cancel(false);
    renderExecutor.shutdown();
  }
}
