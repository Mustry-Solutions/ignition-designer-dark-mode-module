package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Font;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;

/**
 * Keeps the Designer's own {@code UIManager.put} defaults across a theme
 * switch (#102).
 *
 * <p>Swing keeps the look and feel's defaults in a table that every
 * {@code setLookAndFeel} replaces, and the <em>developer</em> defaults — what
 * {@code UIManager.put} writes — in the merged {@code UIDefaults} object's
 * own storage, which is meant to outlive look-and-feel changes. Ignition
 * relies on that: {@code IgnitionLookAndFeel.init()} puts its option-pane and
 * file-chooser icons, the category icons of every JIDE property table and
 * the OK/Cancel mnemonics there at startup, and JIDE's extension writes
 * several hundred more. But Synthetica's {@code uninitialize()} — run by
 * Swing when FlatLaf is installed over it — calls {@code clear()} on that
 * merged object, which empties the developer storage along with the tables.
 * The reinstall on the way back lets Synthetica and JIDE put theirs again;
 * Ignition's are never seen again.
 *
 * <p>The one that shows: {@code CategorizedTable.categoryExpandedIcon}. In a
 * Designer that has never been dark it is Ignition's vector chevron; after a
 * cycle it is missing, JIDE falls back to {@code Tree.expandedIcon}, and
 * Synthetica's tree icon painted outside a Synth context — by a JIDE
 * renderer, not a tree — hands Ignition's {@code TreeExpandedIconPainter} a
 * context with no component. Every paint of the Vision Property Editor then
 * dies in that painter, and the editor is blank.
 *
 * <p>So: copy the developer entries just before FlatLaf goes in, and after
 * the stock reinstall and JIDE's re-put on the way back, put back every one
 * that is missing or different — except the kinds the reinstall must own:
 * Synthetica's objects (painters, {@code ScalableFont}s, anything under
 * {@code de.javasoft}), fonts, UI delegates, and the action and input maps
 * Swing installs lazily. One value {@code init()} puts into the look and
 * feel's own table, {@code Synth.doNotSetTextAA}, goes back there too, since
 * that table is new after the reinstall. Under dark the developer entries
 * stay as FlatLaf and JIDE leave them; that half is a fidelity gap, not a
 * defect, and is left alone here.
 *
 * <p>No reflection: the merged object's {@code containsKey} is
 * {@code Hashtable}'s own and answers for the developer storage alone, while
 * {@code keys()} and {@code get} are the merged views.
 */
final class DeveloperDefaults {

    /** Keys {@code IgnitionLookAndFeel.init()} puts into the look and feel's table, not the developer one. */
    static final List<String> LOOK_AND_FEEL_TABLE_KEYS = List.of("Synth.doNotSetTextAA");

    private DeveloperDefaults() {
    }

    /** A copy of the developer entries as they stand, plus the look-and-feel-table keys above. */
    static Map<String, Object> snapshot() {
        UIDefaults all = UIManager.getDefaults();
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Object key : Collections.list(all.keys())) {
            if (key instanceof String && all.containsKey(key)) {
                Object value = all.get(key);
                if (value != null) {
                    copy.put((String) key, value);
                }
            }
        }
        UIDefaults lookAndFeel = UIManager.getLookAndFeelDefaults();
        for (String key : LOOK_AND_FEEL_TABLE_KEYS) {
            Object value = lookAndFeel.get(key);
            if (value != null) {
                copy.put(key, value);
            }
        }
        DebugLog.detail("DeveloperDefaults: snapshot of " + copy.size() + " developer defaults.");
        return copy;
    }

    /**
     * Put back every restorable entry of {@code stock} that is now missing or
     * different.
     *
     * @return how many were put back
     */
    static int restore(Map<String, Object> stock) {
        if (stock.isEmpty()) {
            DebugLog.log("DeveloperDefaults: no snapshot to restore.");
            return 0;
        }
        UIDefaults all = UIManager.getDefaults();
        UIDefaults lookAndFeel = UIManager.getLookAndFeelDefaults();
        int restored = 0;
        for (Map.Entry<String, Object> entry : stock.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!restorable(value)) {
                continue;
            }
            if (LOOK_AND_FEEL_TABLE_KEYS.contains(key)) {
                if (!Objects.equals(lookAndFeel.get(key), value)) {
                    lookAndFeel.put(key, value);
                    restored++;
                }
            } else if (!all.containsKey(key) || !Objects.equals(all.get(key), value)) {
                UIManager.put(key, value);
                restored++;
            }
        }
        DebugLog.log("DeveloperDefaults: restored " + restored + " of " + stock.size()
            + " developer defaults after the stock reinstall.");
        return restored;
    }

    /**
     * What the reinstall must be allowed to own: Synthetica's objects are
     * bound to the instance that made them, fonts are the restore's own
     * business, UI delegates and lazily-installed maps belong to the fresh
     * UIs.
     */
    static boolean restorable(Object value) {
        if (value == null) {
            return false;
        }
        if (value.getClass().getName().startsWith("de.javasoft.")) {
            return false;
        }
        return !(value instanceof Font || value instanceof ComponentUI
            || value instanceof ActionMap || value instanceof InputMap
            || value instanceof UIDefaults.LazyValue || value instanceof UIDefaults.ActiveValue);
    }
}
