package org.ironsight.cubearray.ui;

import org.ironsight.cubearray.edit.BlockGradient;

import javax.swing.*;
import java.awt.*;

public class BlockIconPanel extends JPanel {

    private static final int GRID_SIZE = 20;
    private static final int ICON_SIZE = 20;

    private final String[][] grid = new String[GRID_SIZE][GRID_SIZE];
    private final BlockIconProvider iconProvider;

    public BlockIconPanel(BlockIconProvider iconProvider) {
        this.iconProvider = iconProvider;
        setPreferredSize(new Dimension(GRID_SIZE * ICON_SIZE, GRID_SIZE * ICON_SIZE));
    }

    public void setGradient(BlockGradient gradient) {
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                grid[x][y] = gradient.getBlock(GRID_SIZE - 1 - y);
            }
        }
        repaint();
    }

    public void drawIcon(Graphics g, int gridX, int gridY) {
        if (gridX < 0 || gridX >= GRID_SIZE || gridY < 0 || gridY >= GRID_SIZE) return;
        String block = grid[gridX][gridY];
        Icon icon = iconProvider.getIcon(block);
        int px = gridX * ICON_SIZE;
        int py = gridY * ICON_SIZE;
        if (icon != null) {
            icon.paintIcon(this, g, px + 1, py + 1);
        } else {
            g.setColor(Color.GRAY);
            g.fillRect(px + 1, py + 1, ICON_SIZE - 2, ICON_SIZE - 2);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                drawIcon(g, x, y);
            }
        }
        g.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i <= GRID_SIZE; i++) {
            g.drawLine(i * ICON_SIZE, 0, i * ICON_SIZE, GRID_SIZE * ICON_SIZE);
            g.drawLine(0, i * ICON_SIZE, GRID_SIZE * ICON_SIZE, i * ICON_SIZE);
        }
    }
}
