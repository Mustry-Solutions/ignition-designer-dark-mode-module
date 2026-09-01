package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An icon that was near-invisible on the light theme must not become the
 * brightest thing on the dark one (#60).
 *
 * <h2>Why the obvious fix was wrong</h2>
 *
 * <p>The pair swap picks the brighter of a button's enabled and disabled icons.
 * When the brighter one is the icon already installed the swap changes nothing,
 * yet it still claimed the button and blocked the smart invert. The tempting fix
 * — treat a no-op swap as "not handled" — regresses real surfaces: a census of
 * the Designer's own debug log shows the main toolbar's {@code JideButton}
 * VectorIcons (174), the Vision palette's toggles (183) and popup menus (109)
 * all riding that same no-op path, and all of them look right as they are.
 * Inverting them would darken icons that are already correct.
 *
 * <p>What separates the one broken case is CONTRAST, not brightness alone.
 * JIDE's {@code QuickFilterField} clear button is a solid disc measuring 226
 * against a ~250 surface — a contrast of 24, i.e. nearly invisible by design.
 * On our ~60 background that same disc has a contrast of 166 and reads as a
 * bright blob sitting in the search field. Restoring its relative contrast is
 * exactly what the smart invert is for.
 *
 * <p>Both halves are pinned: the subtle icon is inverted, and an icon just below
 * the threshold — standing in for the toolbar — is left exactly as it was.
 */
class SubtleIconTest {

    private TreeIconRecolorer icons;
    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        icons = new TreeIconRecolorer();
    }

    @AfterEach
    void leaveTheJvmLight() {
        icons.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("a near-white icon is inverted; a merely light one is left alone")
    void onlyTheSubtleIconIsInverted() {
        // 226: the clear button's measured brightness. 174: the main toolbar's.
        Icon subtle = disc(0xE2);
        Icon toolbar = disc(0xAE);
        JButton subtleButton = new JButton(subtle);
        JButton toolbarButton = new JButton(toolbar);
        JPanel panel = new JPanel(new GridLayout(2, 1));
        panel.add(subtleButton);
        panel.add(toolbarButton);
        panel.setSize(200, 80);

        manager.apply(true);
        icons.installIn(panel);
        icons.recolorButtonIcons(panel);

        assertTrue(brightness(subtleButton.getIcon(), subtleButton) < 200,
            "the near-white icon kept its brightness, so it is still the loudest "
                + "thing on a dark surface — the clear-search button's bright disc");
        assertSame(toolbar, toolbarButton.getIcon(),
            "an icon below the threshold was inverted too, so this has darkened "
                + "the main toolbar rather than fixing one search field");

        manager.apply(false);
        icons.uninstall();

        assertSame(subtle, subtleButton.getIcon(),
            "the stock icon was not put back on the light restore");
    }

    /** A solid disc of one grey, the shape of the real clear-search icon. */
    private static Icon disc(int grey) {
        BufferedImage image = new BufferedImage(14, 14, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setColor(new Color(grey, grey, grey));
            g2.fillOval(0, 0, 14, 14);
        } finally {
            g2.dispose();
        }
        return new ImageIcon(image);
    }

    private static double brightness(Icon icon, java.awt.Component on) {
        BufferedImage image = new BufferedImage(
            Math.max(icon.getIconWidth(), 1), Math.max(icon.getIconHeight(), 1),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            icon.paintIcon(on, g2, 0, 0);
        } finally {
            g2.dispose();
        }
        long total = 0;
        int counted = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel >>> 24) == 0) {
                    continue;
                }
                total += ThemeManager.luminance(new Color(pixel, false));
                counted++;
            }
        }
        return counted == 0 ? -1 : (double) total / counted;
    }
}
