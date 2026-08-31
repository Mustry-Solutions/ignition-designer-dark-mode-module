package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.client.util.gui.InlineTipLabel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The inline tip banner, which under dark mode was not merely light but
 * ILLEGIBLE ([#47]).
 *
 * <p>{@code InlineTipLabel.paintComponent} is {@code g.setColor(COLOR);
 * g.fillRect(...)} where {@code COLOR} is a {@code private static final}
 * {@code new Color(14083309)} — #D6E4ED. A literal on the Graphics: no
 * look-and-feel swap, no {@code UIManager} override and no component walk can
 * reach it, and an inspection of the component chain comes back reporting a
 * perfectly dark background.
 *
 * <p>What made it illegible rather than just pale is ours. The constructor does
 * {@code setForeground(IgnitionLookAndFeel$Colors.Base900)}, and
 * {@link IaColorTokens} lightens {@code Base900} under dark mode — so the module
 * turned dark-on-pale into light-on-pale. Darkening the fill is therefore not
 * cosmetic: it is what makes the text readable again.
 *
 * <p>Asserted on the PIXELS as well as on the constant. The constant is what
 * the fix changes, but the pixels are the thing that was wrong, and only the
 * render proves the paint actually uses it.
 */
class InlineTipLabelTest {

    /** The stock fill, #D6E4ED — read from the class, not hardcoded twice. */
    private int stockFill;

    private ThemeManager manager;
    private IaColorTokens tokens;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        tokens = new IaColorTokens();
        stockFill = fill().getRGB() & 0xFFFFFF;
    }

    @AfterEach
    void leaveTheJvmLight() {
        tokens.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("the tip fill goes dark and comes back to its exact stock value (#47)")
    void theFillIsDarkenedAndRestored() {
        assertTrue(ThemeManager.luminance(new Color(stockFill)) > 170,
            "the stock fill is " + hex(stockFill) + ", already dark — this test would "
                + "then be asserting nothing. It is #D6E4ED in a stock Designer.");

        tokens.install();

        assertTrue(ThemeManager.luminance(fill()) < 100,
            "the tip fill stayed light under dark mode: " + hex(fill().getRGB()));

        tokens.uninstall();

        // By value, not by luminance: this is a shared static Color instance
        // mutated in place, so an approximate restore would leave every tip
        // banner in the Designer subtly wrong for the rest of the session.
        assertEquals(hex(stockFill), hex(fill().getRGB()),
            "the tip fill did not come back to its stock value");
    }

    @Test
    @DisplayName("no pale band survives in a rendered tip label (#47)")
    void theRenderedBandGoesDark() {
        InlineTipLabel label = new InlineTipLabel("These diagnostics apply to the Designer.");
        label.setSize(420, 40);

        assertTrue(paleFraction(render(label)) > 0.5,
            "the stock tip renders mostly pale — if it does not, the render is not "
                + "exercising paintComponent and the dark assertion below proves nothing");

        manager.apply(true);
        tokens.install();
        SwingUtilitiesLayout.layout(label);

        double pale = paleFraction(render(label));
        assertTrue(pale < 0.02,
            String.format("%.1f%% of the tip banner is still pale under dark mode", pale * 100));
    }

    @Test
    @DisplayName("the tip text stays readable against the fill (#47)")
    void theTextStaysReadable() {
        InlineTipLabel label = new InlineTipLabel("These diagnostics apply to the Designer.");
        label.setSize(420, 40);

        manager.apply(true);
        tokens.install();

        // The Base900 foreground is on the inner text label, not on the panel
        // — the panel's own foreground is whatever the look and feel gave it.
        Color text = tipTextColour(label);
        Color background = fill();
        assertNotEquals(null, text, "no text label inside the tip, so nothing to read");
        int contrast = Math.abs(ThemeManager.luminance(text) - ThemeManager.luminance(background));
        assertTrue(contrast > 90,
            "tip text " + hex(text.getRGB()) + " on fill " + hex(background.getRGB())
                + " differs by only " + contrast + " in luminance — this is the illegibility "
                + "in #47, and darkening the fill is what fixes it");
    }

    /**
     * The foreground of the label carrying the tip text.
     *
     * <p>{@code InlineTipLabel}'s constructor builds {@code new JLabel(text)} and
     * sets {@code Base900} on THAT, so the panel's own foreground says nothing
     * about whether the tip is readable.
     */
    private static Color tipTextColour(java.awt.Container tip) {
        for (java.awt.Component child : tip.getComponents()) {
            if (child instanceof javax.swing.JLabel) {
                javax.swing.JLabel candidate = (javax.swing.JLabel) child;
                if (candidate.getText() != null && !candidate.getText().isEmpty()) {
                    return candidate.getForeground();
                }
            }
            if (child instanceof java.awt.Container) {
                Color nested = tipTextColour((java.awt.Container) child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /** {@code InlineTipLabel.COLOR}, the literal the paint uses. */
    private static Color fill() {
        try {
            Field colour = InlineTipLabel.class.getDeclaredField("COLOR");
            colour.setAccessible(true);
            return (Color) colour.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(
                "InlineTipLabel.COLOR is gone — the fix in IaColorTokens.CLASS_DARK "
                    + "names that field by string and is now dead. Re-check the class.", e);
        }
    }

    /** Fraction of pixels light enough to read as the stock pale band. */
    private static double paleFraction(BufferedImage image) {
        int pale = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (ThemeManager.luminance(new Color(image.getRGB(x, y))) > 170) {
                    pale++;
                }
            }
        }
        return (double) pale / (image.getWidth() * image.getHeight());
    }

    private static BufferedImage render(InlineTipLabel label) {
        SwingUtilitiesLayout.layout(label);
        BufferedImage image = new BufferedImage(
            label.getWidth(), label.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        label.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }

    /** {@code validate()} is a no-op with no peer, so lay out by hand. */
    static final class SwingUtilitiesLayout {
        private SwingUtilitiesLayout() {
        }

        static void layout(java.awt.Component component) {
            if (component instanceof java.awt.Container) {
                ((java.awt.Container) component).doLayout();
                for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                    layout(child);
                }
            }
        }
    }
}
