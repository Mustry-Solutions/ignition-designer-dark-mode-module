package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.UIManager;

/**
 * Every resolvable {@code UIManager} default, rendered so that two snapshots
 * taken at different points in a theme switch can be compared.
 *
 * <p>This is the instrument the harness is built around. A theme switch touches
 * something on the order of 1,400 defaults, and the ones that go wrong are
 * mostly invisible: when #23 was finally diagnosed, 192 defaults came back
 * wrong after a light&rarr;dark&rarr;light cycle and exactly one of them showed
 * up on screen. Looking at a Designer can only ever find that one.
 *
 * <h2>Rendering, not identity</h2>
 *
 * <p>Values are compared as text, because identity comparison is useless here:
 * {@code installJideExtension()} rebuilds its {@code Border} and {@code Icon}
 * instances on every call, so a perfectly clean restore still produces ~200
 * "differences" if you compare by {@code equals} or by reference.
 *
 * <p>The rendering is therefore deliberately uneven. Colours — the payload, and
 * what every bug in this repo has actually been about — are rendered exactly,
 * ARGB and all. Fonts, insets and dimensions are rendered structurally. Borders,
 * icons and everything else collapse to their class name, which is as much as
 * can be said about them without false positives: a restore that puts back a
 * {@code MatteBorder} of a different colour would slip through, and the
 * component-level pass in {@code ThemeManager.swapWhiteTokenBackgrounds} is
 * what covers those.
 */
final class UiDefaultsSnapshot {

    private final Map<String, String> values;

    private UiDefaultsSnapshot(Map<String, String> values) {
        this.values = values;
    }

    /**
     * Snapshot the defaults as they stand right now.
     *
     * <p>Keys come from {@code UIManager.getDefaults()}, which spans both
     * tables — the look and feel's own and the developer defaults. Both matter:
     * the module writes exclusively into the developer table, while the stock
     * Designer keeps most standard Swing colours there too, and the whole of
     * #23 lives in the overlap.
     */
    static UiDefaultsSnapshot take() {
        Map<String, String> values = new TreeMap<>();
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (!(key instanceof String)) {
                continue;
            }
            Object value;
            try {
                value = UIManager.get(key);
            } catch (Throwable resolutionFailed) {
                // A lazy value that cannot be resolved in this environment —
                // "resolvable" is the scope of the comparison, not a silent
                // omission of something we should have checked. There are a
                // handful, and they are the same handful before and after.
                continue;
            }
            values.put((String) key, render(value));
        }
        return new UiDefaultsSnapshot(values);
    }

    /** How many defaults this snapshot covers. */
    int size() {
        return values.size();
    }

    /** The rendered value under {@code key}, or {@code null}. */
    String get(String key) {
        return values.get(key);
    }

    /**
     * One key whose rendered value changed between two snapshots. {@code from}
     * or {@code to} is {@code null} when the key was absent on that side.
     */
    record Difference(String key, String from, String to) {

        /**
         * True when this is Swing's own UI-delegate class cache rather than
         * anything the module wrote.
         *
         * <p>{@code UIDefaults.getUIClass} resolves a UI delegate by name and
         * then caches the loaded {@code Class} straight back into the table,
         * keyed by the class's own name. On {@code UIManager.getDefaults()}
         * that write lands in the DEVELOPER defaults, which survive a
         * look-and-feel swap — so using a look and feel at all leaves a
         * permanent {@code "x.y.ZUI" -> Class[x.y.ZUI]} entry behind, for
         * FlatLaf and Synthetica alike. A stock Designer that has never seen
         * this module already carries three of them.
         *
         * <p>It is a cache keyed by a class name, and it is only ever consulted
         * while that class is the one the active look and feel names — so a
         * FlatLaf entry is dead weight under the stock theme, not a wrong
         * value. Recognised here rather than filtered out of the snapshot, so
         * that a Class the module actually put somewhere would still show up.
         */
        boolean isUiDelegateClassCache() {
            return from == null && ("Class[" + key + "]").equals(to);
        }

        @Override
        public String toString() {
            return key + ": " + from + " -> " + to;
        }
    }

    /**
     * Every key whose value differs from {@code other}'s, sorted.
     *
     * <p>Read {@code before.diff(after)} as "what the cycle did".
     */
    List<Difference> diff(UiDefaultsSnapshot other) {
        List<Difference> differences = new ArrayList<>();
        List<String> keys = new ArrayList<>(values.keySet());
        for (String key : other.values.keySet()) {
            if (!values.containsKey(key)) {
                keys.add(key);
            }
        }
        java.util.Collections.sort(keys);
        for (String key : keys) {
            String mine = values.get(key);
            String theirs = other.values.get(key);
            if (!java.util.Objects.equals(mine, theirs)) {
                differences.add(new Difference(key, mine, theirs));
            }
        }
        return differences;
    }

    /** A diff as a failure message: the count, then the first few in full. */
    static String describe(List<Difference> differences) {
        int shown = Math.min(differences.size(), 40);
        StringBuilder message = new StringBuilder(differences.size() + " default(s) differ");
        if (differences.size() > shown) {
            message.append(" (first ").append(shown).append(" shown)");
        }
        message.append(':');
        for (Difference difference : differences.subList(0, shown)) {
            message.append("\n  ").append(difference);
        }
        return message.toString();
    }

    private static String render(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Color) {
            Color color = (Color) value;
            return String.format("#%08X", color.getRGB());
        }
        if (value instanceof Font) {
            Font font = (Font) value;
            return "Font[" + font.getFamily() + "," + font.getStyle() + "," + font.getSize() + "]";
        }
        if (value instanceof Insets) {
            Insets insets = (Insets) value;
            return "Insets[" + insets.top + "," + insets.left + ","
                + insets.bottom + "," + insets.right + "]";
        }
        if (value instanceof Dimension) {
            Dimension dimension = (Dimension) value;
            return "Dimension[" + dimension.width + "x" + dimension.height + "]";
        }
        if (value instanceof Class) {
            // Named, not collapsed to "java.lang.Class": the name is what tells
            // a UI-delegate cache entry (see Difference.isUiDelegateClassCache)
            // apart from a Class the module itself parked in the defaults.
            return "Class[" + ((Class<?>) value).getName() + "]";
        }
        if (value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }
        // Borders, icons, UI delegates, painters, input maps: JIDE and
        // Synthetica hand out fresh instances every time they are installed,
        // and most of these types inherit Object.toString(), whose identity
        // hash would make every snapshot differ from every other.
        return value.getClass().getName();
    }
}
