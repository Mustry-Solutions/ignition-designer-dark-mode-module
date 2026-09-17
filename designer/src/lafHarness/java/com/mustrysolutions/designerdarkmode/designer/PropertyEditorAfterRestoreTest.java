package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jidesoft.grid.DefaultProperty;
import com.jidesoft.grid.Property;
import com.jidesoft.grid.PropertyTable;
import com.jidesoft.grid.PropertyTableModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Vision Property Editor after a dark-mode drop-out (#102).
 *
 * <p>The Vision Property Editor is a JIDE {@code PropertyTable}, and its
 * category rows are painted by a {@code MarginExpandablePanel} that asks the
 * table for its category icon. In a Designer that icon is Ignition's vector
 * chevron, put into the developer defaults table by
 * {@code IgnitionLookAndFeel.init()} at startup. Synthetica's uninstall, run
 * when FlatLaf goes in, clears that table; the stock reinstall lets JIDE put
 * its own defaults back but not Ignition's, so the category icon falls
 * through to {@code Tree.expandedIcon} — Synthetica's painter, which outside
 * a Synth context hands Ignition's {@code TreeExpandedIconPainter} a context
 * with no component. Every paint of the editor then throws, and it is blank.
 *
 * <p>The harness's stock install runs {@code IgnitionLookAndFeel.init()}, so
 * the icon is there to lose. The light half of the table cannot be rendered
 * headlessly (Synthetica's image painter needs a screen), but the category
 * row's renderer can be painted on its own, and that is the paint that died.
 */
class PropertyEditorAfterRestoreTest {

    private static final String CATEGORY_ICON = "CategorizedTable.categoryExpandedIcon";

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
    @DisplayName("Ignition's category icon survives a cycle, and the category row still paints (#102)")
    void categoryIconSurvivesTheCycle() {
        Icon stock = UIManager.getIcon(CATEGORY_ICON);
        assertNotNull(stock, "the stock install must put Ignition's category icon, or there is nothing to lose");
        assertEquals("com.inductiveautomation.ignition.client.icons.VectorIcon", stock.getClass().getName(),
            "the stock category icon is Ignition's own, not a look-and-feel icon");

        JPanel panel = propertyEditor();
        PropertyTable table = table(panel);
        Component categoryRow = categoryRow(table);
        assertEquals("", tryPaint(categoryRow), "the category row must paint at stock");

        goDark(panel);
        assertNotSame(stock, UIManager.getIcon(CATEGORY_ICON),
            "dark mode left Ignition's icon in place, so bringing it back proves nothing");

        goLight(panel);

        assertSame(stock, UIManager.getIcon(CATEGORY_ICON),
            "Ignition's category icon did not come back after the restore; JIDE property "
                + "tables fall through to Synthetica's tree icon and cannot paint");
        assertSame(stock, table.getCategoryExpandedIcon(),
            "the table itself still holds a look-and-feel icon after the restore");
        assertEquals("", tryPaint(categoryRow(table)),
            "painting the property table's category row after the restore failed");
    }

    // --- fixture --------------------------------------------------------------

    private static JPanel propertyEditor() {
        List<Property> properties = new ArrayList<>();
        properties.add(property("Name", "Common", "Text Field"));
        properties.add(property("Visible", "Common", Boolean.TRUE));
        properties.add(property("Editable", "Behavior", Boolean.TRUE));
        PropertyTable table = new PropertyTable(new PropertyTableModel<>(properties));
        table.expandAll();
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(new JScrollPane(table));
        panel.setSize(400, 300);
        layout(panel);
        return panel;
    }

    private static Property property(String name, String category, Object value) {
        DefaultProperty property = new DefaultProperty();
        property.setName(name);
        property.setCategory(category);
        property.setType(value.getClass());
        property.setValue(value);
        return property;
    }

    private static PropertyTable table(JPanel panel) {
        return (PropertyTable) ((JScrollPane) panel.getComponent(0)).getViewport().getView();
    }

    /** The renderer component of the first row, a category — the MarginExpandablePanel. */
    private static Component categoryRow(PropertyTable table) {
        Component stamp = table.getCellRenderer(0, 0)
            .getTableCellRendererComponent(table, table.getValueAt(0, 0), false, false, 0, 0);
        stamp.setSize(200, 20);
        return stamp;
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

    /** Paint into an image; the first line of whatever it threw, or "". */
    private static String tryPaint(Component component) {
        BufferedImage image = new BufferedImage(
            Math.max(1, component.getWidth()), Math.max(1, component.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
            return "";
        } catch (Throwable t) {
            return t + (t.getStackTrace().length > 0 ? " at " + t.getStackTrace()[0] : "");
        } finally {
            graphics.dispose();
        }
    }

    private static void layout(Component component) {
        component.doLayout();
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }
}
