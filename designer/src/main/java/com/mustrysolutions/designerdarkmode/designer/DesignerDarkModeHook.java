package com.mustrysolutions.designerdarkmode.designer;

import java.awt.event.ItemEvent;
import java.util.EnumMap;
import java.util.Map;

import com.inductiveautomation.ignition.client.util.action.BaseAction;
import com.inductiveautomation.ignition.client.util.action.StateChangeAction;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializer;
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

    private DesignerContext context;

    /**
     * The Tools → Dark Mode actions, one per mode, kept so the selection can
     * be corrected once a switch has actually happened (#15). Built once,
     * with the menu.
     */
    private final Map<ThemeMode, StateChangeAction> modeActions = new EnumMap<>(ThemeMode.class);

    /**
     * True while we are correcting the selection ourselves.
     * {@code StateChangeAction} adds itself as its own {@code ItemListener} in
     * its constructor, so {@code setSelected(...)} calls
     * {@link StateChangeAction#itemStateChanged} — without this guard a
     * correction would read as a user click and switch the theme straight back.
     */
    private boolean syncing;

    @Override
    public void startup(DesignerContext context, LicenseState activationState) {
        this.context = context;
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
                // The mode, not darkActive: Match System stays selected
                // whichever theme it resolved to. A switch that failed has
                // already moved the mode to the theme that is installed.
                syncMenu(themes.getThemeMode());
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

    /**
     * Every save builds a fresh serializer and offers it to each module here.
     * A restyled design token that Vision copied into a component is written
     * with its stock colour (#92, part 2) — see {@link TokenColorDelegate} —
     * and a FlatLaf border is never written at all, as a Synthetica border
     * never is — see {@link LookAndFeelBorders}.
     */
    @Override
    public void configureSerializer(XMLSerializer serializer) {
        TokenColorDelegate.register(serializer, themes.stockTokenRgb());
        LookAndFeelBorders.register(serializer);
    }

    /**
     * Built once and handed back on every call. The Designer does not keep the
     * merge it installs: {@code LoadedModule} installs
     * {@code hook.getModuleMenu()} at startup and, on shutdown (every project
     * switch, and exit), uninstalls {@code hook.getModuleMenu()} — a second
     * call. The SDK's item merges remove only the {@code JMenuItem} their own
     * {@code install} created, so a freshly built merge removed nothing, and
     * each project opened in a session added another Dark Mode and About pair
     * to Tools. The next project gets a new hook, and with it a new merge.
     */
    private MenuBarMerge moduleMenu;

    @Override
    public MenuBarMerge getModuleMenu() {
        if (moduleMenu == null) {
            moduleMenu = buildModuleMenu();
        }
        return moduleMenu;
    }

    private MenuBarMerge buildModuleMenu() {
        for (ThemeMode mode : ThemeMode.values()) {
            modeActions.put(mode, modeAction(mode));
        }
        // Seeded under the guard: StateChangeAction fires itemStateChanged
        // from setSelected. Unguarded, selecting the saved mode in the fresh
        // menu would read as a click and run a full theme switch before the
        // menu is even on screen. (Until the merge was cached, this ran on
        // every getModuleMenu call, including the one LoadedModule.shutdown
        // makes on the way out; until 0.4.0 that also raised the Vision
        // refusal dialog and lost the preference.)
        syncing = true;
        try {
            modeActions.get(themes.getThemeMode()).setSelected(true);
        } finally {
            syncing = false;
        }

        MenuBarMerge merge = new MenuBarMerge("com.mustrysolutions.designerdarkmode");
        JMenuMerge tools =
            new JMenuMerge(WellKnownMenuConstants.TOOLS_MENU_NAME, "designerdarkmode.Menu.Tools");
        // A submenu of three radio items: Light, Dark, and Match System.
        // Light comes first and is the default — a Designer that has never
        // been told otherwise stays stock.
        JMenuMerge darkMode = new JMenuMerge("designerdarkmode.DarkMode",
            "designerdarkmode.Menu.DarkMode", new MoonIcon(16));
        darkMode.addRadioGroup(modeActions.get(ThemeMode.LIGHT),
            modeActions.get(ThemeMode.DARK), modeActions.get(ThemeMode.SYSTEM));
        tools.add(darkMode);
        tools.add(BaseAction.create("designerdarkmode.Action.About", null,
            () -> AboutDialog.show(context != null ? context.getFrame() : null)));
        // The Designer registers its built-in Tools menu under TOOLS_MENU_LOCATION,
        // and the merge matcher compares that group id along with the menu name —
        // any other group creates a duplicate top-level "Tools" menu.
        merge.add(WellKnownMenuConstants.TOOLS_MENU_LOCATION, tools);
        return merge;
    }

    /**
     * One radio item's action. Only the item being selected acts: the
     * group deselects the previous one, and that event is not a choice.
     */
    private StateChangeAction modeAction(ThemeMode mode) {
        String key = "designerdarkmode.Action.DarkMode."
            + (mode == ThemeMode.SYSTEM ? "System" : mode == ThemeMode.DARK ? "Dark" : "Light");
        return new StateChangeAction(key, null) {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (syncing || e.getStateChange() != ItemEvent.SELECTED) {
                    return;
                }
                themes.setThemeMode(mode);
            }
        };
    }

    /**
     * Point the selection at the mode that is actually in effect.
     *
     * <p>The item used to track the <em>request</em>: it was ticked the moment
     * you clicked, whether or not the switch then worked, and a switch that
     * failed left it permanently disagreeing with the Designer in front of you.
     *
     * <p>The target is selected first. Once the menu is built, its radio
     * group deselects the others and their actions follow; a group never lets
     * its selected item be deselected directly, so clearing first would leave
     * the action and its item disagreeing. Before the menu is built there is
     * no group, so the others are cleared here.
     */
    private void syncMenu(ThemeMode mode) {
        StateChangeAction target = modeActions.get(mode);
        if (target == null) {
            return;
        }
        syncing = true;
        try {
            target.setSelected(true);
            for (StateChangeAction action : modeActions.values()) {
                if (action != target && action.isSelected()) {
                    action.setSelected(false);
                }
            }
        } finally {
            syncing = false;
        }
    }

    /** Each menu item is bound to its action, so it follows the action's enabled state. */
    private void setMenuEnabled(boolean enabled) {
        for (StateChangeAction action : modeActions.values()) {
            action.setEnabled(enabled);
        }
    }
}
