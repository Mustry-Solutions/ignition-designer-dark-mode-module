package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.synth.SynthTableUI;
import javax.swing.table.TableCellRenderer;

/**
 * Keeps the default renderers a table's owner set across the switch to dark.
 *
 * <p>A plain {@code JTable} gets its UI inside its own constructor, so every
 * default renderer its owner sets comes after the Synthetica UI is installed.
 * Two things then overwrite that renderer on the way to dark, and neither
 * checks what it is overwriting:
 *
 * <ol>
 *   <li>Synthetica puts its own Object and Boolean renderers over the JDK's
 *       {@code SynthTableUI} ones and keeps the JDK's in its
 *       {@code ComponentPropertyStore}. {@code SyntheticaLookAndFeel
 *       .uninitialize}, inside {@code UIManager.setLookAndFeel}, sets the
 *       kept one back;</li>
 *   <li>{@code SynthTableUI.installDefaults} saves the table's renderers for
 *       the classes below and {@code uninstallDefaults} sets every saved one
 *       back, so the tree walk that swaps in FlatLaf's UI does it again.</li>
 * </ol>
 *
 * <p>The owner's renderer was gone in dark. Back in light its slot held a
 * {@code UIResource}, which Synthetica replaced with its own, so it never
 * came back. Candidates in a Designer include the permission-model editors'
 * Boolean columns, {@code SecurityTable} and {@code ThreadViewerTable}.
 * Both mechanisms belong to a {@code SynthTableUI}, and only a table on one
 * is touched here. JIDE's tables are not: their UIs are their own.
 *
 * <p>So the switch to dark records each table's own renderers before
 * {@code setLookAndFeel} ({@link #capture}) and puts back any the switch
 * replaced once the tree walk is done ({@link #restore}). Every
 * {@code updateUI()} the module runs on a component goes through
 * {@link #updateUi}, which does the same around a single swap. That covers
 * the light-side refresh passes, which replace a Synthetica UI with a fresh
 * one. The light restore itself needs nothing: FlatLaf's UI leaves the
 * renderers alone on the way out, and neither {@code SynthTableUI} nor
 * Synthetica installs over a renderer that is not a look and feel's.
 *
 * <p>"Own" is Swing's rule, with one exception. A renderer that is not a
 * {@code UIResource} belongs to the application and a look and feel must
 * leave it alone. {@code SynthTableUI}'s own renderers do not carry the
 * marker, though, and are no more the table's than Synthetica's are, so
 * anything from {@code javax.swing.plaf} is the look and feel's too. A table
 * whose renderers are all the look and feel's, as most are, is left exactly
 * as the switch leaves it.
 */
final class AppTableRenderers {

    /**
     * The classes {@code SynthTableUI} saves and restores. Parents come before
     * children, so a renderer inherited from Object is put back on Object.
     */
    private static final Class<?>[] SWAPPED_CLASSES = {
        Object.class, Number.class, Double.class, Float.class,
        java.util.Date.class, Icon.class, ImageIcon.class, Boolean.class,
    };

    /** Tables that hold a renderer of their own, from {@link #capture} to {@link #restore}. */
    private final Map<JTable, Map<Class<?>, TableCellRenderer>> captured = new WeakHashMap<>();

    /** Record every table's own renderers. Before {@code setLookAndFeel}. */
    void capture() {
        captured.clear();
        for (Window window : Window.getWindows()) {
            try {
                captureIn(window);
            } catch (Throwable t) {
                DebugLog.log("Could not record the table renderers under "
                    + window.getClass().getName() + "; continuing.", t);
            }
        }
        if (!captured.isEmpty()) {
            DebugLog.detail("Recorded the own default renderers of "
                + captured.size() + " table(s).");
        }
    }

    /** Put back what the switch replaced. After the tree walk. */
    void restore() {
        captured.forEach(AppTableRenderers::restore);
        captured.clear();
    }

    /**
     * {@code component.updateUI()}, with a table's own default renderers put
     * back afterwards. Also put back when the update throws: the outgoing
     * UI's uninstall, which is what overwrites them, runs first.
     */
    static void updateUi(JComponent component) {
        if (!(component instanceof JTable)) {
            component.updateUI();
            return;
        }
        JTable table = (JTable) component;
        Map<Class<?>, TableCellRenderer> before = ownRenderers(table);
        try {
            table.updateUI();
        } finally {
            if (before != null) {
                restore(table, before);
            }
        }
    }

    private void captureIn(Container container) {
        Component[] children;
        try {
            children = container.getComponents();
        } catch (Throwable t) {
            // FilterablePalette's components attribute throws on access (see
            // ThemeManager.childrenOf); its subtree holds no tables of note.
            return;
        }
        for (Component child : children) {
            if (child instanceof JTable) {
                Map<Class<?>, TableCellRenderer> own = ownRenderers((JTable) child);
                if (own != null) {
                    captured.put((JTable) child, own);
                }
            }
            if (child instanceof Container) {
                captureIn((Container) child);
            }
        }
    }

    /**
     * The effective renderer per class, or null when none is the table's own
     * or the table's UI is not one that overwrites them.
     */
    private static Map<Class<?>, TableCellRenderer> ownRenderers(JTable table) {
        if (!(table.getUI() instanceof SynthTableUI)) {
            return null;
        }
        Map<Class<?>, TableCellRenderer> renderers = new LinkedHashMap<>();
        boolean anyOwn = false;
        for (Class<?> valueClass : SWAPPED_CLASSES) {
            TableCellRenderer renderer = table.getDefaultRenderer(valueClass);
            renderers.put(valueClass, renderer);
            anyOwn |= isOwn(renderer);
        }
        return anyOwn ? renderers : null;
    }

    private static void restore(JTable table, Map<Class<?>, TableCellRenderer> before) {
        boolean changed = false;
        try {
            for (Map.Entry<Class<?>, TableCellRenderer> entry : before.entrySet()) {
                Class<?> valueClass = entry.getKey();
                TableCellRenderer own = entry.getValue();
                if (!isOwn(own)) {
                    continue;
                }
                boolean lost = table.getDefaultRenderer(valueClass) != own;
                // Inherited before the switch (Synthetica's UI clears the
                // Date, Number and Icon entries, so they fall through to
                // Object): drop the entry the uninstall put back and it
                // inherits the parent's again, as it did. Also when that entry
                // already holds the same renderer: SynthTableUI saved the
                // inherited one at install and wrote it back as the class's
                // own, and left there it would stop following a later change
                // to the parent's. JTable cannot say whether a mapping is
                // explicit, so one the owner set to the parent's very renderer
                // goes too; it paints the same either way.
                if (valueClass != Object.class && before.get(parentOf(valueClass)) == own) {
                    table.setDefaultRenderer(valueClass, null);
                }
                if (table.getDefaultRenderer(valueClass) != own) {
                    table.setDefaultRenderer(valueClass, own);
                }
                if (!lost) {
                    continue;
                }
                changed = true;
                DebugLog.detail("Kept " + table.getClass().getName() + "'s own "
                    + valueClass.getSimpleName() + " renderer ("
                    + own.getClass().getName() + ") across the switch.");
            }
        } catch (Throwable t) {
            DebugLog.log("Could not put back the default renderers of "
                + table.getClass().getName() + "; continuing.", t);
        }
        if (changed) {
            table.repaint();
        }
    }

    /** Where {@code JTable.getDefaultRenderer} looks next when a class has no entry. */
    private static Class<?> parentOf(Class<?> valueClass) {
        Class<?> parent = valueClass.getSuperclass();
        return parent != null ? parent : Object.class;
    }

    static boolean isOwn(TableCellRenderer renderer) {
        return renderer != null
            && !(renderer instanceof UIResource)
            && !renderer.getClass().getName().startsWith("javax.swing.plaf.");
    }
}
