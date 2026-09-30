package com.mustrysolutions.designerdarkmode.designer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/**
 * Keeps the Designer's cut, copy and paste shortcuts working in trees under
 * dark mode (#168).
 *
 * <p>The Project Browser ({@code NavTreePanel}) removes Ctrl+X/C/V and the
 * {@code CUT}/{@code COPY}/{@code PASTE} keys from its tree's input map when
 * it is built, and walks up the parent chain to do it, so the removal lands
 * in the look and feel's shared {@code Tree.focusInputMap}. With nothing on
 * the tree bound to them, those keystrokes reach the Designer's Edit menu,
 * which copies and pastes resources through the focused frame's
 * {@code EditActionHandler}. Because the map is shared, no tree in a stock
 * Designer binds them, the Tag Browser's included.
 *
 * <p>FlatLaf brings its own {@code Tree.focusInputMap}, which binds all six to
 * Swing's {@code TransferHandler} cut/copy/paste actions. On a tree with a
 * drag-and-drop transfer handler, which both browsers have, those actions
 * are enabled and consume the keystroke, so the Edit menu never sees it. In
 * the Project Browser the shortcut does nothing, while right-click
 * Copy/Paste, which calls the Designer's handler directly, still works. The
 * Tag Browser's transfer handler does copy and paste tags, so its shortcuts
 * kept working; the fix sends them to the Edit menu, as in light.
 *
 * <p>So: record which keystrokes the stock map binds to one of those
 * clipboard actions just before FlatLaf goes in, and once the dark defaults
 * are final remove from FlatLaf's map every clipboard binding the stock map
 * did not have. Recorded rather than hard-coded, so this follows whatever
 * Ignition strips (and stops if it stops). The light side mirrors the same
 * record onto the stock map the reinstall serves, in case that map comes
 * back fresh; today it comes back already stripped and nothing changes.
 *
 * <p>Recorded by action, not by whether a keystroke is bound at all. On
 * macOS, Synthetica's {@code SyntheticaDefaultLookup} loads the menu-shortcut
 * X/C/V back into the shared tree map as {@code cut-to-clipboard},
 * {@code copy-to-clipboard} and {@code paste-from-clipboard} whenever a
 * Synthetica tree installs, which in a Designer is long after the Project
 * Browser's strip. Those are text-editor actions no tree's action map has,
 * so on a stock tree they are inert and the keystroke still reaches the Edit
 * menu; but they are bindings, and a record of "bound" took them for the
 * stock tree's own and let FlatLaf's consuming {@code cut}/{@code copy}/
 * {@code paste} through on every Mac.
 */
final class TreeClipboardKeys {

    static final String MAP_KEY = "Tree.focusInputMap";

    /** The action names {@code TransferHandler}'s cut/copy/paste actions are bound under. */
    static final List<String> CLIPBOARD_ACTIONS = List.of("cut", "copy", "paste");

    /** Every keystroke the stock tree map binds to a clipboard action, or {@code null} before a capture. */
    private Set<KeyStroke> stockClipboardKeys;

    /** Before the dark look and feel goes in. */
    void captureStock() {
        InputMap stock = currentMap();
        if (stock == null) {
            stockClipboardKeys = null;
            DebugLog.log("TreeClipboardKeys: no stock " + MAP_KEY + "; tree clipboard keys left alone.");
            return;
        }
        stockClipboardKeys = new HashSet<>();
        KeyStroke[] keys = stock.allKeys();
        if (keys != null) {
            for (KeyStroke key : keys) {
                if (isClipboardAction(stock.get(key))) {
                    stockClipboardKeys.add(key);
                }
            }
        }
        DebugLog.detail("TreeClipboardKeys: the stock tree map binds " + stockClipboardKeys.size()
            + " keystroke(s) to a clipboard action.");
    }

    /**
     * Remove from the current tree map every clipboard binding the stock map
     * did not have. After the look and feel's defaults are final and before
     * the component trees are updated, so every tree picks the result up.
     *
     * @return how many bindings were removed
     */
    int mirrorStock() {
        InputMap map = currentMap();
        if (stockClipboardKeys == null || map == null) {
            return 0;
        }
        int removed = 0;
        // Through the parent chain, as NavTreePanel does: the binding can
        // live at any level, and allKeys() alone cannot say which.
        for (InputMap level = map; level != null; level = level.getParent()) {
            KeyStroke[] keys = level.keys();
            if (keys == null) {
                continue;
            }
            for (KeyStroke key : keys) {
                if (!stockClipboardKeys.contains(key) && isClipboardAction(level.get(key))) {
                    level.remove(key);
                    removed++;
                }
            }
        }
        DebugLog.detail("TreeClipboardKeys: removed " + removed
            + " clipboard binding(s) the stock tree map does not have.");
        return removed;
    }

    private static boolean isClipboardAction(Object action) {
        return action != null && CLIPBOARD_ACTIONS.contains(action.toString());
    }

    private static InputMap currentMap() {
        Object value = UIManager.get(MAP_KEY);
        return value instanceof InputMap ? (InputMap) value : null;
    }
}
