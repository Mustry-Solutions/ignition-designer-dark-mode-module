package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tree half of {@link LateTableRendererColorTest} (#42).
 *
 * <p>A {@code RecoloringRenderer} is not a {@code Component} either, so
 * moving the unwrap ahead of the light tree update exposes a tree's own
 * renderer to {@code JTree.updateUI()} for the first time, exactly as it does
 * a table's. Whether that costs anything is a different question, and the
 * answer is in the JDK: {@code DefaultTableCellRenderer.updateUI()} nulls
 * both colours unconditionally, while {@code DefaultTreeCellRenderer
 * .updateUI()} re-reads its cached colours from {@code UIManager} and leaves
 * a colour somebody set alone. On top of that {@code TreeIconRecolorer
 * .uninstall()} re-syncs every renderer it has rendered through from the
 * light palette, rather than from a record taken earlier.
 *
 * <p>So no capture is needed on this side, and this is the measurement that
 * says so rather than the reasoning. Two tests, because the outcome alone
 * turned out to pin nothing: a late tree comes back light with the module's
 * re-sync disabled just as well as with it, since the JDK re-read covers the
 * same ground. The re-read is therefore what the tree side rests on, and it
 * belongs to {@code DefaultTreeCellRenderer}, not to us — so it is pinned
 * directly, the way this harness pins any third-party behaviour it depends
 * on. A JDK that stopped doing it would need the tree side to capture
 * colours at wrap time the way {@link CellRendererSanitizer} does, and would
 * fail here rather than in a Designer.
 */
class LateTreeRendererColorTest {

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
    @DisplayName("a tree wrapped after the dark switch draws light again after the restore")
    void aLateWrappedTreeComesBackLight() {
        manager.apply(true);

        // The tree arrives now, after the dark switch — so after the phase
        // that records colours for everything then on screen.
        JTree tree = new JTree();
        DefaultTreeCellRenderer own = new DefaultTreeCellRenderer();
        tree.setCellRenderer(own);
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(new JScrollPane(tree), java.awt.BorderLayout.CENTER);
        panel.setSize(300, 200);

        // The component watcher's pass, then one render through the wrapper —
        // what a paint does, and what puts the renderer in touchedRenderers.
        icons.installIn(panel);
        tree.getCellRenderer().getTreeCellRendererComponent(
            tree, "node", false, false, true, 0, false);

        Color dark = own.getBackgroundNonSelectionColor();
        assertTrue(ThemeManager.luminance(dark) < 128,
            "the renderer never went dark (" + dark + "), so the restore below proves nothing");

        // The light restore, in ThemeManager.apply's order: unwrap, then the
        // tree update, then the uninstall.
        icons.unwrap();
        manager.apply(false);
        SwingUtilities.updateComponentTreeUI(panel);
        icons.uninstall();

        assertEquals(UIManager.getColor("Tree.textBackground"),
            own.getBackgroundNonSelectionColor(),
            "the tree renderer did not come back on the light palette. Unlike a table "
                + "renderer it is re-synced from UIManager rather than from a record taken "
                + "at the switch to dark, so a tree that arrived later needs no capture — "
                + "unless that is no longer true.");
        assertEquals(UIManager.getColor("Tree.textForeground"),
            own.getTextNonSelectionColor(),
            "the tree renderer's text colour did not come back on the light palette");
    }

    /**
     * The JDK behaviour the test above passes on, isolated — no module, no
     * theme switch, just a renderer carrying dark {@code UIResource} colours
     * through an {@code updateUI()} under the light palette.
     *
     * <p>This is the asymmetry with tables in one assertion.
     * {@code DefaultTableCellRenderer.updateUI()} nulls its colours and hands
     * back nothing, which is why a table renderer needs its colours recorded
     * before the light tree update can reach it.
     * {@code DefaultTreeCellRenderer.updateUI()} re-reads the palette instead,
     * so the same exposure costs a tree nothing.
     */
    @Test
    @DisplayName("DefaultTreeCellRenderer.updateUI re-reads the palette, unlike the table one")
    void theTreeRendererRereadsThePaletteOnUpdateUi() {
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        Color darkEra = new javax.swing.plaf.ColorUIResource(0x2B2B2B);
        renderer.setBackgroundNonSelectionColor(darkEra);
        renderer.setTextNonSelectionColor(new javax.swing.plaf.ColorUIResource(0xDDDDDD));
        assertNotEquals(UIManager.getColor("Tree.textBackground"), darkEra,
            "the dark-era colour is what the light palette holds anyway, so the re-read "
                + "below would pass without re-reading anything");

        renderer.updateUI();

        assertEquals(UIManager.getColor("Tree.textBackground"),
            renderer.getBackgroundNonSelectionColor(),
            "DefaultTreeCellRenderer.updateUI() no longer re-reads Tree.textBackground. The "
                + "tree side of the unwrap relies on it: a tree wrapped after the switch to "
                + "dark has no colour record, so without this re-read it keeps the dark-era "
                + "colour through the restore and needs the same capture tables get.");
        assertEquals(UIManager.getColor("Tree.textForeground"),
            renderer.getTextNonSelectionColor(),
            "DefaultTreeCellRenderer.updateUI() no longer re-reads Tree.textForeground");
    }
}
