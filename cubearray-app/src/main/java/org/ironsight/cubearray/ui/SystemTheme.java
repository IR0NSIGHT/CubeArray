package org.ironsight.cubearray.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

final class SystemTheme {
  private static volatile Boolean cachedDark;

  private SystemTheme() {}

  static boolean isDark() {
    Boolean cached = cachedDark;
    return cached != null ? cached : refresh();
  }

  static boolean refresh() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    boolean dark;
    if (os.contains("win")) dark = windowsDark();
    else if (os.contains("mac"))
      dark = commandDark("defaults", "read", "-g", "AppleInterfaceStyle");
    else dark = commandDark("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
    cachedDark = dark;
    return dark;
  }

  private static boolean windowsDark() {
    try {
      Process p =
          new ProcessBuilder(
                  "reg",
                  "query",
                  "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                  "/v",
                  "AppsUseLightTheme")
              .redirectErrorStream(true)
              .start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) if (line.contains("0x0")) return true;
      }
    } catch (Exception ignored) {
      // System mode should fall back to the light theme when probing is unavailable.
    }
    return false;
  }

  private static boolean commandDark(String... command) {
    try {
      Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
        String value = reader.readLine();
        return value != null && value.toLowerCase(Locale.ROOT).contains("dark");
      }
    } catch (Exception ignored) {
      return false;
    }
  }
}
