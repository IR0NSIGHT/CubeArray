package org.ironsight.cubearray.ui;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

final class SystemTheme {
  private SystemTheme() {}

  static boolean isDark() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) return windowsDark();
    if (os.contains("mac")) return commandDark("defaults", "read", "-g", "AppleInterfaceStyle");
    return commandDark("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
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
