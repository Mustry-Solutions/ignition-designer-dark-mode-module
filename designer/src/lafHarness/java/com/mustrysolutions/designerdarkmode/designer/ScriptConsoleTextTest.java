package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Text already in the Script Console when Dark Mode is switched on (#129).
 *
 * <p>The stand-in console is built the way {@code ConsolePanel} builds its own
 * (read from the 8.3.8 bytecode): {@code regular} added under {@code default}
 * with no colour, {@code emphasize} set to {@code Color.blue}, {@code error} to
 * {@code Color.red}, and text written with {@code insertString(offset, text,
 * style)}. That call copies the style's colour into the run, so restyling the
 * style alone left the interpreter banner blue on #3C3F41.
 */
class ScriptConsoleTextTest {

    private static final String BLUE = "#0000FF";
    private static final String RED = "#FF0000";

    private ConsoleTextTheme consoles;
    private JPanel window;
    private StyledDocument document;

    @BeforeEach
    void buildAConsole() {
        consoles = new ConsoleTextTheme();
        JTextPane pane = new JTextPane();
        document = pane.getStyledDocument();
        Style regular = document.addStyle("regular", document.getStyle("default"));
        StyleConstants.setForeground(document.addStyle("emphasize", regular), Color.blue);
        StyleConstants.setForeground(document.addStyle("error", regular), Color.red);
        window = new JPanel();
        window.add(pane);
    }

    @AfterEach
    void leaveTheConsoleLight() {
        consoles.uninstall();
    }

    @Test
    @DisplayName("the banner written before Dark Mode is recoloured, then restored (#129)")
    void textWrittenBeforeInstallIsRecolouredAndRestored() throws Exception {
        write("Jython 2.7.3, executing locally in the Designer.\n", "emphasize");
        write(">>> print 1\n", "regular");
        write("Traceback (most recent call last):\n", "error");
        assertEquals(List.of(BLUE, "unset", RED), foregrounds(),
            "the stand-in console does not carry ConsolePanel's colours");

        consoles.installIn(window);

        List<String> dark = foregrounds();
        assertEquals("unset", dark.get(1),
            "a regular run was given its own colour, so it no longer follows the style");
        for (int run : new int[] {0, 2}) {
            assertTrue(ThemeManager.luminance(Color.decode(dark.get(run))) > 120,
                "run " + run + " is still unreadable on the dark console: " + dark.get(run));
        }

        consoles.uninstall();

        assertEquals(List.of(BLUE, "unset", RED), foregrounds(),
            "the console text did not come back to its stock colours");
    }

    @Test
    @DisplayName("text written while dark is stock-coloured once Dark Mode is off (#129)")
    void textWrittenWhileDarkIsRestored() throws Exception {
        consoles.installIn(window);
        write("printed while dark\n", "emphasize");
        write("failed while dark\n", "error");
        assertFalse(foregrounds().contains(BLUE), "the restyle did not reach new text");

        consoles.uninstall();

        assertEquals(List.of(BLUE, RED), foregrounds(),
            "text written in the dark colours stayed light on the light console");
    }

    @Test
    @DisplayName("a run in a colour the console styles never use is left alone")
    void foreignColoursAreLeftAlone() throws Exception {
        SimpleAttributeSet green = new SimpleAttributeSet();
        StyleConstants.setForeground(green, new Color(0x008000));
        document.insertString(0, "someone else's colour\n", green);

        consoles.installIn(window);
        assertEquals(List.of("#008000"), foregrounds());
        consoles.uninstall();
        assertEquals(List.of("#008000"), foregrounds());
    }

    private void write(String text, String styleName) throws Exception {
        document.insertString(document.getLength(), text, document.getStyle(styleName));
    }

    /** Each run's OWN foreground ("unset" when it inherits one), repeats collapsed. */
    private List<String> foregrounds() {
        List<String> colours = new ArrayList<>();
        int position = 0;
        while (position < document.getLength()) {
            javax.swing.text.Element run = document.getCharacterElement(position);
            AttributeSet own = run.getAttributes();
            String hex = own.isDefined(StyleConstants.Foreground)
                ? String.format("#%06X", StyleConstants.getForeground(own).getRGB() & 0xFFFFFF)
                : "unset";
            if (colours.isEmpty() || !colours.get(colours.size() - 1).equals(hex)) {
                colours.add(hex);
            }
            position = Math.max(run.getEndOffset(), position + 1);
        }
        return colours;
    }
}
