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
 * Recolours the Designer's console output.
 *
 * <p><strong>There are two consoles, coloured two different ways.</strong> The
 * Script Console and the diagnostics console use named document styles, handled
 * below. The Designer's <em>Output Console</em> dock does not: it stamps a
 * foreground onto every inserted run (#52), which needs a different treatment
 * entirely — see {@link #themeOutputConsole}.
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
            DebugLog.detail("ConsoleTextTheme: restyled " + restyled + " console style(s).");
        }
        themeOutputConsole(true);
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
            DebugLog.detail("ConsoleTextTheme: restored " + originals.size() + " console style(s).");
        }
        originals.clear();
        wasUndefined.clear();
        themeOutputConsole(false);
    }

    private static final String OUTPUT_CONSOLE =
        "com.inductiveautomation.ignition.client.util.gui.OutputConsole";

    /**
     * The Designer's Output Console dock, which colours its text per RUN rather
     * than through named styles (#52).
     *
     * <p>{@code OutputConsole} adds two {@code ConsoleAppender}s to the
     * bifurcated {@code System.out} and {@code System.err}, holding
     * {@code Color.black} and {@code Color.red}, and every appended line is
     * inserted with {@code StyleConstants.setForeground(attrs, thatColour)}.
     * Since the Designer routes its logging through stdout, that is *all* of
     * the console: near-black text on #3C3F41 chrome.
     *
     * <p>Neither colour may be mutated — they are the JDK's shared globals, and
     * rewriting them would change black and red for the whole JVM (the same
     * rule that keeps {@code Base000} off-limits in {@link IaColorTokens}). So
     * this does two things instead:
     *
     * <ul>
     *   <li>rewrites the foreground of the text already in the document, run by
     *       run;</li>
     *   <li>points each appender's own colour field at the dark-mode value, so
     *       lines appended later arrive correct.</li>
     * </ul>
     *
     * <p>It is exactly reversible without remembering offsets, which matters
     * because the document is trimmed from the front as it grows and any
     * offsets we stored would rot. The mapping is by COLOUR: on the way back,
     * a run wearing our normal colour becomes {@code Color.black} again and one
     * wearing our error colour becomes {@code Color.red}. A run coloured by
     * anything else was never ours and is left alone.
     */
    private void themeOutputConsole(boolean dark) {
        try {
            Class<?> consoleClass = Class.forName(OUTPUT_CONSOLE);
            Object console = consoleClass.getMethod("getInstance").invoke(null);
            if (console == null) {
                return;
            }
            java.lang.reflect.Field paneField = consoleClass.getDeclaredField("pane");
            paneField.setAccessible(true);
            JTextPane pane = (JTextPane) paneField.get(console);
            if (pane == null) {
                return;
            }
            int rewritten = recolourRuns(pane.getStyledDocument(), dark);
            int appenders = repointAppenders(consoleClass, dark);
            DebugLog.detail("ConsoleTextTheme: Output Console — " + rewritten
                + " run(s) recoloured, " + appenders + " appender(s) repointed.");
        } catch (ClassNotFoundException absent) {
            DebugLog.detail("ConsoleTextTheme: no OutputConsole on this version.");
        } catch (Throwable t) {
            DebugLog.log("ConsoleTextTheme: the Output Console could not be recoloured.", t);
        }
    }

    /** Swap every run wearing one of the two known colours for its counterpart. */
    private int recolourRuns(StyledDocument document, boolean dark) {
        if (document == null) {
            return 0;
        }
        Color fromNormal = dark ? Color.black : DARK.get("regular");
        Color toNormal = dark ? DARK.get("regular") : Color.black;
        Color fromError = dark ? Color.red : DARK.get("error");
        Color toError = dark ? DARK.get("error") : Color.red;
        int rewritten = 0;
        int position = 0;
        while (position < document.getLength()) {
            javax.swing.text.Element run = document.getCharacterElement(position);
            int end = Math.max(run.getEndOffset(), position + 1);
            Color foreground = StyleConstants.getForeground(run.getAttributes());
            Color replacement = null;
            if (fromNormal.equals(foreground)) {
                replacement = toNormal;
            } else if (fromError.equals(foreground)) {
                replacement = toError;
            }
            if (replacement != null) {
                javax.swing.text.SimpleAttributeSet attributes =
                    new javax.swing.text.SimpleAttributeSet();
                StyleConstants.setForeground(attributes, replacement);
                document.setCharacterAttributes(
                    run.getStartOffset(), end - run.getStartOffset(), attributes, false);
                rewritten++;
            }
            position = end;
        }
        return rewritten;
    }

    /** Point the two appenders at the colours later lines should arrive in. */
    private int repointAppenders(Class<?> consoleClass, boolean dark) throws Exception {
        int repointed = 0;
        for (String streamName : new String[] {"_out", "_err"}) {
            java.lang.reflect.Field streamField = consoleClass.getDeclaredField(streamName);
            streamField.setAccessible(true);
            Object stream = streamField.get(null);
            if (stream == null) {
                continue;
            }
            java.lang.reflect.Field subsField =
                stream.getClass().getDeclaredField("subs");
            subsField.setAccessible(true);
            Object subs = subsField.get(stream);
            if (!(subs instanceof Iterable)) {
                continue;
            }
            for (Object sub : (Iterable<?>) subs) {
                java.lang.reflect.Field colourField;
                try {
                    colourField = sub.getClass().getDeclaredField("bg");
                } catch (NoSuchFieldException notAnAppender) {
                    continue;
                }
                colourField.setAccessible(true);
                Color current = (Color) colourField.get(sub);
                Color replacement = replacementFor(current, dark);
                if (replacement != null) {
                    colourField.set(sub, replacement);
                    repointed++;
                }
            }
        }
        return repointed;
    }

    private Color replacementFor(Color current, boolean dark) {
        if (dark) {
            if (Color.black.equals(current)) {
                return DARK.get("regular");
            }
            return Color.red.equals(current) ? DARK.get("error") : null;
        }
        if (DARK.get("regular").equals(current)) {
            return Color.black;
        }
        return DARK.get("error").equals(current) ? Color.red : null;
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
