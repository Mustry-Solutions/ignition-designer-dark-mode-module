package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * styles</em> on the styled document: {@code regular} (no colour of its own,
 * so it follows {@code default}), {@code emphasize} ({@code Color.blue}) and
 * {@code error} ({@code Color.red}). Restyling those four objects recolours
 * <em>future</em> text only. {@code insertString(offset, text, style)} copies
 * the style's attributes into the run, so a run written in {@code emphasize}
 * keeps its own {@code Color.blue} however the style changes afterwards (#129
 * — the interpreter banner, written before Dark Mode is switched on). Runs
 * written in {@code regular} carry no foreground and do follow
 * {@code default}, so restyling covers them.
 *
 * <p>The runs that carry a copied colour are therefore rewritten by colour,
 * the same way as the Output Console's: blue and red to their dark values on
 * the way in, and back on the way out. The way out matters as much: text
 * printed while dark carries a copy of the dark colour, and without the
 * reverse mapping it would stay light blue on the light background.
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
    /**
     * Every console pane themed since the last uninstall, so the restore
     * reaches a console whose window has since closed as well as open ones.
     */
    private final Set<JTextPane> themed = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Recolour every console currently in the UI. Safe to re-run. */
    void install() {
        themePanes(findConsolePanes());
        themeOutputConsole(true);
    }

    /** Recolour the consoles under one container; the harness's way in. */
    void installIn(Container container) {
        List<JTextPane> panes = new ArrayList<>();
        collect(container, panes);
        themePanes(panes);
    }

    private void themePanes(List<JTextPane> panes) {
        int restyled = 0;
        int rewritten = 0;
        for (JTextPane pane : panes) {
            themed.add(pane);
            StyledDocument document = pane.getStyledDocument();
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
            rewritten += recolourRuns(document, consoleRunColours(true));
        }
        if (restyled > 0 || rewritten > 0) {
            DebugLog.detail("ConsoleTextTheme: restyled " + restyled + " console style(s), "
                + rewritten + " existing run(s) recoloured.");
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
            DebugLog.detail("ConsoleTextTheme: restored " + originals.size() + " console style(s).");
        }
        originals.clear();
        wasUndefined.clear();
        int rewritten = 0;
        for (JTextPane pane : themed) {
            try {
                followPaneForeground(pane);
                rewritten += recolourRuns(pane.getStyledDocument(), consoleRunColours(false));
            } catch (Throwable t) {
                DebugLog.log("ConsoleTextTheme: could not restore a console's text.", t);
            }
        }
        themed.clear();
        if (rewritten > 0) {
            DebugLog.detail("ConsoleTextTheme: " + rewritten + " console run(s) given back "
                + "their stock colour.");
        }
        themeOutputConsole(false);
    }

    /**
     * Give {@code default} the pane's own foreground back, not the colour
     * recorded at install.
     *
     * <p>{@code default} is not an ordinary style: {@code BasicTextPaneUI}
     * copies the pane's foreground into it whenever that foreground changes,
     * which the look-and-feel swap does. {@code install} runs after the dark
     * look and feel is in, so what it records for {@code default} is FlatLaf's
     * {@code #DDDDDD}; {@code uninstall} runs after the light one is back, so
     * writing that record back put near-white text on the white console
     * (every prompt, typed line and {@code print}). The pane's foreground at
     * this point is the light one, and copying it is what Swing would have
     * done had we never touched the style.
     */
    private static void followPaneForeground(JTextPane pane) {
        Style style = pane.getStyledDocument().getStyle("default");
        if (style != null && pane.getForeground() != null) {
            StyleConstants.setForeground(style, pane.getForeground());
        }
    }

    // Package-private so ReflectiveSurfaceTest can assert this name still
    // resolves against the Ignition the harness runs.
    static final String OUTPUT_CONSOLE =
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
            int rewritten = recolourRuns(pane.getStyledDocument(), outputConsoleColours(dark));
            int appenders = repointAppenders(consoleClass, dark);
            DebugLog.detail("ConsoleTextTheme: Output Console — " + rewritten
                + " run(s) recoloured, " + appenders + " appender(s) repointed.");
        } catch (ClassNotFoundException absent) {
            DebugLog.detail("ConsoleTextTheme: no OutputConsole on this version.");
        } catch (Throwable t) {
            DebugLog.log("ConsoleTextTheme: the Output Console could not be recoloured.", t);
        }
    }

    /** Output Console runs: {@code OutputConsole}'s two appender colours. */
    private static Map<Color, Color> outputConsoleColours(boolean dark) {
        return mapping(dark, Color.black, DARK.get("regular"), Color.red, DARK.get("error"));
    }

    /**
     * Script Console and diagnostics console runs: the colours ConsolePanel's
     * {@code emphasize} and {@code error} styles copy into each run. Runs in
     * {@code regular} have no foreground of their own and are deliberately not
     * in the map; they follow the restyled {@code default}, and stamping a
     * colour on them would cut them loose from it.
     */
    private static Map<Color, Color> consoleRunColours(boolean dark) {
        return mapping(dark, Color.blue, DARK.get("emphasize"), Color.red, DARK.get("error"));
    }

    private static Map<Color, Color> mapping(
            boolean dark, Color stockA, Color darkA, Color stockB, Color darkB) {
        return dark ? Map.of(stockA, darkA, stockB, darkB) : Map.of(darkA, stockA, darkB, stockB);
    }

    /**
     * Swap every run whose own foreground is a key of {@code colours} for its
     * value. Only an explicitly defined foreground counts: a run that inherits
     * its colour is left to its style.
     */
    private int recolourRuns(StyledDocument document, Map<Color, Color> colours) {
        if (document == null) {
            return 0;
        }
        int rewritten = 0;
        int position = 0;
        while (position < document.getLength()) {
            javax.swing.text.Element run = document.getCharacterElement(position);
            int end = Math.max(run.getEndOffset(), position + 1);
            javax.swing.text.AttributeSet own = run.getAttributes();
            Color replacement = own.isDefined(StyleConstants.Foreground)
                ? colours.get(StyleConstants.getForeground(own))
                : null;
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
     * Text panes whose document defines Ignition's console styles. Looking for the
     * style names rather than the panel class keeps this working if IA moves
     * or renames the panel, and keeps it away from unrelated text panes.
     */
    private List<JTextPane> findConsolePanes() {
        List<JTextPane> panes = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            collect(window, panes);
        }
        return panes;
    }

    private void collect(Container container, List<JTextPane> out) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTextPane) {
                StyledDocument document = ((JTextPane) child).getStyledDocument();
                if (document != null && document.getStyle("emphasize") != null) {
                    out.add((JTextPane) child);
                }
            }
            if (child instanceof Container) {
                collect((Container) child, out);
            }
        }
    }
}
