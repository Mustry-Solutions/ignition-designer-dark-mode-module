package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;

import com.inductiveautomation.ignition.client.util.action.BaseAction;
import com.inductiveautomation.ignition.designer.model.menu.JMenuMerge;
import com.inductiveautomation.ignition.designer.model.menu.MenuBarMerge;
import com.inductiveautomation.ignition.designer.model.menu.WellKnownMenuConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Opening another project must not leave a second copy of our Tools items.
 *
 * <p>The Designer never keeps the merge it installed. {@code LoadedModule}
 * installs {@code hook.getModuleMenu()} at startup, and at shutdown (every
 * project switch, and the Designer's own exit) it removes
 * {@code hook.getModuleMenu()} — calling the method again. The SDK's
 * {@code ActionMenuItemMerge.uninstall} removes the {@code JMenuItem} that
 * <em>its own</em> {@code install} created, so a merge built fresh for the
 * uninstall removes nothing, and the next project's hook adds its items next
 * to the stale ones. Found on an 8.1 Designer after File → Open; the
 * {@code LoadedModule} and merge classes are the same on 8.3.
 *
 * <p>Driven through the real SDK merge classes, in the order
 * {@code LoadedModule} uses: a hook installs, is shut down, and a NEW hook
 * (the Designer instantiates one per project) installs again.
 */
class ModuleMenuReinstallTest {

    private JMenuBar menuBar;

    @BeforeEach
    void designerMenuBarWithAToolsMenu() throws Exception {
        DesignerLookAndFeel.installStock();
        SwingUtilities.invokeAndWait(() -> {
            // The Designer builds its own Tools menu through the same merge
            // model, at TOOLS_MENU_LOCATION; a module's items only land in it
            // when both name and group match (see getModuleMenu).
            menuBar = new JMenuBar();
            MenuBarMerge designer = new MenuBarMerge("designer");
            JMenuMerge tools = new JMenuMerge(WellKnownMenuConstants.TOOLS_MENU_NAME, "Tools");
            tools.add(BaseAction.create("Console", null, () -> { }));
            designer.add(WellKnownMenuConstants.TOOLS_MENU_LOCATION, tools);
            designer.install(menuBar);
        });
    }

    @Test
    @DisplayName("shutdown removes exactly what startup installed")
    void uninstallRemovesTheInstalledItems() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DesignerDarkModeHook hook = new DesignerDarkModeHook();
            hook.getModuleMenu().install(menuBar);
            assertEquals(2, ourItems(), "startup adds Dark Mode and About");

            hook.getModuleMenu().uninstall(menuBar);
            assertEquals(0, ourItems(), "shutdown leaves none of ours behind");
        });
    }

    @Test
    @DisplayName("a project switch leaves one Dark Mode and one About")
    void projectSwitchDoesNotDuplicate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DesignerDarkModeHook first = new DesignerDarkModeHook();
            first.getModuleMenu().install(menuBar);
            first.getModuleMenu().uninstall(menuBar);

            DesignerDarkModeHook second = new DesignerDarkModeHook();
            second.getModuleMenu().install(menuBar);
            assertEquals(2, ourItems(), "the second project's Tools menu");
        });
    }

    /**
     * Tools → Dark Mode is a submenu of three radio items, exactly one of
     * them selected. Which one depends on the preference of whoever runs the
     * build, so only the count is asserted; nothing here clicks an item,
     * which would write that preference.
     */
    @Test
    @DisplayName("Dark Mode is a submenu with Light, Dark and Match System, one selected")
    void darkModeIsASubmenuOfThreeModes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DesignerDarkModeHook hook = new DesignerDarkModeHook();
            hook.getModuleMenu().install(menuBar);

            JMenu darkMode = null;
            for (Component component : toolsMenu().getMenuComponents()) {
                if (component instanceof JMenu
                        && ((JMenu) component).getText().contains("designerdarkmode.Menu.DarkMode")) {
                    darkMode = (JMenu) component;
                }
            }
            assertNotNull(darkMode, "Tools carries the Dark Mode submenu");
            assertNotNull(darkMode.getIcon(), "with the moon icon the checkbox had");

            int radios = 0;
            int selected = 0;
            for (Component component : darkMode.getMenuComponents()) {
                if (component instanceof JRadioButtonMenuItem) {
                    radios++;
                    if (((JRadioButtonMenuItem) component).isSelected()) {
                        selected++;
                    }
                }
            }
            assertEquals(3, radios, "Light, Dark and Match System");
            assertEquals(1, selected, "the saved mode, and only it");

            hook.getModuleMenu().uninstall(menuBar);
            assertEquals(0, ourItems(), "the submenu goes with the module");
        });
    }

    /**
     * Each merge into Tools re-sorts every item already there by its
     * merge's menu id, read from a client property. A submenu that came from
     * a nested merge has no such property and sorted with the Designer's own
     * items, so once Vision merged after us the live Tools menu read
     * Translation Manager, Dark Mode, Symbol Factory, Keyboard Layouts, About.
     */
    @Test
    @DisplayName("Dark Mode and About stay together when another module merges into Tools after us")
    void ourItemsStayTogetherAfterALaterMerge() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DesignerDarkModeHook hook = new DesignerDarkModeHook();
            hook.getModuleMenu().install(menuBar);

            MenuBarMerge vision = new MenuBarMerge("com.inductiveautomation.vision");
            JMenuMerge tools = new JMenuMerge(WellKnownMenuConstants.TOOLS_MENU_NAME, "Tools");
            tools.add(BaseAction.create("Symbol Factory", null, () -> { }));
            vision.add(WellKnownMenuConstants.TOOLS_MENU_LOCATION, tools);
            vision.install(menuBar);

            List<String> order = new ArrayList<>();
            for (Component component : toolsMenu().getMenuComponents()) {
                if (component instanceof JMenuItem) {
                    order.add(((JMenuItem) component).getText());
                }
            }
            int darkMode = indexContaining(order, "designerdarkmode.Menu.DarkMode");
            int about = indexContaining(order, "designerdarkmode.Action.About");
            assertEquals(darkMode + 1, about, "Dark Mode directly above About: " + order);
            assertTrue(darkMode > indexContaining(order, "Symbol Factory"),
                "both after the other module's items, as the checkbox was: " + order);
        });
    }

    private static int indexContaining(List<String> texts, String key) {
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i) != null && texts.get(i).contains(key)) {
                return i;
            }
        }
        throw new AssertionError("no item for " + key + " in " + texts);
    }

    /**
     * Our items in the Tools menu. No resource bundle is registered here, so
     * each action's name is still its {@code designerdarkmode.} bundle key.
     */
    private int ourItems() {
        int count = 0;
        for (Component component : toolsMenu().getMenuComponents()) {
            if (component instanceof JMenuItem) {
                Action action = ((JMenuItem) component).getAction();
                String name = action == null ? null : String.valueOf(action.getValue(Action.NAME));
                String text = ((JMenuItem) component).getText();
                if ((name != null && name.contains("designerdarkmode."))
                        || (text != null && text.contains("designerdarkmode."))) {
                    count++;
                }
            }
        }
        return count;
    }

    /** The one Tools menu: a merge that missed it would have added a second. */
    private JMenu toolsMenu() {
        JMenu found = null;
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && WellKnownMenuConstants.TOOLS_MENU_NAME.equals(menu.getName())) {
                if (found != null) {
                    throw new AssertionError("a second Tools menu on the bar");
                }
                found = menu;
            }
        }
        if (found == null) {
            throw new AssertionError("no Tools menu on the bar");
        }
        return found;
    }
}
