package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;

import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * A crescent-moon glyph for the Dark Mode menu item, drawn vectorially so it
 * follows the current menu foreground color in both light and dark themes.
 */
public class MoonIcon implements Icon {

    private final int size;

    public MoonIcon(int size) {
        this.size = size;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color color = UIManager.getColor("MenuItem.foreground");
        if (color == null) {
            color = c != null ? c.getForeground() : Color.GRAY;
        }
        g2.setColor(color);
        Area moon = new Area(new Ellipse2D.Float(x + 2f, y + 2f, size - 4f, size - 4f));
        moon.subtract(new Area(new Ellipse2D.Float(x + 6f, y + 1f, size - 4f, size - 4f)));
        g2.fill(moon);
        g2.dispose();
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }
}
