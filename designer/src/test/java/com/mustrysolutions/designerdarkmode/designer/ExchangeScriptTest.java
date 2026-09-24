package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Exchange script's checkbox, taken out of play (#89). Plain Swing and
 * headless: the menu bar the script would insert into, built here the way
 * the script builds it (a {@code JCheckBoxMenuItem("Dark Mode")} at index 0
 * of View).
 */
class ExchangeScriptTest {

    private static JMenuBar designerMenuBar(boolean withScriptCheckbox, boolean ticked) {
        JMenuBar bar = new JMenuBar();
        bar.add(new JMenu("File"));
        JMenu view = new JMenu("View");
        view.add(new JMenuItem("Reset Panels"));
        if (withScriptCheckbox) {
            JCheckBoxMenuItem box = new JCheckBoxMenuItem("Dark Mode");
            box.setSelected(ticked);
            view.getPopupMenu().insert(box, 0);
        }
        bar.add(view);
        JMenu tools = new JMenu("Tools");
        tools.add(new JCheckBoxMenuItem("Dark Mode")); // ours, not the script's
        bar.add(tools);
        return bar;
    }

    @Test
    @DisplayName("the checkbox is found in View by text, and ours under Tools is not it")
    void findsTheScriptsCheckboxOnly() {
        JCheckBoxMenuItem found = ExchangeScript.findCheckbox(designerMenuBar(true, false));
        assertNotNull(found);
        assertEquals("Dark Mode", found.getText());
        assertSame(((JMenu) designerMenuBar(true, false).getMenu(1)).getMenuComponent(0).getClass(), found.getClass());
        assertNull(ExchangeScript.findCheckbox(designerMenuBar(false, false)),
            "without the script only our Tools item exists, and it must not count");
        assertNull(ExchangeScript.findCheckbox(null));
    }

    @Test
    @DisplayName("neutralising unticks, disables and labels; it reports whether it was ticked")
    void neutraliseTakesTheCheckboxOutOfPlay() {
        JCheckBoxMenuItem ticked = new JCheckBoxMenuItem("Dark Mode");
        ticked.setSelected(true);
        AtomicInteger actions = new AtomicInteger();
        ticked.addActionListener(e -> actions.incrementAndGet()); // the script's listener

        assertTrue(ExchangeScript.neutralise(ticked));
        assertFalse(ticked.isSelected());
        assertFalse(ticked.isEnabled());
        assertEquals(ExchangeScript.TOOLTIP, ticked.getToolTipText());
        assertEquals(0, actions.get(), "unticking must not fire the script's action listener (its light paint)");

        JCheckBoxMenuItem unticked = new JCheckBoxMenuItem("Dark Mode");
        assertFalse(ExchangeScript.neutralise(unticked));
        assertFalse(unticked.isEnabled());
    }

    @Test
    @DisplayName("a check notices once, and a second check leaves a neutralised checkbox alone")
    void checkNoticesOnce() {
        JMenuBar bar = designerMenuBar(true, true);
        List<String> notices = new ArrayList<>();
        ExchangeScript script = new ExchangeScript(() -> bar, () -> null, notices::add);

        assertTrue(script.check());
        JCheckBoxMenuItem box = ExchangeScript.findCheckbox(bar);
        assertFalse(box.isEnabled());
        assertFalse(box.isSelected());
        assertEquals(1, notices.size());
        assertTrue(notices.get(0).contains("relaunch"), "it was ticked, so its paint is on: " + notices.get(0));
        assertTrue(notices.get(0).contains(ExchangeScript.CLIENT_TAG));

        assertTrue(script.check());
        assertEquals(1, notices.size(), "one notice per session");

        // The script's setDarkMode(True) from a console ticks the box even
        // though it is disabled; the next check unticks it again.
        box.setSelected(true);
        assertTrue(script.check());
        assertFalse(box.isSelected());
        assertEquals(1, notices.size());
    }

    @Test
    @DisplayName("a project without the script, and no frame, is silent")
    void silentWithoutTheScript() {
        List<String> notices = new ArrayList<>();
        ExchangeScript script = new ExchangeScript(() -> null, () -> null, notices::add);
        assertFalse(script.check());
        assertTrue(notices.isEmpty());
        assertFalse(script.hasScriptModule());

        ExchangeScript withMenu = new ExchangeScript(() -> designerMenuBar(false, false), () -> new Object(), notices::add);
        assertFalse(withMenu.check());
        assertTrue(notices.isEmpty());
    }

    @Test
    @DisplayName("finds the script module through the project's getResource(ResourcePath), on either line")
    void findsTheScriptModuleByReflection() {
        // The resource classes live in a different package on 8.1 and 8.3, so
        // hasScriptModule builds the path from getResource's own parameter
        // type. The fake package stands in for both; the project class is
        // package-private, so the method must be invoked through the public
        // interface, and the resource-id overload must not be chosen.
        String script = ExchangeScript.SCRIPT_LIBRARY_MODULE + "/" + ExchangeScript.SCRIPT_LIBRARY_TYPE
            + "/" + ExchangeScript.SCRIPT_MODULE_PATH;
        Object withScript = com.mustrysolutions.designerdarkmode.designer.fakeresource.Project.holding(script);
        Object withoutScript = com.mustrysolutions.designerdarkmode.designer.fakeresource.Project.holding("ignition/script-python/other");

        assertTrue(new ExchangeScript(() -> null, () -> withScript, notice -> { }).hasScriptModule());
        assertFalse(new ExchangeScript(() -> null, () -> withoutScript, notice -> { }).hasScriptModule());
    }

    @Test
    @DisplayName("the messages name the script, the tag to remove and our own menu item")
    void messagesSayWhatToDo() {
        for (String message : new String[] {
                ExchangeScript.checkboxNotice(false), ExchangeScript.checkboxNotice(true),
                ExchangeScript.presenceNotice()}) {
            assertTrue(message.contains(ExchangeScript.SCRIPT_NAME), message);
            assertTrue(message.contains(ExchangeScript.CLIENT_TAG), message);
        }
        assertTrue(ExchangeScript.checkboxNotice(false).contains("Tools → Dark Mode"));
        assertFalse(ExchangeScript.checkboxNotice(false).contains("relaunch"));
        assertTrue(ExchangeScript.TOOLTIP.contains("Tools → Dark Mode"));
    }

}
