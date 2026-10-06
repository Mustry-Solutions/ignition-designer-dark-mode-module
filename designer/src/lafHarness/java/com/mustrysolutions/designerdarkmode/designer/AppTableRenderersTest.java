package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.ui.FlatTableUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A default renderer or editor the application sets on a plain
 * {@code JTable} after construction survives a light→dark→light cycle.
 *
 * <p>{@code JTable}'s constructor installs the Synthetica UI, so an owner's
 * renderers always come after it, and two things on the way to dark set the
 * renderer back without looking at it: Synthetica's
 * {@code ComponentPropertyStore}, restored in {@code uninitialize}, and
 * {@code SynthTableUI.uninstallDefaults}, in the tree walk. The custom
 * renderer was gone in dark and, its slot then holding a {@code UIResource},
 * replaced by Synthetica's own in light. The store does the same to the
 * Object and Number editors. See {@link AppTableRenderers} and
 * {@link SyntheticaPropertyStore}.
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
        objectRenderer.calls = 0;
        booleanRenderer.calls = 0;
        paint(table);
        assertTrue(objectRenderer.calls > 0, "painting the table under dark never asked the "
            + "custom Object renderer for a cell");
        assertTrue(booleanRenderer.calls > 0, "painting the table under dark never asked the "
            + "custom Boolean renderer for a cell");

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
     * Date and Number inherit the owner's Object renderer. From the second
     * switch to dark on, {@code SynthTableUI} saved that inherited renderer at
     * install and writes it back as Date's and Number's own, so a later
     * change to the Object renderer stopped reaching them.
     */
    @Test
    @DisplayName("inherited renderers still follow the Object renderer after repeated cycles")
    void inheritedRenderersStillFollowObjectAfterRepeatedCycles() {
        JTable table = new JTable(new DefaultTableModel(
            new Object[][] {{"a"}}, new Object[] {"Name"}));
        table.setDefaultRenderer(Object.class, new AppObjectRenderer());
        frame(table);

        for (int cycle = 0; cycle < 2; cycle++) {
            manager.apply(true);
            assertEquals(List.of(), manager.failedPhases());
            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases());
        }

        AppObjectRenderer later = new AppObjectRenderer();
        table.setDefaultRenderer(Object.class, later);
        for (Class<?> valueClass : new Class<?>[] {java.util.Date.class, Number.class}) {
            assertSame(later, table.getDefaultRenderer(valueClass),
                valueClass.getSimpleName() + " kept a renderer of its own after two cycles ("
                    + describe(table.getDefaultRenderer(valueClass)) + ") and no longer "
                    + "inherits the Object renderer");
        }
    }

    /**
     * Synthetica's write-back reaches every table it styled, not only those in
     * a window: a panel the Designer keeps off-screen while the theme changes
     * lost its table's renderer before anything could record it.
     */
    @Test
    @DisplayName("a table outside any window keeps its renderers and editor through a cycle")
    void tableOutsideAnyWindowKeepsItsRenderersAndEditor() {
        JTable table = new JTable(new DefaultTableModel(
            new Object[][] {{"a", Boolean.TRUE}}, new Object[] {"Name", "Enabled"}));
        AppObjectRenderer objectRenderer = new AppObjectRenderer();
        AppBooleanRenderer booleanRenderer = new AppBooleanRenderer();
        TableCellEditor editor = new DefaultCellEditor(new JTextField());
        table.setDefaultRenderer(Object.class, objectRenderer);
        table.setDefaultRenderer(Boolean.class, booleanRenderer);
        table.setDefaultEditor(Object.class, editor);
        JPanel offScreen = new JPanel();
        offScreen.add(new JScrollPane(table));

        manager.apply(true);
        assertEquals(List.of(), manager.failedPhases());
        // What the component watcher does when the panel is shown under dark.
        ThemeManager.refreshStaleUiDelegates(offScreen);
        assertSame(objectRenderer.component, cell(table, Object.class, "a"),
            "the off-screen table's Object renderer was lost under dark; it now has "
                + describe(table.getDefaultRenderer(Object.class)));
        assertSame(booleanRenderer.component, cell(table, Boolean.class, Boolean.TRUE),
            "the off-screen table's Boolean renderer was lost under dark; it now has "
                + describe(table.getDefaultRenderer(Boolean.class)));
        assertSame(editor, table.getDefaultEditor(Object.class),
            "the off-screen table's Object editor was lost under dark");

        manager.apply(false);
        assertEquals(List.of(), manager.failedPhases());
        ThemeManager.refreshStaleUiDelegates(offScreen);
        assertSame(objectRenderer, table.getDefaultRenderer(Object.class),
            "the off-screen table's Object renderer was lost across the cycle; it now has "
                + describe(table.getDefaultRenderer(Object.class)));
        assertSame(booleanRenderer, table.getDefaultRenderer(Boolean.class),
            "the off-screen table's Boolean renderer was lost across the cycle; it now has "
                + describe(table.getDefaultRenderer(Boolean.class)));
        assertSame(editor, table.getDefaultEditor(Object.class),
            "the off-screen table's Object editor was lost across the cycle");
    }

    @Test
    @DisplayName("custom Object and Number default editors survive repeated dark/light cycles")
    void customDefaultEditorsSurviveRepeatedCycles() {
        JTable table = new JTable(new DefaultTableModel(
            new Object[][] {{"a", 1}}, new Object[] {"Name", "Count"}) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 1 ? Number.class : Object.class;
            }
        });
        DefaultCellEditor objectEditor = new DefaultCellEditor(new JTextField());
        DefaultCellEditor numberEditor = new DefaultCellEditor(new JTextField());
        table.setDefaultEditor(Object.class, objectEditor);
        table.setDefaultEditor(Number.class, numberEditor);
        frame(table);

        for (int cycle = 1; cycle <= 2; cycle++) {
            manager.apply(true);
            assertEquals(List.of(), manager.failedPhases());
            assertSame(objectEditor, table.getDefaultEditor(Object.class),
                "cycle " + cycle + ": the custom Object editor was replaced under dark by "
                    + table.getDefaultEditor(Object.class).getClass().getName());
            assertSame(numberEditor, table.getDefaultEditor(Number.class),
                "cycle " + cycle + ": the custom Number editor was replaced under dark by "
                    + table.getDefaultEditor(Number.class).getClass().getName());
            // And it is what an edit actually starts.
            assertEditsWith(table, 0, objectEditor, "cycle " + cycle + ", dark");
            assertEditsWith(table, 1, numberEditor, "cycle " + cycle + ", dark");

            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases());
            assertSame(objectEditor, table.getDefaultEditor(Object.class),
                "cycle " + cycle + ": the custom Object editor was lost; the table now has "
                    + table.getDefaultEditor(Object.class).getClass().getName());
            assertSame(numberEditor, table.getDefaultEditor(Number.class),
                "cycle " + cycle + ": the custom Number editor was lost; the table now has "
                    + table.getDefaultEditor(Number.class).getClass().getName());
            assertEditsWith(table, 0, objectEditor, "cycle " + cycle + ", light");
        }
    }

    /**
     * The Designer attaches panels it built earlier, often under the other
     * look and feel, and the component watcher corrects them as they arrive.
     * This drives that real path: the attach fires the watcher's container
     * listener, and the rescan runs the passes that follow, the renderer
     * sanitizer among them.
     */
    @Test
    @DisplayName("a table attached while dark is corrected by the watcher and keeps its own")
    void tableAttachedWhileDarkKeepsItsOwn() throws Exception {
        JTable table = new JTable(new DefaultTableModel(
            new Object[][] {{"a", Boolean.TRUE}}, new Object[] {"Name", "Enabled"}) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 1 ? Boolean.class : Object.class;
            }
        });
        AppObjectRenderer objectRenderer = new AppObjectRenderer();
        AppBooleanRenderer booleanRenderer = new AppBooleanRenderer();
        DefaultCellEditor editor = new DefaultCellEditor(new JTextField());
        table.setDefaultRenderer(Object.class, objectRenderer);
        table.setDefaultRenderer(Boolean.class, booleanRenderer);
        table.setDefaultEditor(Object.class, editor);
        JPanel builtEarlier = new JPanel();
        builtEarlier.add(new JScrollPane(table));
        JPanel host = new JPanel();
        frame(host);

        manager.apply(true);
        assertEquals(List.of(), manager.failedPhases());
        host.add(builtEarlier);
        assertTrue(table.getUI() instanceof FlatTableUI,
            "the watcher did not refresh the attached table; its UI is "
                + table.getUI().getClass().getName());
        Method rescan = ThemeManager.class.getDeclaredMethod("rescanTick");
        rescan.setAccessible(true);
        rescan.invoke(manager);

        assertSame(objectRenderer.component, cell(table, Object.class, "a"),
            "the attached table's Object renderer was lost under dark; it now has "
                + describe(table.getDefaultRenderer(Object.class)));
        assertSame(booleanRenderer.component, cell(table, Boolean.class, Boolean.TRUE),
            "the attached table's Boolean renderer was lost under dark; it now has "
                + describe(table.getDefaultRenderer(Boolean.class)));
        objectRenderer.calls = 0;
        paint(table);
        assertTrue(objectRenderer.calls > 0,
            "painting the attached table under dark never asked its Object renderer");
        assertEditsWith(table, 0, editor, "dark");

        manager.apply(false);
        assertEquals(List.of(), manager.failedPhases());
        assertSame(objectRenderer, table.getDefaultRenderer(Object.class),
            "the attached table's Object renderer was lost across the cycle; it now has "
                + describe(table.getDefaultRenderer(Object.class)));
        assertSame(booleanRenderer, table.getDefaultRenderer(Boolean.class),
            "the attached table's Boolean renderer was lost across the cycle; it now has "
                + describe(table.getDefaultRenderer(Boolean.class)));
        assertSame(editor, table.getDefaultEditor(Object.class),
            "the attached table's Object editor was lost across the cycle");
    }

    /**
     * The other half: a table with no renderer of its own still gets the look
     * and feel's. The JDK's {@code SynthTableUI} renderers are not
     * {@code UIResource}s, and one kept into dark would paint Synth cells
     * under FlatLaf.
     */
    @Test
    @DisplayName("a table with only the look and feel's renderers and editors is left to the look and feel")
    void lookAndFeelRenderersAreNotKept() {
        JTable table = new JTable(new DefaultTableModel(
            new Object[][] {{"a", Boolean.TRUE}}, new Object[] {"Name", "Enabled"}));
        Class<?> lightObject = table.getDefaultRenderer(Object.class).getClass();
        Class<?> lightBoolean = table.getDefaultRenderer(Boolean.class).getClass();
        Class<?> lightEditor = table.getDefaultEditor(Object.class).getClass();
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
        assertFalse(table.getDefaultEditor(Object.class).getClass().getName()
                .startsWith("de.javasoft.plaf.synthetica."),
            "Synthetica's Object editor was kept into dark");

        manager.apply(false);
        assertEquals(List.of(), manager.failedPhases());
        assertSame(lightObject, table.getDefaultRenderer(Object.class).getClass(),
            "the light Object renderer is not the stock one after a cycle");
        assertSame(lightBoolean, table.getDefaultRenderer(Boolean.class).getClass(),
            "the light Boolean renderer is not the stock one after a cycle");
        assertSame(lightEditor, table.getDefaultEditor(Object.class).getClass(),
            "the light Object editor is not the stock one after a cycle");
    }

    private static Component cell(JTable table, Class<?> valueClass, Object value) {
        return table.getDefaultRenderer(valueClass)
            .getTableCellRendererComponent(table, value, false, false, 0, 0);
    }

    private static String describe(TableCellRenderer renderer) {
        return renderer == null ? "null" : renderer.getClass().getName();
    }

    private static void assertEditsWith(JTable table, int column, DefaultCellEditor editor, String when) {
        assertTrue(table.editCellAt(0, column), when + ": column " + column + " would not start an edit");
        try {
            assertSame(editor.getComponent(), table.getEditorComponent(),
                when + ": an edit in column " + column + " did not use the custom editor");
        } finally {
            table.removeEditor();
        }
    }

    private static void paint(JTable table) {
        BufferedImage image = new BufferedImage(
            Math.max(1, table.getWidth()), Math.max(1, table.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            table.paint(g);
        } finally {
            g.dispose();
        }
    }

    private void frame(JTable table) {
        frame(new JScrollPane(table));
    }

    private void frame(Component content) {
        JFrame frame = new JFrame("custom table renderers");
        frames.add(frame);
        frame.getContentPane().add(content);
        frame.pack();
    }

    /** An application's renderer: not a UIResource, so a look and feel must leave it. */
    private static final class AppObjectRenderer implements TableCellRenderer {

        final JLabel component = new JLabel();
        int calls;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            calls++;
            component.setText(String.valueOf(value));
            return component;
        }
    }

    private static final class AppBooleanRenderer implements TableCellRenderer {

        final JCheckBox component = new JCheckBox();
        int calls;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            calls++;
            component.setSelected(Boolean.TRUE.equals(value));
            return component;
        }
    }
}
