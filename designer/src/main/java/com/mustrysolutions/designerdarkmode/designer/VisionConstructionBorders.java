package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * After a tree update under dark mode, puts a Vision component's
 * look-and-feel border back to what a fresh instance of its class has
 * (#92, part 4).
 *
 * <p>A save writes {@code setBorder} when the component's border differs
 * from its clean copy's — a fresh instance built under the current look and
 * feel. Under Synthetica the two never differ in a way that is written:
 * every Synth delegate hands its component a {@code SynthBorder}, and the
 * serializer's own rule makes any two of those equal. Under FlatLaf they can,
 * in both directions, because a tree update's {@code installBorder} treats
 * {@code null} and a {@code UIResource} alike while constructors do not:
 *
 * <ul>
 *   <li>A Comments Panel is a scroll pane whose constructor sets its border
 *       to {@code null}. Under FlatLaf a fresh one stays {@code null} (under
 *       Synthetica it is re-bordered during construction), so its clean copy
 *       has no border — and the tree update gives the live one
 *       {@code ScrollPane.border}. The save writes
 *       {@code <o cls="com.formdev.flatlaf.ui.FlatScrollPaneBorder"/>} and a
 *       Vision client cannot open the window. Found live on 0.4.0.</li>
 *   <li>A Date Time Popup Selector is a panel whose constructor borrows
 *       {@code TextField.border}, so its clean copy has that; FlatLaf defines
 *       no {@code Panel.border}, so the tree update strips the live one to
 *       {@code null}, and the save writes {@code setBorder <null/>} — a
 *       borderless selector on the client.</li>
 * </ul>
 *
 * <p>Neither can be settled at the save: {@link LookAndFeelBorders} makes two
 * FlatLaf borders of a class equal, but the serializer consults no delegate
 * for a {@code null}. So it is settled before, in the module's tree walk,
 * by the same yardstick the save will use: a fresh instance of the class,
 * built once per switch. A live border that is the look and feel's (a
 * {@code UIResource}, or none) is set to the fresh one's — {@code null} for
 * the Comments Panel, FlatLaf's shared {@code TextField.border} instance for
 * the selector — and a border the user set is left alone. The fresh
 * instance is what the serializer builds for its clean copy anyway, so this
 * adds no construction a save would not have done.
 *
 * <p>Dark only, and only inside Vision content ({@link VisionWindows}): under
 * Synthetica the saves are clean as they are, and constructing arbitrary
 * Designer classes to compare against would be reckless. The cache is
 * cleared at every switch, since a fresh instance is only fresh under the
 * look and feel that built it.
 */
final class VisionConstructionBorders {

    /** What a fresh instance of a class has for a border, or that none could be built. */
    static final class Fresh {
        static final Fresh UNBUILDABLE = new Fresh(false, null);
        final boolean built;
        final Border border;

        Fresh(boolean built, Border border) {
            this.built = built;
            this.border = border;
        }

        static Fresh with(Border border) {
            return new Fresh(true, border);
        }
    }

    /** The fresh instance's border per class, built once per switch. */
    private static final Map<Class<?>, Fresh> FRESH = new HashMap<>();

    private VisionConstructionBorders() {
    }

    /** Forget every fresh border; at each switch, before any tree update. */
    static void clear() {
        FRESH.clear();
    }

    /**
     * Align {@code updated}'s look-and-feel border with a fresh instance's,
     * if dark mode is installed and the component is Vision content.
     *
     * @return true when the border was changed
     */
    static boolean align(JComponent updated) {
        if (!(UIManager.getLookAndFeel() instanceof FlatDarkLaf) || !insideVisionContent(updated)) {
            return false;
        }
        return alignWithFresh(updated, freshBorder(updated.getClass()));
    }

    /**
     * The alignment itself, given the fresh border: a test seam for the
     * harness, which has no Vision content to be inside of.
     *
     * @param fresh the fresh instance's border, or {@link Fresh#UNBUILDABLE}
     *        (nothing is changed then)
     */
    static boolean alignWithFresh(JComponent updated, Fresh fresh) {
        if (!fresh.built) {
            return false;
        }
        Border live = updated.getBorder();
        if (live != null && !(live instanceof UIResource)) {
            return false; // the user's, or the component's own
        }
        Border target = fresh.border;
        if (live == target) {
            return false;
        }
        if (live != null && target != null && live.getClass() == target.getClass()) {
            return false; // LookAndFeelBorders makes these equal at the save
        }
        updated.setBorder(target);
        return true;
    }

    /** A fresh instance's border, built once per class and switch. */
    static Fresh freshBorder(Class<?> type) {
        return FRESH.computeIfAbsent(type, VisionConstructionBorders::buildFresh);
    }

    private static Fresh buildFresh(Class<?> type) {
        try {
            Object fresh = type.getDeclaredConstructor().newInstance();
            return fresh instanceof JComponent
                ? Fresh.with(((JComponent) fresh).getBorder())
                : Fresh.UNBUILDABLE;
        } catch (Throwable t) {
            DebugLog.detail("VisionConstructionBorders: no fresh " + type.getName()
                + " to compare against (" + t + "); its border is left as the tree update made it.");
            return Fresh.UNBUILDABLE;
        }
    }

    static boolean insideVisionContent(Component component) {
        for (Component p = component.getParent(); p != null; p = p.getParent()) {
            if (VisionWindows.isVisionTopLevel(p)) {
                return true;
            }
        }
        return false;
    }
}
