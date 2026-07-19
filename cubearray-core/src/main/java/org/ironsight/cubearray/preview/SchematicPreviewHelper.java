package org.ironsight.cubearray.preview;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.ironsight.cubearray.platform.AppLogger;
import org.ironsight.cubearray.platform.ResourceUtils;
import org.ironsight.cubearray.render.CubeSetup;
import org.ironsight.cubearray.render.InstancedCubes;
import org.ironsight.cubearray.schematic.SchemReader;
import org.pepsoft.worldpainter.objects.WPObject;

public class SchematicPreviewHelper {
  private static final Logger logger = AppLogger.get(SchematicPreviewHelper.class);
  private static final SchematicPreviewHelper INSTANCE = new SchematicPreviewHelper();

  private final ExecutorService renderExecutor =
      new ThreadPoolExecutor(
          1,
          1,
          0L,
          TimeUnit.MILLISECONDS,
          new PriorityBlockingQueue<>(),
          r -> {
            Thread t = new Thread(r, "render-worker");
            t.setDaemon(true);
            return t;
          });

  private static class PriorityTask implements Runnable, Comparable<PriorityTask> {
    private final Runnable task;
    private final long priority;

    PriorityTask(Runnable task, long priority) {
      this.task = task;
      this.priority = priority;
    }

    @Override
    public void run() {
      task.run();
    }

    @Override
    public int compareTo(PriorityTask other) {
      return Long.compare(this.priority, other.priority);
    }
  }

  private final Map<String, Icon> iconCache = new HashMap<>();

  private Consumer<Integer> pendingRenderCountChangedCallback;

  private SchematicPreviewHelper() {}

  public void setPendingRenderCountChangedCallback(Consumer<Integer> callback) {
    this.pendingRenderCountChangedCallback = callback;
  }

  private void firePendingRenderCountChanged() {
    if (pendingRenderCountChangedCallback != null) {
      ThreadPoolExecutor tpe = (ThreadPoolExecutor) renderExecutor;
      int count = tpe.getQueue().size() + (tpe.getActiveCount() > 0 ? 1 : 0);
      pendingRenderCountChangedCallback.accept(count);
    }
  }

  public static SchematicPreviewHelper getInstance() {
    return INSTANCE;
  }

  public Icon getIcon(File file) {
    return iconCache.computeIfAbsent(
        file.getAbsolutePath(),
        k -> {
          Path thumbPath = ResourceUtils.getThumbPathForFile(file);
          if (thumbPath.toFile().exists()) {
            return new ImageIcon(thumbPath.toString());
          }
          Path renderPath = ResourceUtils.getRenderPathForFile(file);
          if (renderPath.toFile().exists()) {
            return new ImageIcon(
                new ImageIcon(renderPath.toString())
                    .getImage()
                    .getScaledInstance(64, 64, Image.SCALE_SMOOTH));
          }
          return generatePlaceholderIcon(file);
        });
  }

  public void invalidateIcon(File file) {
    iconCache.remove(file.getAbsolutePath());
  }

  public void showPreviewDialog(File file, Component parent) {
    Path renderPath = ResourceUtils.getRenderPathForFile(file);
    if (!Files.exists(renderPath)) {
      JOptionPane.showMessageDialog(
          parent, "No render available yet.", file.getName(), JOptionPane.PLAIN_MESSAGE);
      return;
    }
    ImageIcon image =
        new ImageIcon(
            new ImageIcon(renderPath.toString())
                .getImage()
                .getScaledInstance(640, 640, Image.SCALE_SMOOTH));

    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.add(new JLabel(image), BorderLayout.CENTER);

    JButton openBtn = new JButton("Open render in folder");
    openBtn.addActionListener(e -> ResourceUtils.revealFileInFolder(renderPath));
    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
    btnPanel.add(openBtn);
    panel.add(btnPanel, BorderLayout.SOUTH);

    JOptionPane.showMessageDialog(parent, panel, file.getName(), JOptionPane.PLAIN_MESSAGE);
  }

  public void render(File file, WPObject obj, Runnable onComplete) {
    if (file == null || obj == null) return;
    if (!ResourceUtils.needsNewRender(file)) {
      if (onComplete != null) onComplete.run();
      return;
    }
    firePendingRenderCountChanged();
    renderExecutor.execute(
        new PriorityTask(
            () -> {
              try {
                ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
                CubeSetup setup = SchemReader.prepareData(List.of(obj));
                if (setup == null) return;
                Path renderPath = ResourceUtils.getRenderPathForFile(file);
                Files.createDirectories(renderPath.getParent());
                InstancedCubes.renderToFile(setup, renderPath, 640, 640);
                try {
                  BufferedImage full = ImageIO.read(renderPath.toFile());
                  BufferedImage thumb = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                  Graphics2D g = thumb.createGraphics();
                  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                  g.drawImage(full, 0, 0, 64, 64, null);
                  g.dispose();
                  ImageIO.write(thumb, "PNG", ResourceUtils.getThumbPathForFile(file).toFile());
                } catch (Exception e) {
                  logger.log(Level.FINE, "Failed to generate thumbnail for " + file.getName(), e);
                }
                SwingUtilities.invokeLater(
                    () -> {
                      if (onComplete != null) onComplete.run();
                    });
              } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to render icon for " + file.getName(), e);
              } finally {
                SwingUtilities.invokeLater(
                    () -> firePendingRenderCountChanged());
              }
            },
            file.length()));
  }

  public void dispose() {
    renderExecutor.shutdown();
  }

  private static Icon generatePlaceholderIcon(File f) {
    var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    var g = image.createGraphics();
    g.setColor(new Color(0x33, 0x33, 0x33));
    g.fillRect(0, 0, 64, 64);
    g.setColor(new Color(0x88, 0x88, 0x88));
    g.setFont(g.getFont().deriveFont(24f));
    var fm = g.getFontMetrics();
    String letter = f.getName().substring(0, 1).toUpperCase();
    int x = (64 - fm.stringWidth(letter)) / 2;
    int y = (64 - fm.getHeight()) / 2 + fm.getAscent();
    g.drawString(letter, x, y);
    g.dispose();
    return new ImageIcon(image);
  }
}
