package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.client.IgnitionLookAndFeel;
import com.inductiveautomation.ignition.client.icons.SvgIconUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Button icons that IA tints with a design token, and that the module then
 * inverted back into the dark (the Event Stream editor's Enabled / Disabled /
 * show-test-panel toggles, follow-up on [#79]).
 *
 * <p>{@code EventStreamResourceEditorPanel} builds three {@code JToggleButton}s
 * whose icons come from {@code SvgIconUtil.getIcon(name, 16, 16,
 * IgnitionLookAndFeel$Colors.IconDefault)}. The icon keeps that {@code Color}
 * as its paint, and {@link IaColorTokens} restyles the {@code IconDefault}
 * instance in place — so under dark mode the glyph already renders light,
 * with no help from anyone.
 *
 * <p>{@link TreeIconRecolorer#recolorButtonIcons} then walks every button and
 * hands any icon it does not recognise to the smart invert, which turns light
 * neutral pixels dark. That is the right move for a stock dark glyph; for a
 * glyph the token pass has already lightened it is the illegibility the
 * reporter saw, and it happens whichever side of the switch the editor was
 * built on.
 *
 * <p>Asserted on the rendered pixels: what the button paints is what the
 * user sees, and only the render proves the invert was (or was not) applied.
 */
class TokenTintedButtonIconTest {

    /** Below this mean brightness, a glyph disappears on the dark chrome. */
    private static final double READABLE_ON_DARK = 150;

    private ThemeManager manager;
    private IaColorTokens tokens;
    private TreeIconRecolorer icons;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        tokens = new IaColorTokens();
        icons = new TreeIconRecolorer();
    }

    @AfterEach
    void leaveTheJvmLight() {
        icons.uninstall();
        tokens.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("a token-tinted toggle built after the switch keeps its light glyph (#79)")
    void builtAfterTheSwitch() {
        manager.apply(true);
        tokens.install();
        icons.install();

        JToggleButton toggle = eventStreamToggle();
        double before = brightness(toggle.getIcon());
        assertTrue(before > READABLE_ON_DARK,
            "sanity: the token pass alone should already render the glyph light, "
                + "measured " + before);

        icons.recolorButtonIcons(parentOf(toggle));

        double after = brightness(toggle.getIcon());
        assertTrue(after > READABLE_ON_DARK,
            "the button-icon pass turned an already-light token glyph dark: "
                + before + " -> " + after);
    }

    @Test
    @DisplayName("a token-tinted toggle built before the switch ends up light too (#79)")
    void builtBeforeTheSwitch() {
        JToggleButton toggle = eventStreamToggle();
        JPanel parent = parentOf(toggle);
        assertTrue(brightness(toggle.getIcon()) < READABLE_ON_DARK,
            "sanity: the stock glyph is dark on light chrome");

        // ThemeManager.apply order: tokens first, then the icon passes.
        manager.apply(true);
        tokens.install();
        icons.install();
        icons.recolorButtonIcons(parent);

        double after = brightness(toggle.getIcon());
        assertTrue(after > READABLE_ON_DARK,
            "a toggle that was on screen before the switch renders a dark glyph: " + after);
    }

    /** What the Event Stream editor builds for "Enabled". */
    private static JToggleButton eventStreamToggle() {
        Icon icon = SvgIconUtil.getIcon("preview", 16, 16, IgnitionLookAndFeel.Colors.IconDefault);
        JToggleButton toggle = new JToggleButton("Enabled", icon);
        toggle.setSize(90, 24);
        return toggle;
    }

    private static JPanel parentOf(JToggleButton toggle) {
        JPanel panel = new JPanel();
        panel.add(toggle);
        return panel;
    }

    /** Mean luminance of the glyph's visible pixels, 0-255. */
    private static double brightness(Icon icon) {
        int w = Math.max(icon.getIconWidth(), 1);
        int h = Math.max(icon.getIconHeight(), 1);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        icon.paintIcon(new JPanel(), g, 0, 0);
        g.dispose();
        long sum = 0;
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) < 64) {
                    continue;
                }
                sum += ThemeManager.luminance(new Color(argb));
                count++;
            }
        }
        return count == 0 ? 0 : (double) sum / count;
    }
}
