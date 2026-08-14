package org.ironsight.cubearray.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.JTable;
import javax.swing.UIManager;

/** JTable with a quiet surface and row-level selection/hover treatment. */
public class ModernTable extends JTable {
  public ModernTable() {
    setShowGrid(false);
    setIntercellSpacing(new java.awt.Dimension(0, 0));
    setOpaque(false);
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Color surface = UIManager.getColor("App.surfaceColor");
    if (surface == null) surface = UIManager.getColor("Table.background");
    if (surface != null) {
      g.setColor(surface);
      g.fillRect(0, 0, getWidth(), getHeight());
    }
    Color selection = UIManager.getColor("App.brandSoftColor");
    if (selection != null) {
      g.setColor(selection);
      for (int row : getSelectedRows()) {
        Rectangle bounds = getCellRect(row, 0, true);
        Rectangle last = getCellRect(row, Math.max(0, getColumnCount() - 1), true);
        int right = Math.min(getWidth(), last.x + last.width);
        g.fillRoundRect(
            bounds.x + 2,
            bounds.y + 2,
            Math.max(0, right - bounds.x - 4),
            Math.max(0, bounds.height - 4),
            Ui.px(8),
            Ui.px(8));
      }
    }
    g.dispose();
    super.paintComponent(graphics);
  }
}
