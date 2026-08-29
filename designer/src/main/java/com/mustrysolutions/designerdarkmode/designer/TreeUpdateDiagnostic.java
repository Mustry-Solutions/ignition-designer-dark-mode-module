package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeSet;

import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * Says what went wrong when {@code updateComponentTreeUI} fails on a window.
 *
 * <p>The failure itself is already survivable and already logged with its stack
 * (#11). What the stack cannot say is <em>which component</em>, and — the part
 * #12 actually worries about — <em>how much of the tree never got refreshed</em>
 * as a result. A delegate name in a stack trace does not tell you whether the
 * subtree that got skipped was an empty panel or the whole Perspective editor.
 *
 * <p>So this runs only on the failure path, walks the window that failed, and
 * reports the three things that distinguish the candidate explanations:
 *
 * <ol>
 *   <li><b>components with no font at all.</b> The NPE is
 *       {@code Font.getFamily()} on a null, and Swing's
 *       {@code installColorsAndFont} <em>unsets</em> a component's font when the
 *       key it reads resolves to nothing. FlatLaf defines ~40 font keys that
 *       stock Synthetica does not ({@code RootPane.font}, {@code TitlePane.font},
 *       {@code MenuBar.font}, …), and {@code getFont()} returns null only when
 *       the whole ancestor chain is unset — hence the chain is printed;</li>
 *   <li><b>font keys that resolve to null right now</b>, which is the same
 *       question asked of the shared state rather than of components. Headlessly
 *       none of them ever do; if one does here, that is the answer;</li>
 *   <li><b>components left on a delegate from the wrong look and feel</b>, which
 *       is the actual damage — the part of the tree the aborted update never
 *       reached.</li>
 * </ol>
 *
 * <p>Never throws. It runs inside a {@code catch} block during a switch that has
 * already gone wrong, and a diagnostic that fails there would replace a useful
 * report with a confusing second failure.
 */
final class TreeUpdateDiagnostic {

    /** Enough to identify a pattern; a Designer window holds thousands. */
    private static final int MAX_REPORTED = 12;

    /** Ancestor chain depth. Deep enough to place a component, short enough to read. */
    private static final int MAX_CHAIN = 6;

    private TreeUpdateDiagnostic() {
    }

    /** Walk the failed window and write the findings to the debug log. */
    static void report(Container window, Throwable failure) {
        try {
            DebugLog.log(describe(window, failure));
        } catch (Throwable diagnosticFailed) {
            // Deliberately swallowed, and the only place in this module that is
            // the right call: we are already inside the handler for a failed
            // theme switch. Losing the diagnostic is survivable; replacing the
            // real failure with this one is not.
            DebugLog.log("Tree-update diagnostic failed; the original failure above stands.",
                diagnosticFailed);
        }
    }

    /** The report text. Separate from {@link #report} so it can be tested. */
    static String describe(Container window, Throwable failure) {
        StringBuilder report = new StringBuilder("Tree-update diagnostic for ")
            .append(window.getClass().getName())
            .append(" after ").append(failure == null ? "(no throwable)" : failure);

        Findings found = new Findings();
        boolean darkActive = UIManager.getLookAndFeel() instanceof com.formdev.flatlaf.FlatDarkLaf;
        collect(window, "", found, darkActive);

        report.append("\n  ").append(found.walked).append(" component(s) walked.");

        report.append("\n  no font at all (getFont() == null): ").append(found.nullFontCount);
        appendAll(report, found.nullFonts, found.nullFontCount);

        List<String> nullFontKeys = nullFontKeys();
        report.append("\n  UIManager font keys resolving to null: ").append(nullFontKeys.size());
        appendAll(report, nullFontKeys, nullFontKeys.size());

        report.append("\n  left on a ").append(darkActive ? "Synthetica" : "FlatLaf")
            .append(" delegate under the ").append(darkActive ? "dark" : "stock")
            .append(" theme (the subtree the update never reached): ")
            .append(found.staleCount);
        appendAll(report, found.staleDelegates, found.staleCount);

        return report.toString();
    }

    /**
     * Counts are of EVERYTHING found; only the first {@link #MAX_REPORTED} are
     * described. Keeping those two numbers apart is the point of this class:
     * "the update skipped 12 components" and "the update skipped 1,400 of
     * which here are 12" are different bug reports, and capping the count as
     * well as the detail would silently turn the second into the first.
     */
    private static void appendAll(StringBuilder report, List<String> sample, int total) {
        for (String entry : sample) {
            report.append("\n    ").append(entry);
        }
        if (total > sample.size()) {
            report.append("\n    ... and ").append(total - sample.size()).append(" more");
        }
    }

    /** Full counts, with a bounded sample of each. */
    private static final class Findings {
        int walked;
        int nullFontCount;
        int staleCount;
        final List<String> nullFonts = new ArrayList<>();
        final List<String> staleDelegates = new ArrayList<>();
    }

    private static void collect(Component component, String chain, Findings found,
            boolean darkActive) {
        found.walked++;
        String here = chain.isEmpty()
            ? component.getClass().getSimpleName()
            : chain + " > " + component.getClass().getSimpleName();

        Font font;
        try {
            font = component.getFont();
        } catch (Throwable unreadable) {
            font = null;
        }
        if (font == null) {
            found.nullFontCount++;
            if (found.nullFonts.size() < MAX_REPORTED) {
                found.nullFonts.add(component.getClass().getName()
                    + "  ownFontSet=" + component.isFontSet()
                    + "  ui=" + uiOf(component)
                    + "\n      under: " + trim(here));
            }
        }

        String ui = uiOf(component);
        boolean stale = darkActive
            ? (ui.contains("Synthetica") || ui.contains("synth"))
            : ui.contains("flatlaf");
        if (stale) {
            found.staleCount++;
            if (found.staleDelegates.size() < MAX_REPORTED) {
                found.staleDelegates.add(component.getClass().getName() + "  ui=" + ui
                    + "\n      under: " + trim(here));
            }
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, here, found, darkActive);
            }
        }
    }

    /** Keep the deepest {@link #MAX_CHAIN} levels — the near ancestors identify it. */
    private static String trim(String chain) {
        String[] parts = chain.split(" > ");
        if (parts.length <= MAX_CHAIN) {
            return chain;
        }
        StringBuilder trimmed = new StringBuilder("...");
        for (int i = parts.length - MAX_CHAIN; i < parts.length; i++) {
            trimmed.append(" > ").append(parts[i]);
        }
        return trimmed.toString();
    }

    private static String uiOf(Component component) {
        if (!(component instanceof JComponent)) {
            return "-";
        }
        try {
            Object ui = component.getClass().getMethod("getUI").invoke(component);
            return ui == null ? "null" : ui.getClass().getName();
        } catch (Throwable noSuchAccessor) {
            return "-";
        }
    }

    /**
     * Font keys that resolve to nothing right now.
     *
     * <p>Matched on the key name rather than on the value's type, because a key
     * that resolves to null has no type to inspect — which is precisely the
     * case being hunted.
     */
    private static List<String> nullFontKeys() {
        List<String> nulls = new ArrayList<>();
        try {
            java.util.Set<String> seen = new TreeSet<>();
            Enumeration<Object> keys = UIManager.getDefaults().keys();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                if (!(key instanceof String)) {
                    continue;
                }
                String name = (String) key;
                if (!name.toLowerCase(java.util.Locale.ROOT).contains("font") || !seen.add(name)) {
                    continue;
                }
                try {
                    if (UIManager.get(name) == null) {
                        nulls.add(name);
                    }
                } catch (Throwable unresolvable) {
                    nulls.add(name + " (threw on resolve)");
                }
            }
        } catch (Throwable enumerationFailed) {
            nulls.add("(could not enumerate: " + enumerationFailed + ")");
        }
        return nulls;
    }
}
