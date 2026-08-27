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

    @Override
    public void startup(DesignerContext context, LicenseState activationState) {
        BundleUtil.get().addBundle(BUNDLE_PREFIX, getClass(), BUNDLE_PREFIX);
        themes.startup(context);
    }

    @Override
    public void shutdown() {
        themes.shutdown();
        BundleUtil.get().removeBundle(BUNDLE_PREFIX);
    }

    @Override
    public MenuBarMerge getModuleMenu() {
        StateChangeAction darkMode =
                new StateChangeAction("designerdarkmode.Action.DarkMode", new MoonIcon(16)) {
            @Override
            public void itemStateChanged(ItemEvent e) {
                themes.setDark(e.getStateChange() == ItemEvent.SELECTED);
            }
        };
        darkMode.setSelected(themes.isDarkModeEnabled());

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
}
