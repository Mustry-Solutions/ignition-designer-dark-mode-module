package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The colour rule behind {@link BlockWorkspaceTheme}, against the literal
 * colours {@code BasicBlockUI} and its subclasses actually carry.
 *
 * <p>The rule has to hold in both directions. Miss a light fill and the block
 * stays illegible; catch a colour that was already right and the START block —
 * the one thing on an alarm pipeline canvas that reads correctly today — gets
 * repainted for no reason.
 */
class BlockWorkspaceThemeTest {

    /** {@code BasicBlockUI.backgroundConnected}. */
    private static final Color STOCK_CONNECTED_FILL = new Color(176, 216, 234);
    /** {@code BasicBlockUI.backgroundUnconnected}, via {@code toColor("#EEEEEE")}. */
    private static final Color STOCK_UNCONNECTED_FILL = new Color(0xEEEEEE);
    /** {@code BasicBlockUI.borderUnconnected}. */
    private static final Color STOCK_UNCONNECTED_BORDER = new Color(207, 202, 198);
    /** IA's orange: {@code borderSelected}, and every colour on {@code StartBlock$UI}. */
    private static final Color IA_ORANGE = new Color(247, 144, 30);
    /** The FlatLaf dark panel background a block is seen against. */
    private static final Color CANVAS = new Color(0x3C3F41);

    @Test
    @DisplayName("the stock fills are both judged light")
    void theStockFillsAreDarkened() {
        assertTrue(BlockWorkspaceTheme.shouldDarkenFill(STOCK_CONNECTED_FILL));
        assertTrue(BlockWorkspaceTheme.shouldDarkenFill(STOCK_UNCONNECTED_FILL));
    }

    @Test
    @DisplayName("IA's orange is left alone, so StartBlock keeps the look that works")
    void theOrangeIsNotTouched() {
        assertFalse(BlockWorkspaceTheme.shouldDarkenFill(IA_ORANGE),
            "StartBlock$UI sets this as its fill; darkening it would break the one"
                + " block that reads correctly under dark mode today");
        assertFalse(BlockWorkspaceTheme.shouldLightenStroke(IA_ORANGE),
            "the same orange is borderSelected — the selection affordance");
    }

    @Test
    @DisplayName("a darkened fill stays its own hue and clears the canvas behind it")
    void aDarkenedFillIsStillBlueAndStillVisible() {
        Color darkened = BlockWorkspaceTheme.darkenFill(STOCK_CONNECTED_FILL);

        assertTrue(BlockWorkspaceTheme.luminance(darkened) < 120,
            "should be dark enough for light label text: " + darkened);
        assertTrue(darkened.getBlue() > darkened.getRed(),
            "a blue block should stay blue: " + darkened);
        assertTrue(BlockWorkspaceTheme.luminance(darkened)
                != BlockWorkspaceTheme.luminance(CANVAS),
            "a block that matches the canvas exactly has no edge");
    }

    @Test
    @DisplayName("the near-white unconnected fill stays visible against the canvas")
    void theUnconnectedFillDoesNotVanishIntoTheCanvas() {
        Color darkened = BlockWorkspaceTheme.darkenFill(STOCK_UNCONNECTED_FILL);

        assertTrue(BlockWorkspaceTheme.luminance(darkened)
                > BlockWorkspaceTheme.luminance(CANVAS),
            "an unconnected block should still read as a block: " + darkened
                + " on " + CANVAS);
    }

    @Test
    @DisplayName("only strokes that would disappear are lifted")
    void strokesAreJudgedOnWhetherTheyWouldBeSeen() {
        assertTrue(BlockWorkspaceTheme.shouldLightenStroke(Color.DARK_GRAY),
            "borderConnected is DARK_GRAY, which vanishes on a dark canvas");
        assertFalse(BlockWorkspaceTheme.shouldLightenStroke(STOCK_UNCONNECTED_BORDER),
            "borderUnconnected is already pale and reads fine");

        Color lifted = BlockWorkspaceTheme.lightenStroke(Color.DARK_GRAY);
        assertTrue(BlockWorkspaceTheme.luminance(lifted)
                > BlockWorkspaceTheme.luminance(CANVAS) + 30,
            "a lifted stroke has to stand off the canvas: " + lifted);
    }

    @Test
    @DisplayName("translucency survives a recolour")
    void alphaIsPreserved() {
        Color translucent = new Color(176, 216, 234, 128);
        assertEquals(128, BlockWorkspaceTheme.darkenFill(translucent).getAlpha());
        assertEquals(64, BlockWorkspaceTheme.lightenStroke(new Color(64, 64, 64, 64)).getAlpha());
    }
}
