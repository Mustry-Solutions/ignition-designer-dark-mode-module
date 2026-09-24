package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
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
