package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

/**
 * Dark-adapts the blocks on a block-and-connector workspace — alarm
 * notification pipelines and SFC charts.
 *
 * <h2>Why the blocks stay light</h2>
 *
 * <p>{@code BasicBlockUI} is a {@code JPanel}, but it does not paint itself
 * from its own background: {@code paintComponent} fills a shape with one of two
 * {@code Color} fields and strokes it with one of three others. All five are
 * assigned literal colours in the constructor, verified against
 * {@code designer-8.3.6.jar}:
 *
 * <pre>
 * backgroundUnconnected = TypeUtilities.toColor("#EEEEEE");
 * backgroundConnected   = new Color(176, 216, 234);   // #B0D8EA
 * borderConnected       = Color.DARK_GRAY;
 * borderSelected        = new Color(247, 144, 30);    // #F7901E
 * borderUnconnected     = new Color(207, 202, 198);   // #CFCAC6
 * </pre>
 *
 * <p>Literal colours are reached by no look-and-feel swap and by no
 * {@code UIManager} override, so the fills stay pale. The <em>text</em> does
 * not: {@code initHeader} builds a plain {@code new JLabel(getTitle())} and
 * never calls {@code setForeground}, so the title inherits
 * {@code Label.foreground} — light under FlatLaf. Light text on a pale fill is
 * the illegible result reported on 2026-08-29.
 *
 * <p>The fix is to darken the fills rather than to fight the labels. Correcting
 * the label foregrounds instead would leave pale blocks sitting on a dark
 * canvas, which is a different kind of wrong.
 *
 * <h2>Why this is a luminance rule and not a table</h2>
 *
 * <p>Subclasses override these colours through the same public setters, and at
 * least one carries a colour that is already right: alarm pipelines'
 * {@code StartBlock$UI} sets its fill and all three borders to IA's
 * {@code #F7901E} orange, which is exactly why the START block is the one thing
 * on that canvas that reads correctly today. A per-field table would have
 * repainted it.
 *
 * <p>So each colour is judged on what it is, not on which field holds it: a
 * fill is darkened only if it is <em>light</em>, a border is lightened only if
 * it is <em>dark</em>, and anything already suited to a dark canvas — the
 * orange, the cyan pin highlight, the pale unconnected border — is left exactly
 * alone. Hue and saturation are preserved so a blue block stays blue.
 */
final class BlockWorkspaceTheme {

    // Package-private so ReflectiveSurfaceTest can assert this name still
    // resolves against the Ignition the harness runs.
    static final String BASIC_BLOCK_UI =
        "com.inductiveautomation.ignition.designer.blockandconnector.blockui.BasicBlockUI";

    /** Fill fields, and the properties behind them. */
    // Package-private so ReflectiveSurfaceTest can assert this name still
    // resolves against the Ignition the harness runs.
    static final String[] FILLS = {"BackgroundConnected", "BackgroundUnconnected"};

    /**
     * Stroke fields. {@code BorderSelected} is included so a subclass that
     * darkened it is corrected too; the stock orange fails the test below and
     * is left as it is.
     */
    // Package-private so ReflectiveSurfaceTest can assert this name still
    // resolves against the Ignition the harness runs.
    static final String[] STROKES =
        {"BorderConnected", "BorderUnconnected", "BorderSelected"};

    /**
     * Above this, a fill is too light to carry the theme's light label text.
     * The stock fills sit at 206 (#B0D8EA) and 238 (#EEEEEE); IA's orange sits
     * at 162 and must stay clear of it.
     */
    private static final int FILL_IS_LIGHT = 180;

    /** Below this, a stroke disappears against the dark canvas. */
    private static final int STROKE_IS_DARK = 100;

    /** Brightness a darkened fill lands on — clear of the canvas, under the text. */
    private static final float DARK_FILL_BRIGHTNESS = 0.33f;

    /** A washed-out fill turns grey when darkened unless its saturation is lifted. */
    private static final float DARK_FILL_SATURATION_GAIN = 1.6f;

    /** Brightness a lightened stroke lands on. */
    private static final float LIGHT_STROKE_BRIGHTNESS = 0.58f;

    /** Block UI -> the colours it had before we touched it. */
    private final Map<JComponent, Map<String, Color>> originals = new IdentityHashMap<>();

    /** Darken the fills and lift the strokes on every block on screen. Re-runnable. */
    void install() {
        int themed = 0;
        for (JComponent block : findBlockUis()) {
            if (originals.containsKey(block)) {
                continue;
            }
            Map<String, Color> before = new LinkedHashMap<>();
            applyTo(block, before);
            if (!before.isEmpty()) {
                originals.put(block, before);
                block.repaint();
                themed++;
            }
        }
        if (themed > 0) {
            DebugLog.detail("BlockWorkspaceTheme: dark-adapted " + themed + " block(s).");
        }
    }

    /** Put every block back exactly as it was. */
    void uninstall() {
        originals.forEach((block, before) -> {
            try {
                before.forEach((property, original) -> set(block, property, original));
                block.repaint();
            } catch (Throwable t) {
                DebugLog.log("BlockWorkspaceTheme: could not restore a block's colours.", t);
            }
        });
        if (!originals.isEmpty()) {
            DebugLog.detail("BlockWorkspaceTheme: restored "
                + originals.size() + " block(s).");
        }
        originals.clear();
    }

    /** Swap what needs swapping, recording each original into {@code before}. */
    private void applyTo(JComponent block, Map<String, Color> before) {
        for (String property : FILLS) {
            Color current = get(block, property);
            if (current != null && shouldDarkenFill(current)) {
                before.put(property, current);
                set(block, property, darkenFill(current));
            }
        }
        for (String property : STROKES) {
            Color current = get(block, property);
            if (current != null && shouldLightenStroke(current)) {
                before.put(property, current);
                set(block, property, lightenStroke(current));
            }
        }
    }

    /** Perceived brightness, 0-255. */
    static int luminance(Color color) {
        return (int) Math.round(
            0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
    }

    static boolean shouldDarkenFill(Color fill) {
        return luminance(fill) > FILL_IS_LIGHT;
    }

    static boolean shouldLightenStroke(Color stroke) {
        return luminance(stroke) < STROKE_IS_DARK;
    }

    /** The same hue, dark enough for light text to sit on. */
    static Color darkenFill(Color fill) {
        float[] hsb = Color.RGBtoHSB(fill.getRed(), fill.getGreen(), fill.getBlue(), null);
        return withAlpha(fill, Color.getHSBColor(
            hsb[0], Math.min(1f, hsb[1] * DARK_FILL_SATURATION_GAIN), DARK_FILL_BRIGHTNESS));
    }

    /** The same hue, light enough to be seen against the dark canvas. */
    static Color lightenStroke(Color stroke) {
        float[] hsb = Color.RGBtoHSB(
            stroke.getRed(), stroke.getGreen(), stroke.getBlue(), null);
        return withAlpha(stroke, Color.getHSBColor(hsb[0], hsb[1], LIGHT_STROKE_BRIGHTNESS));
    }

    /** {@code Color.getHSBColor} always returns an opaque colour; keep the original's alpha. */
    private static Color withAlpha(Color original, Color recoloured) {
        return original.getAlpha() == 255 ? recoloured
            : new Color(recoloured.getRed(), recoloured.getGreen(),
                recoloured.getBlue(), original.getAlpha());
    }

    private static Color get(JComponent block, String property) {
        try {
            Object value = block.getClass().getMethod("get" + property).invoke(block);
            return value instanceof Color ? (Color) value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void set(JComponent block, String property, Color color) {
        try {
            block.getClass().getMethod("set" + property, Color.class).invoke(block, color);
        } catch (Throwable t) {
            DebugLog.log("BlockWorkspaceTheme: could not set " + property
                + " on " + block.getClass().getName(), t);
        }
    }

    private List<JComponent> findBlockUis() {
        List<JComponent> blocks = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            collect(window, blocks);
        }
        return blocks;
    }

    private void collect(Container container, List<JComponent> out) {
        for (Component child : container.getComponents()) {
            if (child instanceof JComponent && isBlockUi(child.getClass())) {
                out.add((JComponent) child);
            }
            if (child instanceof Container) {
                collect((Container) child, out);
            }
        }
    }

    /**
     * Matched by walking the superclass chain by name rather than through
     * {@code Class.forName}: the blockandconnector classes only load once a
     * pipeline or chart workspace is opened, and a Designer that never opens
     * one should not pay for a lookup that cannot succeed.
     */
    private static boolean isBlockUi(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (BASIC_BLOCK_UI.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }
}
