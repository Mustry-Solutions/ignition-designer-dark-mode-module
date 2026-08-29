package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

/**
 * Dark-mode net for table and list cell renderers, the counterpart to
 * TreeIconRecolorer's tree wrapper. Many Designer renderers (the Tag Browser
 * tree table, Tag Editor property tables and category lists) snapshot light
 * colors at construction — setBackground(UIManager.getColor("Panel.background"))
 * under the light theme, setForeground(Color.BLACK) — and renderer components
 * live outside the component hierarchy, so neither updateComponentTreeUI nor
 * hierarchy walks ever reach them. Wrapping the renderer corrects the returned
 * component on every render:
 *
 * 1) a white (identity Color.WHITE) or stale light UIResource background
 *    becomes the current Table background;
 * 2) a dark foreground over a dark background is lifted to the current Table
 *    foreground.
 *
 * install() is idempotent and safe to re-run from the component watcher;
 * uninstall() restores every original renderer.
 */
public class CellRendererSanitizer {

    /** Value classes whose per-class default renderers get wrapped. */
    private static final Class<?>[] DEFAULT_RENDERER_CLASSES = {
        Object.class, String.class, Number.class, Integer.class, Long.class,
        Float.class, Double.class, Boolean.class, java.util.Date.class,
    };

    private final Map<JTable, TableCellRenderer[]> wrappedColumns = new WeakHashMap<>();
    private final Map<JTable, Map<Class<?>, TableCellRenderer>> wrappedDefaults =
        new WeakHashMap<>();
    private final Map<JTableHeader, TableCellRenderer> wrappedHeaders = new WeakHashMap<>();
    private final Map<JList<?>, ListCellRenderer<?>> wrappedLists = new WeakHashMap<>();

    private Color darkBackground;
    private Color lightForeground;

    /**
     * Colors sanitize() overwrote, per renderer component. Renderers like the
     * Tag Browser's HeaderRenderer set their colors once in the constructor —
     * without restoring, they'd stay dark forever after a switch back to
     * light (the renderer instances outlive the theme).
     */
    private final Map<Component, Color> mutatedBackgrounds = new WeakHashMap<>();
    private final Map<Component, Color> mutatedForegrounds = new WeakHashMap<>();

    /** Wrap the renderers of every table, header, and list currently in the UI. */
    public void install() {
        darkBackground = orDefault(UIManager.getColor("Table.background"), new Color(0x3A3D3F));
        lightForeground = orDefault(UIManager.getColor("Table.foreground"), new Color(0xDDE0E3));
        for (Window window : Window.getWindows()) {
            installIn(window);
        }
    }

    /** Package-private so tests can drive the walk without a real Window. */
    void installIn(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTable) {
                wrapTable((JTable) child);
                interceptRendererPane((JTable) child);
            } else if (child instanceof JTableHeader) {
                wrapHeader((JTableHeader) child);
            } else if (child instanceof JList) {
                wrapList((JList<?>) child);
            }
            if (child instanceof Container) {
                installIn((Container) child);
            }
        }
    }

    /**
     * Universal net for tables whose renderers are resolved dynamically (JIDE
     * grid tables like the Tag Editor's property table resolve renderers per
     * cell, bypassing both column and per-class default renderers). Every
     * renderer paint goes through the UI's CellRendererPane, so replacing it
     * with a sanitizing subclass catches every mechanism at once. The
     * protected BasicTableUI.rendererPane field is reachable because the
     * Designer JVM opens javax.swing.plaf.basic. On the light restore,
     * updateComponentTreeUI recreates the UI (and with it a fresh pane), so no
     * explicit undo is needed beyond letting that happen.
     */
    private final Map<JTable, Boolean> interceptedPanes = new WeakHashMap<>();
    private boolean rendererPaneUnavailable;

    private void interceptRendererPane(JTable table) {
        if (rendererPaneUnavailable || interceptedPanes.containsKey(table)) {
            return;
        }
        javax.swing.plaf.TableUI ui = table.getUI();
        if (!(ui instanceof javax.swing.plaf.basic.BasicTableUI)) {
            return;
        }
        try {
            java.lang.reflect.Field paneField =
                javax.swing.plaf.basic.BasicTableUI.class.getDeclaredField("rendererPane");
            paneField.setAccessible(true);
            javax.swing.CellRendererPane original =
                (javax.swing.CellRendererPane) paneField.get(ui);
            if (original == null || original instanceof SanitizingCellRendererPane) {
                return;
            }
            javax.swing.CellRendererPane replacement = new SanitizingCellRendererPane();
            paneField.set(ui, replacement);
            table.remove(original);
            table.add(replacement);
            // JIDE's BasicJideTableUI hands the pane to its paint delegate at
            // install time (new BasicJideTableUIDelegate(table, rendererPane))
            // — swap the captured reference too or JIDE tables keep painting
            // through the original pane.
            replacePaneInDelegate(ui, original, replacement);
            interceptedPanes.put(table, Boolean.TRUE);
            table.repaint();
        } catch (Throwable t) {
            // Log once and stop trying rather than spamming per table.
            rendererPaneUnavailable = true;
            DebugLog.log("CellRendererPane interception unavailable.", t);
        }
    }

    /**
     * Find any field on the UI (or objects one level down, like JIDE's
     * _delegate) still holding the original pane and point it at the
     * replacement.
     */
    private static void replacePaneInDelegate(Object ui,
            javax.swing.CellRendererPane original, javax.swing.CellRendererPane replacement) {
        for (Class<?> type = ui.getClass(); type != null; type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(ui);
                    if (value == original) {
                        field.set(ui, replacement);
                    } else if (value != null && field.getName().contains("delegate")) {
                        for (Class<?> delegateType = value.getClass(); delegateType != null;
                                delegateType = delegateType.getSuperclass()) {
                            for (java.lang.reflect.Field inner : delegateType.getDeclaredFields()) {
                                inner.setAccessible(true);
                                if (inner.get(value) == original) {
                                    inner.set(value, replacement);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // Inaccessible field — skip it.
                }
            }
        }
    }

    /** Renderer classes already reported as still-light, to log each once. */
    private final java.util.Set<String> reportedStragglers = new java.util.HashSet<>();

    /** Reentrancy guard: refreshing delegates repaints, which re-enters here. */
    private boolean refreshingDelegates = false;

    /** Name renderer components that stay light even after sanitizing. */
    private void reportLightStragglers(Component component) {
        Color background = component.getBackground();
        if (background != null && luminance(background) > 170
                && reportedStragglers.add(component.getClass().getName())) {
            DebugLog.detail("Renderer straggler: " + component.getClass().getName()
                + " bg=" + String.format("#%06X", background.getRGB() & 0xFFFFFF)
                + (background instanceof UIResource ? "|uires" : "|explicit"));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                reportLightStragglers(child);
            }
        }
    }

    private class SanitizingCellRendererPane extends javax.swing.CellRendererPane {
        @Override
        public void paintComponent(java.awt.Graphics g, Component c, Container p,
                int x, int y, int w, int h, boolean shouldValidate) {
            if (c != null) {
                try {
                    // Cached renderer components keep Synthetica delegates
                    // (light rounded combo/field look under dark). IA reuses
                    // and reconfigures these renderers when you switch tag
                    // editor categories, RE-installing a stale delegate — so
                    // check every paint (cheap: hasStaleUi short-circuits) and
                    // refresh whenever it has gone stale again, not just once.
                    // Synchronous so there is no wrong-style flash; the
                    // reentrancy guard stops the refresh's repaint recursing.
                    if (c instanceof javax.swing.JComponent && !refreshingDelegates
                            && ThemeManager.hasStaleUi(c, true)) {
                        refreshingDelegates = true;
                        try {
                            ThemeManager.refreshStaleUiDelegates((javax.swing.JComponent) c);
                        } finally {
                            refreshingDelegates = false;
                        }
                    }
                    sanitize(c);
                    if (DebugLog.verbose()) {
                        // A recursive walk of the renderer's own tree, on every
                        // painted cell, for the sake of one deduplicated log
                        // line. Worth it while hunting a light straggler; far
                        // too expensive to leave on in an ordinary session.
                        reportLightStragglers(c);
                    }
                } catch (Throwable ignored) {
                    // Never let theming break a paint.
                }
            }
            super.paintComponent(g, c, p, x, y, w, h, shouldValidate);
        }
    }

    private void wrapTable(JTable table) {
        if (wrappedColumns.containsKey(table)) {
            return;
        }
        int columnCount = table.getColumnModel().getColumnCount();
        TableCellRenderer[] originals = new TableCellRenderer[columnCount];
        for (int i = 0; i < columnCount; i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            TableCellRenderer renderer = column.getCellRenderer();
            if (renderer != null && !(renderer instanceof SanitizingTableRenderer)) {
                originals[i] = renderer;
                column.setCellRenderer(new SanitizingTableRenderer(renderer));
            }
        }
        wrappedColumns.put(table, originals);
        // Cells with no column renderer are painted by the per-class default
        // renderers (the Tag Editor's white value cells) — wrap those too.
        Map<Class<?>, TableCellRenderer> defaults = new java.util.HashMap<>();
        for (Class<?> valueClass : DEFAULT_RENDERER_CLASSES) {
            TableCellRenderer renderer = table.getDefaultRenderer(valueClass);
            if (renderer != null && !(renderer instanceof SanitizingTableRenderer)) {
                defaults.put(valueClass, renderer);
                table.setDefaultRenderer(valueClass, new SanitizingTableRenderer(renderer));
            }
        }
        wrappedDefaults.put(table, defaults);
        table.repaint();
    }

    private void wrapHeader(JTableHeader header) {
        TableCellRenderer renderer = header.getDefaultRenderer();
        if (renderer == null || renderer instanceof SanitizingTableRenderer
                || wrappedHeaders.containsKey(header)) {
            return;
        }
        wrappedHeaders.put(header, renderer);
        header.setDefaultRenderer(new SanitizingTableRenderer(renderer));
        header.repaint();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wrapList(JList<?> list) {
        if (wrappedLists.containsKey(list) || skippedLists.containsKey(list)) {
            return;
        }
        ListCellRenderer<?> renderer = installedRenderer(list);
        if (renderer == null) {
            skippedLists.put(list, Boolean.TRUE);
            return;
        }
        if (renderer instanceof SanitizingListRenderer) {
            return;
        }
        wrappedLists.put(list, renderer);
        list.setCellRenderer(new SanitizingListRenderer(renderer));
        list.repaint();
    }

    /** Lists skipped by installedRenderer, to log each class once. */
    private static final java.util.Set<String> reportedDecoratingLists =
        new java.util.HashSet<>();

    /**
     * Lists installedRenderer has already declined. The watcher re-walks every
     * window on a 150ms debounce, and a declined list never lands in
     * wrappedLists — without this it would pay for the reflective lookup on
     * every pass, forever.
     */
    private final Map<JList<?>, Boolean> skippedLists = new WeakHashMap<>();

    /**
     * Accessors a decorating list uses to publish the renderer it really
     * delegates to: JIDE's CheckBoxList family, and SwingX's JXList. Between
     * them these cover every decorating JList on the Designer's classpath
     * (a census of it found 32 JList subclasses, 9 of them decorating, all
     * reachable through one of these two names).
     */
    private static final String[] DELEGATE_ACCESSORS = {
        "getActualCellRenderer", "getWrappedCellRenderer",
    };

    /**
     * The renderer {@code setCellRenderer} replaces — which is not always the
     * one {@code getCellRenderer()} hands back.
     *
     * <p>JIDE's {@code CheckBoxList} (Vision's list-style property editors,
     * among others) returns a {@code CheckBoxListCellRenderer} decorator that
     * it re-points at JList's own renderer field on <em>every</em>
     * {@code getCellRenderer()} call; SwingX's {@code JXList} keeps a
     * {@code DelegatingRenderer} in that field and pushes what you set into
     * <em>it</em>. Either way, wrapping what {@code getCellRenderer()} returned
     * builds a cycle: the list hands out the decorator, the decorator delegates
     * to our wrapper, our wrapper delegates back to the decorator, and the
     * first paint recurses until the stack overflows. Both publish the real
     * renderer separately; wrapping that puts the wrapper <em>under</em> the
     * decorator, where it belongs.
     *
     * <p>Some other JList subclass could decorate without publishing anything,
     * and there is no safe way to reach the real renderer there — nor to put
     * one back afterwards, since the probe that would detect the cycle also
     * overwrites what the decorator was delegating to. Return null and leave
     * such a list unsanitized: a light cell is a blemish, a StackOverflowError
     * takes the Designer down.
     */
    private static ListCellRenderer<?> installedRenderer(JList<?> list) {
        try {
            if (list.getClass().getMethod("getCellRenderer").getDeclaringClass()
                    == JList.class) {
                return list.getCellRenderer();
            }
            for (String accessor : DELEGATE_ACCESSORS) {
                java.lang.reflect.Method method = findMethod(list.getClass(), accessor);
                if (method != null
                        && ListCellRenderer.class.isAssignableFrom(method.getReturnType())) {
                    return (ListCellRenderer<?>) method.invoke(list);
                }
            }
        } catch (Throwable t) {
            DebugLog.log("Cell renderer lookup failed for " + list.getClass().getName(), t);
            return null;
        }
        if (reportedDecoratingLists.add(list.getClass().getName())) {
            DebugLog.detail("List renderer left unwrapped (decorating list): "
                + list.getClass().getName());
        }
        return null;
    }

    private static java.lang.reflect.Method findMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    /** Restore every original renderer. */
    public void uninstall() {
        wrappedColumns.forEach((table, originals) -> {
            int columnCount = Math.min(originals.length, table.getColumnModel().getColumnCount());
            for (int i = 0; i < columnCount; i++) {
                TableColumn column = table.getColumnModel().getColumn(i);
                if (column.getCellRenderer() instanceof SanitizingTableRenderer) {
                    column.setCellRenderer(originals[i]);
                }
            }
            table.repaint();
        });
        wrappedColumns.clear();
        wrappedDefaults.forEach((table, defaults) -> {
            defaults.forEach((valueClass, original) -> {
                if (table.getDefaultRenderer(valueClass) instanceof SanitizingTableRenderer) {
                    table.setDefaultRenderer(valueClass, original);
                }
            });
            table.repaint();
        });
        wrappedDefaults.clear();
        wrappedHeaders.forEach((header, original) -> {
            if (header.getDefaultRenderer() instanceof SanitizingTableRenderer) {
                header.setDefaultRenderer(original);
                header.repaint();
            }
        });
        wrappedHeaders.clear();
        wrappedLists.forEach(this::restoreList);
        wrappedLists.clear();
        skippedLists.clear();
        // The panes themselves are discarded when updateComponentTreeUI
        // rebuilds the table UIs; just forget them so a later dark install
        // re-intercepts the fresh ones.
        interceptedPanes.clear();
        // Undo the color mutations on long-lived renderer instances so light
        // mode gets their constructor-era colors back.
        mutatedBackgrounds.forEach(Component::setBackground);
        mutatedBackgrounds.clear();
        mutatedForegrounds.forEach(Component::setForeground);
        mutatedForegrounds.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void restoreList(JList<?> list, ListCellRenderer<?> original) {
        // installedRenderer, not getCellRenderer: on a decorating list the
        // wrapper sits under the decorator, so getCellRenderer() reports the
        // decorator and the restore would silently be skipped.
        if (installedRenderer(list) instanceof SanitizingListRenderer) {
            list.setCellRenderer((ListCellRenderer) original);
            list.repaint();
        }
    }

    private void sanitize(Component component) {
        // The sanitizing renderer pane survives until updateComponentTreeUI
        // rebuilds the UI on the light restore — never darken outside dark mode.
        if (!(UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatDarkLaf)) {
            return;
        }
        // Renderer components are designer chrome; any light background there
        // is a light-theme leftover (explicit whites included, not just the
        // WHITE token or stale UIResources).
        Color background = component.getBackground();
        if (background == Color.WHITE
                || (background != null && luminance(background) > 200)
                || (background instanceof UIResource && luminance(background) > 160)) {
            if (!mutatedBackgrounds.containsKey(component)) {
                mutatedBackgrounds.put(component, background);
            }
            component.setBackground(darkBackground);
        }
        Color foreground = component.getForeground();
        Color effective = component.getBackground();
        if (foreground != null && effective != null
                && luminance(foreground) < 90 && luminance(effective) < 90) {
            if (!mutatedForegrounds.containsKey(component)) {
                mutatedForegrounds.put(component, foreground);
            }
            component.setForeground(lightForeground);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                sanitize(child);
            }
        }
    }

    private static int luminance(Color color) {
        return (color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114) / 1000;
    }

    private static Color orDefault(Color color, Color fallback) {
        return color != null ? color : fallback;
    }

    private class SanitizingTableRenderer implements TableCellRenderer {

        private final TableCellRenderer delegate;

        SanitizingTableRenderer(TableCellRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = delegate.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column);
            sanitize(component);
            return component;
        }
    }

    private class SanitizingListRenderer<E> implements ListCellRenderer<E> {

        private final ListCellRenderer<E> delegate;

        /**
         * Backstop for a delegate that renders back through us. installedRenderer
         * keeps the known decorating list out of that shape; if some other list
         * still manages it, one blank-ish fallback cell beats a StackOverflowError
         * in the paint loop.
         */
        private boolean rendering;

        SanitizingListRenderer(ListCellRenderer<E> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends E> list, E value,
                int index, boolean isSelected, boolean cellHasFocus) {
            if (rendering) {
                if (reportedDecoratingLists.add(delegate.getClass().getName())) {
                    DebugLog.log("Cell renderer recursion broken at "
                        + delegate.getClass().getName() + "; list left unstyled.");
                }
                return new javax.swing.DefaultListCellRenderer()
                    .getListCellRendererComponent(list, value, index, isSelected,
                        cellHasFocus);
            }
            Component component;
            rendering = true;
            try {
                component = delegate.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            } finally {
                rendering = false;
            }
            sanitize(component);
            return component;
        }
    }
}
