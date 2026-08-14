package org.ironsight.cubearray.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

public class Icons {
  private static final int SIZE = 28;
  private static final Map<String, Icon> cache = new HashMap<>();
  private static final Map<String, String> svgFilenames =
      Map.ofEntries(
          Map.entry("settings", "settings_wheel.svg"),
          Map.entry("search", "search.svg"),
          Map.entry("folder", "folder.svg"),
          Map.entry("menu", "menu.svg"),
          Map.entry("tree", "tree.svg"),
          Map.entry("import", "import.svg"),
          Map.entry("reload", "reload.svg"),
          Map.entry("render", "render.svg"),
          Map.entry("delete", "delete.svg"),
          Map.entry("up", "up.svg"),
          Map.entry("close", "close.svg"));
  private static final Map<String, String> filenames =
      Map.of(
          "settings", "icons8-settings-50.png",
          "search", "icons8-search-50.png",
          "folder", "icons8-folder-50.png",
          "menu", "icons8-menu-50.png",
          "tree", "icons8-tree-52.png");

  public static Icon get(String name) {
    return get(name, SIZE);
  }

  public static Icon get(String name, int size) {
    return cache.computeIfAbsent(name + ":" + size, key -> load(name, size));
  }

  private static Icon load(String name, int size) {
    String svgFile = svgFilenames.get(name);
    if (svgFile != null) {
      URL svg = Icons.class.getClassLoader().getResource("icons/ui_icons/" + svgFile);
      if (svg != null) {
        return new FlatSVGIcon(svg)
            .derive(size, size)
            .setColorFilter(
                new FlatSVGIcon.ColorFilter(
                    (component, color) -> {
                      Color foreground = UIManager.getColor("Label.foreground");
                      return foreground != null ? foreground : color;
                    }));
      }
    }
    String file = filenames.get(name);
    if (file == null) return null;
    URL url = Icons.class.getClassLoader().getResource("icons/ui_icons/" + file);
    if (url == null) return null;
    return new ImageIcon(
        new ImageIcon(url).getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH));
  }
}
