package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jidesoft.editor.CodeEditor;
import com.jidesoft.editor.SyntaxStyle;
import com.jidesoft.editor.SyntaxStyleSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * JIDE's {@code CodeEditor} under dark mode (#48) — the Database Query Browser
 * and every expression editor in the Designer.
 *
 * <p>Nothing about this editor's colours reaches {@code UIManager}, so a full
 * defaults diff comes back clean while the editor paints a cream band across
 * the current line, a black caret on dark chrome, and near-black syntax tokens.
 * The stock values, measured here rather than assumed:
 *
 * <pre>
 *   lineHighlight #FFFFD7   caret #000000   selection #A0B3F0
 *   styles: #000000, #000080, #650099, #990033, #FF0000, ...
 * </pre>
 *
 * <p>The test is written against those *properties* rather than pixels because
 * this is state an assertion can read directly — the rendering-based shape is
 * for bugs where the wrong colour never passes through any readable state
 * ({@code TagBrowserHeaderBandTest}).
 */
class CodeEditorThemeTest {

    /** Below this, a foreground cannot be read on #3A3D3F chrome. */
    private static final int UNREADABLE = 120;

    private ThemeManager manager;
    private CodeEditorTheme editors;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        editors = new CodeEditorTheme();
    }

    @AfterEach
    void leaveTheJvmLight() {
        editors.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("the editor's own colours go dark and come back exactly (#48)")
    void theEditorColoursAreThemedAndRestored() {
        JPanel panel = panelWith(new CodeEditor());
        CodeEditor editor = (CodeEditor) panel.getComponent(0);
        Map<String, String> stock = editorColours(editor);

        assertTrue(ThemeManager.luminance(editor.getLineHighlightColor()) > 200,
            "the stock line highlight is not light (" + stock.get("lineHighlight")
                + "), so this test would be asserting nothing");

        goDark(panel);

        assertTrue(ThemeManager.luminance(editor.getLineHighlightColor()) < 100,
            "the current-line band stayed light: " + hex(editor.getLineHighlightColor()));
        assertTrue(ThemeManager.luminance(editor.getCaretColor()) > 150,
            "the caret stayed dark on a dark background — invisible: "
                + hex(editor.getCaretColor()));
        assertTrue(ThemeManager.luminance(editor.getSelectionColor()) < 120,
            "the selection stayed light: " + hex(editor.getSelectionColor()));

        editors.uninstall();

        assertEquals(stock, editorColours(editor),
            "the editor's colours did not come back to their stock values");
    }

    @Test
    @DisplayName("no syntax token is left unreadable on dark chrome (#48)")
    void theSyntaxStylesAreLifted() {
        JPanel panel = panelWith(new CodeEditor());
        CodeEditor editor = (CodeEditor) panel.getComponent(0);
        Map<String, String> stock = styleColours(editor);

        assertTrue(unreadable(editor).size() >= 4,
            "only " + unreadable(editor).size() + " stock styles are too dark to read; "
                + "this test exists because a stock schema is full of them");

        goDark(panel);

        assertEquals(List.of(), unreadable(editor),
            "these syntax colours are still too dark to read on dark chrome");

        editors.uninstall();

        assertEquals(stock, styleColours(editor),
            "the syntax styles did not come back to their stock values");
    }

    @Test
    @DisplayName("lifting a colour keeps its hue, so a keyword still reads as a keyword")
    void liftingKeepsHue() {
        // #000080, the schema's keyword navy: unreadable dark, and it has to
        // come out BLUE rather than a generic light grey.
        Color lifted = CodeEditorTheme.lift(new Color(0x000080));
        float[] hsb = Color.RGBtoHSB(lifted.getRed(), lifted.getGreen(), lifted.getBlue(), null);
        float[] stock = Color.RGBtoHSB(0x00, 0x00, 0x80, null);

        assertEquals(stock[0], hsb[0], 0.02f, "the hue moved: " + hex(lifted));
        assertTrue(ThemeManager.luminance(lifted) > UNREADABLE,
            "still unreadable after lifting: " + hex(lifted));
        assertTrue(lifted.getBlue() > lifted.getRed() && lifted.getBlue() > lifted.getGreen(),
            "a lifted navy should still be blue-dominant: " + hex(lifted));
    }

    /** Styles whose foreground cannot be read on the dark editor background. */
    private static List<String> unreadable(CodeEditor editor) {
        List<String> bad = new ArrayList<>();
        SyntaxStyleSchema schema = editor.getStyles();
        for (int i = 0; i < schema.getStyleCount(); i++) {
            SyntaxStyle style = schema.getStyleByIndex(i);
            if (style != null && style.getForeground() != null
                    && ThemeManager.luminance(style.getForeground()) < UNREADABLE) {
                bad.add("[" + i + "] " + hex(style.getForeground()));
            }
        }
        return bad;
    }

    private static Map<String, String> editorColours(CodeEditor editor) {
        Map<String, String> colours = new LinkedHashMap<>();
        colours.put("lineHighlight", hex(editor.getLineHighlightColor()));
        colours.put("caret", hex(editor.getCaretColor()));
        colours.put("selection", hex(editor.getSelectionColor()));
        colours.put("bracketHighlight", hex(editor.getBracketHighlightColor()));
        return colours;
    }

    private static Map<String, String> styleColours(CodeEditor editor) {
        Map<String, String> colours = new LinkedHashMap<>();
        SyntaxStyleSchema schema = editor.getStyles();
        for (int i = 0; i < schema.getStyleCount(); i++) {
            SyntaxStyle style = schema.getStyleByIndex(i);
            if (style != null) {
                colours.put("[" + i + "]fg", hex(style.getForeground()));
                colours.put("[" + i + "]bg", hex(style.getBackground()));
            }
        }
        return colours;
    }

    /** What {@code apply(true)} does that can reach a detached editor. */
    private void goDark(JPanel panel) {
        manager.apply(true);
        javax.swing.SwingUtilities.updateComponentTreeUI(panel);
        manager.swapWhiteTokenBackgrounds(panel);
        editors.installIn(panel);
    }

    private static JPanel panelWith(CodeEditor editor) {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(editor, java.awt.BorderLayout.CENTER);
        panel.setSize(600, 300);
        return panel;
    }

    private static String hex(Color colour) {
        return colour == null ? "null" : String.format("#%06X", colour.getRGB() & 0xFFFFFF);
    }
}
