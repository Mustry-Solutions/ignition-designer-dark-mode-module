package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Text a component hard-codes dark, on a background that is already dark (#59).
 *
 * <h2>What went wrong</h2>
 *
 * <p>An EXPLICIT (non-{@code UIResource}) foreground is invisible to the look
 * and feel: {@code updateComponentTreeUI} will not touch it, by design. The
 * module lifted such foregrounds only as a RIDER on a background swap — white
 * to dark, stale-light to dark — and its standalone foreground branch required
 * the colour be a {@code UIResource}. A component that hard-codes near-black
 * text and whose background is already our dark one therefore matched no
 * branch at all and kept its dark text.
 *
 * <p>Found in a Designer on Reporting's Report Overview, where the inspector
 * reported {@code HeaderLabel} and {@code AntialiasLabel} both carrying a
 * literal {@code #454545} on our {@code #3C3F41} — a contrast ratio of about
 * 1.1:1. The labels were not merely dim; they were invisible.
 *
 * <h2>Why the restore half is in the same test</h2>
 *
 * <p>The originals were drained from {@code liftedForegrounds} inside the loop
 * over {@code whiteSwapped}, so a lift made by any OTHER branch was recorded
 * and never put back — leaving {@code #DDE0E3} text on a light background once
 * the user switched back, which reads as permanently disabled. Widening the
 * lift without fixing that would have widened the leak with it, so both halves
 * are pinned here.
 */
class HardCodedDarkTextTest {

    /** What Reporting's Report Overview labels actually carry. */
    private static final Color HARD_CODED_DARK = new Color(0x454545);
    /** What those labels actually sit on under dark mode. */
    private static final Color DARK_SURFACE = new Color(0x3C3F41);
    /** Light, but SATURATED — the swap leaves it alone as possible content. */
    private static final Color LIGHT_CONTENT = new Color(0xFFE0A0);
    /** Light and neutral — chrome, so the swap darkens it and lifts its text. */
    private static final Color LIGHT_CHROME = new Color(0xEEEEEF);

    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
    }

    @AfterEach
    void leaveTheJvmLight() {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("hard-coded dark text on a dark background is lifted, then put back")
    void darkOnDarkIsLifted() {
        JLabel label = new JLabel("LAST SCHEDULED RUN");
        label.setForeground(HARD_CODED_DARK);
        JPanel panel = panelWith(label);
        panel.setBackground(DARK_SURFACE);

        manager.apply(true);
        manager.swapWhiteTokenBackgrounds(panel);

        assertTrue(ThemeManager.luminance(label.getForeground()) > 150,
            "the label kept its hard-coded " + hex(HARD_CODED_DARK) + " on a "
                + hex(DARK_SURFACE) + " background, which is a contrast ratio of "
                + "about 1.1:1 — the Report Overview headings were invisible");

        manager.apply(false);

        assertEquals(HARD_CODED_DARK, label.getForeground(),
            "the lifted foreground was not restored, so the label now paints "
                + "near-white text on the light theme");
    }

    @Test
    @DisplayName("a lift made by the background branch is restored too")
    void aLiftFromAnyBranchIsRestored() {
        JLabel label = new JLabel("SESSION PROPS");
        label.setForeground(HARD_CODED_DARK);
        label.setBackground(LIGHT_CHROME);
        label.setOpaque(true);
        JPanel panel = panelWith(label);

        manager.apply(true);
        manager.swapWhiteTokenBackgrounds(panel);

        assertTrue(ThemeManager.luminance(label.getForeground()) > 150,
            "this label's background was darkened but its text was not lifted, so "
                + "this test cannot show the restore losing it");

        manager.apply(false);

        assertEquals(HARD_CODED_DARK, label.getForeground(),
            "a foreground lifted by the background branch was never restored — "
                + "#DDE0E3 text on the light theme, which reads as disabled");
    }

    @Test
    @DisplayName("dark text on a LIGHT background is left alone")
    void darkOnLightIsNotLifted() {
        JLabel label = new JLabel("12.5 psi");
        label.setForeground(HARD_CODED_DARK);
        label.setBackground(LIGHT_CONTENT);
        label.setOpaque(true);
        JPanel panel = panelWith(label);

        manager.apply(true);
        manager.swapWhiteTokenBackgrounds(panel);

        // The guard that keeps the widened lift from becoming a blanket one:
        // this text is perfectly readable where it is, and lifting it would
        // turn light-on-light.
        assertEquals(HARD_CODED_DARK, label.getForeground(),
            "dark text was lifted on a background that stayed light, so the lift "
                + "is unconditional and has made this label illegible instead");
    }

    private static JPanel panelWith(JLabel label) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.CENTER);
        panel.setSize(300, 60);
        return panel;
    }

    private static String hex(Color color) {
        return String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }
}
