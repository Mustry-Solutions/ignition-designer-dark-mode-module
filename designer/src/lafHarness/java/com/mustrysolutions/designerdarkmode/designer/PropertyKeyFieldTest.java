package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.client.jsonedit.JsonEditor;
import com.inductiveautomation.ignition.client.jsonedit.KeyEditorField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The property NAME column of the Perspective property editor, black on dark.
 *
 * <p>Reported on the forum against the announcement's own screenshot ("black
 * text on dark gray background"). Every property name in the Session Props
 * editor is a {@code KeyEditorField}: a borderless {@code JTextField} whose
 * class fixes its uneditable text to {@code Color.BLACK} in the constructor,
 * then calls {@code setEditable(false)} for any key the schema locks — which
 * is every built-in property. The values beside them go through the same base
 * class but are handed {@code Color.GRAY} and per-type colours, which is why
 * only the names are unreadable.
 */
class PropertyKeyFieldTest {

    private static final String SESSION_PROPS =
        "{\"host\": \"192.168.107.1\", \"locale\": \"en-US\","
            + " \"auth\": {\"authenticated\": true, \"user\": {\"id\": \"x\"}}}";

    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
    }

    @AfterEach
    void leaveTheJvmLight() {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("read-only property names are legible under dark mode")
    void readOnlyKeysAreLegible() throws Exception {
        JPanel panel = propertyEditor(false);
        List<KeyEditorField> keys = keyFields(panel);
        assertFalse(keys.isEmpty(), "no KeyEditorField was built — the editor changed shape");

        manager.apply(true);
        manager.swapWhiteTokenBackgrounds(panel);

        assertEquals(List.of(), unreadable(keys),
            "property names still carry near-black text on a dark background");
    }

    @Test
    @DisplayName("editable property names are legible under dark mode")
    void editableKeysAreLegible() throws Exception {
        JPanel panel = propertyEditor(true);
        List<KeyEditorField> keys = keyFields(panel);
        assertFalse(keys.isEmpty(), "no KeyEditorField was built — the editor changed shape");

        manager.apply(true);
        manager.swapWhiteTokenBackgrounds(panel);

        assertEquals(List.of(), unreadable(keys),
            "property names still carry near-black text on a dark background");
    }

    @Test
    @DisplayName("property rows built while dark mode is on are legible")
    void rowsBuiltUnderDarkAreLegible() throws Exception {
        // The Designer's order: the switch happens first, and the rows are
        // built later when a view or the session props are opened.
        manager.apply(true);
        JPanel panel = propertyEditor(false);
        List<KeyEditorField> keys = keyFields(panel);
        assertFalse(keys.isEmpty(), "no KeyEditorField was built — the editor changed shape");
        StringBuilder chain = new StringBuilder();
        KeyEditorField first = keys.get(0);
        chain.append(String.format("own bg set=%s bg=%s uires=%s fg=#%06X; parents:",
            first.isBackgroundSet(), first.getBackground(),
            first.getBackground() instanceof javax.swing.plaf.UIResource,
            first.getForeground().getRGB() & 0xFFFFFF));
        for (Container p = first.getParent(); p != null && !(p instanceof javax.swing.JRootPane); p = p.getParent()) {
            chain.append(String.format(" %s[set=%s bg=%s]", p.getClass().getSimpleName(),
                p.isBackgroundSet(), p.getBackground() == null ? null
                    : String.format("#%06X", p.getBackground().getRGB() & 0xFFFFFF)));
        }
        System.out.println("BEFORE WALK: " + chain);

        manager.swapWhiteTokenBackgrounds(panel);

        assertEquals(List.of(), unreadable(keys),
            "property names built under dark mode carry near-black text: " + chain);
    }

    @Test
    @DisplayName("a key field with no background of its own is still lifted")
    void keyWithoutOwnBackgroundIsLifted() throws Exception {
        // The state #23 documented in this very editor: BasicTextUI installs
        // the field's background from TextField.background, and when that
        // resolves to nothing the field has NO background of its own —
        // getBackground() then falls through to NodeEditor$FilterWrapper,
        // which permanently holds the filter-match amber (#F7901E). A lift
        // that reads "is the background dark?" sees amber and leaves the
        // black text alone.
        manager.apply(true);
        JPanel panel = propertyEditor(false);
        List<KeyEditorField> keys = keyFields(panel);
        for (KeyEditorField key : keys) {
            key.setBackground(null);
        }
        assertTrue(ThemeManager.luminance(keys.get(0).getBackground()) > 100,
            "the fixture no longer models the amber fall-through");

        manager.swapWhiteTokenBackgrounds(panel);

        assertEquals(List.of(), unreadable(keys),
            "a property name over the amber filter wrapper kept its black text");
    }

    @Test
    @DisplayName("IA re-applying its uneditable colour after the lift keeps the key light")
    void reappliedUneditableColourStaysLight() throws Exception {
        // BorderlessField.setEditable(false) writes its private
        // uneditableForeground — Color.BLACK for a key — straight through
        // JTextField.setForeground, bypassing the override our lift went
        // through. Any later editability toggle therefore puts black back
        // unless the uneditable colour itself was replaced.
        manager.apply(true);
        JPanel panel = propertyEditor(false);
        List<KeyEditorField> keys = keyFields(panel);
        manager.swapWhiteTokenBackgrounds(panel);
        assertEquals(List.of(), unreadable(keys), "the lift itself did not happen");

        for (KeyEditorField key : keys) {
            key.setEditable(false);
        }

        assertEquals(List.of(), unreadable(keys),
            "setEditable(false) put the stock black back over our lift");
    }

    @Test
    @DisplayName("the rendered name column has no black text under dark mode")
    void renderedKeysAreNotBlack() throws Exception {
        // Built under FlatLaf: with no Window, apply() cannot refresh the UI
        // delegates of a tree built earlier, and a Synthetica text-field
        // painter cannot paint headlessly.
        manager.apply(true);
        JPanel panel = propertyEditor(false);
        List<KeyEditorField> keys = keyFields(panel);
        manager.swapWhiteTokenBackgrounds(panel);
        BufferedImage image = render(panel);

        List<String> black = new ArrayList<>();
        for (KeyEditorField key : keys) {
            Point at = SwingUtilities.convertPoint(key, 0, 0, panel);
            int dark = 0;
            int light = 0;
            for (int y = at.y; y < at.y + key.getHeight() && y < image.getHeight(); y++) {
                for (int x = at.x; x < at.x + key.getWidth() && x < image.getWidth(); x++) {
                    int lum = ThemeManager.luminance(new Color(image.getRGB(x, y)));
                    if (lum < 20) {
                        dark++;
                    } else if (lum > 150) {
                        light++;
                    }
                }
            }
            if (dark > 0 && light == 0) {
                black.add(key.getText() + " (" + dark + " near-black px, 0 light px)");
            }
        }
        assertEquals(List.of(), black, "the name column renders black glyphs on the dark panel");
    }

    @Test
    @DisplayName("the light restore puts the stock black back")
    void restorePutsBlackBack() throws Exception {
        JPanel panel = propertyEditor(false);
        List<KeyEditorField> keys = keyFields(panel);

        manager.apply(true);
        manager.swapWhiteTokenBackgrounds(panel);
        manager.apply(false);

        for (KeyEditorField key : keys) {
            assertEquals(Color.BLACK, key.getForeground(),
                "'" + key.getText() + "' did not go back to the stock black on light");
            // What IA does the next time a key's editability is toggled.
            key.setEditable(false);
            assertEquals(Color.BLACK, key.getForeground(),
                "'" + key.getText() + "': the uneditable colour was not restored");
        }
    }

    /** A JSON editor over the session-props shape, laid out, keys locked or not. */
    private static JPanel propertyEditor(boolean keysEditable) {
        JsonEditor editor = new JsonEditor(SESSION_PROPS, false);
        editor.expandAll();
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(new Color(0x3C3F41));
        panel.add(editor, java.awt.BorderLayout.CENTER);
        panel.setSize(360, 240);
        // FlatLaf's text-field painting reaches for the root pane; give the
        // tree one without a window, as the Designer's dock would.
        javax.swing.JRootPane root = new javax.swing.JRootPane();
        root.setSize(360, 240);
        root.getContentPane().add(panel, java.awt.BorderLayout.CENTER);
        layout(root);
        layout(root);
        // What KeyEditorField's constructor does for a node whose key the
        // schema locks — every built-in Perspective property, and every
        // session prop. A schema-less DocumentModel leaves keys editable, so
        // the locked case is reproduced by the same public call.
        if (!keysEditable) {
            for (KeyEditorField key : keyFields(panel)) {
                key.setEditable(false);
            }
        }
        return panel;
    }

    private static List<KeyEditorField> keyFields(Container root) {
        List<KeyEditorField> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(Container container, List<KeyEditorField> into) {
        for (Component child : container.getComponents()) {
            if (child instanceof KeyEditorField) {
                into.add((KeyEditorField) child);
            }
            if (child instanceof Container) {
                collect((Container) child, into);
            }
        }
    }

    private static List<String> unreadable(List<KeyEditorField> keys) {
        List<String> bad = new ArrayList<>();
        for (KeyEditorField key : keys) {
            Color fg = key.getForeground();
            Color bg = key.getBackground();
            if (ThemeManager.luminance(fg) < 150) {
                bad.add(String.format("'%s' fg=#%06X bg=#%06X editable=%s",
                    key.getText(), fg.getRGB() & 0xFFFFFF,
                    bg == null ? 0 : bg.getRGB() & 0xFFFFFF, key.isEditable()));
            }
        }
        return bad;
    }

    private static BufferedImage render(JPanel panel) {
        layout(panel);
        layout(panel);
        BufferedImage image = new BufferedImage(
            panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static void layout(Component component) {
        if (component instanceof Container) {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }
}
