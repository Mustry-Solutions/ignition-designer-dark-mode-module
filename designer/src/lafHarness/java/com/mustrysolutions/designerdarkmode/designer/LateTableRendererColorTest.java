package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A table that appears while dark mode is already on (#42).
 *
 * <p>{@code captureStockColors} runs once, as a phase of the switch to dark,
 * over the windows that exist then. A table opened afterwards — a query
 * result, a Tag Editor — is wrapped by the component watcher instead, and its
 * renderers are in no colour record at all.
 *
 * <p>That did not matter while the wrappers stayed on across the light tree
 * update: {@code JTable.updateUI()} only reaches a cell renderer that is a
 * {@code Component}, and a {@code SanitizingTableRenderer} is not one, so the
 * renderer underneath was never touched. The unwrap now runs BEFORE the tree
 * update (so the light look and feel reinstalls its own renderers), which
 * exposes those originals to {@code updateUI()} — and
 * {@code DefaultTableCellRenderer.updateUI()} nulls both of its colours.
 *
 * <p>A renderer that colours itself in its constructor and is never rebuilt
 * has nothing to get those colours back from: {@code mutatedBackgrounds}
 * holds only what {@code sanitize()} overwrote, and a renderer already dark
 * enough is not sanitized at all. The colours here are chosen to fall through
 * every branch of {@code sanitize()} for exactly that reason — this is the
 * gap, not a renderer the module already tracks.
 */
class LateTableRendererColorTest {

    /**
     * Dark enough that {@code sanitize()} leaves both alone: the background is
     * below every lightness threshold and not a {@code UIResource}, and the
     * foreground is too light for the low-contrast rule.
     */
    private static final Color SELF_BACKGROUND = new Color(0x20, 0x30, 0x40);
    private static final Color SELF_FOREGROUND = new Color(0xDD, 0xDD, 0xDD);

    private CellRendererSanitizer renderers;
    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        renderers = new CellRendererSanitizer();
    }

    @AfterEach
    void leaveTheJvmLight() {
        renderers.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("a renderer wrapped after the dark switch keeps its own colours through the restore")
    void aLateWrappedRendererKeepsItsConstructorColors() {
        manager.apply(true);

        // The table arrives now — after the dark switch, so after
        // captureStockColors has already run over what existed then.
        SelfColouringRenderer own = new SelfColouringRenderer();
        JTable table = new JTable(new DefaultTableModel(
            new Object[][] {{"row"}}, new Object[] {"Name"}));
        table.getColumnModel().getColumn(0).setCellRenderer(own);
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(new JScrollPane(table), java.awt.BorderLayout.CENTER);
        panel.setSize(300, 200);

        // The component watcher's pass: wrap only, no colour capture.
        renderers.installIn(panel);

        // The light restore, in ThemeManager.apply's order: unwrap, then the
        // tree update, then the uninstall. The panel is not in a window, so
        // apply(false) switches the look and feel and walks nothing — the
        // tree update over it is driven here, exactly where apply would.
        renderers.unwrap();
        manager.apply(false);
        SwingUtilities.updateComponentTreeUI(panel);
        renderers.uninstall();

        assertEquals(SELF_BACKGROUND, own.getBackground(),
            "the renderer lost the background it set in its constructor. The early unwrap "
                + "exposes it to JTable.updateUI(), which nulls both colours, and nothing "
                + "recorded them: the table was not in the UI when captureStockColors ran, "
                + "and sanitize() had no light colour to overwrite.");
        assertEquals(SELF_FOREGROUND, own.getForeground(),
            "the renderer lost the foreground it set in its constructor");
    }

    /** The shape the record exists for: colours set once, in the constructor. */
    private static final class SelfColouringRenderer extends DefaultTableCellRenderer {
        SelfColouringRenderer() {
            setBackground(SELF_BACKGROUND);
            setForeground(SELF_FOREGROUND);
        }
    }
}
