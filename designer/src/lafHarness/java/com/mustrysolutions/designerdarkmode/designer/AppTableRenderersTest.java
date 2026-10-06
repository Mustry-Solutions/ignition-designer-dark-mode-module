package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A default renderer the application sets on a plain {@code JTable} after
 * construction survives a light→dark→light cycle.
 *
 * <p>{@code JTable}'s constructor installs the Synthetica UI, so an owner's
 * renderers always come after it, and two things on the way to dark set the
 * renderer back without looking at it: Synthetica's
 * {@code ComponentPropertyStore}, restored in {@code uninitialize}, and
 * {@code SynthTableUI.uninstallDefaults}, in the tree walk. The custom
 * renderer was gone in dark and, its slot then holding a {@code UIResource},
 * replaced by Synthetica's own in light. See {@link AppTableRenderers}.
 *
 * <p>Windowed, because only the walks over {@code Window.getWindows()} reach
 * a table, and headlessly that list is empty.
 */
@ExtendWith(RunOnEdt.class)
class AppTableRenderersTest {

    private ThemeManager manager;
    private final List<Window> frames = new ArrayList<>();

    @BeforeAll
    static void requireADisplay() {
        boolean headless = GraphicsEnvironment.isHeadless();
        if (Boolean.getBoolean(WindowedCycleTest.WINDOWED_PROPERTY)) {
            assertFalse(headless, "-Pharness.windowed=true was given, but this JVM is headless");
        }
        Assumptions.assumeFalse(headless,
            "no display: the tree walk that swaps a table's UI reaches no window. Run with "
                + "-Pharness.windowed=true on a machine with a display.");
    }

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = ManagerCleanup.newManager();
        manager.captureStockLaf();
    }

    @AfterEach
    void leaveTheJvmLightAndWindowless() {
        try {
            if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
                manager.apply(false);
            }
        } finally {
            frames.forEach(Window::dispose);
            frames.clear();
        }
    }

    @Test
    @DisplayName("custom Object and Boolean default renderers survive a dark/light cycle")
    void customDefaultRenderersSurviveACycle() {
        JTable table = new JTable(new DefaultTableModel(
            new Object[][] {{"a", Boolean.TRUE}}, new Object[] {"Name", "Enabled"}) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 1 ? Boolean.class : Object.class;
            }
        });
        AppObjectRenderer objectRenderer = new AppObjectRenderer();
        AppBooleanRenderer booleanRenderer = new AppBooleanRenderer();
        // After construction, as an owner does: the Synthetica UI is
        // already installed by now.
        table.setDefaultRenderer(Object.class, objectRenderer);
        table.setDefaultRenderer(Boolean.class, booleanRenderer);
        frame(table);

        manager.apply(true);
        assertEquals(List.of(), manager.failedPhases());
        // Under dark the sanitizer may wrap either renderer, so ask what a
        // cell actually paints with rather than which object is installed.
        assertSame(objectRenderer.component, cell(table, Object.class, "a"),
            "the custom Object renderer no longer paints its cells under dark; the default "
                + "renderer is " + describe(table.getDefaultRenderer(Object.class)));
        assertSame(booleanRenderer.component, cell(table, Boolean.class, Boolean.TRUE),
            "the custom Boolean renderer no longer paints its cells under dark; the default "
                + "renderer is " + describe(table.getDefaultRenderer(Boolean.class)));

        manager.apply(false);
        assertEquals(List.of(), manager.failedPhases());
        assertSame(objectRenderer, table.getDefaultRenderer(Object.class),
            "the custom Object renderer was lost across the cycle; the table now has "
                + describe(table.getDefaultRenderer(Object.class)));
        assertSame(booleanRenderer, table.getDefaultRenderer(Boolean.class),
            "the custom Boolean renderer was lost across the cycle; the table now has "
                + describe(table.getDefaultRenderer(Boolean.class)));
    }

    /**
     * The other half: a table with no renderer of its own still gets the look
     * and feel's. The JDK's {@code SynthTableUI} renderers are not
     * {@code UIResource}s, and one kept into dark would paint Synth cells
     * under FlatLaf.
     */
    @Test
    @DisplayName("a table with only the look and feel's renderers is left to the look and feel")
    void lookAndFeelRenderersAreNotKept() {
        JTable table = new JTable(new DefaultTableModel(
            new Object[][] {{"a", Boolean.TRUE}}, new Object[] {"Name", "Enabled"}));
        Class<?> lightObject = table.getDefaultRenderer(Object.class).getClass();
        Class<?> lightBoolean = table.getDefaultRenderer(Boolean.class).getClass();
        frame(table);

        manager.apply(true);
        assertEquals(List.of(), manager.failedPhases());
        for (Class<?> valueClass : new Class<?>[] {Object.class, Boolean.class}) {
            Component component = cell(table, valueClass,
                valueClass == Boolean.class ? Boolean.TRUE : "a");
            assertFalse(component.getClass().getName().startsWith("javax.swing.plaf.synth."),
                "a Synth " + valueClass.getSimpleName() + " renderer was kept into dark: "
                    + component.getClass().getName());
        }

        manager.apply(false);
        assertEquals(List.of(), manager.failedPhases());
        assertSame(lightObject, table.getDefaultRenderer(Object.class).getClass(),
            "the light Object renderer is not the stock one after a cycle");
        assertSame(lightBoolean, table.getDefaultRenderer(Boolean.class).getClass(),
            "the light Boolean renderer is not the stock one after a cycle");
    }

    private static Component cell(JTable table, Class<?> valueClass, Object value) {
        return table.getDefaultRenderer(valueClass)
            .getTableCellRendererComponent(table, value, false, false, 0, 0);
    }

    private static String describe(TableCellRenderer renderer) {
        return renderer == null ? "null" : renderer.getClass().getName();
    }

    private void frame(JTable table) {
        JFrame frame = new JFrame("custom table renderers");
        frames.add(frame);
        frame.getContentPane().add(new JScrollPane(table));
        frame.pack();
    }

    /** An application's renderer: not a UIResource, so a look and feel must leave it. */
    private static final class AppObjectRenderer implements TableCellRenderer {

        final JLabel component = new JLabel();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            component.setText(String.valueOf(value));
            return component;
        }
    }

    private static final class AppBooleanRenderer implements TableCellRenderer {

        final JCheckBox component = new JCheckBox();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            component.setSelected(Boolean.TRUE.equals(value));
            return component;
        }
    }
}
