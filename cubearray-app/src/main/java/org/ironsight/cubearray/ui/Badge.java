package org.ironsight.cubearray.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.UIManager;

public class Badge extends JLabel {
  private final String backgroundKey;
  private final String foregroundKey;

  public Badge(String text, String tone) {
    super(text);
    backgroundKey = "App." + tone + "SoftColor";
    foregroundKey = "App." + tone + "Color";
    setOpaque(false);
    setBorder(Ui.padding(4, 8, 4, 8));
  }

  @Override
  public Color getForeground() {
    Color color = UIManager.getColor(foregroundKey);
    return color != null ? color : super.getForeground();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Color background = UIManager.getColor(backgroundKey);
    if (background != null) {
      g.setColor(background);
      g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
    }
    g.dispose();
    super.paintComponent(graphics);
  }
}
