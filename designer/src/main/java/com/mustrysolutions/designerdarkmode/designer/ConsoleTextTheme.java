package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JTextPane;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Recolours the Designer's console output — the Script Console's interactive
 * interpreter and the diagnostics console.
 *
 * <p>Console text is coloured per character through the document's styles, not
 * through the component, so the look-and-feel swap leaves it exactly as it was:
 * near-black normal output and {@code Color.blue} banners on a now-dark
 * background.
 *
 * <p>Ignition's {@code ConsolePanel} registers its colours as <em>named
 * styles</em> on the styled document — {@code regular}, {@code emphasize} and
 * {@code error} — rather than stamping attributes onto each run. That is the
 * useful detail: restyling the four style objects recolours all existing and
 * future text at once, and is exactly reversible. Rewriting character
 * attributes across the document would be neither.
 *
 * <p>Styles are looked up by name, so only documents that actually define them
 * (the consoles) are touched; a user's text pane elsewhere is left alone.
 */
final class ConsoleTextTheme {

    /** {@code default} is Swing's own; the rest are Ignition's ConsolePanel styles. */
    private static final String[] STYLE_NAMES = {"default", "regular", "emphasize", "error"};

    private static final Map<String, Color> DARK = Map.of(
        "default", new Color(0xC8CDD1),
        "regular", new Color(0xC8CDD1),
        // Ignition uses Color.blue for the banner and Color.red for errors —
        // both unreadable on a dark background. Keep the hue, lift the value.
        "emphasize", new Color(0x6FB3E8),
        "error", new Color(0xFF7B72));

    /** Style -> its stock foreground, or null when the style did not define one. */
    private final Map<Style, Color> originals = new IdentityHashMap<>();
    /** Styles that had no explicit foreground, so the restore can remove it again. */
    private final Map<Style, Boolean> wasUndefined = new IdentityHashMap<>();

    /** Recolour every console style currently in the UI. Safe to re-run. */
    void install() {
        int restyled = 0;
        for (StyledDocument document : findConsoleDocuments()) {
            for (String name : STYLE_NAMES) {
                Style style = document.getStyle(name);
                if (style == null || originals.containsKey(style)) {
                    continue;
                }
                Color dark = DARK.get(name);
                if (dark == null) {
                    continue;
                }
                boolean defined = style.isDefined(StyleConstants.Foreground);
                wasUndefined.put(style, !defined);
                originals.put(style, defined ? StyleConstants.getForeground(style) : null);
                StyleConstants.setForeground(style, dark);
                restyled++;
            }
        }
        if (restyled > 0) {
            DebugLog.log("ConsoleTextTheme: restyled " + restyled + " console style(s).");
        }
    }

    /** Put every console style back exactly as it was. */
    void uninstall() {
        originals.forEach((style, original) -> {
            try {
                if (Boolean.TRUE.equals(wasUndefined.get(style))) {
                    // It inherited its colour before we intervened; setting an
                    // explicit value back would not be a restore.
                    style.removeAttribute(StyleConstants.Foreground);
                } else if (original != null) {
                    StyleConstants.setForeground(style, original);
                }
            } catch (Throwable t) {
                DebugLog.log("ConsoleTextTheme: could not restore a console style.", t);
            }
        });
        if (!originals.isEmpty()) {
            DebugLog.log("ConsoleTextTheme: restored " + originals.size() + " console style(s).");
        }
        originals.clear();
        wasUndefined.clear();
    }

    /**
     * Styled documents that define Ignition's console styles. Looking for the
     * style names rather than the panel class keeps this working if IA moves
     * or renames the panel, and keeps it away from unrelated text panes.
     */
    private List<StyledDocument> findConsoleDocuments() {
        List<StyledDocument> documents = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            collect(window, documents);
        }
        return documents;
    }

    private void collect(Container container, List<StyledDocument> out) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTextPane) {
                StyledDocument document = ((JTextPane) child).getStyledDocument();
                if (document != null && document.getStyle("emphasize") != null) {
                    out.add(document);
                }
            }
            if (child instanceof Container) {
                collect((Container) child, out);
            }
        }
    }
}
