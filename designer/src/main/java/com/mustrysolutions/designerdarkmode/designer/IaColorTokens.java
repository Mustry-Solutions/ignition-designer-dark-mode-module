package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retro-themes Ignition's own design tokens (IgnitionLookAndFeel$Colors).
 *
 * IA's designer chrome — the Perspective component palette, property editor,
 * workspace surround, section headers — is colored by handing components the
 * static token Colors (setBackground(Colors.Base100) at construction) or by
 * painting with them directly, so neither UIManager overrides nor
 * updateComponentTreeUI ever reach those colors. The token fields are static
 * final, but the Designer JVM runs with --add-opens java.desktop/java.awt, so
 * the shared java.awt.Color INSTANCES can be restyled by mutating their
 * internal RGB value — every component and painter holding a token then
 * renders dark on its next repaint, no matter when it was built.
 *
 * Base000 is java.awt.Color.WHITE itself; mutating that instance would
 * corrupt white JVM-wide (including Vision/Perspective content rendering), so
 * it is never touched — ThemeManager swaps it per-component instead.
 */
final class IaColorTokens {

    private static final String COLORS_CLASS =
        "com.inductiveautomation.ignition.client.IgnitionLookAndFeel$Colors";

    /**
     * Dark replacement per token field; unlisted tokens (brand blues, status
     * colors, alpha-bearing colors) keep their stock value. Aliased fields
     * (Background=Base100, Border=Base500, ButtonForeground=Base900,
     * Inherited=DisabledForeground) share instances, so mutating the primary
     * covers them. The neutral ramp is inverted: light surfaces become dark
     * surfaces, near-black text becomes near-white text.
     */
    private static final Map<String, Integer> DARK = Map.ofEntries(
        Map.entry("Base100", 0x46494B),
        Map.entry("Base300", 0x3F4346),
        Map.entry("Base400", 0x4E5254),
        Map.entry("Base500", 0x5F6467),
        Map.entry("Base700", 0xB0B6BA),
        Map.entry("Base800", 0xC4C9CD),
        Map.entry("Base900", 0xDDE0E3),
        Map.entry("Active", 0x2D4964),
        Map.entry("DisabledForeground", 0x8A9094),
        Map.entry("DisabledBackground", 0x45484A),
        Map.entry("NonEditableBackground", 0x404345),
        Map.entry("LiveValue", 0x4FA3E3),
        Map.entry("IconDefault", 0xA0B2C0),
        Map.entry("IconNeutral", 0x8FA5B5),
        Map.entry("WorkspaceBackground", 0x2E3133));

    /**
     * Non-token color constants hardcoded in specific designer classes,
     * mutated the same way. NodeEditor drives the JSON property editor
     * (Perspective property editor rows): its gutter is the "bindings" column,
     * HOVER_BACKGROUND the row-hover tint, and paintFauxGutter paints with
     * these directly — unreachable by any component-level fix.
     */
    private static final Map<String, Map<String, Integer>> CLASS_DARK = Map.of(
        "com.inductiveautomation.ignition.client.jsonedit.NodeEditor", Map.of(
            "GUTTER_BACKGROUND", 0x3F4244,
            "GUTTER_BORDER", 0x55595B,
            "HOVER_BACKGROUND", 0x3B4754,
            "INDENT_LINE", 0x505355,
            "WARNING_COLOR", 0x4E4636),
        // Welcome-workspace "create resource" tile hover/selection (#DDE5EB).
        "com.inductiveautomation.ignition.designer.workspacewelcome.ResourceBuilderPanel", Map.of(
            "SELECTED", 0x2D4964));

    private final Logger log = LoggerFactory.getLogger(IaColorTokens.class);

    /** Token instance -> its stock ARGB, captured before mutation. */
    private final Map<Color, Integer> originals = new IdentityHashMap<>();

    private Field valueField;
    private Field frgbField;
    private Field fvalueField;

    /** Mutate the token instances to the dark palette. Safe to re-run. */
    void install() {
        if (!originals.isEmpty()) {
            return;
        }
        try {
            reflectColorInternals();
            Class<?> colors = Class.forName(COLORS_CLASS);
            int mutated = 0;
            for (Field field : colors.getFields()) {
                if (field.getType() != Color.class) {
                    continue;
                }
                Integer dark = DARK.get(field.getName());
                if (dark == null) {
                    continue;
                }
                Color token = (Color) field.get(null);
                // Never mutate a JDK-global constant that IA aliased.
                if (token == null || token == Color.WHITE || token == Color.BLACK
                        || token == Color.GRAY || token == Color.LIGHT_GRAY
                        || token == Color.DARK_GRAY) {
                    continue;
                }
                if (!originals.containsKey(token)) {
                    originals.put(token, token.getRGB());
                    setColorValue(token, 0xFF000000 | dark);
                    mutated++;
                }
            }
            mutated += installClassColors();
            DebugLog.log("IaColorTokens: restyled " + mutated + " design-token color(s).");
        } catch (Throwable t) {
            log.warn("Could not restyle the Ignition designer color tokens.", t);
            DebugLog.log("IaColorTokens install FAILED.", t);
        }
    }

    private int installClassColors() {
        int mutated = 0;
        for (Map.Entry<String, Map<String, Integer>> entry : CLASS_DARK.entrySet()) {
            try {
                Class<?> owner = Class.forName(entry.getKey());
                for (Map.Entry<String, Integer> colorEntry : entry.getValue().entrySet()) {
                    Field field = owner.getDeclaredField(colorEntry.getKey());
                    field.setAccessible(true);
                    Color color = (Color) field.get(null);
                    if (color == null || color == Color.WHITE || color == Color.BLACK
                            || originals.containsKey(color)) {
                        continue;
                    }
                    originals.put(color, color.getRGB());
                    setColorValue(color, 0xFF000000 | colorEntry.getValue());
                    mutated++;
                }
            } catch (Throwable t) {
                log.warn("Could not restyle colors in " + entry.getKey(), t);
                DebugLog.log("IaColorTokens class colors FAILED for " + entry.getKey(), t);
            }
        }
        return mutated;
    }

    /** Put every mutated token back to its stock value. */
    void uninstall() {
        originals.forEach((token, argb) -> {
            try {
                setColorValue(token, argb);
            } catch (Exception e) {
                log.warn("Could not restore a designer color token.", e);
            }
        });
        originals.clear();
    }

    private void reflectColorInternals() throws Exception {
        if (valueField != null) {
            return;
        }
        valueField = Color.class.getDeclaredField("value");
        valueField.setAccessible(true);
        // Lazily-computed caches that would go stale after a value change.
        frgbField = Color.class.getDeclaredField("frgbvalue");
        frgbField.setAccessible(true);
        fvalueField = Color.class.getDeclaredField("fvalue");
        fvalueField.setAccessible(true);
    }

    private void setColorValue(Color color, int argb) throws Exception {
        valueField.setInt(color, argb);
        frgbField.set(color, null);
        fvalueField.set(color, null);
    }
}
