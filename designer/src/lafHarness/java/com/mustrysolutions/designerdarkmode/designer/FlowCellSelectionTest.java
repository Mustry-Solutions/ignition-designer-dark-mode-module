package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.designer.gui.flowpane.render.FlowCellContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The selected section of an Event Stream, whose name was unreadable under
 * dark mode ([#79]).
 *
 * <p>The strip of sections at the top of an Event Stream editor (Source,
 * Encoder, Filter, Transform, Handler) is a {@code FlowPanel}; each section is
 * a {@code FlowCellContent}. Its {@code paintSelected} fills a rounded
 * rectangle straight onto the Graphics with one of four {@code private static
 * final} literals, verified against {@code designer-8.3.8.jar}:
 *
 * <pre>
 * SELECTED_BACKGROUND = new Color(14542315);   // #DDE5EB
 * SELECTED_BORDER     = new Color(12303291);   // #BBBBBB
 * HOVER_BACKGROUND    = new Color(15462386);   // #EBEFF2
 * HOVER_BORDER        = new Color(13421772);   // #CCCCCC
 * </pre>
 *
 * <p>A literal on the Graphics: no look-and-feel swap, no {@code UIManager}
 * override and no component walk reaches it, and the reporter's own inspector
 * dump came back clean ({@code FlowCellContent bg=#3C3F41|uires}). The type
 * label inside the cell ("Tag Event") is a plain {@code JLabel} that inherits
 * {@code Label.foreground} — light under FlatLaf — so selecting or hovering a
 * section put light text on a pale card.
 *
 * <p>The fix darkens the card through {@link IaColorTokens#CLASS_DARK}, the
 * same in-place mutation that already handles the welcome workspace's
 * identical #DDE5EB tile selection. Asserted on the PIXELS as well as on the
 * constants: the constants are what the fix changes, but the pixels are what
 * was wrong, and only the render proves {@code paintSelected} uses them.
 */
class FlowCellSelectionTest {

    /** What {@code FlowCellBody} hands the content on an Event Stream, per the #79 dump. */
    private static final int CELL_WIDTH = 110;

    /** Fixed by the {@code FlowCellContent} constructor. */
    private static final int CELL_HEIGHT = 60;

    /** Below this luminance gap, text on a fill is what #79 reported. */
    private static final int READABLE = 90;

    private ThemeManager manager;
    private IaColorTokens tokens;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        tokens = new IaColorTokens();
    }

    @AfterEach
    void leaveTheJvmLight() {
        tokens.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("the selected and hover fills go dark and come back to their exact stock values (#79)")
    void theFillsAreDarkenedAndRestored() {
        for (String fill : new String[] {"SELECTED_BACKGROUND", "HOVER_BACKGROUND"}) {
            int stock = constant(fill).getRGB() & 0xFFFFFF;
            assertTrue(ThemeManager.luminance(new Color(stock)) > 200,
                fill + " is " + hex(stock) + " in a stock Designer, already dark — this "
                    + "test would then be asserting nothing.");

            tokens.install();

            assertTrue(ThemeManager.luminance(constant(fill)) < 100,
                fill + " stayed light under dark mode: " + hex(constant(fill).getRGB()));

            tokens.uninstall();

            // By value, not by luminance: a shared static Color instance mutated
            // in place, so an approximate restore would leave every Event Stream
            // in the Designer subtly wrong for the rest of the session.
            assertEquals(hex(stock), hex(constant(fill).getRGB()),
                fill + " did not come back to its stock value");
        }
    }

    @Test
    @DisplayName("the outline still separates a dark card from the dark canvas (#79)")
    void theOutlinesStayVisible() {
        tokens.install();

        for (String[] pair : new String[][] {
            {"SELECTED_BACKGROUND", "SELECTED_BORDER"},
            {"HOVER_BACKGROUND", "HOVER_BORDER"},
        }) {
            Color fill = constant(pair[0]);
            Color border = constant(pair[1]);
            int gap = Math.abs(ThemeManager.luminance(border) - ThemeManager.luminance(fill));
            assertTrue(gap >= 20,
                pair[1] + " " + hex(border.getRGB()) + " is indistinguishable from "
                    + pair[0] + " " + hex(fill.getRGB()) + " (luminance gap " + gap + ")");
            assertTrue(ThemeManager.luminance(border) < 200,
                pair[1] + " " + hex(border.getRGB()) + " is a bright outline on a dark card");
        }
    }

    @Test
    @DisplayName("the section name stays readable on a selected section (#79)")
    void theSelectedSectionStaysReadable() {
        FlowCellContent cell = eventStreamSection();
        cell.setSelected(true);

        assertRendersTheStockFill(cell, "SELECTED_BACKGROUND");

        switchToDark(cell);

        assertReadable(cell, "selected", "SELECTED_BACKGROUND");
    }

    @Test
    @DisplayName("the section name stays readable on a hovered section (#79)")
    void theHoveredSectionStaysReadable() {
        FlowCellContent cell = eventStreamSection();
        cell.setHovered(true);

        assertRendersTheStockFill(cell, "HOVER_BACKGROUND");

        switchToDark(cell);

        assertReadable(cell, "hovered", "HOVER_BACKGROUND");
    }

    /**
     * What a switch does to a section that is on screen: the look and feel
     * swap, the token pass, and the {@code updateComponentTreeUI} walk that
     * {@code ThemeManager.apply} runs over every window. The cell here is in
     * no window, so the walk is applied by hand — without it the cell keeps
     * its Synthetica delegate under FlatLaf, which is a different bug (and one
     * that paints differently enough to pass a contrast check by accident).
     */
    private void switchToDark(FlowCellContent cell) {
        manager.apply(true);
        tokens.install();
        SwingUtilities.updateComponentTreeUI(cell);
        cell.setSize(CELL_WIDTH, CELL_HEIGHT);
    }

    /** A section as the Event Stream editor builds it: enabled, named, sized. */
    private static FlowCellContent eventStreamSection() {
        FlowCellContent cell = new FlowCellContent(CELL_WIDTH);
        cell.setTypeText("Tag Event");
        cell.setEnabled(true);
        cell.setSize(CELL_WIDTH, CELL_HEIGHT);
        return cell;
    }

    /**
     * The stock render puts the stock literal at the sample point — which
     * proves both that the sample point is on the card and that the render
     * exercises {@code paintSelected}. Without this, a dark assertion below
     * could pass on an unpainted image.
     */
    private static void assertRendersTheStockFill(FlowCellContent cell, String fill) {
        int stock = constant(fill).getRGB() & 0xFFFFFF;
        int painted = cardPixel(render(cell));
        assertEquals(hex(stock), hex(painted),
            "the stock render does not show " + fill + " at the sample point, so the "
                + "render is not exercising paintSelected and the dark assertion "
                + "would prove nothing");
    }

    private static void assertReadable(FlowCellContent cell, String state, String fillName) {
        Color fill = new Color(cardPixel(render(cell)));
        // The dark render must still be painting the literal — mutated or not
        // — at the sample point. Pinning this keeps the contrast assertion
        // honest: a stale delegate or an unpainted image could otherwise pass
        // it without the card ever being drawn.
        assertEquals(hex(constant(fillName).getRGB()), hex(fill.getRGB()),
            "the dark render does not show " + fillName + " at the sample point, so the "
                + "render is not exercising paintSelected and the contrast check below "
                + "would prove nothing");
        Color text = sectionNameColour(cell);
        assertNotNull(text, "no named label inside the section, so nothing to read");
        int contrast = Math.abs(ThemeManager.luminance(text) - ThemeManager.luminance(fill));
        assertTrue(contrast > READABLE,
            "section name " + hex(text.getRGB()) + " on the " + state + " card "
                + hex(fill.getRGB()) + " differs by only " + contrast
                + " in luminance — this is the illegibility in #79");
    }

    /**
     * The foreground of the label carrying the section name. The other label
     * in the cell holds the icon and has an empty string for text.
     */
    private static Color sectionNameColour(Container cell) {
        for (Component child : cell.getComponents()) {
            if (child instanceof JLabel) {
                JLabel candidate = (JLabel) child;
                if (candidate.getText() != null && !candidate.getText().isEmpty()) {
                    return candidate.getForeground();
                }
            }
        }
        return null;
    }

    /**
     * A pixel on the card, clear of the outline stroke at x=1 and of the
     * 32px icon centred in the cell.
     */
    private static int cardPixel(BufferedImage image) {
        return image.getRGB(5, CELL_HEIGHT / 2) & 0xFFFFFF;
    }

    /** A {@code FlowCellContent} literal, read from the class rather than hardcoded twice. */
    private static Color constant(String name) {
        try {
            Field field = FlowCellContent.class.getDeclaredField(name);
            field.setAccessible(true);
            return (Color) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("FlowCellContent." + name + " is gone — the fix in "
                + "IaColorTokens.CLASS_DARK names that field by string and is now dead. "
                + "Re-check the class.", e);
        }
    }

    private static BufferedImage render(FlowCellContent cell) {
        InlineTipLabelTest.SwingUtilitiesLayout.layout(cell);
        BufferedImage image = new BufferedImage(
            cell.getWidth(), cell.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        cell.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static String hex(int rgb) {
        return String.format("#%06X", rgb & 0xFFFFFF);
    }
}
