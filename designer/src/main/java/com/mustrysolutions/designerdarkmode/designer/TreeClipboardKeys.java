package com.mustrysolutions.designerdarkmode.designer;

import java.util.Arrays;
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
 * are enabled and consume the keystroke, so the Edit menu never sees it: the
 * shortcut does nothing, while right-click Copy/Paste, which calls the
 * Designer's handler directly, still works.
 *
 * <p>So: record which keystrokes the stock map binds just before FlatLaf goes
 * in, and once the dark defaults are final remove from FlatLaf's map every
 * clipboard binding the stock map did not have. Recorded rather than
 * hard-coded, so this follows whatever Ignition strips (and stops if it
 * stops). The light side mirrors the same record onto the stock map the
 * reinstall serves, in case that map comes back fresh; today it comes back
 * already stripped and nothing changes.
 */
final class TreeClipboardKeys {

    static final String MAP_KEY = "Tree.focusInputMap";

    /** The action names {@code TransferHandler}'s cut/copy/paste actions are bound under. */
    static final List<String> CLIPBOARD_ACTIONS = List.of("cut", "copy", "paste");

    /** Every keystroke the stock tree map binds, or {@code null} before a capture. */
    private Set<KeyStroke> stockBound;

    /** Before the dark look and feel goes in. */
    void captureStock() {
        InputMap stock = currentMap();
        if (stock == null) {
            stockBound = null;
            DebugLog.log("TreeClipboardKeys: no stock " + MAP_KEY + "; tree clipboard keys left alone.");
            return;
        }
        KeyStroke[] keys = stock.allKeys();
        stockBound = keys == null ? new HashSet<>() : new HashSet<>(Arrays.asList(keys));
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
        if (stockBound == null || map == null) {
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
                Object action = level.get(key);
                if (!stockBound.contains(key) && CLIPBOARD_ACTIONS.contains(String.valueOf(action))) {
                    level.remove(key);
                    removed++;
                }
            }
        }
        DebugLog.detail("TreeClipboardKeys: removed " + removed
            + " clipboard binding(s) the stock tree map does not have.");
        return removed;
    }

    private static InputMap currentMap() {
        Object value = UIManager.get(MAP_KEY);
        return value instanceof InputMap ? (InputMap) value : null;
    }
}
