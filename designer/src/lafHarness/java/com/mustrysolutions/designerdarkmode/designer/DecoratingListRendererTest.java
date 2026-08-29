package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jidesoft.swing.CheckBoxList;
import org.jdesktop.swingx.JXList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CellRendererSanitizer}'s list wrapping, against the real classes that
 * broke it.
 *
 * <p>{@code getCellRenderer()} does not always return the renderer
 * {@code setCellRenderer()} replaces. JIDE's {@code CheckBoxList} returns a
 * {@code CheckBoxListCellRenderer} that it re-points at JList's own renderer
 * field on every call; SwingX's {@code JXList} keeps a
 * {@code DelegatingRenderer} in that field and pushes what you set into
 * <em>it</em>. Wrapping what came back and storing the wrapper in the field
 * left the two delegating to each other — a StackOverflowError on the first
 * painted cell, reported from a Vision window.
 *
 * <p>Not crashing is only half of it: these lists still have to come out dark,
 * so the dark cases render a deliberately white renderer and assert the cell
 * that reaches the screen. {@code ListRendererWrappingTest} pins the same rule
 * structurally in the fast suite; this one is the evidence that the structure
 * is JIDE's and SwingX's.
 */
class DecoratingListRendererTest {

    /** The shape the sanitizer exists for: a renderer that snapshot white. */
    private static final class MarkerRenderer implements ListCellRenderer<Object> {

        private final JLabel component = new JLabel("marker");
        private boolean called;

        MarkerRenderer() {
            component.setOpaque(true);
            component.setBackground(Color.WHITE);
            component.setForeground(Color.BLACK);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            called = true;
            return component;
        }
    }

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JPanel panelWith(JList list, ListCellRenderer<Object> renderer) {
        list.setCellRenderer(renderer);
        JPanel panel = new JPanel();
        panel.add(list);
        return panel;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Component render(JList list) {
        return list.getCellRenderer()
            .getListCellRendererComponent(list, "row", 0, false, false);
    }

    private static void assertDarkCell(Component cell) {
        Color background = cell.getBackground();
        assertNotSame(Color.WHITE, background, "the cell kept the renderer's white");
        assertTrue(ThemeManager.luminance(background) < 120,
            "the painted cell has to be dark, was " + background);
    }

    @Test
    @DisplayName("a CheckBoxList renders a cell without recursing after install")
    void checkBoxListDoesNotRecurse() {
        CheckBoxList list = new CheckBoxList(new Object[] {"row"});
        MarkerRenderer marker = new MarkerRenderer();
        CellRendererSanitizer sanitizer = new CellRendererSanitizer();

        sanitizer.installIn(panelWith(list, marker));

        assertNotSame(marker, list.getActualCellRenderer(),
            "the wrapper belongs on the field the decorator delegates to");

        // Would not return at all before the fix.
        render(list);
        assertTrue(marker.called, "the wrapper must still reach the list's own renderer");

        sanitizer.uninstall();
        assertSame(marker, list.getActualCellRenderer(),
            "and the light restore has to find the wrapper under the decorator");
    }

    @Test
    @DisplayName("a JXList renders a cell without recursing after install")
    void swingXListDoesNotRecurse() {
        JXList list = new JXList(new Object[] {"row"});
        MarkerRenderer marker = new MarkerRenderer();
        CellRendererSanitizer sanitizer = new CellRendererSanitizer();

        sanitizer.installIn(panelWith(list, marker));

        assertNotSame(marker, list.getWrappedCellRenderer(),
            "JXList publishes its real renderer under a different name, same shape");

        render(list);
        assertTrue(marker.called);

        sanitizer.uninstall();
        assertSame(marker, list.getWrappedCellRenderer());
    }

    /**
     * The point of wrapping in the first place, end to end: a renderer holding
     * a constructor-era white has to reach the screen dark, through JIDE's
     * decorator — which copies the delegate's colours onto itself and clears
     * them on the delegate — and past the component watcher, which reinstalls
     * the UI of the label the decorator has just added and writes that UI's
     * own dark defaults over ours. Assert the cell, not the mechanism.
     */
    @Test
    @DisplayName("a CheckBoxList's light renderer reaches the screen dark")
    void checkBoxListCellIsSanitized() {
        CheckBoxList list = new CheckBoxList(new Object[] {"row"});
        MarkerRenderer marker = new MarkerRenderer();
        JPanel panel = panelWith(list, marker);

        manager.apply(true);
        CellRendererSanitizer sanitizer = new CellRendererSanitizer();
        sanitizer.install();
        sanitizer.installIn(panel);

        assertDarkCell(render(list));

        sanitizer.uninstall();
        assertEquals(Color.WHITE, marker.component.getBackground(),
            "and the light restore has to give the renderer its own colour back");
    }

    @Test
    @DisplayName("a JXList's light renderer reaches the screen dark")
    void swingXCellIsSanitized() {
        JXList list = new JXList(new Object[] {"row"});
        MarkerRenderer marker = new MarkerRenderer();
        JPanel panel = panelWith(list, marker);

        manager.apply(true);
        CellRendererSanitizer sanitizer = new CellRendererSanitizer();
        sanitizer.install();
        sanitizer.installIn(panel);

        assertDarkCell(render(list));

        sanitizer.uninstall();
        assertEquals(Color.WHITE, marker.component.getBackground());
    }

    /**
     * The watcher re-runs install() on a 150ms debounce, and a theme can be
     * toggled all day. Neither may stack wrappers on top of wrappers.
     */
    @Test
    @DisplayName("repeated installs and cycles leave exactly one wrapper")
    void repeatedInstallsDoNotStack() {
        CheckBoxList list = new CheckBoxList(new Object[] {"row"});
        MarkerRenderer marker = new MarkerRenderer();
        JPanel panel = panelWith(list, marker);
        CellRendererSanitizer sanitizer = new CellRendererSanitizer();

        for (int i = 0; i < 3; i++) {
            sanitizer.installIn(panel);
            sanitizer.installIn(panel);
            render(list);
            sanitizer.uninstall();
            assertSame(marker, list.getActualCellRenderer(),
                "cycle " + i + " must end on the list's own renderer");
        }

        sanitizer.installIn(panel);
        ListCellRenderer<?> wrapper = list.getActualCellRenderer();
        sanitizer.installIn(panel);
        assertSame(wrapper, list.getActualCellRenderer(),
            "a second install must recognize its own wrapper, not wrap it again");
    }

    /**
     * A census, not a unit test: every JList on the Designer's classpath that
     * decorates its renderer has to be one this module can reach past, or it
     * silently goes unstyled. If an SDK bump adds one, this is where it shows
     * up — as a name to add to the sanitizer's accessor list, not as a crash.
     */
    @Test
    @DisplayName("every decorating JList on the Designer classpath publishes its real renderer")
    void decoratingListsAreAllReachable() {
        List<String> unreachable = new ArrayList<>();
        int subclasses = 0;
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!entry.endsWith(".jar")) {
                continue;
            }
            try (JarFile jar = new JarFile(entry)) {
                for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();) {
                    String path = entries.nextElement().getName();
                    if (!path.endsWith(".class") || path.contains("module-info")) {
                        continue;
                    }
                    String name = path.substring(0, path.length() - ".class".length())
                        .replace('/', '.');
                    Class<?> type;
                    try {
                        // Resolve without initializing: this walks tens of
                        // thousands of classes, and running their static
                        // initializers would be both slow and destructive.
                        type = Class.forName(name, false, getClass().getClassLoader());
                    } catch (Throwable notLoadable) {
                        continue;
                    }
                    if (!JList.class.isAssignableFrom(type) || type == JList.class) {
                        continue;
                    }
                    subclasses++;
                    if (decorates(type) && !publishesRealRenderer(type)) {
                        unreachable.add(name);
                    }
                }
            } catch (Throwable unreadableJar) {
                continue;
            }
        }
        assertTrue(subclasses > 20,
            "sanity: the scan must actually be seeing the Designer's classpath, saw "
                + subclasses + " JList subclasses");
        assertEquals(List.of(), unreachable,
            "these lists decorate their renderer with no way to reach the real one");
    }

    private static boolean decorates(Class<?> type) {
        try {
            return type.getMethod("getCellRenderer").getDeclaringClass() != JList.class;
        } catch (Throwable inaccessible) {
            return false;
        }
    }

    private static boolean publishesRealRenderer(Class<?> type) {
        for (String accessor : new String[] {"getActualCellRenderer", "getWrappedCellRenderer"}) {
            try {
                if (ListCellRenderer.class.isAssignableFrom(
                        type.getMethod(accessor).getReturnType())) {
                    return true;
                }
            } catch (NoSuchMethodException absent) {
                continue;
            }
        }
        return false;
    }
}
