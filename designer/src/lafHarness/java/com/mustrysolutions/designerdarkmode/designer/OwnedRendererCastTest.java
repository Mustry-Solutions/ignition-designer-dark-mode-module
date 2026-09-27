package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.swing.CellRendererPane;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The Tag Editor's override icons must still work under dark (#135, #136).
 *
 * <p>A member a tag inherits from its UDT definition shows an override icon
 * beside it, and a click on the icon overrides it or puts the definition's
 * back. Both places look the icon's bounds up by casting their own renderer
 * from a {@code mousePressed}:
 *
 * <ul>
 *   <li>{@code AlarmListPanel.isOverrideClicked} does
 *       {@code (AlarmRenderer) list.getCellRenderer()};</li>
 *   <li>{@code EventScriptEditor.onOverrideIcon} does
 *       {@code (EventScriptEditor$Renderer) tree.getCellRenderer()}.</li>
 * </ul>
 *
 * <p>Wrapped by {@code CellRendererSanitizer} and {@code TreeIconRecolorer},
 * each cast threw on the EDT and the click did nothing. This is the list and
 * tree version of {@link TableRendererCastTest}. Lists get the tables' guard:
 * a renderer declared inside the list's owner is left alone. Trees name the
 * one renderer found cast, because the ownership rule would also take the
 * Project Browser off the wrapper. Both halves are pinned, as there: the real
 * panel's renderer is skipped yet its cells are still themed, and the guard
 * is no wider than it says.
 *
 * <p>The event tree's toggle acts on the selected event ({@code currentEvent}),
 * and IA's listener runs before the tree selects the pressed row, in a stock
 * light Designer too. So a user selects the event first and then clicks its
 * icon, and {@link #aRealClickOnAnEventOverrideTogglesThatEvent} does the
 * same. It builds the editor light and switches with it open, as a Tag Editor
 * open at the time of the toggle would be.
 */
class OwnedRendererCastTest {

    private static final String EVENTS =
        "com.inductiveautomation.ignition.designer.tags.editing.propeditors.events.";

    private ThemeManager manager;
    private CellRendererSanitizer renderers;
    private TreeIconRecolorer icons;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        renderers = new CellRendererSanitizer();
        icons = new TreeIconRecolorer();
    }

    @AfterEach
    void leaveTheJvmLight() {
        renderers.unwrap();
        renderers.uninstall();
        icons.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    // ---- #135: the alarm list ------------------------------------------------

    @Test
    @DisplayName("a real click on an inherited alarm's override icon toggles it under dark")
    void aRealClickOnAnAlarmOverrideToggles() throws Throwable {
        requireADisplay();
        onEdt(this::aRealClickOnAnAlarmOverrideTogglesOnEdt);
    }

    private void aRealClickOnAnAlarmOverrideTogglesOnEdt() throws Exception {
        manager.apply(true);
        List<String> removed = new ArrayList<>();
        Object alarm = stub(Class.forName(
            "com.inductiveautomation.ignition.common.alarming.config.AlarmDefinition"),
            method -> {
                switch (method.getName()) {
                    case "getName": return args -> "HighAlarm";
                    case "getNonNull": return args -> args[1];
                    default: return null;
                }
            });
        Object config = stub(Class.forName(
            "com.inductiveautomation.ignition.common.alarming.config.AlarmConfiguration"),
            method -> {
                switch (method.getName()) {
                    case "getDefinitions": return args -> new ArrayList<>(List.of(alarm));
                    // Inherited and already overridden, so the toggle takes
                    // the override away: remove(name), no copy to construct.
                    case "isInherited":
                    case "isOverridden": return args -> true;
                    case "remove": return args -> { removed.add((String) args[0]); return null; };
                    default: return null;
                }
            });
        Class<?> controllerType = Class.forName(
            "com.inductiveautomation.ignition.designer.tags.editing.propeditors.alarms."
                + "AlarmListPanel$AlarmListController");
        Object controller = stub(controllerType,
            method -> "isConfigInherited".equals(method.getName()) ? args -> true : null);
        JPanel alarms = (JPanel) Class.forName(
            "com.inductiveautomation.ignition.designer.tags.editing.propeditors.alarms."
                + "AlarmListPanel").getConstructor().newInstance();
        alarms.getClass().getMethod("init", controllerType, Class.forName(
            "com.inductiveautomation.ignition.common.alarming.config.AlarmConfiguration"))
            .invoke(alarms, controller, config);
        JList<?> list = (JList<?>) field(alarms, "list");
        ListCellRenderer<?> stock = list.getCellRenderer();
        JPanel panel = inPanel(alarms, 400, 300);

        renderers.installIn(panel);

        assertSame(stock, list.getCellRenderer(),
            "the alarm list's renderer was wrapped; AlarmListPanel casts it back on a press, "
                + "so the override icon does nothing");
        assertNotEquals(CellRendererPane.class, rendererPane(list, "BasicListUI").getClass(),
            "the skipped list's renderer pane was not intercepted, so its cells paint with "
                + "no sanitizing at all");

        Rectangle row = list.getCellBounds(0, 0);
        int y = (int) row.getCenterY();
        for (int x = row.x; x < row.x + row.width && removed.isEmpty(); x++) {
            press(list, x, y);
        }
        assertEquals(List.of("HighAlarm"), removed,
            "no press along the alarm's row reached toggleOverride");
    }

    @Test
    @DisplayName("an ordinary list's nested renderer is still wrapped (the guard is not a blanket skip)")
    void anOrdinaryListIsStillWrapped() {
        JList<String> list = new JList<>(new String[] {"one", "two"});
        ListCellRenderer<Object> stock = new NestedListRenderer();
        list.setCellRenderer(stock);
        JPanel panel = inPanel(list, 300, 120);

        manager.apply(true);
        renderers.installIn(panel);

        assertNotSame(stock, list.getCellRenderer(),
            "an ordinary list was skipped too, so the guard has disabled the pass "
                + "rather than narrowed it");
    }

    // ---- #136: the event-scripts tree ----------------------------------------

    @Test
    @DisplayName("a real click on an inherited event's override icon toggles that event under dark")
    void aRealClickOnAnEventOverrideTogglesThatEvent() throws Throwable {
        requireADisplay();
        onEdt(this::aRealClickOnAnEventOverrideTogglesThatEventOnEdt);
    }

    private void aRealClickOnAnEventOverrideTogglesThatEventOnEdt() throws Exception {
        List<String> removed = new ArrayList<>();
        JPanel editor = eventScriptEditor(removed);
        JTree tree = (JTree) field(editor, "tree");
        TreeCellRenderer stock = tree.getCellRenderer();
        JPanel panel = inPanel(editor, 700, 500);

        // Built light, then switched: the refresh re-installs the tree UI's
        // mouse handler behind IA's press listener, as in a Tag Editor that
        // was open when Dark Mode went on.
        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(panel);
        layOut(panel);
        icons.installIn(panel);
        renderers.installIn(panel);

        assertSame(stock, tree.getCellRenderer(),
            "the event tree's renderer was wrapped; EventScriptEditor casts it back on a "
                + "press, so the override icon does nothing");

        int target = eventRows(tree).get(0);
        String targetId = eventId(tree, target);
        tree.setSelectionRow(target);
        Rectangle row = tree.getRowBounds(target);
        int y = (int) row.getCenterY();
        for (int x = 0; x < tree.getWidth() && removed.isEmpty(); x++) {
            press(tree, x, y);
        }
        assertEquals(List.of(targetId), removed,
            "no press along the selected event's row reached toggleOverride");
    }

    @Test
    @DisplayName("the event tree left unwrapped still has its icons recoloured")
    void theOwnedTreeIsStillRecoloured() throws Throwable {
        requireADisplay();
        onEdt(this::theOwnedTreeIsStillRecolouredOnEdt);
    }

    private void theOwnedTreeIsStillRecolouredOnEdt() throws Exception {
        manager.apply(true);
        JPanel editor = eventScriptEditor(new ArrayList<>());
        JTree tree = (JTree) field(editor, "tree");
        JPanel panel = inPanel(editor, 700, 500);
        icons.installIn(panel);

        assertTrue(rendererPane(tree, "BasicTreeUI") != null
                && rendererPane(tree, "BasicTreeUI").getClass() != CellRendererPane.class,
            "the owned tree's renderer pane was not intercepted, so nothing recolours it");

        // The renderer is its own component. Its icons are vector icons; a
        // recoloured one is the tinted ImageIcon copy TreeIconRecolorer paints.
        Component renderer = (Component) tree.getCellRenderer();
        renderedRow(tree, eventRows(tree).get(0));
        assertTrue(tintedIcons(renderer) == 0 && !labelIcons(renderer).isEmpty(),
            "the event renderer has no stock icons to recolour");
        BufferedImage image = new BufferedImage(tree.getWidth(), tree.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            tree.paint(g);
        } finally {
            g.dispose();
        }
        // Read straight after the paint: the next render puts stock icons back.
        assertTrue(tintedIcons(renderer) > 0,
            "the last row painted still has only stock icons, so nothing recoloured it");
    }

    @Test
    @DisplayName("a tree whose own panel declares its renderer is still wrapped (the Project Browser's shape)")
    void anOwnedTreeThatIsNotKnownToCastIsStillWrapped() {
        TreeOwningPanel owner = new TreeOwningPanel();
        TreeCellRenderer stock = owner.tree.getCellRenderer();
        JPanel panel = inPanel(owner, 300, 200);

        manager.apply(true);
        icons.installIn(panel);

        assertNotSame(stock, owner.tree.getCellRenderer(),
            "a tree was skipped for its renderer's owner alone; that moves the Project "
                + "Browser and 16 other trees off the wrapper, not just the event tree");
    }


    // ---- helpers --------------------------------------------------------------

    /**
     * The real panels need a display to be built at all: AlarmListPanel asks
     * the toolkit for its menu shortcut key, and the event editor's
     * collapsible panes install a DropTarget. CI runs the harness windowed.
     */
    private static void requireADisplay() {
        boolean headless = GraphicsEnvironment.isHeadless();
        if (Boolean.getBoolean(WindowedCycleTest.WINDOWED_PROPERTY)) {
            assertFalse(headless, "-Pharness.windowed=true was given, but this JVM is headless.");
        }
        Assumptions.assumeFalse(headless,
            "no display: AlarmListPanel and EventScriptEditor cannot be built headlessly.");
    }

    private static void onEdt(Executable task) throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                task.execute();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    /** A real EventScriptEditor whose every event is inherited and overridden. */
    private static JPanel eventScriptEditor(List<String> removed) throws Exception {
        Class<?> contextType =
            Class.forName("com.inductiveautomation.ignition.designer.model.DesignerContext");
        Object context = stub(contextType, method -> null);
        Class<?> editorType = Class.forName(EVENTS + "EventScriptEditor");
        JPanel editor = (JPanel) editorType.getConstructor(contextType).newInstance(context);
        Object scripts = stub(Class.forName(
            "com.inductiveautomation.ignition.common.sqltags.model.scripts.TagEventScripts"),
            method -> {
                switch (method.getName()) {
                    case "isInherited":
                    case "isOverridden": return args -> true;
                    case "remove": return args -> { removed.add((String) args[0]); return null; };
                    case "get": return args -> "";
                    default: return null;
                }
            });
        Field eventScripts = editorType.getDeclaredField("eventScripts");
        eventScripts.setAccessible(true);
        eventScripts.set(editor, scripts);
        Method refresh = editorType.getDeclaredMethod("refreshEventTree");
        refresh.setAccessible(true);
        refresh.invoke(editor);
        return editor;
    }

    private static List<Integer> eventRows(JTree tree) throws Exception {
        Class<?> eventNode = Class.forName(EVENTS + "EventScriptEditor$EventNode");
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < tree.getRowCount(); i++) {
            if (eventNode.isInstance(tree.getPathForRow(i).getLastPathComponent())) {
                rows.add(i);
            }
        }
        return rows;
    }

    private static String eventId(JTree tree, int row) throws Exception {
        Object node = tree.getPathForRow(row).getLastPathComponent();
        Method getId = node.getClass().getDeclaredMethod("getId");
        getId.setAccessible(true);
        return (String) getId.invoke(node);
    }

    private static Component renderedRow(JTree tree, int row) {
        TreePath path = tree.getPathForRow(row);
        return tree.getCellRenderer().getTreeCellRendererComponent(tree,
            path.getLastPathComponent(), false, false, true, row, false);
    }

    private static long tintedIcons(Component component) {
        return labelIcons(component).values().stream()
            .filter(icon -> icon instanceof ImageIcon
                && ((ImageIcon) icon).getImage() instanceof BufferedImage)
            .count();
    }

    private static Map<JLabel, Icon> labelIcons(Component component) {
        Map<JLabel, Icon> icons = new IdentityHashMap<>();
        collectLabelIcons(component, icons);
        return icons;
    }

    private static void collectLabelIcons(Component component, Map<JLabel, Icon> out) {
        if (component instanceof JLabel && ((JLabel) component).getIcon() != null) {
            out.put((JLabel) component, ((JLabel) component).getIcon());
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectLabelIcons(child, out);
            }
        }
    }

    /** A press and release at a point, through the component's own dispatch. */
    private static void press(Component target, int x, int y) {
        long now = System.currentTimeMillis();
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, now,
            MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, now,
            0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    /**
     * An interface implemented by a handler that answers the named methods
     * and returns a type's zero value for the rest (defaults included, which
     * a Proxy would otherwise leave abstract).
     */
    private static Object stub(Class<?> type,
            Function<Method, Function<Object[], Object>> answers) {
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
                if (returns == int.class || returns == long.class || returns == short.class
                        || returns == byte.class || returns == char.class) {
                    return returns == long.class ? 0L : returns == char.class ? (Object) '\0'
                        : returns == short.class ? (Object) (short) 0
                        : returns == byte.class ? (Object) (byte) 0 : (Object) 0;
                }
                if (returns == float.class || returns == double.class) {
                    return returns == float.class ? (Object) 0f : (Object) 0d;
                }
                return null;
            });
    }

    private static JPanel inPanel(Component content, int width, int height) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(content, BorderLayout.CENTER);
        panel.setSize(width, height);
        layOut(panel);
        return panel;
    }

    /** Headless, validate() is a no-op: lay the tree out by hand, top down. */
    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layOut((Container) child);
            }
        }
    }

    private static CellRendererPane rendererPane(Component component, String basicUi)
            throws Exception {
        Object ui = component.getClass().getMethod("getUI").invoke(component);
        Field pane = Class.forName("javax.swing.plaf.basic." + basicUi)
            .getDeclaredField("rendererPane");
        pane.setAccessible(true);
        return (CellRendererPane) pane.get(ui);
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException next) {
                // keep looking up the hierarchy
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** Nested, like AlarmRenderer, but in a class that owns no list. */
    private static final class NestedListRenderer extends DefaultListCellRenderer {
    }

    /** NavTreePanel's shape: a panel that declares its tree's renderer. */
    private static final class TreeOwningPanel extends JPanel {

        final JTree tree = new JTree();

        TreeOwningPanel() {
            super(new BorderLayout());
            tree.setCellRenderer(new Renderer());
            add(tree, BorderLayout.CENTER);
        }

        private static final class Renderer extends DefaultTreeCellRenderer {
        }
    }
}
