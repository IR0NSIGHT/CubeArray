package org.ironsight.cubearray.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.Timer;
import javax.swing.UIManager;

public class ToggleSwitch extends JComponent {
  private boolean selected;
  private float position;
  private final Timer animation = new Timer(15, e -> animate());

  public ToggleSwitch() {
    setFocusable(true);
    enableEvents(java.awt.AWTEvent.KEY_EVENT_MASK);
    addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mousePressed(java.awt.event.MouseEvent e) {
            setSelected(!selected);
            requestFocusInWindow();
          }
        });
  }

  public boolean isSelected() {
    return selected;
  }

  private void animate() {
    float target = selected ? 1f : 0f;
    position += (target - position) * .25f;
    if (Math.abs(target - position) < .01f) {
      position = target;
      animation.stop();
    }
    repaint();
  }

  public void setSelected(boolean selected) {
    boolean old = this.selected;
    this.selected = selected;
    firePropertyChange("selected", old, selected);
    animation.start();
  }

  @Override
  protected void processKeyEvent(KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED
        && (e.getKeyCode() == KeyEvent.VK_SPACE || e.getKeyCode() == KeyEvent.VK_ENTER)) {
      setSelected(!selected);
      e.consume();
      return;
    }
    super.processKeyEvent(e);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(Ui.px(42), Ui.px(24));
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int h = getHeight() - 4;
    g.setColor(
        selected ? UIManager.getColor("App.brandColor") : UIManager.getColor("App.borderColor"));
    g.fillRoundRect(2, 2, getWidth() - 4, h, h, h);
    g.setColor(UIManager.getColor("Panel.background"));
    int diameter = h - 4;
    int x = 4 + Math.round(position * (getWidth() - diameter - 8));
    g.fillOval(x, 4, diameter, diameter);
    if (hasFocus()) {
      g.setColor(UIManager.getColor("Component.focusColor"));
      g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, h, h);
    }
    g.dispose();
  }
}
