package org.ironsight.cubearray.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public class Card extends JPanel {
  private final boolean dropZone;

  public Card() {
    this(false);
  }

  public Card(boolean dropZone) {
    this.dropZone = dropZone;
    setOpaque(false);
    setBorder(new EmptyBorder(Ui.px(16), Ui.px(16), Ui.px(16), Ui.px(16)));
  }

  @Override
  public Insets getInsets() {
    return new Insets(Ui.px(16), Ui.px(16), Ui.px(16), Ui.px(16));
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int arc = Ui.px(10);
    for (int i = 3; i >= 1; i--) {
      Color shadow = new Color(0, 0, 0, 10 + i * 4);
      g.setColor(shadow);
      g.fillRoundRect(i, i, getWidth() - i * 2, getHeight() - i * 2, arc, arc);
    }
    Color surface = UIManager.getColor("App.surfaceColor");
    if (surface != null) {
      g.setColor(surface);
      g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
    }
    Color border = UIManager.getColor("App.borderColor");
    if (border == null) border = Color.GRAY;
    g.setColor(border);
    if (dropZone) {
      for (int x = 2; x < getWidth() - 2; x += 8)
        g.drawLine(x, 1, Math.min(x + 4, getWidth() - 2), 1);
      for (int x = 2; x < getWidth() - 2; x += 8)
        g.drawLine(x, getHeight() - 2, Math.min(x + 4, getWidth() - 2), getHeight() - 2);
    } else {
      g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
    }
    g.dispose();
    super.paintComponent(graphics);
  }
}
