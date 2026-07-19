package org.ironsight.cubearray.ui;

import java.awt.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

public class Icons {
  private static final int SIZE = 28;
  private static final Map<String, Icon> cache = new HashMap<>();
  private static final Map<String, String> filenames =
      Map.of(
          "settings", "icons8-settings-50.png",
          "search", "icons8-search-50.png",
          "folder", "icons8-folder-50.png",
          "menu", "icons8-menu-50.png");

  public static Icon get(String name) {
    return cache.computeIfAbsent(name, Icons::load);
  }

  private static Icon load(String name) {
    String file = filenames.get(name);
    if (file == null) return null;
    URL url = Icons.class.getClassLoader().getResource("icons/ui_icons/" + file);
    if (url == null) return null;
    return new ImageIcon(
        new ImageIcon(url).getImage().getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH));
  }
}
