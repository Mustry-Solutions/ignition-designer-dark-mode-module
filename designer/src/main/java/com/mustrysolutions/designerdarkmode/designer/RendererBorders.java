package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

/**
 * Keeps a renderer component's OWN {@code null} border across a delegate
 * refresh, in both directions.
 *
 * <p>{@code installBorder} treats a {@code null} border as unset and fills it
 * in. Vision's property editor cells are built from components that clear
 * their border on purpose — {@code EditorTextField} in its constructor,
 * {@code EditorFormattedField} in the {@code init()} every constructor calls —
 * so a stock-built value sits flush in its row. Any refresh of such a cached
 * editor boxed it: the dark paint's refresh gave it {@code FlatTextBorder},
 * the light watcher's gave it {@code SynthBorder}. Found live on 8.3.6, where
 * the Title and Titlebar Height values were boxed after switches a
 * never-dark Designer would not show.
 *
 * <p>So: note which components had no border before a refresh, and afterwards
 * give each one that gained a look-and-feel border what a fresh instance of
 * its class has under the look and feel now in force — the yardstick
 * {@link VisionConstructionBorders} uses for saves. A class that clears its own
 * border gets {@code null} back; a plain label keeps the look and feel's.
 *
 * <p>Separate from {@link VisionConstructionBorders} because it may build a
 * fresh instance with a one-{@code Object} constructor when there is no no-arg
 * one ({@code EditorFormattedField} has none). The save's yardstick must stay
 * what the serializer itself can build, so that fallback lives here only, for
 * renderer components — which is all that is ever passed in.
 */
final class RendererBorders {

    /** A fresh instance's border per class, built once per class and switch. */
    private static final Map<Class<?>, VisionConstructionBorders.Fresh> FRESH = new HashMap<>();

    private RendererBorders() {
    }

    /** Forget every fresh border; at each switch, since fresh is per look and feel. */
    static void clear() {
        FRESH.clear();
    }

    /** The components under {@code root} that have no border now, before a refresh. */
    static List<JComponent> borderless(Component root) {
        List<JComponent> into = new ArrayList<>();
        collect(root, into);
        return into;
    }

    private static void collect(Component component, List<JComponent> into) {
        if (component instanceof JComponent && ((JComponent) component).getBorder() == null) {
            into.add((JComponent) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, into);
            }
        }
    }

    /**
     * After the refresh: give each component that gained a border what a fresh
     * instance of its class has.
     *
     * @return how many borders were changed
     */
    static int restore(List<JComponent> borderless) {
        int changed = 0;
        for (JComponent component : borderless) {
            if (component.getBorder() != null
                    && VisionConstructionBorders.alignWithFresh(component, fresh(component.getClass()))) {
                changed++;
            }
        }
        return changed;
    }

    /** A fresh instance's border, built once per class and switch. */
    static VisionConstructionBorders.Fresh fresh(Class<?> type) {
        return FRESH.computeIfAbsent(type, RendererBorders::build);
    }

    /**
     * Only Swing text components are ever built. A fresh instance is built on
     * the paint path (the dark renderer pane refreshes as it paints), so its
     * constructor must be one that does nothing but configure itself; a
     * renderer component of any other kind whose border was cleared is left
     * as the refresh made it. Vision's value editors that clear their border
     * are all text components ({@code EditorTextField},
     * {@code EditorFormattedField}), and the one-{@code Object} fallback is
     * narrower still: only a {@code JFormattedTextField}, whose
     * {@code (Object value)} constructor accepts {@code null}.
     */
    private static VisionConstructionBorders.Fresh build(Class<?> type) {
        if (!javax.swing.text.JTextComponent.class.isAssignableFrom(type)) {
            return VisionConstructionBorders.Fresh.UNBUILDABLE;
        }
        Object fresh = null;
        try {
            fresh = type.getDeclaredConstructor().newInstance();
        } catch (Throwable noArg) {
            if (!javax.swing.JFormattedTextField.class.isAssignableFrom(type)) {
                DebugLog.detail("RendererBorders: no no-arg " + type.getName()
                    + " to compare against (" + noArg + "); its border is left as the refresh made it.");
                return VisionConstructionBorders.Fresh.UNBUILDABLE;
            }
            // A formatted field's (Object value) constructor, given null.
            try {
                Constructor<?> byValue = type.getConstructor(Object.class);
                fresh = byValue.newInstance((Object) null);
            } catch (Throwable t) {
                DebugLog.detail("RendererBorders: no fresh " + type.getName()
                    + " to compare against (" + t + "); its border is left as the refresh made it.");
            }
        }
        return fresh instanceof JComponent
            ? VisionConstructionBorders.Fresh.with(((JComponent) fresh).getBorder())
            : VisionConstructionBorders.Fresh.UNBUILDABLE;
    }
}
