package com.mustrysolutions.designerdarkmode.designer;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.plaf.UIResource;

/**
 * Debug helper: Cmd/Ctrl+Shift+I (or +F12 where F-keys are real keys) dumps
 * the component chain under the mouse pointer to the debug log — class,
 * background/foreground (marking UIResource vs explicit, set vs inherited),
 * opacity, and UI delegate per level. This identifies exactly which component
 * paints a stubborn light area under dark mode, instead of guessing from
 * bytecode. On macOS the F-keys default to media keys, hence the letter chord.
 */
public final class ComponentInspector {

    private AWTEventListener listener;

    public void install() {
        if (listener != null) {
            return;
        }
        listener = event -> {
            if (event instanceof KeyEvent) {
                KeyEvent key = (KeyEvent) event;
                boolean chord = (key.isControlDown() || key.isMetaDown()) && key.isShiftDown();
                boolean trigger = key.getKeyCode() == KeyEvent.VK_F12
                    || key.getKeyCode() == KeyEvent.VK_I;
                if (key.getID() == KeyEvent.KEY_PRESSED && chord && trigger) {
                    dumpUnderMouse();
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK);
    }

    public void uninstall() {
        if (listener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
            listener = null;
        }
    }

    private void dumpUnderMouse() {
        PointerInfo pointer = MouseInfo.getPointerInfo();
        if (pointer == null) {
            return;
        }
        Point screen = pointer.getLocation();
        for (Window window : Window.getWindows()) {
            if (!window.isShowing()) {
                continue;
            }
            Point local = new Point(screen);
            SwingUtilities.convertPointFromScreen(local, window);
            if (!window.contains(local)) {
                continue;
            }
            Component deepest = SwingUtilities.getDeepestComponentAt(window, local.x, local.y);
            if (deepest == null) {
                continue;
            }
            DebugLog.log("=== Inspector @ " + screen.x + "," + screen.y
                + " in " + window.getClass().getName() + " ===");
            int depth = 0;
            for (Component c = deepest; c != null && depth < 25; c = c.getParent(), depth++) {
                DebugLog.log("  ".repeat(1) + describe(c));
            }
        }
    }

    private static String describe(Component c) {
        StringBuilder sb = new StringBuilder(c.getClass().getName());
        sb.append(" bg=").append(describeColor(c.getBackground(), c.isBackgroundSet()));
        sb.append(" fg=").append(describeColor(c.getForeground(), c.isForegroundSet()));
        if (c instanceof JComponent) {
            if (((JComponent) c).isOpaque()) {
                sb.append(" opaque");
            }
            try {
                Object ui = c.getClass().getMethod("getUI").invoke(c);
                if (ui != null) {
                    sb.append(" ui=").append(ui.getClass().getName());
                }
            } catch (Exception ignored) {
                // Not every JComponent exposes getUI.
            }
        }
        return sb.toString();
    }

    private static String describeColor(Color color, boolean set) {
        if (color == null) {
            return "null";
        }
        return String.format("#%06X", color.getRGB() & 0xFFFFFF)
            + (color instanceof UIResource ? "|uires" : "|explicit")
            + (set ? "" : "|inherited");
    }
}
