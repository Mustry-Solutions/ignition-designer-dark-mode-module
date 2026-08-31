package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Dark theme for JIDE's {@code CodeEditor}, the counterpart to
 * {@link ScriptEditorTheme} (#48).
 *
 * <h2>Why a second editor theme</h2>
 *
 * <p>"The script editor" is two different components. The Python editors — the
 * Script Console, the Project Library, event scripts — are
 * {@code RSyntaxTextArea}, and {@link ScriptEditorTheme} themes those through
 * IA's own {@code NamedTheme}. Everything else that edits code is JIDE's
 * {@code com.jidesoft.editor.CodeEditor}: the Database Query Browser, and every
 * expression editor in the Designer — Vision and Perspective bindings, tag
 * expressions, alarm expressions, named queries. A census of the 8.3.6 jars
 * counts 46 classes referring to it.
 *
 * <p>Its colours live on the component and in its {@code SyntaxStyleSchema},
 * not in {@code UIManager}, so the look-and-feel swap does not touch them and
 * the hierarchy walk only ever reached the parts that are real components (the
 * margin and the line-number gutter, which do come out dark). Everything the
 * editor paints itself stayed light:
 *
 * <pre>
 *   lineHighlight    #FFFFD7   the cream band across the current line
 *   caret            #000000   black, on a dark background — invisible
 *   selection        #A0B3F0
 *   bracketHighlight #98CCFF
 *   14 syntax styles mostly near-black: #000000, #000080, #650099, #990033
 * </pre>
 *
 * <h2>How it themes</h2>
 *
 * <p>Foregrounds are <em>lifted</em> rather than replaced: each syntax colour
 * keeps its hue and is raised to a readable brightness, so IA's own token
 * assignment survives — a string stays greenish, a keyword stays blue. A fixed
 * dark palette would throw away the one thing the schema is for.
 *
 * <p>Every value is recorded before it is changed and put back on
 * {@link #uninstall()}, by identity: a {@code SyntaxStyleSchema} may be shared
 * between editors, so the same {@code SyntaxStyle} can be reached twice and
 * must only be recorded once.
 *
 * <p>Reflection throughout. JIDE is not SDK surface and the module does not
 * compile against it, the same reasoning as {@code LookAndFeelFactory} in
 * {@link ThemeManager}: a JIDE that has moved must cost this pass, not the
 * Designer.
 */
final class CodeEditorTheme {

    // Package-private so ReflectiveSurfaceTest can assert this name still
    // resolves against the Ignition the harness runs.
    static final String CODE_EDITOR = "com.jidesoft.editor.CodeEditor";

    /** Just above the editor's own background, so the current line reads. */
    private static final Color LINE_HIGHLIGHT = new Color(0x45494B);
    private static final Color CARET = new Color(0xDDE0E3);
    /** The Active token's dark value, so a selection matches the rest of the UI. */
    private static final Color SELECTION = new Color(0x2D4964);
    private static final Color BRACKET_HIGHLIGHT = new Color(0x4A6E8F);

    /** A foreground dimmer than this cannot be read on dark chrome. */
    private static final int TOO_DARK = 140;
    /**
     * The luminance a lifted foreground is raised to.
     *
     * <p>Luminance, not HSB brightness. Blue contributes 11% of perceived
     * luminance against green's 59%, so a pure blue at full brightness —
     * #0000FF — still measures 29 and is unreadable on #3A3D3F chrome. Raising
     * "brightness" is the intuitive move and the wrong one; the first cut of
     * this pass did exactly that and left five of the fourteen syntax styles
     * unreadable.
     */
    private static final int TARGET_LUMINANCE = 175;
    /** A style background lighter than this is a light-theme highlight. */
    private static final int TOO_LIGHT = 200;

    /** editor -> {lineHighlight, caret, selection, bracketHighlight}, as found. */
    private final Map<Object, Color[]> editorColors = new IdentityHashMap<>();
    /** syntax style -> {foreground, background}, as found. */
    private final Map<Object, Color[]> styleColors = new IdentityHashMap<>();

    private boolean unavailable;

    /** Theme every code editor currently in the UI. Safe to re-run. */
    void install() {
        for (Window window : Window.getWindows()) {
            installIn(window);
        }
    }

    /** Package-private so tests can drive the walk without a real Window. */
    void installIn(Container container) {
        if (unavailable) {
            return;
        }
        for (Component child : container.getComponents()) {
            if (isCodeEditor(child)) {
                theme(child);
            }
            if (child instanceof Container) {
                installIn((Container) child);
            }
        }
    }

    /** Put every recorded colour back. */
    void uninstall() {
        editorColors.forEach((editor, colors) -> {
            try {
                set(editor, "setLineHighlightColor", colors[0]);
                set(editor, "setCaretColor", colors[1]);
                set(editor, "setSelectionColor", colors[2]);
                set(editor, "setBracketHighlightColor", colors[3]);
                ((Component) editor).repaint();
            } catch (Throwable t) {
                DebugLog.log("Could not restore a CodeEditor's colours.", t);
            }
        });
        editorColors.clear();
        styleColors.forEach((style, colors) -> {
            try {
                set(style, "setForeground", colors[0]);
                set(style, "setBackground", colors[1]);
            } catch (Throwable t) {
                DebugLog.log("Could not restore a syntax style.", t);
            }
        });
        styleColors.clear();
    }

    private static boolean isCodeEditor(Component component) {
        for (Class<?> type = component.getClass(); type != null; type = type.getSuperclass()) {
            if (CODE_EDITOR.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private void theme(Object editor) {
        try {
            if (!editorColors.containsKey(editor)) {
                editorColors.put(editor, new Color[] {
                    get(editor, "getLineHighlightColor"),
                    get(editor, "getCaretColor"),
                    get(editor, "getSelectionColor"),
                    get(editor, "getBracketHighlightColor"),
                });
            }
            set(editor, "setLineHighlightColor", LINE_HIGHLIGHT);
            set(editor, "setCaretColor", CARET);
            set(editor, "setSelectionColor", SELECTION);
            set(editor, "setBracketHighlightColor", BRACKET_HIGHLIGHT);
            themeStyles(editor.getClass().getMethod("getStyles").invoke(editor));
            ((Component) editor).repaint();
        } catch (Throwable t) {
            // One editor that will not theme is a light editor, not a broken
            // Designer — but a JIDE that has moved would fail on every one of
            // them, so stop after the first rather than log 46 times.
            unavailable = true;
            DebugLog.log("CodeEditor theming unavailable; SQL and expression "
                + "editors will stay light.", t);
        }
    }

    private void themeStyles(Object schema) throws Exception {
        if (schema == null) {
            return;
        }
        int count = (Integer) schema.getClass().getMethod("getStyleCount").invoke(schema);
        Method byIndex = schema.getClass().getMethod("getStyleByIndex", int.class);
        for (int i = 0; i < count; i++) {
            Object style = byIndex.invoke(schema, i);
            if (style == null || styleColors.containsKey(style)) {
                continue;
            }
            Color foreground = get(style, "getForeground");
            Color background = get(style, "getBackground");
            styleColors.put(style, new Color[] {foreground, background});
            if (foreground != null && ThemeManager.luminance(foreground) < TOO_DARK) {
                set(style, "setForeground", lift(foreground));
            }
            if (background != null && ThemeManager.luminance(background) > TOO_LIGHT) {
                set(style, "setBackground", darken(background));
            }
        }
    }

    /**
     * The same colour, bright enough to read on dark chrome.
     *
     * <p>Two steps, and both keep the hue. First the colour is taken to full
     * HSB brightness, so a navy keyword becomes a real blue rather than a
     * washed grey. Then it is mixed toward white until it clears
     * {@link #TARGET_LUMINANCE} — mixing with white preserves hue exactly, and
     * because luminance is a weighted sum it is linear in the mix, so the right
     * amount is arithmetic rather than a search.
     *
     * <p>A colour with no saturation skips the first step: a comment grey
     * should come back a lighter grey, not white.
     */
    static Color lift(Color colour) {
        float[] hsb = Color.RGBtoHSB(
            colour.getRed(), colour.getGreen(), colour.getBlue(), null);
        Color vivid = hsb[1] == 0f
            ? colour : Color.getHSBColor(hsb[0], hsb[1], 1f);
        int luminance = ThemeManager.luminance(vivid);
        if (luminance >= TARGET_LUMINANCE) {
            return vivid;
        }
        float mix = (TARGET_LUMINANCE - luminance) / (255f - luminance);
        return new Color(
            toward255(vivid.getRed(), mix),
            toward255(vivid.getGreen(), mix),
            toward255(vivid.getBlue(), mix));
    }

    private static int toward255(int channel, float mix) {
        return Math.min(255, Math.round(channel + (255 - channel) * mix));
    }

    /** A light style background (a highlight band) at dark-chrome brightness. */
    static Color darken(Color colour) {
        float[] hsb = Color.RGBtoHSB(
            colour.getRed(), colour.getGreen(), colour.getBlue(), null);
        return Color.getHSBColor(hsb[0], Math.min(hsb[1], 0.40f), 0.32f);
    }

    private static Color get(Object target, String getter) throws Exception {
        return (Color) target.getClass().getMethod(getter).invoke(target);
    }

    private static void set(Object target, String setter, Color value) throws Exception {
        target.getClass().getMethod(setter, Color.class).invoke(target, value);
    }
}
