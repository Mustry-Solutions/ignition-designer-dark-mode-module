package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.LookAndFeel;
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
 * <p>Two pieces of third-party behaviour are transcribed here, because
 * neither can run as itself in the harness. {@code NavTreePanel}'s strip
 * (from its bytecode, 8.3.8; 8.1.55 is the same) needs a running
 * {@code IgnitionDesigner}. And on macOS, Synthetica's
 * {@code SyntheticaDefaultLookup} loads the menu-shortcut X/C/V into the
 * shared tree map as {@code cut-to-clipboard}, {@code copy-to-clipboard} and
 * {@code paste-from-clipboard} each time a Synthetica tree installs, which in
 * a Designer is after the Project Browser's strip. Those are text-editor
 * action names that no tree's action map has, so they are inert on a tree;
 * but they are bindings, and a capture that asked only whether a keystroke
 * was bound took them for the stock tree's own and let FlatLaf's consuming
 * ones through (the macOS row of #169's first CI run, in the light restore).
 * The lookup only runs on macOS; transcribed, the case runs on every row.
 *
 * <p>The Edit menu is stood in for by an ancestor binding on the scroll pane:
 * like a menu accelerator, it is only consulted once the focused tree has not
 * consumed the keystroke. The trees are updated with
 * {@code updateComponentTreeUI} rather than through a window, so this runs
 * headless too.
 */
class TreeClipboardKeysTest {

    private ThemeManager manager;
    /** Every change made to a shared map here, oldest first: {map, key, previous value or null}. */
    private final List<Object[]> sharedMapEdits = new ArrayList<>();

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
        for (int i = sharedMapEdits.size() - 1; i >= 0; i--) {
            Object[] edit = sharedMapEdits.get(i);
            InputMap map = (InputMap) edit[0];
            KeyStroke key = (KeyStroke) edit[1];
            if (edit[2] == null) {
                map.remove(key);
            } else {
                map.put(key, edit[2]);
            }
        }
    }

    @Test
    @DisplayName("the Project Browser's tree leaves Ctrl+X/C/V to the Designer under dark and after the restore")
    void clipboardKeysReachTheDesignerUnderDark() {
        Harness browser = new Harness();
        stripLikeNavTreePanel(browser.tree);
        browser.assertKeysReachTheDesigner("stock");

        // Every Synthetica tree the Designer installs after the Project
        // Browser, on macOS.
        loadLikeSyntheticaOnMac(browser.tree.getInputMap().getParent());
        browser.assertKeysReachTheDesigner("stock, after a later tree's install");

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
    @DisplayName("without the Project Browser's strip, dark consumes exactly the clipboard keys stock does")
    void darkConsumesWhatStockConsumes() {
        // The module mirrors the stock tree rather than stripping outright:
        // an Ignition that stops stripping gets FlatLaf's bindings back
        // wherever its own tree would have consumed the keystroke.
        Harness tree = new Harness();
        Map<String, String> stock = tree.consumingActions();

        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(tree.scrollPane);
        assertEquals(stock, tree.consumingActions(),
            "the clipboard keystrokes a tree consumes, stock vs dark");
    }

    /** The six keystrokes the Project Browser strips. */
    private static List<KeyStroke> clipboardKeys() {
        return List.of(
            KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask()),
            KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask()),
            KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask()),
            KeyStroke.getKeyStroke("pressed CUT"),
            KeyStroke.getKeyStroke("pressed COPY"),
            KeyStroke.getKeyStroke("pressed PASTE"));
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
            String[] names = {"cut", "copy", "paste", "cut", "copy", "paste"};
            List<KeyStroke> keys = clipboardKeys();
            for (int i = 0; i < keys.size(); i++) {
                ancestor.put(keys.get(i), "designer-" + names[i]);
            }
            for (String name : new String[] {"cut", "copy", "paste"}) {
                scrollPane.getActionMap().put("designer-" + name, new AbstractAction(name) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        designerActions.add(name);
                    }
                });
            }
        }

        /** The tree's own action for a keystroke, which would consume it, or null. */
        Action consumingAction(KeyStroke key) {
            Object binding = tree.getInputMap().get(key);
            return binding == null ? null : tree.getActionMap().get(binding);
        }

        /** Keystroke to the binding of every clipboard keystroke the tree would consume. */
        Map<String, String> consumingActions() {
            Map<String, String> out = new LinkedHashMap<>();
            for (KeyStroke key : clipboardKeys()) {
                if (consumingAction(key) != null) {
                    out.put(key.toString(), String.valueOf(tree.getInputMap().get(key)));
                }
            }
            return out;
        }

        void assertKeysReachTheDesigner(String state) {
            for (KeyStroke key : clipboardKeys()) {
                assertNull(consumingAction(key), state + ": the tree consumes " + key
                    + " (bound to " + tree.getInputMap().get(key) + ")");
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

    /** {@code NavTreePanel}'s constructor, transcribed. */
    private void stripLikeNavTreePanel(JTree tree) {
        List<KeyStroke> clipboard = clipboardKeys();
        InputMap map = tree.getInputMap();
        do {
            for (KeyStroke key : clipboard) {
                KeyStroke[] own = map.keys();
                if (own != null && Arrays.asList(own).contains(key)) {
                    sharedMapEdits.add(new Object[] {map, key, map.get(key)});
                }
                map.remove(key);
            }
            map = map.getParent();
        } while (map != null && map.get(clipboard.get(0)) != null);
    }

    /**
     * What {@code SyntheticaDefaultLookup.getDefault} does to a tree's input
     * map on macOS, transcribed with the platform's menu modifier (the
     * original's "meta").
     */
    private void loadLikeSyntheticaOnMac(InputMap map) {
        Object[] bindings = {
            KeyStroke.getKeyStroke(KeyEvent.VK_X, menuMask()), "cut-to-clipboard",
            KeyStroke.getKeyStroke(KeyEvent.VK_C, menuMask()), "copy-to-clipboard",
            KeyStroke.getKeyStroke(KeyEvent.VK_V, menuMask()), "paste-from-clipboard",
            KeyStroke.getKeyStroke(KeyEvent.VK_A, menuMask()), "select-all",
        };
        for (int i = 0; i < bindings.length; i += 2) {
            KeyStroke key = (KeyStroke) bindings[i];
            KeyStroke[] own = map.keys();
            boolean had = own != null && Arrays.asList(own).contains(key);
            sharedMapEdits.add(new Object[] {map, key, had ? map.get(key) : null});
        }
        LookAndFeel.loadKeyBindings(map, bindings);
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
