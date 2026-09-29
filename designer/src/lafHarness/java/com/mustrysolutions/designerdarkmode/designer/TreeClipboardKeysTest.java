package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cut, copy and paste shortcuts in the Project Browser under dark mode (#168).
 *
 * <p>{@code NavTreePanel} strips the clipboard keystrokes from its tree's
 * input map chain when it is built, which takes them out of the look and
 * feel's shared {@code Tree.focusInputMap} too, so they reach the Designer's
 * Edit menu. FlatLaf's tree map binds them again, to Swing's
 * {@code TransferHandler} actions, and on a tree with a transfer handler
 * those consume the keystroke before the Edit menu can see it.
 *
 * <p>{@code NavTreePanel} needs a running {@code IgnitionDesigner} to build,
 * so its strip is transcribed below from its bytecode (8.3.8). The Edit menu
 * is stood in for by an ancestor binding on the scroll pane: like a menu
 * accelerator, it is only consulted once the focused tree has not consumed
 * the keystroke. The trees are updated with {@code updateComponentTreeUI}
 * rather than through a window, so this runs headless too.
 */
class TreeClipboardKeysTest {

    private ThemeManager manager;
    /** What the transcribed strip took out of the shared stock map, to put back. */
    private final List<Object[]> stripped = new ArrayList<>();

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
    }

    @AfterEach
    void leaveTheJvmLightAndTheStockMapWhole() {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        for (Object[] entry : stripped) {
            ((InputMap) entry[0]).put((KeyStroke) entry[1], entry[2]);
        }
    }

    @Test
    @DisplayName("the Project Browser's tree leaves Ctrl+X/C/V to the Designer under dark and after the restore")
    void clipboardKeysReachTheDesignerUnderDark() {
        Harness browser = new Harness();
        stripLikeNavTreePanel(browser.tree);
        browser.assertKeysReachTheDesigner("stock");

        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(browser.scrollPane);
        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertTrue(manager.failedPhases().isEmpty(), "failed phases: " + manager.failedPhases());
        browser.assertKeysReachTheDesigner("dark");

        // A tree built under dark takes the same shared map.
        Harness late = new Harness();
        late.assertKeysReachTheDesigner("dark, tree built after the switch");

        manager.apply(false);
        SwingUtilities.updateComponentTreeUI(browser.scrollPane);
        browser.assertKeysReachTheDesigner("light again");
    }

    @Test
    @DisplayName("without the Project Browser's strip, dark keeps FlatLaf's tree clipboard bindings")
    void onlyWhatStockLacksIsRemoved() {
        // The module mirrors the stock map rather than stripping outright:
        // an Ignition that stops stripping gets FlatLaf's bindings back.
        Harness tree = new Harness();
        assertNotNull(tree.binding(KeyEvent.VK_C), "stock binds Ctrl+C on a tree nobody stripped");

        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(tree.scrollPane);
        assertEquals("copy", String.valueOf(tree.binding(KeyEvent.VK_C)));
        assertEquals("paste", String.valueOf(tree.binding(KeyEvent.VK_V)));
        assertEquals("cut", String.valueOf(tree.binding(KeyEvent.VK_X)));
    }

    /** A tree with a transfer handler in a scroll pane that stands in for the Edit menu. */
    private static final class Harness {
        final KeyTree tree = new KeyTree();
        final JScrollPane scrollPane = new JScrollPane(tree);
        final List<String> designerActions = new ArrayList<>();

        Harness() {
            // What makes Swing's clipboard actions enabled, and so consume
            // the keystroke. (Drag itself is not needed, and refuses headless.)
            tree.setTransferHandler(new TransferHandler("selectionPath"));
            tree.setSelectionRow(0);
            InputMap ancestor = scrollPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            for (String name : new String[] {"cut", "copy", "paste"}) {
                scrollPane.getActionMap().put("designer-" + name, new AbstractAction(name) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        designerActions.add(name);
                    }
                });
            }
            ancestor.put(KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask()), "designer-cut");
            ancestor.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask()), "designer-copy");
            ancestor.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask()), "designer-paste");
            ancestor.put(KeyStroke.getKeyStroke("pressed CUT"), "designer-cut");
            ancestor.put(KeyStroke.getKeyStroke("pressed COPY"), "designer-copy");
            ancestor.put(KeyStroke.getKeyStroke("pressed PASTE"), "designer-paste");
        }

        Object binding(int keyCode) {
            return tree.getInputMap().get(KeyStroke.getKeyStroke(keyCode, menuMask()));
        }

        void assertKeysReachTheDesigner(String state) {
            for (int keyCode : new int[] {KeyEvent.VK_X, KeyEvent.VK_C, KeyEvent.VK_V}) {
                assertNull(binding(keyCode), state + ": the tree binds "
                    + KeyStroke.getKeyStroke(keyCode, menuMask()) + " to " + binding(keyCode));
            }
            designerActions.clear();
            tree.press(KeyEvent.VK_X, menuMask());
            tree.press(KeyEvent.VK_C, menuMask());
            tree.press(KeyEvent.VK_V, menuMask());
            tree.press(KeyEvent.VK_CUT, 0);
            tree.press(KeyEvent.VK_COPY, 0);
            tree.press(KeyEvent.VK_PASTE, 0);
            assertEquals(List.of("cut", "copy", "paste", "cut", "copy", "paste"), designerActions,
                state + ": the Designer's Edit actions did not all receive the keystrokes");
        }
    }

    /** Exposes the key-binding path a real keystroke takes. */
    private static final class KeyTree extends JTree {
        void press(int keyCode, int modifiers) {
            processKeyEvent(new KeyEvent(this, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                modifiers, keyCode, KeyEvent.CHAR_UNDEFINED));
        }
    }

    /** {@code NavTreePanel}'s constructor, transcribed. Records what it removes. */
    private void stripLikeNavTreePanel(JTree tree) {
        KeyStroke[] clipboard = {
            KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask()),
            KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask()),
            KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask()),
            KeyStroke.getKeyStroke("pressed COPY"),
            KeyStroke.getKeyStroke("pressed CUT"),
            KeyStroke.getKeyStroke("pressed PASTE"),
        };
        InputMap map = tree.getInputMap();
        do {
            for (KeyStroke key : clipboard) {
                KeyStroke[] own = map.keys();
                if (own != null && java.util.Arrays.asList(own).contains(key)) {
                    stripped.add(new Object[] {map, key, map.get(key)});
                }
                map.remove(key);
            }
            map = map.getParent();
        } while (map != null && map.get(clipboard[0]) != null);
    }

    /** The menu shortcut modifier; the toolkit refuses to say headless. */
    private static int menuMask() {
        if (!GraphicsEnvironment.isHeadless()) {
            return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        }
        return System.getProperty("os.name", "").toLowerCase().contains("mac")
            ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    }
}
