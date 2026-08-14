package org.ironsight.cubearray.ui;

import java.awt.*;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;

public class BlockIconProvider {
  private final int defaultSize;
  private final int smallSize;
  private final Map<String, Icon> cache = new ConcurrentHashMap<>();
  private final ClassLoader classLoader;

  public BlockIconProvider(int defaultSize, int smallSize) {
    this.defaultSize = defaultSize;
    this.smallSize = smallSize;
    this.classLoader = getClass().getClassLoader();
  }

  public Icon getIcon(String block) {
    String key = normalizeKey(block);
    if ("minecraft:air".equals(key)) return airIcon(defaultSize);
    Icon cached = cache.get(key);
    if (cached != null) return cached;
    Icon icon = loadIcon(key, defaultSize);
    if (icon != null) {
      cache.put(key, icon);
      return icon;
    }
    Icon unknown = unknownIcon();
    cache.put(key, unknown);
    return unknown;
  }

  public Icon getIconSmall(String block) {
    String key = normalizeKey(block);
    if ("minecraft:air".equals(key)) return airIcon(smallSize);
    String cacheKey = key + "__small__";
    Icon cached = cache.get(cacheKey);
    if (cached != null) return cached;
    Icon icon = loadIcon(key, smallSize);
    if (icon != null) {
      cache.put(cacheKey, icon);
      return icon;
    }
    Icon unknown = unknownIconSmall();
    if (unknown != null) cache.put(cacheKey, unknown);
    return unknown;
  }

  private static String normalizeKey(String block) {
    int metaStart = block.indexOf('[');
    String key = metaStart >= 0 ? block.substring(0, metaStart) : block;
    if (!key.contains(":")) key = "minecraft:" + key;
    return key;
  }

  private Icon airIcon(int size) {
    return cache.computeIfAbsent("__air__" + size, ignored -> new AirIcon(size));
  }

  private Icon loadIcon(String key, int size) {
    String path = "icons/" + key.replace(':', '_') + ".png";
    URL url = classLoader.getResource(path);
    if (url == null) return null;
    return new ImageIcon(
        new ImageIcon(url).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH));
  }

  private Icon unknownIcon() {
    Icon cached = cache.get("__unknown__");
    if (cached != null) return cached;
    URL url = classLoader.getResource("icons/unknown.png");
    if (url == null) return null;
    Icon icon =
        new ImageIcon(
            new ImageIcon(url)
                .getImage()
                .getScaledInstance(defaultSize, defaultSize, Image.SCALE_SMOOTH));
    cache.put("__unknown__", icon);
    return icon;
  }

  private Icon unknownIconSmall() {
    Icon cached = cache.get("__unknown_small__");
    if (cached != null) return cached;
    URL url = classLoader.getResource("icons/unknown.png");
    if (url == null) return null;
    Icon icon =
        new ImageIcon(
            new ImageIcon(url)
                .getImage()
                .getScaledInstance(smallSize, smallSize, Image.SCALE_SMOOTH));
    cache.put("__unknown_small__", icon);
    return icon;
  }

  private static final class AirIcon implements Icon {
    private static final Color COLOR = new Color(126, 211, 239);
    private final int size;

    private AirIcon(int size) {
      this.size = size;
    }

    @Override
    public int getIconWidth() {
      return size;
    }

    @Override
    public int getIconHeight() {
      return size;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(COLOR);
      int inset = Math.max(2, size / 6);
      g.setStroke(new BasicStroke(Math.max(1, size / 12f)));
      g.drawOval(x + inset, y + inset, size - inset * 2 - 1, size - inset * 2 - 1);
      g.dispose();
    }
  }
}
