package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two pure predicates behind "should this be restyled?".
 *
 * <p>{@code isNeutral} is the one that decides whether a colour is incidental
 * chrome or a deliberate choice: greys get darkened, brand and status colours
 * are left alone. Getting it wrong is not a crash, it is a Designer that
 * quietly repaints someone's error red or a Perspective binding icon — so the
 * threshold is worth pinning.
 */
class ThemeManagerColorMathTest {

    @Test
    @DisplayName("luminance spans the full range")
    void luminanceEndpoints() {
        assertEquals(0, ThemeManager.luminance(Color.BLACK));
        assertEquals(255, ThemeManager.luminance(Color.WHITE));
    }

    /**
     * The weighting is what makes this perceptual rather than a plain mean:
     * the eye is most sensitive to green and least to blue. Fully saturated
     * blue is dark enough to sit below the {@code < 90} "lift this foreground"
     * threshold while green sits far above it, which is the whole reason the
     * weighted form is used.
     */
    @Test
    @DisplayName("luminance is green-weighted, not a flat mean")
    void luminanceIsWeighted() {
        int red = ThemeManager.luminance(Color.RED);
        int green = ThemeManager.luminance(Color.GREEN);
        int blue = ThemeManager.luminance(Color.BLUE);

        assertTrue(green > red, "green must outweigh red (" + green + " vs " + red + ")");
        assertTrue(red > blue, "red must outweigh blue (" + red + " vs " + blue + ")");
        assertTrue(blue < 90, "saturated blue reads as dark (" + blue + ")");
        assertTrue(green >= 90, "saturated green does not (" + green + ")");
    }

    @Test
    @DisplayName("greys are neutral")
    void greysAreNeutral() {
        assertTrue(ThemeManager.isNeutral(Color.WHITE));
        assertTrue(ThemeManager.isNeutral(Color.BLACK));
        assertTrue(ThemeManager.isNeutral(new Color(0x80, 0x80, 0x80)));
        // Ignition's Base100 (#FAFAFB) — off-white, but still chrome.
        assertTrue(ThemeManager.isNeutral(new Color(0xFA, 0xFA, 0xFB)));
    }

    @Test
    @DisplayName("deliberate colours are not neutral and must survive theming")
    void saturatedColoursAreNotNeutral() {
        assertFalse(ThemeManager.isNeutral(Color.RED), "status red");
        assertFalse(ThemeManager.isNeutral(Color.GREEN), "status green");
        assertFalse(ThemeManager.isNeutral(new Color(0x44, 0x7E, 0xBC)), "IA brand blue");
        assertFalse(ThemeManager.isNeutral(new Color(0xFF, 0xC0, 0x00)), "warning amber");
    }

    @Test
    @DisplayName("the neutral boundary is a channel spread of 24")
    void neutralBoundary() {
        assertTrue(ThemeManager.isNeutral(new Color(100, 100, 123)), "spread 23 is neutral");
        assertFalse(ThemeManager.isNeutral(new Color(100, 100, 124)), "spread 24 is not");
    }
}
