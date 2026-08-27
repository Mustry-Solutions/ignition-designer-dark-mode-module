package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Color;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ignition draws some chrome with a border in the {@code Base000} token, which
 * <em>is</em> the {@code Color.WHITE} instance. {@link IaColorTokens} must not
 * rewrite that instance (see {@code IaColorTokensTest}), so such borders are
 * substituted per component instead — the same identity-based compensation the
 * background pass makes, extended to borders.
 *
 * <p>The identity rule is the part worth pinning: an IA-owned colour that
 * merely <em>equals</em> white is a private instance and must be left alone,
 * because it is not the shared token and the component may be relying on it.
 */
class WhiteBorderSwapTest {

    @Test
    @DisplayName("a white matte border is darkened, insets preserved")
    void darkensWhiteMatte() {
        Border result = ThemeManager.darkenWhiteBorder(
            BorderFactory.createMatteBorder(1, 2, 3, 4, Color.WHITE));

        assertNotNull(result);
        MatteBorder matte = assertInstanceOf(MatteBorder.class, result);
        assertEquals(ThemeManager.DARK_BORDER_LINE, matte.getMatteColor());
        assertEquals(new Insets(1, 2, 3, 4), matte.getBorderInsets(),
            "the border's geometry must survive the colour swap");
    }

    /**
     * The whole reason this pass exists is that the shared instance cannot be
     * mutated. A private colour that happens to equal white is not that
     * instance and is none of our business.
     */
    @Test
    @DisplayName("a private colour equal to white is left alone")
    void ignoresPrivateWhite() {
        Color privateWhite = new Color(255, 255, 255);
        assertEquals(Color.WHITE, privateWhite, "sanity: equal by value");

        assertNull(ThemeManager.darkenWhiteBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 1, privateWhite)));
    }

    @Test
    @DisplayName("borders in other colours are left alone")
    void ignoresOtherColours() {
        assertNull(ThemeManager.darkenWhiteBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(0xC0C5CA))));
        assertNull(ThemeManager.darkenWhiteBorder(
            BorderFactory.createLineBorder(Color.RED, 2)));
    }

    @Test
    @DisplayName("a white line border is darkened, thickness preserved")
    void darkensWhiteLine() {
        LineBorder result = assertInstanceOf(LineBorder.class,
            ThemeManager.darkenWhiteBorder(BorderFactory.createLineBorder(Color.WHITE, 3)));

        assertEquals(ThemeManager.DARK_BORDER_LINE, result.getLineColor());
        assertEquals(3, result.getThickness());
    }

    /**
     * The tag table's header nests one matte border inside another, only the
     * outer of which is white — so a compound border has to be darkened
     * selectively rather than wholesale.
     */
    @Test
    @DisplayName("only the white half of a compound border is replaced")
    void darkensCompoundSelectively() {
        Border keep = BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(0xC0C5CA));
        Border white = BorderFactory.createMatteBorder(2, 0, 0, 0, Color.WHITE);

        CompoundBorder result = assertInstanceOf(CompoundBorder.class,
            ThemeManager.darkenWhiteBorder(BorderFactory.createCompoundBorder(white, keep)));

        assertEquals(ThemeManager.DARK_BORDER_LINE,
            ((MatteBorder) result.getOutsideBorder()).getMatteColor(),
            "the white half is darkened");
        assertSame(keep, result.getInsideBorder(),
            "the other half is kept as-is, not rebuilt");
    }

    @Test
    @DisplayName("a compound border with no white is left alone")
    void ignoresCompoundWithoutWhite() {
        assertNull(ThemeManager.darkenWhiteBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 1, 1, 1, new Color(0xC0C5CA)),
            BorderFactory.createLineBorder(new Color(0x808080), 1))));
    }

    @Test
    @DisplayName("no border is not a border to darken")
    void ignoresNull() {
        assertNull(ThemeManager.darkenWhiteBorder(null));
        assertNull(ThemeManager.darkenWhiteBorder(BorderFactory.createEmptyBorder()));
    }
}
