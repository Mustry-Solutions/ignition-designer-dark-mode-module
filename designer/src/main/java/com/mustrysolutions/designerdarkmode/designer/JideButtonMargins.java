package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Window;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.plaf.UIResource;

/**
 * Puts back the margin an application gave a JIDE button when the switch's
 * tree update replaced it (#174).
 *
 * <p>Under any Synth look and feel, Synthetica included, JIDE's
 * {@code BasicJideButtonUI.updateMargin} sets the margin from the Synth style
 * on every {@code updateUI}, whether or not the application set one. Swing's
 * own button UIs leave an application's margin alone. Vision sets its palette
 * items' margin after building them, so in a stock Designer it lasts until the
 * next tree update; the light restore is one, and the items lost their 20 px
 * indent. Taken before the tree update and put back after it.
 */
final class JideButtonMargins {

    static final String JIDE_BUTTON_CLASS = "com.jidesoft.swing.JideButton";

    private final Map<AbstractButton, Insets> margins = new IdentityHashMap<>();

    private JideButtonMargins() {
    }

    /** The application-set margins of every JIDE button in these windows. */
    static JideButtonMargins capture(Window[] windows) {
        JideButtonMargins captured = new JideButtonMargins();
        for (Window window : windows) {
            captured.walk(window);
        }
        return captured;
    }

    /**
     * Put back each captured margin the tree update replaced.
     *
     * @return how many were put back
     */
    int restore() {
        int restored = 0;
        for (Map.Entry<AbstractButton, Insets> entry : margins.entrySet()) {
            AbstractButton button = entry.getKey();
            if (!entry.getValue().equals(button.getMargin())) {
                button.setMargin(entry.getValue());
                restored++;
            }
        }
        if (restored > 0) {
            DebugLog.log("JideButtonMargins: put back " + restored + " application margin"
                + (restored == 1 ? "" : "s") + " the tree update replaced.");
        }
        return restored;
    }

    private void walk(Component component) {
        if (component instanceof AbstractButton && isJideButton(component.getClass())) {
            Insets margin = ((AbstractButton) component).getMargin();
            if (margin != null && !(margin instanceof UIResource)) {
                margins.put((AbstractButton) component, margin);
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                walk(child);
            }
        }
    }

    static boolean isJideButton(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (JIDE_BUTTON_CLASS.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
