package org.ironsight.cubearray.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import javax.swing.UIManager;
import org.junit.Test;

public class UiComponentsTest {
  @Test
  public void badgeResolvesThemeForegroundDuringPaint() {
    UIManager.put("App.successColor", new Color(20, 140, 80));
    Badge badge = new Badge("Ready", "success");

    assertThat(badge.getForeground()).isEqualTo(new Color(20, 140, 80));
    assertThat(badge.isOpaque()).isFalse();
  }

  @Test
  public void togglePublishesSelectionChanges() {
    ToggleSwitch toggle = new ToggleSwitch();
    boolean[] changed = {false};
    toggle.addPropertyChangeListener("selected", e -> changed[0] = true);

    toggle.setSelected(true);

    assertThat(toggle.isSelected()).isTrue();
    assertThat(changed[0]).isTrue();
    assertThat(toggle.getPreferredSize().width).isGreaterThan(0);
  }

  @Test
  public void iconsAreAvailableAtMultipleSizes() {
    assertThat(Icons.get("search", 16)).isNotNull();
    assertThat(Icons.get("search", 24)).isNotNull();
    assertThat(Icons.get("delete", 16)).isNotNull();
  }
}
