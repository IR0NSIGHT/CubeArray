package org.ironsight.cubearray.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;
import com.formdev.flatlaf.fonts.jetbrains_mono.FlatJetBrainsMonoFont;
import java.awt.Window;
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
  private static Mode mode = Mode.SYSTEM;
  private static float fontScale = 1f;

  private ThemeManager() {}

  public static void install() {
    FlatLaf.registerCustomDefaultsSource("com.ironsight.cubearray.themes");
    FlatInterFont.install();
    FlatJetBrainsMonoFont.install();
    FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
    FlatLaf.setPreferredMonospacedFontFamily(FlatJetBrainsMonoFont.FAMILY);
    mode = readMode();
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

  public static void setFontScale(float scale) {
    fontScale = Math.max(.8f, Math.min(1.4f, scale));
    var baseFont = UIManager.getFont("defaultFont");
    if (baseFont != null) UIManager.put("defaultFont", baseFont.deriveFont(13f * fontScale));
    for (Window window : Window.getWindows()) SwingUtilities.updateComponentTreeUI(window);
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
      UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
      setFontScale(fontScale);
      for (Window window : Window.getWindows()) {
        SwingUtilities.updateComponentTreeUI(window);
        window.invalidate();
        window.repaint();
      }
      if (animate) FlatAnimatedLafChange.hideSnapshotWithAnimation();
    } catch (Exception e) {
      if (animate) FlatAnimatedLafChange.hideSnapshotWithAnimation();
      throw new IllegalStateException("Unable to install CubeArray theme", e);
    }
  }
}
