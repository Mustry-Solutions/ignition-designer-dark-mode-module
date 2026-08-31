package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.client.util.gui.OutputConsole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Designer's Output Console dock (#52).
 *
 * <p>This console does not use named document styles, so the rest of
 * {@link ConsoleTextTheme} never reached it. {@code OutputConsole} registers two
 * {@code ConsoleAppender}s on the bifurcated {@code System.out} / {@code
 * System.err} holding {@code Color.black} and {@code Color.red}, and stamps that
 * colour onto every inserted run. Since the Designer's logging goes through
 * stdout, that is the whole console: near-black text on #3C3F41.
 *
 * <p>Neither colour can be mutated — they are the JDK globals — so the pass
 * rewrites the runs already in the document and repoints the appenders for
 * lines still to come. Both halves are asserted here, and so is the property
 * that makes the restore possible without storing offsets: the mapping is by
 * colour, so it survives the document being trimmed as it grows.
 */
class OutputConsoleTest {

    private ThemeManager manager;
    private ConsoleTextTheme consoles;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        consoles = new ConsoleTextTheme();
    }

    @AfterEach
    void leaveTheJvmLight() {
        consoles.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("existing console text is recoloured and comes back black (#52)")
    void theTextIsRecolouredAndRestored() throws Exception {
        StyledDocument document = consoleDocument();
        append(document, "INFO designer.main -- Starting module: Perspective\n", Color.black);
        append(document, "ERROR something went wrong\n", Color.red);

        assertEquals(List.of("#000000", "#FF0000"), foregrounds(document),
            "the stand-in console does not carry the colours OutputConsole actually "
                + "stamps, so this test would prove nothing");

        consoles.install();

        List<String> dark = foregrounds(document);
        assertNotEquals(List.of("#000000", "#FF0000"), dark, "nothing was recoloured");
        dark.forEach(colour -> assertTrue(
            ThemeManager.luminance(Color.decode(colour)) > 120,
            "console text is still too dark to read: " + colour));

        consoles.uninstall();

        assertEquals(List.of("#000000", "#FF0000"), foregrounds(document),
            "the console text did not come back to its stock colours");
    }

    @Test
    @DisplayName("the appenders are repointed, so later lines arrive readable (#52)")
    void theAppendersAreRepointed() throws Exception {
        consoleDocument();
        List<String> stock = appenderColours();
        assertEquals(List.of("#000000", "#FF0000"), stock,
            "the appenders do not hold black and red (" + stock + ")");

        consoles.install();

        appenderColours().forEach(colour -> assertTrue(
            ThemeManager.luminance(Color.decode(colour)) > 120,
            "an appender still writes near-black text: " + colour));

        consoles.uninstall();

        assertEquals(stock, appenderColours(),
            "the appenders were not pointed back at black and red");
    }

    /**
     * The real {@code OutputConsole} singleton and its text pane.
     *
     * <p>{@code init} needs a {@code PopupWindowParent}; the harness supplies a
     * do-nothing one so the console builds without a Designer around it.
     */
    private static StyledDocument consoleDocument() throws Exception {
        if (OutputConsole.getInstance() == null) {
            OutputConsole.init(new HarnessPopupParent());
        }
        OutputConsole console = OutputConsole.getInstance();
        Field pane = OutputConsole.class.getDeclaredField("pane");
        pane.setAccessible(true);
        return ((JTextPane) pane.get(console)).getStyledDocument();
    }

    private static void append(StyledDocument document, String text, Color colour)
            throws Exception {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, colour);
        document.insertString(document.getLength(), text, attributes);
    }

    /** The distinct foregrounds in the document, in order. */
    private static List<String> foregrounds(StyledDocument document) {
        List<String> colours = new ArrayList<>();
        int position = 0;
        while (position < document.getLength()) {
            javax.swing.text.Element run = document.getCharacterElement(position);
            String hex = hex(StyleConstants.getForeground(run.getAttributes()));
            if (colours.isEmpty() || !colours.get(colours.size() - 1).equals(hex)) {
                colours.add(hex);
            }
            position = Math.max(run.getEndOffset(), position + 1);
        }
        return colours;
    }

    /** The colour each ConsoleAppender will stamp on the next line it writes. */
    private static List<String> appenderColours() throws Exception {
        List<String> colours = new ArrayList<>();
        for (String streamName : new String[] {"_out", "_err"}) {
            Field streamField = OutputConsole.class.getDeclaredField(streamName);
            streamField.setAccessible(true);
            Object stream = streamField.get(null);
            Field subsField = stream.getClass().getDeclaredField("subs");
            subsField.setAccessible(true);
            for (Object sub : (Iterable<?>) subsField.get(stream)) {
                try {
                    Field colour = sub.getClass().getDeclaredField("bg");
                    colour.setAccessible(true);
                    colours.add(hex((Color) colour.get(sub)));
                } catch (NoSuchFieldException notAnAppender) {
                    continue;
                }
            }
        }
        return colours;
    }

    private static String hex(Color colour) {
        return colour == null ? "unset" : String.format("#%06X", colour.getRGB() & 0xFFFFFF);
    }

    /** The least a {@code PopupWindowParent} can be and still let the console build. */
    private static final class HarnessPopupParent
            implements com.inductiveautomation.ignition.client.util.gui.PopupWindowParent {
        @Override
        public void close() {
        }

        @Override
        public void open() {
        }

        @Override
        public boolean isShowing() {
            return false;
        }

        @Override
        public void pack() {
        }

        @Override
        public void setTitle(String title) {
        }

        @Override
        public void setContents(
                com.inductiveautomation.ignition.client.util.gui.PopupWindowContents contents) {
        }

        @Override
        public java.awt.Component getComponent() {
            return null;
        }

        @Override
        public void setDefaultButton(javax.swing.JButton button) {
        }

        @Override
        public void center() {
        }

        @Override
        public void setToolBar(javax.swing.AbstractButton[] buttons) {
        }
    }
}
