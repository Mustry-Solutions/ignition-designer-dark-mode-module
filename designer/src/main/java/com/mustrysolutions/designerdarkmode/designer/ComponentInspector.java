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
                dumpScrollPaneParts(c);
            }
        }
    }

    /**
     * A scroll pane's parts are not reachable by hit-testing when they are
     * short. An empty JTable has almost no height, so the pointer falls
     * straight past it to the viewport and the table never appears in the
     * chain — which is exactly how a pale band at the top of a viewport can
     * survive a dozen "everything is dark" inspections.
     *
     * <p>So report the parts directly rather than waiting for the pointer to
     * land on one: the viewport's view, the row and column header views, and
     * the corners. Each is described with its bounds, so a band can be matched
     * to whichever part actually occupies those pixels.
     */
    private static void dumpScrollPaneParts(Component c) {
        if (!(c instanceof javax.swing.JScrollPane)) {
            return;
        }
        javax.swing.JScrollPane pane = (javax.swing.JScrollPane) c;
        logPart("viewport.view", pane.getViewport() == null
            ? null : pane.getViewport().getView());
        logPart("rowHeader.view", pane.getRowHeader() == null
            ? null : pane.getRowHeader().getView());
        logPart("columnHeader.view", pane.getColumnHeader() == null
            ? null : pane.getColumnHeader().getView());
        for (String corner : new String[] {
            javax.swing.JScrollPane.UPPER_LEFT_CORNER,
            javax.swing.JScrollPane.UPPER_RIGHT_CORNER,
            javax.swing.JScrollPane.LOWER_LEFT_CORNER,
            javax.swing.JScrollPane.LOWER_RIGHT_CORNER,
        }) {
            try {
                logPart(corner, pane.getCorner(corner));
            } catch (Throwable ignored) {
                // Not every scroll pane accepts every corner constant.
            }
        }
    }

    private static void logPart(String label, Component part) {
        if (part != null) {
            DebugLog.log("      [" + label + "] " + describe(part));
        }
    }

    private static String describe(Component c) {
        StringBuilder sb = new StringBuilder(c.getClass().getName());
        sb.append(" bg=").append(describeColor(c.getBackground(), c.isBackgroundSet()));
        sb.append(" fg=").append(describeColor(c.getForeground(), c.isForegroundSet()));
        // Bounds, so a light band can be matched to the component that
        // actually occupies those pixels rather than the one the pointer
        // happens to hit.
        sb.append(String.format(" @%d,%d %dx%d", c.getX(), c.getY(),
            c.getWidth(), c.getHeight()));
        if (c instanceof JComponent) {
            if (((JComponent) c).isOpaque()) {
                sb.append(" opaque");
            }
            // Borders were the inspector's blind spot: it reported background,
            // foreground, opacity and UI delegate, so a component painting a
            // LIGHT BORDER looked completely clean here while drawing a pale
            // band on screen. Two dozen samples over one such band all came
            // back dark before this was added.
            sb.append(describeBorder(((JComponent) c).getBorder()));
            // A JScrollPane paints a SECOND border around its viewport, which
            // getBorder() does not report. It is the natural place for a thin
            // band hugging the viewport edge, and it was invisible here.
            if (c instanceof javax.swing.JScrollPane) {
                String viewport =
                    describeBorder(((javax.swing.JScrollPane) c).getViewportBorder());
                if (!viewport.isEmpty()) {
                    sb.append(viewport.replace(" border=", " viewportBorder="));
                }
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

    /**
     * Border class and, where the border exposes one, its colour. A pale band
     * that no background explains is usually a border — this is the only place
     * that would show it.
     */
    private static String describeBorder(javax.swing.border.Border border) {
        if (border == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" border=")
            .append(border.getClass().getSimpleName());
        try {
            if (border instanceof javax.swing.border.LineBorder) {
                sb.append('(')
                    .append(describeColor(
                        ((javax.swing.border.LineBorder) border).getLineColor(), true))
                    .append(')');
            } else if (border instanceof javax.swing.border.MatteBorder) {
                sb.append('(')
                    .append(describeColor(
                        ((javax.swing.border.MatteBorder) border).getMatteColor(), true))
                    .append(')');
            } else if (border instanceof javax.swing.border.CompoundBorder) {
                javax.swing.border.CompoundBorder compound =
                    (javax.swing.border.CompoundBorder) border;
                sb.append('[')
                    .append(describeBorder(compound.getOutsideBorder()).trim())
                    .append(' ')
                    .append(describeBorder(compound.getInsideBorder()).trim())
                    .append(']');
            }
        } catch (Throwable ignored) {
            // A border that will not describe itself is still worth naming.
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
