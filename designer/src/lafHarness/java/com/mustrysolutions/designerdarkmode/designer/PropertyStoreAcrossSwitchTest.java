package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * What else Synthetica's uninstall writes back besides button layout (#174's
 * follow-up audit), and that the switch keeps what the application set.
 *
 * <p>Synthetica records some of a component's properties each time it styles
 * it and writes every recorded value back when it is uninstalled, which is
 * the first step of every switch to dark. Of the kinds it records under
 * Ignition's theme, three undid the application:
 *
 * <ul>
 *   <li><b>Toolbar separator size.</b> A separator built with a size, which
 *       is what {@code JToolBar.addSeparator(Dimension)} does, is styled
 *       before the size is set, so the record is {@code null}, and Synthetica
 *       writes a plain 10&times;10 for a {@code null}. A size set later went
 *       back to Synthetica's 1&times;1. Either way the size is not a
 *       {@code UIResource}, so it stayed for the session. The UDT definition
 *       hierarchy toolbar's 4&times;0 separator is one.</li>
 *   <li><b>Table default editors and renderers.</b> Synthetica replaces
 *       {@code JTable}'s Object and Number editors (and Object and Boolean
 *       renderers) with its own and records the ones it replaced. A table
 *       that sets its own afterwards got {@code JTable}'s back. The Tag
 *       Browser's table sets Ignition's {@code NumberCellEditor} in its
 *       constructor and lost it for the session.</li>
 *   <li><b>Combo box layout.</b> Recorded for every combo, but Synthetica has
 *       no code to read this kind's value, so the record is {@code null} and
 *       the write-back removes the layout. A combo the switch does not
 *       reach kept no layout into light, and once shown its arrow button was
 *       0&times;0.</li>
 * </ul>
 */
@ExtendWith(RunOnEdt.class)
class PropertyStoreAcrossSwitchTest {

    private ThemeManager manager;
    private JFrame frame;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = ManagerCleanup.newManager();
        manager.captureStockLaf();
    }

    @AfterEach
    void leaveTheJvmLight() {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        if (frame != null) {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("a toolbar separator keeps the size its application gave it")
    void separatorSize() {
        JToolBar toolBar = new JToolBar();
        toolBar.addSeparator(new Dimension(4, 0));
        JToolBar.Separator added = (JToolBar.Separator) toolBar.getComponent(0);
        JToolBar.Separator built = new JToolBar.Separator(new Dimension(3, 24));
        JToolBar.Separator setLater = new JToolBar.Separator();
        setLater.setSeparatorSize(new Dimension(5, 30));
        inWindowIfPossible(toolBar, built, setLater);

        manager.apply(true);
        assertEquals(new Dimension(4, 0), added.getSeparatorSize(), "addSeparator(Dimension) under dark");
        assertEquals(new Dimension(3, 24), built.getSeparatorSize(), "built with a size, under dark");
        assertEquals(new Dimension(5, 30), setLater.getSeparatorSize(), "size set after styling, under dark");

        manager.apply(false);
        assertEquals(new Dimension(4, 0), added.getSeparatorSize(), "addSeparator(Dimension) after the restore");
        assertEquals(new Dimension(3, 24), built.getSeparatorSize(), "built with a size, after the restore");
        assertEquals(new Dimension(5, 30), setLater.getSeparatorSize(), "size set after styling, after the restore");
    }

    @Test
    @DisplayName("a stock separator still holds a look-and-feel size in dark, not a fixed one")
    void stockSeparatorSize() {
        JToolBar.Separator stock = new JToolBar.Separator();

        manager.apply(true);
        Dimension size = stock.getSeparatorSize();
        assertTrue(size == null || size instanceof UIResource,
            "a stock separator came out of the switch with a fixed size: " + size);
    }

    @Test
    @DisplayName("a table keeps the default editors and renderers its application set")
    void tableDefaults() {
        JTable table = new JTable(3, 3);
        TableCellRenderer objectRenderer = new DefaultTableCellRenderer();
        TableCellRenderer booleanRenderer = new DefaultTableCellRenderer();
        TableCellEditor objectEditor = new DefaultCellEditor(new JTextField());
        TableCellEditor numberEditor = new DefaultCellEditor(new JTextField());
        table.setDefaultRenderer(Object.class, objectRenderer);
        table.setDefaultRenderer(Boolean.class, booleanRenderer);
        table.setDefaultEditor(Object.class, objectEditor);
        table.setDefaultEditor(Number.class, numberEditor);

        // Detached: a table the switch does not reach. In a window, Swing's own
        // SynthTableUI puts its install-time renderers back when the UI is
        // replaced, which is a different bug; the editors are the store's alone.
        manager.apply(true);
        assertSame(objectRenderer, table.getDefaultRenderer(Object.class), "Object renderer under dark");
        assertSame(booleanRenderer, table.getDefaultRenderer(Boolean.class), "Boolean renderer under dark");
        assertSame(objectEditor, table.getDefaultEditor(Object.class), "Object editor under dark");
        assertSame(numberEditor, table.getDefaultEditor(Number.class), "Number editor under dark");
    }

    @Test
    @DisplayName("a table in a window keeps its default editors through dark and the restore")
    void tableEditorsInWindow() {
        assumeWindowed();
        JTable table = new JTable(3, 3);
        TableCellEditor objectEditor = new DefaultCellEditor(new JTextField());
        TableCellEditor numberEditor = new DefaultCellEditor(new JTextField());
        table.setDefaultEditor(Object.class, objectEditor);
        table.setDefaultEditor(Number.class, numberEditor);
        inWindow(new JScrollPane(table));

        manager.apply(true);
        assertSame(objectEditor, table.getDefaultEditor(Object.class), "Object editor under dark");
        assertSame(numberEditor, table.getDefaultEditor(Number.class), "Number editor under dark");
        manager.apply(false);
        assertSame(objectEditor, table.getDefaultEditor(Object.class), "Object editor after the restore");
        assertSame(numberEditor, table.getDefaultEditor(Number.class), "Number editor after the restore");
    }

    @Test
    @DisplayName("a stock table still gets JTable's own editor back, not Synthetica's")
    void stockTableEditor() {
        JTable table = new JTable(3, 3);
        assertTrue(table.getDefaultEditor(Object.class).getClass().getName().startsWith("de.javasoft."),
            "precondition: Synthetica installs its own default editor on a stock table");

        manager.apply(true);
        assertFalse(table.getDefaultEditor(Object.class).getClass().getName().startsWith("de.javasoft."),
            "a stock table kept Synthetica's editor into dark");
    }

    @Test
    @DisplayName("the Tag Browser's table keeps Ignition's number editor")
    void tagBrowserNumberEditor() throws Exception {
        JTable table = tagEditorTable();
        TableCellEditor ignitions = table.getDefaultEditor(Number.class);
        assertEquals("com.inductiveautomation.ignition.designer.util.NumberCellEditor",
            ignitions.getClass().getName(), "precondition: the table sets Ignition's number editor");
        inWindowIfPossible(table);

        manager.apply(true);
        assertSame(ignitions, table.getDefaultEditor(Number.class), "number editor under dark");
        manager.apply(false);
        assertSame(ignitions, table.getDefaultEditor(Number.class), "number editor after the restore");
    }

    @Test
    @DisplayName("a combo box the switch does not reach keeps a layout, and its arrow button")
    void offTreeComboLayout() {
        JComboBox<String> combo = new JComboBox<>(new String[] {"alpha", "beta"});
        // Built and held, but not in any window during the switch: a lazily
        // attached panel, as a Designer has plenty of.
        manager.apply(true);
        assertNotNull(combo.getLayout(), "the switch to dark removed the combo's layout");
        manager.apply(false);
        assertNotNull(combo.getLayout(), "the combo had no layout after the restore");

        if (!GraphicsEnvironment.isHeadless()) {
            inWindow(combo);
            Component arrow = null;
            for (Component child : combo.getComponents()) {
                if (child instanceof JButton) {
                    arrow = child;
                }
            }
            assertNotNull(arrow, "the combo has no arrow button");
            assertTrue(arrow.getWidth() > 0 && arrow.getHeight() > 0,
                "the combo's arrow button was laid out at " + arrow.getBounds());
        }
    }

    /**
     * The real {@code TreeTablePanel$TagEditorTable}. Its outer panel is only
     * read for the Designer context, which its value renderer keeps but does
     * not use while building, so a bare instance stands in for it.
     */
    private static JTable tagEditorTable() throws Exception {
        Class<?> panel;
        try {
            panel = Class.forName("com.inductiveautomation.ignition.designer.tags.tree.treetable.TreeTablePanel");
        } catch (ClassNotFoundException absent) {
            Assumptions.abort("this Ignition line has no TreeTablePanel");
            throw absent;
        }
        Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object outer = ((sun.misc.Unsafe) theUnsafe.get(null)).allocateInstance(panel);
        return (JTable) Class.forName(panel.getName() + "$TagEditorTable")
            .getConstructor(panel, javax.swing.table.TableModel.class)
            .newInstance(outer, new DefaultTableModel(2, 2));
    }

    private void inWindowIfPossible(Component... components) {
        if (!GraphicsEnvironment.isHeadless()) {
            inWindow(components);
        }
    }

    private void inWindow(Component... components) {
        if (frame == null) {
            frame = new JFrame("PropertyStoreAcrossSwitchTest");
            frame.setContentPane(new JPanel(new FlowLayout()));
        }
        for (Component component : components) {
            frame.getContentPane().add(component);
        }
        frame.pack();
    }

    private static void assumeWindowed() {
        boolean headless = GraphicsEnvironment.isHeadless();
        if (Boolean.getBoolean(WindowedCycleTest.WINDOWED_PROPERTY)) {
            assertFalse(headless, "-Pharness.windowed=true was given, but this JVM is headless.");
        }
        Assumptions.assumeFalse(headless, "the switch's tree update only reaches a table in a window");
    }
}
