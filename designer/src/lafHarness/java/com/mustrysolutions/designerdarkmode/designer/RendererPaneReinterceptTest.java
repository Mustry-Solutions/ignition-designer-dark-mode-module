package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.reflect.Field;

import javax.swing.CellRendererPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTableUI;
import javax.swing.table.DefaultTableModel;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The renderer-pane interception has to survive a table's UI being rebuilt.
 *
 * <h2>What went wrong</h2>
 *
 * <p>Some Designer tables resolve a renderer per cell, bypassing both the column
 * renderers and the per-class defaults — JIDE's property grids do. The only
 * thing that catches those is replacing the table's {@code CellRendererPane},
 * because every renderer paint goes through it.
 *
 * <p>That interception remembered which tables it had already done. But
 * {@code updateComponentTreeUI} rebuilds a table's UI, and a rebuilt
 * {@code BasicTableUI} installs a FRESH pane — so a table intercepted once and
 * then refreshed silently lost the interception, and every cell it painted
 * afterwards went unthemed.
 *
 * <p>Found in a Designer: the Tag Editor's property table showed white combo
 * cells carrying our own lightened text — a pale fill with light text on it,
 * illegible, and the same shape as [#47]. A probe of the live Designer showed
 * its {@code rendererPane} was a plain {@code CellRendererPane} while the Tag
 * Browser's table next to it had ours.
 */
class RendererPaneReinterceptTest {

    private ThemeManager manager;
    private CellRendererSanitizer renderers;

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
    @DisplayName("a table whose UI is rebuilt is intercepted again")
    void theInterceptionSurvivesAUiRebuild() throws Exception {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        JTable table = new JTable(new DefaultTableModel(new Object[] {"Value"}, 2));
        panel.add(table, java.awt.BorderLayout.CENTER);
        panel.setSize(300, 120);

        manager.apply(true);
        renderers.installIn(panel);

        String intercepted = paneClass(table);
        assertNotEquals("javax.swing.CellRendererPane", intercepted,
            "the pane was never intercepted, so this test cannot show it being lost");

        // What a later theme pass, a rescan, or IA's own code does routinely.
        SwingUtilities.updateComponentTreeUI(panel);
        assertEquals("javax.swing.CellRendererPane", paneClass(table),
            "the UI rebuild did not replace the pane, so the regression this "
                + "test guards cannot happen and the assertion below is empty");

        renderers.installIn(panel);

        assertEquals(intercepted, paneClass(table),
            "the table was not re-intercepted after its UI was rebuilt — every cell "
                + "it paints from here on is unthemed, which is what the Tag Editor's "
                + "white combo cells were");
    }

    /** The class of the pane the table's UI will paint renderers through. */
    private static String paneClass(JTable table) throws Exception {
        Field pane = BasicTableUI.class.getDeclaredField("rendererPane");
        pane.setAccessible(true);
        Object value = pane.get(table.getUI());
        return value == null ? "null" : ((CellRendererPane) value).getClass().getName();
    }
}
