package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How {@link CellRendererSanitizer} may wrap a {@link JList}'s cell renderer.
 *
 * <p>The rule the sanitizer got wrong: {@code getCellRenderer()} does not
 * always return the renderer {@code setCellRenderer()} replaces. JIDE's
 * {@code CheckBoxList} — Vision uses it, and opening a Vision window is what
 * surfaced this — hands back a decorator that it re-points at JList's own
 * renderer field on every call. Wrapping the decorator and storing the wrapper
 * in that field makes the two delegate to each other, and the first painted
 * cell recurses until the stack overflows.
 *
 * <p>These lists are stand-ins for that shape, not for JIDE: the sanitizer is
 * compiled without JIDE on its classpath and recognizes the decoration
 * structurally, so the structure is what is pinned here. The real
 * {@code CheckBoxList} is exercised by the look-and-feel harness.
 */
class ListRendererWrappingTest {

    /** Renders one fixed component, so pass-through is an identity check. */
    static final class MarkerRenderer implements ListCellRenderer<Object> {

        final JLabel component = new JLabel("marker");

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            return component;
        }
    }

    /**
     * A JIDE {@code CheckBoxList} in miniature: {@code getCellRenderer()}
     * returns a decorator whose delegate is re-read from JList's own field
     * every time, and the field's value is published separately.
     */
    static class DecoratingList extends JList<Object> {

        // Lazy, and null-checked below, because the JList constructor asks for
        // the renderer while installing its UI — before any field of this
        // class is assigned. CheckBoxList has the same null check.
        private Decorator decorator;

        @Override
        public ListCellRenderer<? super Object> getCellRenderer() {
            if (decorator == null) {
                decorator = new Decorator();
            }
            decorator.actual = getActualCellRenderer();
            return decorator;
        }

        public ListCellRenderer<? super Object> getActualCellRenderer() {
            return super.getCellRenderer();
        }
    }

    /** The same decoration, with no way to reach the real renderer. */
    static final class OpaqueDecoratingList extends JList<Object> {

        private Decorator decorator;

        @Override
        public ListCellRenderer<? super Object> getCellRenderer() {
            if (decorator == null) {
                decorator = new Decorator();
            }
            decorator.actual = super.getCellRenderer();
            return decorator;
        }

        // Deliberately not getActualCellRenderer: this list keeps its real
        // renderer to itself, which is the case the sanitizer must decline.
        ListCellRenderer<? super Object> peekAtRealRenderer() {
            return super.getCellRenderer();
        }
    }

    static final class Decorator implements ListCellRenderer<Object> {

        private ListCellRenderer<? super Object> actual;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            return actual.getListCellRendererComponent(list, value, index, isSelected,
                cellHasFocus);
        }
    }

    private static JPanel panelWith(JList<Object> list, ListCellRenderer<Object> renderer) {
        list.setListData(new Object[] {"row"});
        list.setCellRenderer(renderer);
        JPanel panel = new JPanel();
        panel.add(list);
        return panel;
    }

    private static Component render(JList<Object> list) {
        return list.getCellRenderer()
            .getListCellRendererComponent(list, "row", 0, false, false);
    }

    @Test
    @DisplayName("an ordinary list gets its renderer wrapped, and still renders it")
    void wrapsOrdinaryList() {
        JList<Object> list = new JList<>();
        MarkerRenderer marker = new MarkerRenderer();
        CellRendererSanitizer sanitizer = new CellRendererSanitizer();

        sanitizer.installIn(panelWith(list, marker));

        assertNotSame(marker, list.getCellRenderer(), "the renderer should be wrapped");
        assertSame(marker.component, render(list),
            "the wrapper must return what the original renderer produced");
    }

    /**
     * The regression. Before the fix this call did not fail an assertion — it
     * threw StackOverflowError out of the paint, taking the Designer's event
     * thread with it.
     */
    @Test
    @DisplayName("a decorating list renders without recursing between wrapper and decorator")
    void doesNotRecurseOnDecoratingList() {
        DecoratingList list = new DecoratingList();
        MarkerRenderer marker = new MarkerRenderer();
        CellRendererSanitizer sanitizer = new CellRendererSanitizer();

        sanitizer.installIn(panelWith(list, marker));

        assertNotSame(marker, list.getActualCellRenderer(),
            "the wrapper belongs under the decorator, on the field it delegates to");
        // Identity, not merely "did not overflow": the recursion backstop
        // inside the wrapper returns a substitute component, so anything but
        // the marker means the cycle was built and then papered over.
        assertSame(marker.component, render(list),
            "the decorator must still reach the original renderer");
    }

    @Test
    @DisplayName("a decorating list whose real renderer is unreachable is left alone")
    void skipsOpaqueDecoratingList() {
        OpaqueDecoratingList list = new OpaqueDecoratingList();
        MarkerRenderer marker = new MarkerRenderer();
        CellRendererSanitizer sanitizer = new CellRendererSanitizer();

        sanitizer.installIn(panelWith(list, marker));

        assertSame(marker, list.peekAtRealRenderer(),
            "no wrapper can be installed safely here, so none should be");
        assertSame(marker.component, render(list));
    }

    @Test
    @DisplayName("uninstall puts a decorating list's own renderer back")
    void restoresDecoratingList() {
        DecoratingList list = new DecoratingList();
        MarkerRenderer marker = new MarkerRenderer();
        CellRendererSanitizer sanitizer = new CellRendererSanitizer();

        sanitizer.installIn(panelWith(list, marker));
        sanitizer.uninstall();

        assertSame(marker, list.getActualCellRenderer(),
            "the restore looks under the decorator, where the wrapper went");
    }
}
