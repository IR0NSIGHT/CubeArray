package org.ironsight.cubearray.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import java.awt.Window;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class ThemeManager {
  public enum Mode {
    LIGHT,
    DARK,
    SYSTEM
  }

  private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);
  private static final String MODE = "mode";
  private static final String FONT_SCALE = "fontScale";
  private static final String LIGHT_THEME = "lightTheme";
  private static final String DARK_THEME = "darkTheme";
  private static Mode mode = Mode.SYSTEM;
  private static float fontScale = 1f;
  private static String lightTheme = FlatLightLaf.class.getName();
  private static String darkTheme = FlatDarkLaf.class.getName();

  public record ThemeOption(String name, String className) {
    @Override
    public String toString() {
      return name;
    }
  }

  private ThemeManager() {}

  public static void install() {
    FlatLaf.registerCustomDefaultsSource("com.ironsight.cubearray.themes");
    FlatInterFont.install();
    FlatJetBrainsMonoFont.install();
    FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
    FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);
    mode = readMode();
    fontScale = PREFS.getFloat(FONT_SCALE, 1f);
    lightTheme = PREFS.get(LIGHT_THEME, FlatLightLaf.class.getName());
    darkTheme = PREFS.get(DARK_THEME, FlatDarkLaf.class.getName());
    installLookAndFeel(false);
  }

  public static Mode mode() {
    return mode;
  }

  public static void setMode(Mode newMode) {
    if (newMode == null || newMode == mode) return;
    mode = newMode;
    PREFS.put(MODE, mode.name());
    installLookAndFeel(true);
  }

  public static float fontScale() {
    return fontScale;
  }

  public static String lightTheme() {
    return lightTheme;
  }

  public static String darkTheme() {
    return darkTheme;
  }

  public static List<ThemeOption> lightThemes() {
    return themes(false);
  }

  public static List<ThemeOption> darkThemes() {
    return themes(true);
  }

  public static void setLightTheme(String className) {
    if (className == null || className.equals(lightTheme)) return;
    lightTheme = className;
    PREFS.put(LIGHT_THEME, className);
    if (!isDarkActive()) installLookAndFeel(true);
  }

  public static void setDarkTheme(String className) {
    if (className == null || className.equals(darkTheme)) return;
    darkTheme = className;
    PREFS.put(DARK_THEME, className);
    if (isDarkActive()) installLookAndFeel(true);
  }

  public static void setFontScale(float scale) {
    fontScale = Math.max(.8f, Math.min(1.4f, scale));
    PREFS.putFloat(FONT_SCALE, fontScale);
    var baseFont = UIManager.getFont("defaultFont");
    if (baseFont != null) UIManager.put("defaultFont", baseFont.deriveFont(13f * fontScale));
    updateWindows();
  }

  private static Mode readMode() {
    try {
      return Mode.valueOf(PREFS.get(MODE, Mode.SYSTEM.name()));
    } catch (IllegalArgumentException ignored) {
      return Mode.SYSTEM;
    }
  }

  private static void installLookAndFeel(boolean animate) {
    boolean dark = mode == Mode.DARK || (mode == Mode.SYSTEM && SystemTheme.isDark());
    try {
      if (animate) FlatAnimatedLafChange.showSnapshot();
      UIManager.setLookAndFeel(dark ? darkTheme : lightTheme);
      setFontScale(fontScale);
      updateWindows();
      if (animate) FlatAnimatedLafChange.hideSnapshotWithAnimation();
    } catch (Exception e) {
      if (animate) FlatAnimatedLafChange.hideSnapshotWithAnimation();
      throw new IllegalStateException("Unable to install CubeArray theme", e);
    }
  }

  private static boolean isDarkActive() {
    return mode == Mode.DARK || (mode == Mode.SYSTEM && SystemTheme.isDark());
  }

  private static List<ThemeOption> themes(boolean dark) {
    ThemeOption defaultTheme =
        new ThemeOption(
            dark ? "Default Dark" : "Default Light",
            dark ? FlatDarkLaf.class.getName() : FlatLightLaf.class.getName());
    return java.util.stream.Stream.concat(
            java.util.stream.Stream.of(defaultTheme),
            Arrays.stream(FlatAllIJThemes.INFOS)
                .filter(info -> info.isDark() == dark)
                .map(info -> new ThemeOption(info.getName(), info.getClassName())))
        .toList();
  }

  private static void updateWindows() {
    Runnable update =
        () -> {
          for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            window.invalidate();
            window.repaint();
          }
        };
    if (SwingUtilities.isEventDispatchThread()) {
      update.run();
      return;
    }
    try {
      SwingUtilities.invokeAndWait(update);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to update themed windows", e);
    }
  }
}
