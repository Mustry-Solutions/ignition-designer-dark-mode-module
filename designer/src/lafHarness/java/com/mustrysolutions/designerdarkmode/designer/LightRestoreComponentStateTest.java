package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.client.util.gui.SimpleTreeTable;
import com.jidesoft.grid.QuickFilterField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a light&rarr;dark&rarr;light cycle leaves behind on the COMPONENTS, as
 * opposed to on {@code UIManager} (#45).
 *
 * <p>{@link ThemeSwitchCycleTest} pins the defaults tables, and they came back
 * clean the whole time these two bugs were on screen: the Tag Browser's {@code
 * Value} header and the filter above the Perspective property editor both
 * stayed dark after switching back to light. Neither colour lives in a default.
 * Both live on a component — or, worse, on a cell renderer, which is not even
 * in the hierarchy.
 *
 * <p>So this test builds the two real components, drives the module's real
 * passes over them in the order {@link ThemeManager#apply} runs them, and
 * compares component state before and after. It is the component-level
 * counterpart the defaults diff has always been missing, and the reason the
 * harness could report a perfect restore while a Designer showed two obvious
 * blemishes.
 *
 * <h2>The two mechanisms</h2>
 *
 * <ul>
 *   <li><b>The header renderer.</b> {@code JTableHeader.updateUI()} calls
 *       {@code updateComponentTreeUI} on its default renderer — but only when
 *       that renderer is a {@code Component}. Going dark it is, so Swing calls
 *       {@code DefaultTableCellRenderer.updateUI()}, which is {@code
 *       super.updateUI(); setForeground(null); setBackground(null);} —
 *       destroying the colours {@code SimpleHeaderRenderer} set in its
 *       constructor and never sets again. Coming back the renderer is wrapped
 *       in a {@code SanitizingTableRenderer}, which is NOT a Component, so
 *       Swing skips it: no colours, and a FlatLaf delegate, for the rest of
 *       the session.</li>
 *   <li><b>The filter field.</b> {@code updateComponentTreeUI} walks parent
 *       first, and JIDE's {@code LabeledTextField.updateUI()} ends in {@code
 *       setEnabled()}, which does {@code setBackground(getTextField()
 *       .getBackground())}. On the way back to light the inner field is still
 *       dark at that instant, so the wrapper copies FlatLaf's {@code #46494B}
 *       onto itself and keeps it.</li>
 * </ul>
 */
class LightRestoreComponentStateTest {

    private ThemeManager manager;
    private CellRendererSanitizer renderers;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        // Class-init under the LIGHT theme, as a Designer does: the renderer
        // reads Panel.background in its constructor and never re-reads it.
        Class.forName(SimpleTreeTable.class.getName());
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
    @DisplayName("the Tag Browser's header renderer comes back exactly as it was (#45)")
    void theHeaderRendererComesBack() {
        JPanel panel = designerPanel();
        Map<String, String> stock = rendererState(panel);

        goDark(panel);
        assertNotEquals(stock, rendererState(panel),
            "dark mode left the header renderer untouched, so restoring it proves nothing");

        goLight(panel);

        assertEquals(stock, rendererState(panel),
            "the Tag Browser's Value header did not come back to its stock state");
    }

    @Test
    @DisplayName("nothing is left holding a dark look-and-feel colour (#45)")
    void nothingStaysDark() {
        JPanel panel = designerPanel();

        goDark(panel);
        assertNotEquals(List.of(), darkComponents(panel),
            "nothing went dark, so finding nothing dark afterwards proves nothing");

        goLight(panel);

        assertEquals(List.of(), darkComponents(panel),
            "these components kept a dark look-and-feel colour through the light restore");
    }

    /**
     * Components still holding a dark UIResource background. UIResource is the
     * point: it means the look and feel put it there, so under the light theme
     * it can only be a leftover.
     */
    private static List<String> darkComponents(Container root) {
        List<String> dark = new ArrayList<>();
        collectDark(root, dark);
        return dark;
    }

    private static void collectDark(Component component, List<String> dark) {
        if (component.isBackgroundSet()
                && component.getBackground() instanceof UIResource
                && ThemeManager.luminance(component.getBackground()) < 100) {
            dark.add(component.getClass().getName()
                + String.format(" bg=#%06X", component.getBackground().getRGB() & 0xFFFFFF));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectDark(child, dark);
            }
        }
    }

    /**
     * The header renderer's state, read the way a paint reads it — by asking
     * the renderer for its component, which is where a cell renderer's state
     * lives. Nothing in the hierarchy holds it.
     */
    private static Map<String, String> rendererState(JPanel panel) {
        JTableHeader header = header(panel);
        TableCellRenderer renderer = header.getDefaultRenderer();
        Component stamp = renderer.getTableCellRendererComponent(
            header.getTable(), "Value", false, false, -1, 0);
        Map<String, String> state = new LinkedHashMap<>();
        state.put("background", colour(stamp.isBackgroundSet() ? stamp.getBackground() : null));
        state.put("foreground", colour(stamp.isForegroundSet() ? stamp.getForeground() : null));
        if (stamp instanceof javax.swing.JComponent) {
            javax.swing.JComponent swing = (javax.swing.JComponent) stamp;
            state.put("border", String.valueOf(swing.getBorder()));
            // The delegate matters on its own: a renderer left on a FlatLaf UI
            // paints through FlatLaf for the rest of the session, however
            // right its colours are.
            state.put("ui", swing.getUI() == null ? "null" : swing.getUI().getClass().getName());
        }
        return state;
    }

    private static String colour(Color colour) {
        if (colour == null) {
            return "unset";
        }
        return String.format("#%06X%s", colour.getRGB() & 0xFFFFFF,
            colour instanceof UIResource ? "|uires" : "|explicit");
    }

    /** Everything {@code apply(true)} does that can reach a detached tree. */
    private void goDark(JPanel panel) {
        renderers.captureStockColorsIn(panel);
        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(panel);
        renderers.install();
        renderers.installIn(panel);
        manager.swapWhiteTokenBackgrounds(panel);
    }

    /** The same for {@code apply(false)}, in the order that switch runs them. */
    private void goLight(JPanel panel) {
        manager.apply(false);
        SwingUtilities.updateComponentTreeUI(panel);
        renderers.uninstall();
        manager.refreshComponentsLeftDark(panel);
    }

    private static JTableHeader header(JPanel panel) {
        for (Component child : panel.getComponents()) {
            if (child instanceof SimpleTreeTable) {
                return (JTableHeader) ((SimpleTreeTable) child).getColumnHeader().getView();
            }
        }
        throw new IllegalStateException("no tree table in the panel");
    }

    /**
     * The two panels this is about: the Tag Browser's tree table, and a JIDE
     * {@link QuickFilterField} — the class the Perspective property editor
     * uses for its filter ({@code PropertyEditorFrame.filterField}).
     */
    private static JPanel designerPanel() {
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Value"}, 0);
        for (int row = 0; row < 6; row++) {
            model.addRow(new Object[] {"value " + row});
        }
        JTable table = new JTable(model);
        SimpleTreeTable pane = new SimpleTreeTable(new JTree(), table, "Tag");
        // What configureEnclosingScrollPane() does once a Designer's hierarchy
        // is realized; nothing is realized here.
        pane.setColumnHeaderView(table.getTableHeader());

        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setOpaque(true);
        panel.add(pane, java.awt.BorderLayout.CENTER);
        panel.add(new QuickFilterField() {
            @Override
            public void applyFilter(String text) {
            }
        }, java.awt.BorderLayout.NORTH);
        panel.setSize(336, 240);
        assertTrue(panel.getComponentCount() == 2, "the panel under test lost a component");
        return panel;
    }
}
