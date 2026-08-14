package org.ironsight.cubearray.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ThemeManagerTest {
  private final Preferences preferences = Preferences.userNodeForPackage(ThemeManager.class);
  private ThemeManager.Mode previousMode;
  private float previousFontScale;

  @Before
  public void saveThemePreferences() {
    previousMode = ThemeManager.mode();
    previousFontScale = ThemeManager.fontScale();
  }

  @After
  public void restoreThemePreferences() throws Exception {
    runOnEdt(
        () -> {
          ThemeManager.setFontScale(previousFontScale);
          ThemeManager.setMode(previousMode);
        });
    preferences.put("mode", previousMode.name());
    preferences.putFloat("fontScale", previousFontScale);
  }

  @Test
  public void lightAndDarkThemesLoadCubeArrayTokens() throws Exception {
    runOnEdt(() -> ThemeManager.setMode(ThemeManager.Mode.LIGHT));

    assertThat(UIManager.getLookAndFeel()).isInstanceOf(FlatLaf.class);
    assertThat(UIManager.getColor("App.brandColor")).isEqualTo(new Color(0x3B7DED));
    assertThat(UIManager.getColor("App.borderColor")).isNotNull();
    assertThat(UIManager.getColor("App.searchHighlightColor")).isNotNull();

    Color lightBorder = UIManager.getColor("App.borderColor");
    runOnEdt(() -> ThemeManager.setMode(ThemeManager.Mode.DARK));
    assertThat(UIManager.getColor("App.borderColor")).isNotEqualTo(lightBorder);
    assertThat(UIManager.getColor("App.brandSoftColor")).isNotNull();
  }

  @Test
  public void fontScaleIsClamped() throws Exception {
    float previous = ThemeManager.fontScale();
    try {
      runOnEdt(() -> ThemeManager.setFontScale(2f));
      assertThat(ThemeManager.fontScale()).isEqualTo(1.4f);
      runOnEdt(() -> ThemeManager.setFontScale(.1f));
      assertThat(ThemeManager.fontScale()).isEqualTo(.8f);
    } finally {
      runOnEdt(() -> ThemeManager.setFontScale(previous));
    }
  }

  private static void runOnEdt(Runnable action) throws Exception {
    if (SwingUtilities.isEventDispatchThread()) action.run();
    else SwingUtilities.invokeAndWait(action);
  }
}
