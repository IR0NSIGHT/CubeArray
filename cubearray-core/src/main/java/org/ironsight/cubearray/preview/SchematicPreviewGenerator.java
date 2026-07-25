package org.ironsight.cubearray.preview;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
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
import org.joml.Vector3f;
import org.ironsight.cubearray.schematic.SchemReader;
import org.pepsoft.worldpainter.objects.WPObject;

public class SchematicPreviewGenerator  {
  private static final Logger logger = AppLogger.get(SchematicPreviewGenerator.class);
  private static final SchematicPreviewGenerator INSTANCE = new SchematicPreviewGenerator();

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

  static class PriorityTask implements Runnable, Comparable<PriorityTask> {
    private final Runnable task;
    private final long priority;
    private final String filePath;

    PriorityTask(Runnable task, long priority) {
      this(task, priority, null);
    }

    PriorityTask(Runnable task, long priority, String filePath) {
      this.task = task;
      this.priority = priority;
      this.filePath = filePath;
    }

    @Override
    public void run() {
      task.run();
    }

    @Override
    public int compareTo(PriorityTask other) {
      return Long.compare(this.priority, other.priority);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof PriorityTask that)) return false;
      return filePath != null && filePath.equals(that.filePath);
    }

    @Override
    public int hashCode() {
      return filePath != null ? filePath.hashCode() : 0;
    }
  }

  private final Map<String, Icon> iconCache = new ConcurrentHashMap<>();
  private final Set<String> pendingFiles = ConcurrentHashMap.newKeySet();

  private Consumer<Integer> pendingRenderCountChangedCallback;

  private List<InstancedCubes.CameraState> cameraSetups;

  private SchematicPreviewGenerator() {
    this.cameraSetups =
        List.of(
            new InstancedCubes.CameraState(
                null, (float) Math.toRadians(210), (float) Math.toRadians(30), 0f, 0f),
            new InstancedCubes.CameraState(null, (float) Math.toRadians(90), 0f, 0f, 0f),
            new InstancedCubes.CameraState(null, (float) Math.toRadians(180), 0f, 0f, 0f),
            new InstancedCubes.CameraState(null, 0f, (float) Math.toRadians(90), 0f, 0f));
  }

  public void setPendingRenderCountChangedCallback(Consumer<Integer> callback) {
    this.pendingRenderCountChangedCallback = callback;
  }

  public void setCameraSetups(List<InstancedCubes.CameraState> cameraSetups) {
    this.cameraSetups = cameraSetups;
  }

  public List<InstancedCubes.CameraState> getCameraSetups() {
    return cameraSetups;
  }

  private void firePendingRenderCountChanged() {
    if (pendingRenderCountChangedCallback != null) {
      ThreadPoolExecutor tpe = (ThreadPoolExecutor) renderExecutor;
      int count = tpe.getQueue().size() + (tpe.getActiveCount() > 0 ? 1 : 0);
      pendingRenderCountChangedCallback.accept(count);
    }
  }

  public static SchematicPreviewGenerator getInstance() {
    return INSTANCE;
  }

  public Icon getIcon(File file) {
    return iconCache.computeIfAbsent(
        file.getAbsolutePath(),
        k -> {
          Path renderPath = ResourceUtils.getRenderPathForFile(file);
          Path p0 = renderPath.resolveSibling(
              insertSuffix(renderPath.getFileName().toString(), "_0"));

          BufferedImage composite = new BufferedImage(256, 64, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = composite.createGraphics();

          if (p0.toFile().exists()) {
            for (int i = 0; i < 4; i++) {
              Path thumb = ResourceUtils.getThumbPathForFile(file).resolveSibling(
                  insertSuffix(ResourceUtils.getThumbPathForFile(file).getFileName().toString(), "_" + i));
              if (thumb.toFile().exists()) {
                try {
                  BufferedImage angle = ImageIO.read(thumb.toFile());
                  if (angle != null) {
                    g.drawImage(angle, i * 64, 0, null);
                    continue;
                  }
                } catch (Exception e) {
                  logger.log(Level.FINE, "Failed to read thumb " + thumb, e);
                }
              }
              Path p = renderPath.resolveSibling(
                  insertSuffix(renderPath.getFileName().toString(), "_" + i));
              if (p.toFile().exists()) {
                try {
                  BufferedImage angle = ImageIO.read(p.toFile());
                  if (angle != null) {
                    g.drawImage(
                        angle.getScaledInstance(64, 64, Image.SCALE_SMOOTH),
                        i * 64, 0, null);
                    continue;
                  }
                } catch (Exception e) {
                  logger.log(Level.FINE, "Failed to read render " + p, e);
                }
              }
              g.setColor(new Color(0x33, 0x33, 0x33));
              g.fillRect(i * 64, 0, 64, 64);
            }
          } else {
            Path thumbPath = ResourceUtils.getThumbPathForFile(file);
            Icon single;
            if (thumbPath.toFile().exists()) {
              single = new ImageIcon(thumbPath.toString());
            } else if (renderPath.toFile().exists()) {
              Image scaled = new ImageIcon(renderPath.toString())
                  .getImage()
                  .getScaledInstance(64, 64, Image.SCALE_SMOOTH);
              single = new ImageIcon(scaled);
            } else {
              single = generatePlaceholderIcon(file);
            }
            single.paintIcon(null, g, 0, 0);
          }

          g.dispose();
          return new ImageIcon(composite);
        });
  }

  public void invalidateIcon(File file) {
    iconCache.remove(file.getAbsolutePath());
  }

  public void showPreviewDialog(File file, Component parent) {
    showPreviewDialog(file, parent, 0);
  }

  public void showPreviewDialog(File file, Component parent, int initialIndex) {
    Path renderPath = ResourceUtils.getRenderPathForFile(file);

    int tmp = 0;
    for (int i = 0; i < 10; i++) {
      Path p = renderPath.resolveSibling(
          insertSuffix(renderPath.getFileName().toString(), "_" + i));
      if (p.toFile().exists()) tmp++;
    }
    if (tmp == 0 && renderPath.toFile().exists()) tmp = 1;
    final int count = tmp;

    if (count == 0) {
      JOptionPane.showMessageDialog(
          parent, "No render available yet.", file.getName(), JOptionPane.PLAIN_MESSAGE);
      return;
    }

    int[] currentIndex = {Math.max(0, Math.min(count - 1, initialIndex))};
    JLabel imageLabel = new JLabel();
    imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

    Runnable updateImage =
        () -> {
          Path path =
              count > 1
                  ? renderPath.resolveSibling(
                      insertSuffix(renderPath.getFileName().toString(), "_" + currentIndex[0]))
                  : renderPath;
          ImageIcon icon =
              new ImageIcon(
                  new ImageIcon(path.toString())
                      .getImage()
                      .getScaledInstance(640, 640, Image.SCALE_SMOOTH));
          imageLabel.setIcon(icon);
        };
    updateImage.run();

    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.add(imageLabel, BorderLayout.CENTER);

    JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));

    if (count > 1) {
      JButton prevBtn = new JButton("<");
      prevBtn.addActionListener(
          e -> {
            currentIndex[0] = (currentIndex[0] - 1 + count) % count;
            updateImage.run();
          });
      btnPanel.add(prevBtn);
    }

    JButton openBtn = new JButton("Open render in folder");
    openBtn.addActionListener(
        e -> {
          Path path =
              count > 1
                  ? renderPath.resolveSibling(
                      insertSuffix(renderPath.getFileName().toString(), "_" + currentIndex[0]))
                  : renderPath;
          ResourceUtils.revealFileInFolder(path);
        });
    btnPanel.add(openBtn);

    if (count > 1) {
      JButton nextBtn = new JButton(">");
      nextBtn.addActionListener(
          e -> {
            currentIndex[0] = (currentIndex[0] + 1) % count;
            updateImage.run();
          });
      btnPanel.add(nextBtn);
    }

    panel.add(btnPanel, BorderLayout.SOUTH);

    JOptionPane.showMessageDialog(parent, panel, file.getName(), JOptionPane.PLAIN_MESSAGE);
  }

  public void queueRender(File file, WPObject obj, Runnable onComplete) {
    if (file == null || obj == null) return;
    if (!ResourceUtils.needsNewRender(file)) {
      if (onComplete != null) onComplete.run();
      return;
    }
    Path rp = ResourceUtils.getRenderPathForFile(file);
    if (!Files.exists(rp)) {
      logger.info("Render needed for " + file.getName() + ": no cached render");
    } else {
      logger.info("Render needed for " + file.getName() + ": schematic file changed since last render");
    }
    firePendingRenderCountChanged();
    String absPath = file.getAbsolutePath();
    if (!pendingFiles.add(absPath)) {
      logger.fine("Render already queued for " + file.getName());
      return;
    }
    PriorityTask task = new PriorityTask(
        () -> {
          try {
            if (!ResourceUtils.needsNewRender(file)) {
              logger.fine("Render skipped for " + file.getName() + ": already up to date");
              return;
            }
            ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
            CubeSetup setup = SchemReader.prepareData(List.of(obj));
            if (setup == null) return;
            Path renderPath = ResourceUtils.getRenderPathForFile(file);
            Files.createDirectories(renderPath.getParent());

            var dim = new Vector3f(setup.max).sub(setup.min);
            var center = new Vector3f(setup.min).add(setup.max).mul(0.5f);
            float radius = Math.max(dim.x, Math.max(dim.y, dim.z)) * 2;

            List<InstancedCubes.CameraState> effectiveSetups;
            if (cameraSetups != null && !cameraSetups.isEmpty()) {
              effectiveSetups =
                  cameraSetups.stream()
                      .map(
                          cs ->
                              new InstancedCubes.CameraState(
                                  center, cs.yaw(), cs.pitch(), cs.roll(), radius))
                      .toList();
            } else {
              effectiveSetups =
                  List.of(new InstancedCubes.CameraState(center, 0f, 0f, 0f, radius));
            }
            InstancedCubes.renderToFile(setup, renderPath, 640, 640, effectiveSetups);
            try {
              for (int i = 0; i < effectiveSetups.size(); i++) {
                Path anglePath = renderPath.resolveSibling(
                    insertSuffix(renderPath.getFileName().toString(), "_" + i));
                if (anglePath.toFile().exists()) {
                  BufferedImage full = ImageIO.read(anglePath.toFile());
                  BufferedImage thumb = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                  Graphics2D g = thumb.createGraphics();
                  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                  g.drawImage(full, 0, 0, 64, 64, null);
                  g.dispose();
                  Path thumbPath = ResourceUtils.getThumbPathForFile(file).resolveSibling(
                      insertSuffix(ResourceUtils.getThumbPathForFile(file).getFileName().toString(), "_" + i));
                  ImageIO.write(thumb, "PNG", thumbPath.toFile());
                }
              }
            } catch (Exception e) {
              logger.log(Level.FINE, "Failed to generate thumbnails for " + file.getName(), e);
            }
            SwingUtilities.invokeLater(
                () -> {
                  if (onComplete != null) onComplete.run();
                });
          } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to render icon for " + file.getName(), e);
          } finally {
            pendingFiles.remove(absPath);
            SwingUtilities.invokeLater(
                () -> firePendingRenderCountChanged());
          }
        },
        file.length(),
        absPath);

    renderExecutor.execute(task);
  }

  /**
   * Queues loading the given schematics and opening the interactive 3D viewer on the background
   * render thread. Blocks the render thread until the user closes the window.
   */
  public void queueInteractiveRender(List<Path> schematicPaths) {
    if (schematicPaths.isEmpty()) return;
    renderExecutor.execute(
        new PriorityTask(
            () -> {
              try {
                ResourceUtils.copyResourcesToFile(ResourceUtils.TEXTURE_RESOURCES);
                CubeSetup setup =
                    SchemReader.prepareData(
                        SchemReader.loadSchematics(schematicPaths, f -> {}));
                if (setup == null) {
                  SwingUtilities.invokeLater(
                      () ->
                          JOptionPane.showMessageDialog(
                              null,
                              "Error: unable to load schematics from selected files.",
                              "Render Error",
                              JOptionPane.ERROR_MESSAGE));
                  return;
                }
                InstancedCubes.runInteractive(setup);
              } catch (Exception e) {
                logger.log(Level.WARNING, "Interactive render failed", e);
                SwingUtilities.invokeLater(
                    () ->
                        JOptionPane.showMessageDialog(
                            null,
                            "Render failed: " + e.getMessage(),
                            "Render Error",
                            JOptionPane.ERROR_MESSAGE));
              }
            },
            0));
  }

  public void dispose() {
    renderExecutor.shutdown();
  }

  // testing support
  int getPendingRenderCount() {
    ThreadPoolExecutor tpe = (ThreadPoolExecutor) renderExecutor;
    return tpe.getQueue().size() + tpe.getActiveCount();
  }

  private static String insertSuffix(String filename, String suffix) {
    int dot = filename.lastIndexOf('.');
    return dot == -1 ? filename + suffix
                     : filename.substring(0, dot) + suffix + filename.substring(dot);
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
