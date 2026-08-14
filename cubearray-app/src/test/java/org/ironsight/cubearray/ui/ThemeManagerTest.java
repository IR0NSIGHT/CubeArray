package org.ironsight.cubearray.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.FlatLaf;
import java.awt.Color;
import javax.swing.UIManager;
import org.junit.Test;

public class ThemeManagerTest {
  @Test
  public void installLoadsFlatLafAndCubeArrayTokens() {
    ThemeManager.install();

    assertThat(UIManager.getLookAndFeel()).isInstanceOf(FlatLaf.class);
    assertThat(UIManager.getColor("App.brandColor")).isEqualTo(new Color(0x3B7DED));
    assertThat(UIManager.getColor("App.borderColor")).isNotNull();
    assertThat(UIManager.getColor("App.searchHighlightColor")).isNotNull();
  }

  @Test
  public void fontScaleIsClamped() {
    float previous = ThemeManager.fontScale();
    try {
      ThemeManager.setFontScale(2f);
      assertThat(ThemeManager.fontScale()).isEqualTo(1.4f);
      ThemeManager.setFontScale(.1f);
      assertThat(ThemeManager.fontScale()).isEqualTo(.8f);
    } finally {
      ThemeManager.setFontScale(previous);
    }
  }
}
