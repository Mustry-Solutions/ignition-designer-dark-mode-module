package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.swing.UIManager;
import javax.swing.plaf.UIResource;

/**
 * The stock value for a look-and-feel colour a Vision component inherits,
 * for what a save writes.
 *
 * <p>Vision writes a component's inherited colours. A rectangle dropped into
 * a container has no foreground of its own; {@code getForeground()} walks up
 * to the container, which holds the look and feel's {@code Panel.foreground}
 * as a {@code UIResource}, and the platform serializer, comparing that against
 * a parentless clean copy whose foreground is {@code null}, writes it. In a
 * stock Designer that is #2E2E2E and harmless. Under dark mode it is FlatLaf's
 * #DDDDDD: near-white text on a light client, in a file that outlives the
 * theme. The same for backgrounds. The corruption sweep found this on every
 * palette component that inherits.
 *
 * <p>Every Vision container — {@code BasicContainer}, a window's root, a
 * template — carries exactly {@code Panel.foreground} and
 * {@code Panel.background}, so those two are what a child inherits, and the
 * stock values of those two keys are what a stock Designer writes. Captured
 * just before FlatLaf goes in; FlatLaf's instances for the same two keys are
 * captured just after, and matched by identity (FlatLaf serves one shared
 * {@code ColorUIResource} per key). Nothing broader: FlatLaf's dark values
 * stand in for several stock keys each, so a general map is ambiguous and is
 * not attempted.
 *
 * <p>A hand-set colour is never a {@code UIResource} and is left alone.
 */
final class LookAndFeelColors {

    private static final String[] INHERITED_KEYS = {"Panel.foreground", "Panel.background"};

    private final Map<String, Integer> stockByKey = new java.util.HashMap<>();
    private final Map<Color, Integer> darkInstanceToStock = new IdentityHashMap<>();

    /** Before the dark look and feel goes in. */
    void captureStock() {
        stockByKey.clear();
        for (String key : INHERITED_KEYS) {
            Color stock = UIManager.getColor(key);
            if (stock != null) {
                stockByKey.put(key, stock.getRGB());
            }
        }
    }

    /** After the dark look and feel and every default is in. */
    void captureDark() {
        darkInstanceToStock.clear();
        for (String key : INHERITED_KEYS) {
            Color dark = UIManager.getColor(key);
            Integer stock = stockByKey.get(key);
            if (dark != null && stock != null) {
                darkInstanceToStock.put(dark, stock);
            }
        }
        DebugLog.detail("LookAndFeelColors: " + darkInstanceToStock.size()
            + " inherited look-and-feel colour(s) mapped back to stock.");
    }

    void clear() {
        darkInstanceToStock.clear();
    }

    /**
     * The stock ARGB for a dark look-and-feel colour a Vision component
     * inherits, or {@code null} for anything else.
     */
    Integer stockRgb(Color color) {
        if (!(color instanceof UIResource) || darkInstanceToStock.isEmpty()) {
            return null;
        }
        return darkInstanceToStock.get(color);
    }
}
