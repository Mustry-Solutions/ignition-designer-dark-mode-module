package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.JLabel;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * One-line messages in the Designer's own status bar.
 *
 * <p>This module's characteristic failure is partial success: the look-and-feel
 * swap works, one of the later passes does not, and the result is a Designer
 * that is dark except for one light tree. Every pass runs under
 * {@code ThemeManager.safely(...)}, so nothing breaks — but the only record
 * used to be {@code ~/.ignition/designer-dark-mode.log}, which a user has no
 * reason to know exists. The status bar is the Designer's own place for a line
 * of transient text, and it is where the module now says what it could not do.
 *
 * <p>{@code StatusBar.setMessage(text, true)} paints synchronously. That
 * matters: the caller is usually about to block the event dispatch thread for
 * the length of a theme switch, so a message posted the ordinary way would not
 * appear until the thing it describes had already finished.
 *
 * <p>{@code setMessage} also sets the message label's foreground to
 * {@code Color.black} on every call — under dark mode, black text on a dark
 * bar, for the Designer's own messages as much as for ours. A listener lifts
 * the foreground again whenever that happens: the same shape as ThemeManager's
 * white-background enforcer, and for the same reason. IA re-asserts the colour
 * at runtime, so a one-time correction does not hold.
 *
 * <p>The status bar is reached reflectively. {@code DesignerContext} exposes
 * it, but {@code designer.gui.StatusBar} is not SDK surface, and a Designer
 * that has moved it should cost this module its status messages rather than
 * its ability to load.
 */
final class DesignerStatus {

    /** The light foreground the rest of the module uses. */
    private static final Color LIGHT_FOREGROUND = new Color(0xDDE0E3);

    /** Foreground luminance below which a label is unreadable on the dark bar. */
    private static final int TOO_DARK = 90;

    /** The {@code DesignerContext}, held as Object so this class loads without it. */
    private Object context;

    /** Labels we have taken over, and the foreground each carried (null if unset). */
    private final Map<JLabel, Color> liftedForegrounds = new WeakHashMap<>();

    /** The last message we posted, so {@link #clear()} never wipes someone else's. */
    private String ourMessage;

    private final PropertyChangeListener foregroundEnforcer = event -> {
        if (event.getSource() instanceof JLabel
                && event.getNewValue() instanceof Color
                && isTooDark((Color) event.getNewValue())
                && UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            ((JLabel) event.getSource()).setForeground(LIGHT_FOREGROUND);
        }
    };

    void attach(Object designerContext) {
        this.context = designerContext;
    }

    /** Show one line. Never throws — a status message must not break a switch. */
    void message(String text) {
        Object bar = statusBar();
        if (bar == null) {
            return;
        }
        try {
            // Painted synchronously (the boolean): the caller is about to
            // block the event dispatch thread for the length of the switch.
            bar.getClass().getMethod("setMessage", String.class, boolean.class)
                .invoke(bar, text, true);
            ourMessage = text;
            keepLegible((Container) bar);
        } catch (Throwable t) {
            DebugLog.detail("Could not post a status bar message.", t);
        }
    }

    /** Take our own message down, leaving anything the Designer put there. */
    void clear() {
        Object bar = statusBar();
        if (bar == null || ourMessage == null) {
            return;
        }
        try {
            Object current = bar.getClass().getMethod("getMessage").invoke(bar);
            if (ourMessage.equals(current)) {
                bar.getClass().getMethod("clearMessage").invoke(bar);
            }
            ourMessage = null;
        } catch (Throwable t) {
            DebugLog.detail("Could not clear the status bar message.", t);
        }
    }

    /** Keep the status bar readable for as long as the dark theme is in effect. */
    void install() {
        Object bar = statusBar();
        if (bar instanceof Container) {
            keepLegible((Container) bar);
        }
    }

    /** Hand the status bar's labels back exactly as they were. */
    void uninstall() {
        liftedForegrounds.forEach((label, original) -> {
            label.removePropertyChangeListener("foreground", foregroundEnforcer);
            // null puts an inherited foreground back to inherited, rather than
            // freezing it at whatever it happened to be showing.
            label.setForeground(original);
        });
        liftedForegrounds.clear();
    }

    private Object statusBar() {
        if (context == null) {
            return null;
        }
        try {
            Method getStatusBar = context.getClass().getMethod("getStatusBar");
            getStatusBar.setAccessible(true);
            return getStatusBar.invoke(context);
        } catch (Throwable t) {
            DebugLog.detail("The Designer status bar is unavailable.", t);
            return null;
        }
    }

    /**
     * Lift every label in the bar that would be unreadable, and keep watching
     * it: the Designer re-asserts black on the message label every time it
     * posts a message of its own.
     */
    void keepLegible(Container container) {
        if (!(UIManager.getLookAndFeel() instanceof FlatDarkLaf)) {
            return;
        }
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel) {
                JLabel label = (JLabel) child;
                if (!liftedForegrounds.containsKey(label)) {
                    liftedForegrounds.put(label,
                        label.isForegroundSet() ? label.getForeground() : null);
                    label.addPropertyChangeListener("foreground", foregroundEnforcer);
                }
                if (isTooDark(label.getForeground())) {
                    label.setForeground(LIGHT_FOREGROUND);
                }
            }
            if (child instanceof Container) {
                keepLegible((Container) child);
            }
        }
    }

    private static boolean isTooDark(Color color) {
        return color != null && ThemeManager.luminance(color) < TOO_DARK;
    }
}
