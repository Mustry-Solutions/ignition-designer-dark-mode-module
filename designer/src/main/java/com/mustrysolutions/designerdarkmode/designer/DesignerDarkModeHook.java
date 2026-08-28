package com.mustrysolutions.designerdarkmode.designer;

import java.awt.event.ItemEvent;

import com.inductiveautomation.ignition.client.util.action.StateChangeAction;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.model.menu.JMenuMerge;
import com.inductiveautomation.ignition.designer.model.menu.MenuBarMerge;
import com.inductiveautomation.ignition.designer.model.menu.WellKnownMenuConstants;

/**
 * Designer-scope entry point for Designer Dark Mode. Registers the Tools menu
 * entries and applies the saved theme preference on startup.
 */
public class DesignerDarkModeHook extends AbstractDesignerModuleHook {

    private static final String BUNDLE_PREFIX = "designerdarkmode";

    private final ThemeManager themes = new ThemeManager();

    /**
     * The Tools menu action, kept so the checkmark can be corrected once a
     * switch has actually happened (#15). Rebuilding the menu replaces it;
     * the newest one is the one on screen.
     */
    private StateChangeAction darkModeAction;

    /**
     * True while we are correcting the checkmark ourselves.
     * {@code StateChangeAction} adds itself as its own {@code ItemListener} in
     * its constructor, so {@code setSelected(...)} calls
     * {@link StateChangeAction#itemStateChanged} — without this guard a
     * correction would read as a user click and toggle the theme straight back.
     */
    private boolean syncing;

    @Override
    public void startup(DesignerContext context, LicenseState activationState) {
        BundleUtil.get().addBundle(BUNDLE_PREFIX, getClass(), BUNDLE_PREFIX);
        themes.setThemeStateListener(new ThemeManager.ThemeStateListener() {
            @Override
            public void switchStarted() {
                // A switch blocks the event dispatch thread; queued clicks
                // would each schedule another full switch on the way out.
                setMenuEnabled(false);
            }

            @Override
            public void switchFinished(boolean darkActive) {
                setMenuEnabled(true);
                syncMenu(darkActive);
            }
        });
        themes.startup(context);
    }

    @Override
    public void shutdown() {
        themes.shutdown();
        BundleUtil.get().removeBundle(BUNDLE_PREFIX);
        DebugLog.close();
    }

    @Override
    public MenuBarMerge getModuleMenu() {
        StateChangeAction darkMode =
                new StateChangeAction("designerdarkmode.Action.DarkMode", new MoonIcon(16)) {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (syncing) {
                    return;
                }
                themes.setDark(e.getStateChange() == ItemEvent.SELECTED);
            }
        };
        darkMode.setSelected(themes.isDarkModeEnabled());
        darkModeAction = darkMode;

        MenuBarMerge merge = new MenuBarMerge("com.mustrysolutions.designerdarkmode");
        JMenuMerge tools =
            new JMenuMerge(WellKnownMenuConstants.TOOLS_MENU_NAME, "designerdarkmode.Menu.Tools");
        tools.addCheckBox(darkMode);
        // The Designer registers its built-in Tools menu under TOOLS_MENU_LOCATION,
        // and the merge matcher compares that group id along with the menu name —
        // any other group creates a duplicate top-level "Tools" menu.
        merge.add(WellKnownMenuConstants.TOOLS_MENU_LOCATION, tools);
        return merge;
    }

    /**
     * Point the checkmark at the theme that is actually in effect.
     *
     * <p>The item used to track the <em>request</em>: it was ticked the moment
     * you clicked, whether or not the switch then worked, and a switch that
     * failed left it permanently disagreeing with the Designer in front of you.
     */
    private void syncMenu(boolean darkActive) {
        StateChangeAction action = darkModeAction;
        if (action == null || action.isSelected() == darkActive) {
            return;
        }
        syncing = true;
        try {
            action.setSelected(darkActive);
        } finally {
            syncing = false;
        }
    }

    /** The menu item is bound to the action, so it follows the action's enabled state. */
    private void setMenuEnabled(boolean enabled) {
        StateChangeAction action = darkModeAction;
        if (action != null) {
            action.setEnabled(enabled);
        }
    }
}
