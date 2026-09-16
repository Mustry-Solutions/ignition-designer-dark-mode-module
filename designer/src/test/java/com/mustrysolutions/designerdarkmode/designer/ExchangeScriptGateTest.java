package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the gate recognises the Exchange script, and what it says when it does.
 *
 * <p>The script is recognised by what it puts on the menu bar — a
 * {@code JCheckBoxMenuItem("Dark Mode")} it inserts at index 0 of the View
 * menu — because that checkbox is the script's own notion of its state
 * ({@code getDarkMode()} reads {@code selected} from it). Plain Swing, so the
 * menu bar under test is the real thing.
 */
class ExchangeScriptGateTest {

    /** A Designer menu bar as the script leaves it: its checkbox first under View. */
    private static JMenuBar designerMenuBar(JCheckBoxMenuItem scriptCheckbox) {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new JMenuItem("Save"));
        JMenu view = new JMenu("View");
        view.add(new JMenuItem("Reset Panels"));
        if (scriptCheckbox != null) {
            view.getPopupMenu().insert(scriptCheckbox, 0);
        }
        JMenu tools = new JMenu("Tools");
        // This module's own item, which must never be mistaken for the script's.
        tools.add(new JCheckBoxMenuItem(ExchangeScriptGate.CHECKBOX_TEXT));
        bar.add(file);
        bar.add(view);
        bar.add(tools);
        return bar;
    }

    @Test
    @DisplayName("the script's checkbox under View is found; this module's under Tools is not")
    void findsTheScriptCheckboxUnderViewOnly() {
        JCheckBoxMenuItem theirs = new JCheckBoxMenuItem(ExchangeScriptGate.CHECKBOX_TEXT);

        assertSame(theirs, ExchangeScriptGate.findCheckbox(designerMenuBar(theirs)));
        assertNull(ExchangeScriptGate.findCheckbox(designerMenuBar(null)),
            "a Tools → Dark Mode checkbox is ours, not the script's");
        assertNull(ExchangeScriptGate.findCheckbox(new JMenuBar()));
        assertNull(ExchangeScriptGate.findCheckbox(null));
    }

    @Test
    @DisplayName("a View entry that is not a checkbox, or not titled Dark Mode, is not the script")
    void ignoresOtherViewEntries() {
        JMenuBar bar = new JMenuBar();
        JMenu view = new JMenu("View");
        view.add(new JMenuItem(ExchangeScriptGate.CHECKBOX_TEXT));
        view.add(new JCheckBoxMenuItem("Show Grid"));
        bar.add(view);

        assertNull(ExchangeScriptGate.findCheckbox(bar));
    }

    @Test
    @DisplayName("the checkbox need not be first: another module's View entries above it do not hide it")
    void findsTheCheckboxBelowOtherEntries() {
        JCheckBoxMenuItem theirs = new JCheckBoxMenuItem(ExchangeScriptGate.CHECKBOX_TEXT);
        JMenuBar bar = new JMenuBar();
        JMenu view = new JMenu("View");
        view.add(new JMenuItem("Something Else"));
        view.add(theirs);
        bar.add(view);

        assertSame(theirs, ExchangeScriptGate.findCheckbox(bar));
    }

    @Test
    @DisplayName("only a TICKED checkbox blocks; an unticked one has painted nothing yet")
    void blocksOnlyWhileTicked() {
        JCheckBoxMenuItem theirs = new JCheckBoxMenuItem(ExchangeScriptGate.CHECKBOX_TEXT);
        ExchangeScriptGate gate = new MenuBarGate(designerMenuBar(theirs));

        assertNull(gate.blockingReason());
        assertTrue(gate.scriptPresent(), "present, just not ticked");

        theirs.setSelected(true);
        assertNotNull(gate.blockingReason());
        assertTrue(gate.scriptPresent());
    }

    @Test
    @DisplayName("a frame that is not there, or not a JFrame, and a project that is not a collection: nothing found, nothing thrown")
    void toleratesAMissingDesigner() {
        ExchangeScriptGate none = new ExchangeScriptGate();
        assertNull(none.blockingReason());
        assertFalse(none.scriptPresent());
        assertFalse(none.hasScriptModule());

        ExchangeScriptGate odd = new ExchangeScriptGate(() -> "not a frame", () -> "not a project");
        assertNull(odd.blockingReason());
        assertFalse(odd.scriptPresent());
        assertFalse(odd.watchCheckbox(() -> { }), "nothing to watch");
    }

    @Test
    @DisplayName("the watch fires when the checkbox becomes ticked, once per tick, and can be removed")
    void watchFiresOnTick() {
        JCheckBoxMenuItem theirs = new JCheckBoxMenuItem(ExchangeScriptGate.CHECKBOX_TEXT);
        ExchangeScriptGate gate = new MenuBarGate(designerMenuBar(theirs));
        List<String> fired = new ArrayList<>();

        assertTrue(gate.watchCheckbox(() -> fired.add("ticked")));
        assertTrue(gate.watchCheckbox(() -> fired.add("second listener")), "already watching; not added twice");

        theirs.setSelected(true);
        theirs.setSelected(false);
        theirs.setSelected(true);
        assertEquals(List.of("ticked", "ticked"), fired, "SELECTED only, and only the first listener");

        gate.unwatchCheckbox();
        theirs.setSelected(false);
        theirs.setSelected(true);
        assertEquals(List.of("ticked", "ticked"), fired, "removed");
    }

    @Test
    @DisplayName("every message names the script and says which tag to remove")
    void messagesNameTheScriptAndTheFix() {
        String reason = "the Exchange script's View → Dark Mode is ticked";
        for (String message : List.of(
                ExchangeScriptGate.refusalMessage(reason),
                ExchangeScriptGate.dropOutMessage(reason),
                ExchangeScriptGate.coexistenceWarning())) {
            assertTrue(message.contains(ExchangeScriptGate.SCRIPT_NAME), message);
            assertTrue(message.contains(ExchangeScriptGate.CLIENT_TAG), message);
            assertTrue(message.contains("designer.darkModePatch"),
                "says the script library can stay, by its scripting name: " + message);
        }
        assertTrue(ExchangeScriptGate.refusalMessage(reason).startsWith("Dark mode was not applied: " + reason));
        assertTrue(ExchangeScriptGate.dropOutMessage(reason).startsWith("Dark mode was turned off because " + reason));
    }

    /** A gate over a menu bar alone: the frame is stubbed out, the bar is real. */
    private static final class MenuBarGate extends ExchangeScriptGate {

        private final JMenuBar bar;

        MenuBarGate(JMenuBar bar) {
            super(() -> null, () -> null);
            this.bar = bar;
        }

        @Override
        JCheckBoxMenuItem findCheckbox() {
            return findCheckbox(bar);
        }
    }
}
