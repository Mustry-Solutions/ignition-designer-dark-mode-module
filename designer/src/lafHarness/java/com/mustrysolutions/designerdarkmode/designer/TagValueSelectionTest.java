package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Function;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A selected tag's Value cell takes the theme's selection colour (#41).
 *
 * <p>The Tag Browser's Value column, and the Config column of its UDT
 * Definitions tab, are painted by IA's {@code ValueRenderer}, which copies
 * four {@code Tree.*} colours into a private map in its constructor and has
 * no {@code updateUI}. Built at startup in the light theme, it gave a
 * selected row the stock #47A9E6 under dark, with white text, beside the
 * dark-blue Tag cell. The map is re-synced from {@code UIManager} for every
 * cell painted under dark, and again on the way back to light.
 */
class TagValueSelectionTest {

    private ThemeManager manager;
    private CellRendererSanitizer renderers;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = ManagerCleanup.newManager();
        manager.captureStockLaf();
        renderers = new CellRendererSanitizer();
    }

    @AfterEach
    void leaveTheJvmLight() {
        renderers.unwrap();
        renderers.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("a value renderer built light paints a selected cell in the dark selection colour")
    void aSelectedCellTakesTheDarkSelection() throws Exception {
        TableCellRenderer valueRenderer = valueRenderer();
        Color stock = colors(valueRenderer).get("background-selected");
        JTable table = tagTable(valueRenderer);
        JPanel panel = inPanel(table);

        manager.apply(true);
        renderers.installIn(panel);
        table.setRowSelectionInterval(0, 0);
        Component cell = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);

        assertEquals(UIManager.getColor("Tree.selectionBackground"), cell.getBackground(),
            "the selected Value cell kept " + hex(stock) + ", the light theme's selection, "
                + "under dark");
        assertTrue(ThemeManager.luminance(cell.getBackground()) < 140,
            "the dark selection colour is not dark");

        manager.apply(false);
        renderers.uninstall();

        assertEquals(stock, colors(valueRenderer).get("background-selected"),
            "the restore left the dark selection in the renderer's map, so a selected "
                + "tag is dark blue in the light theme");
    }

    @Test
    @DisplayName("a value renderer built dark is given the light colours by the light restore's walk")
    void aRendererBuiltDarkIsResyncedOnTheWayBack() throws Exception {
        manager.apply(true);
        TableCellRenderer valueRenderer = valueRenderer();
        Color dark = colors(valueRenderer).get("background-selected");
        JPanel panel = inPanel(tagTable(valueRenderer));

        manager.apply(false);
        manager.refreshComponentsLeftDark(panel);

        Color light = UIManager.getColor("Tree.selectionBackground");
        assertTrue(!light.equals(dark), "this look and feel's light and dark selections are "
            + "the same colour, so this test shows nothing");
        assertEquals(light, colors(valueRenderer).get("background-selected"),
            "a Tag Browser rebuilt under dark kept a dark selection after the restore");
    }

    private static TableCellRenderer valueRenderer() throws Exception {
        Class<?> contextType =
            Class.forName("com.inductiveautomation.ignition.designer.model.DesignerContext");
        return (TableCellRenderer) Class.forName(
            "com.inductiveautomation.ignition.designer.tags.tree.ValueRenderer")
            .getConstructor(contextType).newInstance(stub(contextType, method -> null));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Color> colors(TableCellRenderer renderer) throws Exception {
        return (Map<String, Color>) renderer.getClass().getMethod("getColors").invoke(renderer);
    }

    /** One row whose value is a tag node holding a good value, as the Tag Browser's are. */
    private static JTable tagTable(TableCellRenderer valueRenderer) throws Exception {
        Class<?> qualityType =
            Class.forName("com.inductiveautomation.ignition.common.model.values.QualityCode");
        Object good = qualityType.getField("Good").get(null);
        Class<?> qualifiedValueType =
            Class.forName("com.inductiveautomation.ignition.common.model.values.QualifiedValue");
        Object value = stub(qualifiedValueType, method -> {
            switch (method.getName()) {
                case "getQuality": return args -> good;
                case "getValue": return args -> 7;
                default: return null;
            }
        });
        Object node = stub(Class.forName(
            "com.inductiveautomation.ignition.client.tags.tree.node.ValueNode"),
            method -> "getValue".equals(method.getName()) ? args -> value : null);
        JTable table = new JTable(new DefaultTableModel(new Object[][] {{node}}, new Object[] {"Value"}));
        table.getColumnModel().getColumn(0).setCellRenderer(valueRenderer);
        return table;
    }

    private static JPanel inPanel(JTable table) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(table, BorderLayout.CENTER);
        panel.setSize(300, 100);
        panel.doLayout();
        return panel;
    }

    private static String hex(Color color) {
        return color == null ? "null" : String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }

    /** As in OwnedRendererCastTest: answers the named methods, zero values for the rest. */
    private static Object stub(Class<?> type, Function<Method, Function<Object[], Object>> answers) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> {
                Function<Object[], Object> answer = answers.apply(method);
                if (answer != null) {
                    return answer.apply(args);
                }
                if (method.getDeclaringClass() == Object.class) {
                    switch (method.getName()) {
                        case "equals": return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        default: return type.getSimpleName() + " stub";
                    }
                }
                Class<?> returns = method.getReturnType();
                if (returns == boolean.class) {
                    return false;
                }
                if (returns == int.class) {
                    return 0;
                }
                if (returns == long.class) {
                    return 0L;
                }
                return null;
            });
    }
}
