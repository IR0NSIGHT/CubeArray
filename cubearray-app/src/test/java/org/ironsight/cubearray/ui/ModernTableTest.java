package org.ironsight.cubearray.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.table.DefaultTableModel;
import org.junit.Test;

public class ModernTableTest {
  @Test
  public void configuresTransparentRowSelection() {
    ModernTable table = table();

    assertThat(table.getShowHorizontalLines()).isFalse();
    assertThat(table.getShowVerticalLines()).isFalse();
    assertThat(table.getSelectionBackground()).isEqualTo(new Color(0, 0, 0, 0));
    assertThat(table.isOpaque()).isFalse();
  }

  @Test
  public void paintsEmptyAndSelectedTablesWithoutError() {
    ModernTable table = table();
    table.setSize(320, 100);
    table.getSelectionModel().setSelectionInterval(0, 0);
    BufferedImage image = new BufferedImage(320, 100, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    table.paint(graphics);
    graphics.dispose();
  }

  @Test
  public void usesThemeSelectionBorderColor() {
    ModernTable table = table();
    table.setSize(320, 100);
    table.getSelectionModel().setSelectionInterval(0, 0);

    assertThat(table.getSelectionBackground()).isEqualTo(new Color(0, 0, 0, 0));
  }

  private static ModernTable table() {
    ModernTable table = new ModernTable();
    table.setModel(new DefaultTableModel(new Object[][] {{"one", "two"}}, new String[] {"A", "B"}));
    return table;
  }
}
