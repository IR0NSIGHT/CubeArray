package org.ironsight.cubearray.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JTable;

/** JTable with a quiet surface and row-level selection/hover treatment. */
public class ModernTable extends JTable {
  private int hoveredRow = -1;

  public ModernTable() {
    setShowGrid(false);
    setIntercellSpacing(new java.awt.Dimension(0, 0));
    setOpaque(false);
    setSelectionBackground(new Color(0, 0, 0, 0));
    addMouseMotionListener(
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            updateHoveredRow(rowAtPoint(e.getPoint()));
          }
        });
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseExited(MouseEvent e) {
            updateHoveredRow(-1);
          }
        });
  }

  private void updateHoveredRow(int row) {
    if (row == hoveredRow) return;
    int old = hoveredRow;
    hoveredRow = row;
    if (old >= 0) repaint(getCellRect(old, 0, true));
    if (row >= 0) repaint(getCellRect(row, 0, true));
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Ui.color("App.surfaceColor", Ui.color("Table.background", getBackground())));
    g.fillRect(0, 0, getWidth(), getHeight());
    paintRows(g, getSelectedRows(), Ui.color("App.brandSoftColor", new Color(0, 0, 0, 0)));
    if (hoveredRow >= 0 && !isRowSelected(hoveredRow)) {
      paintRows(
          g, new int[] {hoveredRow}, Ui.color("App.surfaceHoverColor", new Color(0, 0, 0, 0)));
    }
    g.dispose();
    super.paintComponent(graphics);
  }

  private void paintRows(Graphics2D g, int[] rows, Color color) {
    g.setColor(color);
    for (int row : rows) {
      if (row < 0 || row >= getRowCount() || getColumnCount() == 0) continue;
      Rectangle bounds = getCellRect(row, 0, true);
      Rectangle last = getCellRect(row, getColumnCount() - 1, true);
      int right = Math.max(last.x + last.width, getWidth());
      g.fillRoundRect(
          bounds.x + Ui.px(2),
          bounds.y + Ui.px(2),
          Math.max(0, right - bounds.x - Ui.px(4)),
          Math.max(0, bounds.height - Ui.px(4)),
          Ui.px(8),
          Ui.px(8));
    }
  }
}
