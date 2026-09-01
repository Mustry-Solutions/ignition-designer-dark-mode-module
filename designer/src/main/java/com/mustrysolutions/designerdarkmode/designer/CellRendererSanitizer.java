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
    /**
     * Borders sanitize() replaced, per renderer component (#21).
     *
     * <p>Most renderers re-set their border on every call, so the swap has to
     * happen per render rather than once. Some set it only in the constructor,
     * though, and those would keep a dark line through light mode — hence the
     * restore, the same bargain as the colours above.
     */
    private final Map<javax.swing.JComponent, javax.swing.border.Border> mutatedBorders =
        new WeakHashMap<>();

    /**
     * The colours a renderer component had before dark mode touched anything,
     * and the components whose UI delegate we refreshed (#45).
     *
     * <p>Two different mechanisms erase a renderer's own colours, and both are
     * one-way: {@code DefaultTableCellRenderer.updateUI()} is
     * {@code super.updateUI(); setForeground(null); setBackground(null);}, and
     * a renderer that sets its colours in the CONSTRUCTOR — {@code
     * SimpleTreeTable$SimpleHeaderRenderer} does exactly that, {@code
     * setForeground(BLACK)} and {@code setBackground(UIManager.getColor(
     * "Panel.background"))} — never sets them again. So one {@code updateUI()}
     * costs it those colours for the life of the Designer.
     *
     * <p>It gets one on the way into dark mode, from Swing itself:
     * {@code JTableHeader.updateUI()} calls {@code updateComponentTreeUI} on
     * its default renderer when that renderer is a {@code Component}. On the
     * way back it does NOT, because by then the renderer is wrapped in a
     * {@link SanitizingTableRenderer}, which is not a Component — so the Tag
     * Browser's {@code Value} header came back with no colours of its own and
     * still on a FlatLaf delegate.
     *
     * <p>Hence a snapshot taken before the switch touches anything, restored by
     * {@link #uninstall()}. Keyed weakly: a renderer that goes away with its
     * table must not be held alive by this.
     */
    private final Map<Component, Color[]> stockRendererColors = new WeakHashMap<>();
    private final Map<javax.swing.JComponent, Boolean> refreshedRenderers = new WeakHashMap<>();

    /**
     * Record the colours of every renderer component in the UI, before the
     * switch to dark can wipe them.
     *
     * <p>Runs as its own phase, ahead of {@code updateComponentTrees}, rather
     * than inside {@link #install()}: install() runs at the END of the dark
     * switch, by which point Swing's own renderer update has already nulled
     * the colours this exists to remember.
     */
    public void captureStockColors() {
        for (Window window : Window.getWindows()) {
            captureStockColorsIn(window);
        }
    }

    /** Package-private so tests can drive the walk without a real Window. */
    void captureStockColorsIn(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTable) {
                JTable table = (JTable) child;
                for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
                    rememberStockColors(table.getColumnModel().getColumn(i).getCellRenderer());
                }
                for (Class<?> valueClass : DEFAULT_RENDERER_CLASSES) {
                    rememberStockColors(table.getDefaultRenderer(valueClass));
                }
            } else if (child instanceof JTableHeader) {
                rememberStockColors(((JTableHeader) child).getDefaultRenderer());
            } else if (child instanceof JList) {
                rememberStockColors(installedRenderer((JList<?>) child));
                rememberStockColors(groupRenderer((JList<?>) child));
            }
            if (child instanceof Container) {
                captureStockColorsIn((Container) child);
            }
        }
    }

    /**
     * Record a renderer's colours, and its children's, unless they are already
     * recorded — the walk runs over every window and a renderer instance is
     * routinely shared between tables, so the FIRST reading is the stock one.
     */
    private void rememberStockColors(Object renderer) {
        if (!(renderer instanceof Component)) {
            return;
        }
        Map<Component, Color[]> colors = new java.util.LinkedHashMap<>();
        snapshotColors((Component) renderer, colors);
        colors.forEach(stockRendererColors::putIfAbsent);
    }

    /**
     * Refresh a renderer's stale UI delegate without losing its colours.
     *
     * <p>The refresh is an {@code updateUI()}, and {@code updateUI()} on a
     * {@code DefaultTableCellRenderer} nulls both colours (see
     * {@link #stockRendererColors}). Snapshotting around the call keeps the
     * refresh to what it is for — the delegate — and leaves the colours to
     * {@link #sanitize}, which tracks what it changes.
     */
    private void refreshDelegatePreservingColors(javax.swing.JComponent renderer) {
        Map<Component, Color[]> before = new java.util.LinkedHashMap<>();
        snapshotColors(renderer, before);
        ThemeManager.refreshStaleUiDelegates(renderer);
        before.forEach((component, colors) -> {
            component.setBackground(colors[0]);
            component.setForeground(colors[1]);
        });
        refreshedRenderers.put(renderer, Boolean.TRUE);
    }

    /**
     * One component's own colours and its children's.
     *
     * <p>{@code isBackgroundSet()} rather than {@code getBackground()}: an
     * unset colour inherits from the parent, and recording the inherited value
     * would later PIN it — turning a renderer that follows its table into one
     * that no longer does.
     */
    private static void snapshotColors(Component component, Map<Component, Color[]> into) {
        into.put(component, new Color[] {
            component.isBackgroundSet() ? component.getBackground() : null,
            component.isForegroundSet() ? component.getForeground() : null,
        });
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                snapshotColors(child, into);
            }
        }
    }

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
                wrapGroupRenderer((JList<?>) child);
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
        if (rendererPaneUnavailable) {
            return;
        }
        // Deliberately NOT "have we done this table before". A table's UI is
        // rebuilt whenever updateComponentTreeUI runs over it, and the rebuild
        // installs a FRESH CellRendererPane — so a table intercepted once and
        // remembered forever quietly loses the interception the next time its
        // UI is refreshed, and every cell it paints after that goes unthemed.
        //
        // That is what left the Tag Editor's property table showing white combo
        // cells with our lightened text on them: its pane was a plain
        // CellRendererPane by the time the dialog opened. The idempotence comes
        // from the pane check below instead, which costs one field read.
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
                            refreshDelegatePreservingColors((javax.swing.JComponent) c);
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
        wrappedGroupRenderers.forEach(this::restoreGroupRenderer);
        wrappedGroupRenderers.clear();
        skippedGroupLists.clear();
        // The panes themselves are discarded when updateComponentTreeUI
        // rebuilds the table UIs; just forget them so a later dark install
        // re-intercepts the fresh ones.
        interceptedPanes.clear();
        // Any renderer still on a FlatLaf delegate, whether this class
        // refreshed it or Swing did. Swing will not put it back for us:
        // JTableHeader.updateUI() only reaches a default renderer that is a
        // Component, and the light restore's tree update ran while ours (not a
        // Component) was still wrapped around it. So the Tag Browser's Value
        // header would keep painting through FlatLaf all through light mode.
        // Here the wrapper is gone and the light theme is installed, which is
        // exactly when refreshStaleUiDelegates does the right thing.
        java.util.Set<javax.swing.JComponent> stale =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        stale.addAll(refreshedRenderers.keySet());
        for (Component component : stockRendererColors.keySet()) {
            if (component instanceof javax.swing.JComponent) {
                stale.add((javax.swing.JComponent) component);
            }
        }
        for (javax.swing.JComponent renderer : stale) {
            try {
                refreshDelegatePreservingColors(renderer);
            } catch (Throwable t) {
                DebugLog.log("Could not restore the UI delegate of "
                    + renderer.getClass().getName(), t);
            }
        }
        refreshedRenderers.clear();
        // Undo the color mutations on long-lived renderer instances so light
        // mode gets their constructor-era colors back.
        mutatedBackgrounds.forEach(Component::setBackground);
        mutatedBackgrounds.clear();
        mutatedForegrounds.forEach(Component::setForeground);
        mutatedForegrounds.clear();
        mutatedBorders.forEach(javax.swing.JComponent::setBorder);
        mutatedBorders.clear();
        // Last, and over the top of everything above: the colours the renderer
        // had before the switch to dark. These are the only record of what a
        // renderer that colours itself in its constructor is supposed to look
        // like — mutatedBackgrounds can only hold what sanitize() overwrote,
        // which by then was already null.
        stockRendererColors.forEach((component, colors) -> {
            component.setBackground(colors[0]);
            component.setForeground(colors[1]);
        });
        stockRendererColors.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * A JIDE {@code GroupList} paints its group HEADERS through a second,
     * separate renderer slot that {@code setCellRenderer} never touches, so the
     * rows of such a list come out themed while the headers above them stay in
     * stock light-mode colors.
     *
     * <p>Reporting's Data tab is the case in hand (#59): its
     * {@code SyntheticaSafeGroupList} rendered "StartDate"/"EndDate" correctly
     * dark under light-blue "Parameters" and "Data Sources" bars. The list
     * itself probes clean — {@code bg=#46494B fg=#DDDDDD} — because the headers
     * are not components, exactly like a table's cell renderers.
     *
     * <p>Reflective because JIDE is not a compile dependency here, and
     * name-based for the same reason {@link #installedRenderer} is: matching by
     * SHAPE would also match {@code getGroupCellRenderer} from the other
     * direction and wrap the wrong slot.
     */
    private void wrapGroupRenderer(JList<?> list) {
        if (wrappedGroupRenderers.containsKey(list) || skippedGroupLists.containsKey(list)) {
            return;
        }
        ListCellRenderer<?> renderer = groupRenderer(list);
        java.lang.reflect.Method setter = groupRendererSetter(list);
        if (renderer == null || setter == null) {
            skippedGroupLists.put(list, Boolean.TRUE);
            return;
        }
        if (renderer instanceof SanitizingListRenderer) {
            return;
        }
        try {
            setter.invoke(list, new SanitizingListRenderer(renderer));
            wrappedGroupRenderers.put(list, renderer);
            list.repaint();
        } catch (Throwable t) {
            skippedGroupLists.put(list, Boolean.TRUE);
            DebugLog.log("Could not wrap the group renderer of "
                + list.getClass().getName(), t);
        }
    }

    private void restoreGroupRenderer(JList<?> list, ListCellRenderer<?> original) {
        java.lang.reflect.Method setter = groupRendererSetter(list);
        if (setter == null || !(groupRenderer(list) instanceof SanitizingListRenderer)) {
            return;
        }
        try {
            setter.invoke(list, original);
            list.repaint();
        } catch (Throwable t) {
            DebugLog.log("Could not restore the group renderer of "
                + list.getClass().getName(), t);
        }
    }

    /** The group-header renderer of a JIDE GroupList, or null for a plain list. */
    private static ListCellRenderer<?> groupRenderer(JList<?> list) {
        java.lang.reflect.Method getter = findMethod(list.getClass(), "getGroupCellRenderer");
        if (getter == null || !ListCellRenderer.class.isAssignableFrom(getter.getReturnType())) {
            return null;
        }
        try {
            return (ListCellRenderer<?>) getter.invoke(list);
        } catch (Throwable t) {
            return null;
        }
    }

    private static java.lang.reflect.Method groupRendererSetter(JList<?> list) {
        try {
            return list.getClass().getMethod("setGroupCellRenderer", ListCellRenderer.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private final Map<JList<?>, ListCellRenderer<?>> wrappedGroupRenderers = new WeakHashMap<>();
    /** Lists with no group slot, so the reflective lookup is paid once. */
    private final Map<JList<?>, Boolean> skippedGroupLists = new WeakHashMap<>();

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
        sanitizeBorder(component);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                sanitize(child);
            }
        }
    }

    /**
     * Redraw a light line in a renderer's border (#21).
     *
     * <p>The pale band under the Tag Browser's {@code Tag | Value} header is
     * this: {@code SimpleTreeTable$SimpleHeaderRenderer} gives every header
     * cell a compound border whose bottom is 8px of {@code Color.WHITE} over
     * 1px of {@code Table.gridColor}, and the grid colour is a {@code static
     * final} captured at class-init, so it stays the light theme's #C0C5CA for
     * the life of the Designer. Neither is reachable from the component
     * hierarchy — a header cell is a rubber stamp, stamped through a
     * CellRendererPane — which is why every inspection of that panel came back
     * clean while the band stayed on screen.
     *
     * <p>The identical border on {@code SimpleTreeTable$TreeHeader}, the corner
     * above the row header, IS a real component and has been darkened by the
     * hierarchy walk since #20. That is why the band starts at the tree's right
     * edge rather than at the panel's: the same 8px, half of it already fixed.
     *
     * <p>Anything light qualifies here, not just {@code Color.WHITE}: a
     * renderer is chrome by definition, so there is no user content to
     * misidentify. The threshold matches the stale-UIResource background rule
     * above.
     */
    private void sanitizeBorder(Component component) {
        if (!(component instanceof javax.swing.JComponent)) {
            return;
        }
        javax.swing.JComponent target = (javax.swing.JComponent) component;
        javax.swing.border.Border border = target.getBorder();
        javax.swing.border.Border darkened = ThemeManager.darkenBorder(border,
            colour -> colour == Color.WHITE
                || luminance(colour) > ThemeManager.LIGHT_LINE_LUMINANCE);
        if (darkened == null) {
            // Nothing light in it — including a border this pass already
            // darkened, since DARK_BORDER_LINE fails the same test. That is
            // what makes re-running this on every paint free.
            return;
        }
        // Record the original once, but re-darken every time: the renderer
        // re-sets its border on each getTableCellRendererComponent call, so a
        // one-shot swap is undone before the cell is ever painted.
        if (!mutatedBorders.containsKey(target)) {
            mutatedBorders.put(target, border);
        }
        target.setBorder(darkened);
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
