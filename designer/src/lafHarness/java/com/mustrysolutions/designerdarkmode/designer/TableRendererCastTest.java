package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.CellRendererPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTableUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A table whose own code casts its renderer must not have that renderer
 * wrapped (#130).
 *
 * <p>The Open/Create Project dialog (File → Open…) lists projects in
 * {@code ProjectListTable}. Its mouse listener does
 * {@code (ActionCellRenderer) table.getCellRenderer(hoverRowIndex, column)} in
 * both {@code mousePressed} and {@code mouseMoved} whenever the pointer is in
 * the action column. With the column renderer wrapped, that threw:
 *
 * <pre>
 * java.lang.ClassCastException: CellRendererSanitizer$SanitizingTableRenderer
 *     cannot be cast to ...opencreate.ProjectListTable$ActionCellRenderer
 *   at ...ProjectListTable$TableMouseListener.mousePressed
 * </pre>
 *
 * <p>The EDT swallowed it, so the OPEN buttons did nothing in dark mode and
 * worked again as soon as the Designer went light. Seen live in an 8.1.50
 * Designer; the {@code opencreate} package is byte-identical in 8.1.33 and
 * 8.3.8.
 *
 * <p>This is the table version of {@link TreeRendererCastTest}, but the cast is in
 * a listener, where the tree's shape check cannot see it. The guard is by
 * ownership: a renderer declared inside the table's owner is left alone.
 * Both halves are pinned here, as there: the real dialog's table is skipped
 * and still painted through the sanitizing pane, and an ordinary table is
 * still wrapped, because a guard that skipped everything would pass the
 * first test and quietly disable the pass.
 *
 * <h2>The second cause, found live</h2>
 *
 * <p>With only the cast fixed, OPEN still did nothing in a live 8.3.6
 * Designer: hovering worked, the press selected the row, nothing launched.
 * The press has to start the OPEN cell's editor, and only the table UI's own
 * mouse handler does that. It ignores a consumed event, and IA's listener
 * consumes every press in that column. On a table built the normal way the
 * UI's handler runs first; under FlatLaf the table's UI is replaced (JIDE's
 * {@code BasicCellSpanTableUI}) and the new handler lands after IA's
 * listener. {@link #aRealClickOnOpenLaunches} drives the whole
 * move-press-release sequence through {@code dispatchEvent} and asserts the
 * project is launched, which needs both fixes.
 *
 * <h2>The assumption guard</h2>
 *
 * <p>{@code ProjectListTable} is package-private and built reflectively, so a
 * version that renames it reports a skip rather than a failure that reads
 * like a regression.
 */
class TableRendererCastTest {

    private static final String PROJECT_LIST_TABLE =
        "com.inductiveautomation.ignition.designer.gui.opencreate.ProjectListTable";

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
        renderers.unwrap();
        renderers.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("the project dialog's OPEN column survives its own listener's cast")
    void theOpenButtonsStillWork() throws Exception {
        JPanel projectList = projectListTable(project -> { });
        JTable table = (JTable) field(projectList, "table");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(projectList, BorderLayout.CENTER);
        panel.setSize(900, 300);
        layOut(panel);

        int action = actionColumn(table);
        TableCellRenderer stock = table.getColumnModel().getColumn(action).getCellRenderer();

        manager.apply(true);
        renderers.installIn(panel);

        assertSame(stock, table.getColumnModel().getColumn(action).getCellRenderer(),
            "the action column's renderer was wrapped; ProjectListTable's listener "
                + "casts it, so OPEN now does nothing");

        // What IA's listener does on a hover and a press in the action column.
        // Before the fix both threw ClassCastException.
        setInt(projectList, "hoverRowIndex", 0);
        Rectangle cell = table.getCellRect(0, action, false);
        int x = (int) cell.getCenterX();
        int y = (int) cell.getCenterY();
        MouseEvent move = new MouseEvent(table, MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(), 0, x, y, 0, false, MouseEvent.NOBUTTON);
        MouseEvent press = new MouseEvent(table, MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(), MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
            MouseEvent.BUTTON1);
        for (MouseMotionListener listener : table.getMouseMotionListeners()) {
            if (isIas(listener)) {
                assertDoesNotThrow(() -> listener.mouseMoved(move),
                    "hovering over the OPEN column threw");
            }
        }
        boolean pressed = false;
        for (MouseListener listener : table.getMouseListeners()) {
            if (isIas(listener)) {
                assertDoesNotThrow(() -> listener.mousePressed(press),
                    "pressing OPEN threw, so the click is lost on the EDT");
                pressed = true;
            }
        }
        assertTrue(pressed, "no TableMouseListener on the table, so nothing was pressed");

        // Skipping the wrap must not leave the column unthemed: the renderer
        // pane still sanitizes every cell the table paints.
        assertNotEquals(CellRendererPane.class, rendererPane(table).getClass(),
            "the table's renderer pane was not intercepted, so the skipped column "
                + "paints with no sanitizing at all");
    }

    @Test
    @DisplayName("a real hover, press and release on OPEN launches the project under dark")
    void aRealClickOnOpenLaunches() throws Exception {
        manager.apply(true);
        List<String> launched = new ArrayList<>();
        JPanel projectList = projectListTable(launched::add);
        JTable table = (JTable) field(projectList, "table");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(projectList, BorderLayout.CENTER);
        panel.setSize(900, 300);
        layOut(panel);
        // Found before the pass runs, so a wrapped column fails below rather
        // than making this lookup skip the test.
        int action = actionColumn(table);

        renderers.installIn(panel);

        // IA's listener reads the OPEN button's bounds from the renderer,
        // which a paint lays out. Headless, validate() is a no-op, so render
        // the cell and lay the renderer out by hand instead.
        Rectangle cellBounds = table.getCellRect(0, action, false);
        TableCellRenderer renderer = table.getColumnModel().getColumn(action).getCellRenderer();
        Component rendered = renderer.getTableCellRendererComponent(
            table, table.getValueAt(0, action), false, false, 0, action);
        rendered.setBounds(0, 0, cellBounds.width, cellBounds.height);
        layOut((Container) rendered);
        java.lang.reflect.Method openButton =
            renderer.getClass().getSuperclass().getDeclaredMethod("getOpenButton");
        openButton.setAccessible(true);
        Rectangle button = ((Component) openButton.invoke(renderer)).getBounds();
        assertTrue(button.width > 0, "the OPEN button was never laid out, so no event can hit it");
        // The press is reposted into the cell EDITOR, which needs the same
        // hand layout for the button inside it to receive the click.
        Component editor = table.getColumnModel().getColumn(action).getCellEditor()
            .getTableCellEditorComponent(table, table.getValueAt(0, action), false, 0, action);
        editor.setBounds(0, 0, cellBounds.width, cellBounds.height);
        layOut((Container) editor);

        Rectangle cell = table.getCellRect(0, action, false);
        // Inside both the button and the cell: the renderer can lay out wider than its cell.
        int x = cell.x + button.x + Math.min(button.width, cell.width - button.x) / 2;
        int y = cell.y + button.y + button.height / 2;

        table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(), 0, x, y, 0, false, MouseEvent.NOBUTTON));
        table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_PRESSED,
            System.currentTimeMillis(), MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
            MouseEvent.BUTTON1));
        assertTrue(table.isEditing(),
            "the press did not start the OPEN cell's editor; IA's listener consumed it "
                + "ahead of the table UI's own handler, so the button never sees the click");
        table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_RELEASED,
            System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
        assertEquals(List.of("demo"), launched,
            "the editor started but the OPEN button's action did not launch the project");
    }

    @Test
    @DisplayName("an ordinary table's nested renderer is still wrapped (the guard is not a blanket skip)")
    void anOrdinaryTableIsStillWrapped() {
        // Nested, like ActionCellRenderer, but in a class that is not the
        // table's owner: ownership is the signal, not nesting.
        JTable table = new JTable(new DefaultTableModel(new Object[] {"Name"}, 2));
        TableCellRenderer stock = new NestedRenderer();
        table.getColumnModel().getColumn(0).setCellRenderer(stock);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(table, BorderLayout.CENTER);
        panel.setSize(300, 120);

        manager.apply(true);
        renderers.installIn(panel);

        assertNotSame(stock, table.getColumnModel().getColumn(0).getCellRenderer(),
            "an ordinary table was skipped too, so the guard has disabled the pass "
                + "rather than narrowed it");
    }

    @Test
    @DisplayName("a renderer declared by the panel that holds the table is left alone")
    void aPanelsOwnRendererIsNotWrapped() {
        OwningPanel panel = new OwningPanel();
        TableCellRenderer stock = panel.table.getColumnModel().getColumn(0).getCellRenderer();

        manager.apply(true);
        renderers.installIn(panel);

        assertSame(stock, panel.table.getColumnModel().getColumn(0).getCellRenderer(),
            "a renderer nested in the table's owning panel was wrapped; the panel's "
                + "own code can cast it back");
        // The cast the panel can perform, proving the hazard is real.
        panel.typedRenderer();
    }

    /** Nested in the test, which no table sits in. */
    private static final class NestedRenderer extends DefaultTableCellRenderer {
    }

    /** The ProjectListTable shape without IA: a panel that casts its own renderer. */
    private static final class OwningPanel extends JPanel {
        final JTable table = new JTable(new DefaultTableModel(new Object[] {"Action"}, 2));

        OwningPanel() {
            super(new BorderLayout());
            table.getColumnModel().getColumn(0).setCellRenderer(new ActionRenderer());
            add(table, BorderLayout.CENTER);
            setSize(300, 120);
        }

        /** Mirrors {@code TableMouseListener}'s cast. */
        ActionRenderer typedRenderer() {
            return (ActionRenderer) table.getCellRenderer(0, 0);
        }

        final class ActionRenderer extends DefaultTableCellRenderer {
        }
    }

    // --- building the real dialog table -------------------------------------

    /**
     * A {@code ProjectListTable} holding one project. Its constructor is
     * package-private and takes the manifest type of the running version:
     * {@code ResourceCollectionManifest} on 8.3, {@code ProjectManifest} on 8.1.
     */
    private static JPanel projectListTable(Consumer<String> launcher) throws Exception {
        Class<?> type;
        try {
            type = Class.forName(PROJECT_LIST_TABLE);
        } catch (ClassNotFoundException absent) {
            Assumptions.abort(PROJECT_LIST_TABLE + " is not in this Ignition version");
            return null;
        }
        Constructor<?> constructor = null;
        for (Constructor<?> candidate : type.getDeclaredConstructors()) {
            if (candidate.getParameterCount() == 3) {
                constructor = candidate;
            }
        }
        Assumptions.assumeTrue(constructor != null, "ProjectListTable's constructor changed shape");
        // setTableData removes entries from the children list, so it must be mutable.
        Class<?> manifestType = (Class<?>) ((ParameterizedType)
            constructor.getGenericParameterTypes()[0]).getActualTypeArguments()[1];
        Object manifest = manifestType
            .getConstructor(String.class, String.class, boolean.class, boolean.class, String.class)
            .newInstance("Demo", "A project", true, false, null);
        constructor.setAccessible(true);
        return (JPanel) constructor.newInstance(
            Map.of("demo", manifest), Map.of("demo", new ArrayList<>()), launcher);
    }

    /** The column IA's listener casts: its renderer is the ActionCellRenderer. */
    private static int actionColumn(JTable table) {
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableCellRenderer renderer = table.getColumnModel().getColumn(i).getCellRenderer();
            if (renderer != null && renderer.getClass().getName().endsWith("ActionCellRenderer")) {
                return i;
            }
        }
        Assumptions.abort("no ActionCellRenderer column in ProjectListTable");
        return -1;
    }

    private static boolean isIas(Object listener) {
        return listener.getClass().getName().endsWith("ProjectListTable$TableMouseListener");
    }

    /** No peer, so validate() is a no-op: lay out by hand, twice. */
    private static void layOut(Container container) {
        for (int pass = 0; pass < 2; pass++) {
            layOutOnce(container);
        }
    }

    private static void layOutOnce(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layOutOnce((Container) child);
            }
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static Object rendererPane(JTable table) throws Exception {
        Field pane = BasicTableUI.class.getDeclaredField("rendererPane");
        pane.setAccessible(true);
        return pane.get(table.getUI());
    }
}
