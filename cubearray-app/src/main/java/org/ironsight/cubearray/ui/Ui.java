package org.ironsight.cubearray.ui;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

public final class Ui {
  public static final int XS = 4;
  public static final int SM = 8;
  public static final int MD = 12;
  public static final int LG = 16;
  public static final int XL = 24;
  public static final int XXL = 32;

  private Ui() {}

  public static int px(int value) {
    return UIScale.scale(value);
  }

  public static void style(JComponent component, String style) {
    component.putClientProperty("FlatLaf.styleClass", style);
  }

  public static JTextField searchField(String placeholder) {
    JTextField field = new JTextField();
    field.putClientProperty("JTextField.placeholderText", placeholder);
    style(field, "search");
    return field;
  }

  public static JScrollPane scroll(Component view) {
    JScrollPane scroll = new JScrollPane(view);
    scroll.setBorder(BorderFactory.createEmptyBorder());
    scroll.getViewport().setOpaque(false);
    scroll.setOpaque(false);
    return scroll;
  }

  public static Border hairline(String key) {
    return new AbstractBorder() {
      @Override
      public Insets getBorderInsets(Component c) {
        return new Insets(1, 1, 1, 1);
      }

      @Override
      public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Color color = UIManager.getColor(key);
        if (color == null) color = UIManager.getColor("Component.borderColor");
        if (color == null) return;
        g.setColor(color);
        g.drawRoundRect(x, y, width - 1, height - 1, Ui.px(10), Ui.px(10));
      }
    };
  }

  public static Border padding(int top, int left, int bottom, int right) {
    return new EmptyBorder(px(top), px(left), px(bottom), px(right));
  }
}
