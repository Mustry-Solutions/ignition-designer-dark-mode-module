package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The LOOK-AND-FEEL defaults table, as distinct from the developer defaults
 * table, across a switch cycle.
 *
 * <p>{@code UIManager.put} writes to the developer table. {@code UIManager.get}
 * reads the developer table first and falls back to the look-and-feel table.
 * But {@code UIManager.getLookAndFeelDefaults()} reads the look-and-feel table
 * <em>only</em> — so a key written by the module, by Synthetica's compatibility
 * defaults, or by {@code installJideExtension()} is invisible through it.
 *
 * <p>Under the Designer's stock look and feel that is a wide gap, and it is a
 * gap the module does not create: it is the same size before dark mode and
 * after the restore. Any Ignition code reading a colour through
 * {@code getLookAndFeelDefaults()} therefore gets {@code null} in a stock
 * Designer, and a real colour under dark mode — the opposite of the direction
 * one would guess. Pinned here because it is load-bearing for reading any
 * "why is this null after the restore" stack trace correctly, and because the
 * first such trace was misread this way.
 *
 * <h2>The trace that prompted this, and why it is not about this table</h2>
 *
 * <p>A 2026-08-29 Designer sweep caught the light restore's
 * {@code updateComponentTreeUI} aborting for the whole main frame with
 * {@code NullPointerException: … "newColor" is null} out of
 * {@code BasicContainer.adjustOpacityBasedOnBackgroundColor}, under
 * {@code DockingInternalFrameUI.installDefaults} line 68.
 *
 * <p>That method does read {@code getLookAndFeelDefaults().getColor("control")}
 * — which this test confirms is null under stock — but at line 71, <em>after</em>
 * the throw point. Line 68 is bytecode offset 58 of
 * {@code vision-client-12.3.6.jar}, which is an explicit null:
 *
 * <pre>
 * JComponent contentPane = (JComponent) frame.getContentPane();
 * if (contentPane != null &amp;&amp; contentPane.getBackground() instanceof UIResource) {
 *     contentPane.setBackground(null);          // line 68
 * }
 * frame.setBackground(UIManager.getLookAndFeelDefaults().getColor("control"));  // line 71
 * </pre>
 *
 * <p>A Vision window's content pane is a {@code BasicContainer}, whose
 * {@code setBackground} override cannot take null. So the trigger is the
 * content pane's background being a {@code UIResource} when
 * {@code installDefaults} re-runs — which is what a preceding
 * {@code updateComponentTreeUI} leaves behind, and which never happens in a
 * stock Designer because {@code installDefaults} runs once, at construction.
 * The fix belongs on our side of that boundary, not in this table.
 */
class LookAndFeelDefaultsTableTest {

    /** The key Vision reads through {@code getLookAndFeelDefaults()}. */
    private static final String CONTROL = "control";

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

    /** Colour keys that resolve through {@code UIManager} but are not in the LaF table. */
    private static List<String> coloursMissingFromTheLookAndFeelTable() {
        List<String> missing = new ArrayList<>();
        for (Object key : new ArrayList<>(UIManager.getDefaults().keySet())) {
            if (!(key instanceof String)) {
                continue;
            }
            Object resolved;
            try {
                resolved = UIManager.get(key);
            } catch (Throwable t) {
                continue;
            }
            if (resolved instanceof Color
                    && UIManager.getLookAndFeelDefaults().get(key) == null) {
                missing.add((String) key);
            }
        }
        Collections.sort(missing);
        return missing;
    }

    @Test
    @DisplayName("\"control\" is absent from the look-and-feel table under stock, present under dark")
    void controlIsAbsentFromTheLookAndFeelTableUnderStock() {
        assertNull(UIManager.getLookAndFeelDefaults().getColor(CONTROL),
            "stock: getLookAndFeelDefaults() should not resolve " + CONTROL);
        assertNotNull(UIManager.getColor(CONTROL),
            "stock: UIManager should resolve " + CONTROL + " from the developer table");

        manager.apply(true);
        assertNotNull(UIManager.getLookAndFeelDefaults().getColor(CONTROL),
            "dark: FlatLaf defines " + CONTROL + " in its own table");

        manager.apply(false);
        assertNull(UIManager.getLookAndFeelDefaults().getColor(CONTROL),
            "restored: the table should be back to its stock shape");
        assertNotNull(UIManager.getColor(CONTROL),
            "restored: " + CONTROL + " must still resolve, or #23 is back");
    }

    @Test
    @DisplayName("the restore leaves the look-and-feel table's colour gap exactly as it found it")
    void theRestoreDoesNotWidenTheGap() {
        List<String> stock = coloursMissingFromTheLookAndFeelTable();

        manager.apply(true);
        List<String> dark = coloursMissingFromTheLookAndFeelTable();

        manager.apply(false);
        List<String> restored = coloursMissingFromTheLookAndFeelTable();

        // Dark is expected to be far smaller: FlatLaf puts its whole palette in
        // its own table, so most keys resolve there rather than only in the
        // developer table. The invariant that matters is the round trip.
        assertEquals(stock, restored,
            "the restore changed which colours are reachable through"
                + " getLookAndFeelDefaults(); stock=" + stock.size()
                + " dark=" + dark.size() + " restored=" + restored.size());
    }
}
