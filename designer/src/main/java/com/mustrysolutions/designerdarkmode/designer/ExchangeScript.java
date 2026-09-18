package com.mustrysolutions.designerdarkmode.designer;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollection;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.resourcecollection.ResourceType;

/**
 * Keeps the Exchange's "Dark Mode for the Designer" script from painting
 * over this module (#89).
 *
 * <p>The Exchange resource (Justin Edwards, MIT) is a project-library script
 * plus a Vision client tag, so it lives inside a project: a project that
 * imported it keeps running it in every Designer that opens the project,
 * whether or not this module is installed. Two seconds after such a project
 * opens, the tag inserts a {@code JCheckBoxMenuItem("Dark Mode")} at index 0
 * of the <b>View</b> menu, and that checkbox <em>is</em> the script's state.
 *
 * <p>What actually happens when both run, reproduced on 2026-09-18 with the
 * 1.3.0 script in the dev project (QA checklist §O):
 *
 * <ul>
 *   <li>Present but unticked, the script does nothing to a dark Designer.
 *       Its window monitor paints every newly opened window with explicit
 *       light colours, and the module's component watcher puts them right
 *       before they show.</li>
 *   <li>Ticked on top of the module's dark theme, it paints its own dark:
 *       black text fields, grey table cells, a mixed palette. Those colours
 *       are explicit, not {@code UIResource}s, so a toggle of Tools → Dark
 *       Mode off and on leaves them in place; only a relaunch clears
 *       them.</li>
 *   <li>Unticked again while the module is dark, its "light" branch paints
 *       the toolbar strip and every dock title bar white: a half-light
 *       Designer that looks like a module bug. That one a toggle off and on
 *       does clear, because the module's own passes hunt explicit whites.</li>
 *   <li>Its tree listener, installed by its dark paint, assumes every tree
 *       cell renderer is a {@code DefaultTreeCellRenderer} and throws inside
 *       {@code setCellRenderer} when the module wraps one; the tree-icon
 *       phase now contains that per tree.</li>
 * </ul>
 *
 * <p>So the trap is the checkbox, and the answer is to take it out of play:
 * when it appears it is unticked if ticked, disabled, given a tooltip that
 * says why, and the Designer says so once in the status bar and the log. The
 * module never refuses its own toggle for the script's sake, and never
 * touches the script's paints — a user who ticked the box before the module
 * saw it is told to relaunch. A project that carries the script module but
 * whose tag has not fired (no Vision, or a renamed tag) gets the notice
 * only; there is nothing to disable. Uninstalling the module gives the
 * checkbox back, since the disabling is done to the live menu item and
 * nothing is saved.
 *
 * <p>Detection is plain Swing: a {@code JMenu} titled View on the frame's
 * menu bar holding a {@code JCheckBoxMenuItem} titled Dark Mode — the
 * script's own identity test, by text. It runs on two timers after startup
 * (the tag fires at two seconds; the second timer covers a slow launch), on
 * every Tools → Dark Mode click, and the moment such an item is added to a
 * menu (an AWT container event), which also covers a tag imported
 * mid-session. The script's presence in the project is read through the
 * SDK's {@code ResourceCollection}: a script module at
 * {@value #SCRIPT_MODULE_PATH}.
 */
final class ExchangeScript {

    /**
     * The resource type of a project-library script. Not the SDK's
     * {@code ScriptConfig.RESOURCE_TYPE}, which is {@code ignition/event-scripts}
     * — checked live on 2026-09-18, where the lookup under that type found
     * nothing and the one under this type found the script.
     */
    static final String SCRIPT_LIBRARY_MODULE = "ignition";
    static final String SCRIPT_LIBRARY_TYPE = "script-python";

    /** The script module the Exchange project import creates. */
    static final String SCRIPT_MODULE_PATH = "designer/darkModePatch";

    /** The Vision client tag that adds the checkbox; removing it is the fix. */
    static final String CLIENT_TAG = "designerPatch";

    /** The menu the script inserts its checkbox into. */
    static final String VIEW_MENU_TEXT = "View";

    /** The checkbox's text, which the script itself uses as its identity. */
    static final String CHECKBOX_TEXT = "Dark Mode";

    static final String SCRIPT_NAME = "Dark Mode for the Designer";

    /** When to look after startup: the tag fires at two seconds. */
    static final int[] CHECK_DELAYS_MS = {3000, 12000};

    static final String TOOLTIP = "Disabled by the Designer Dark Mode module: use Tools → Dark Mode. "
        + "To use the \"" + SCRIPT_NAME + "\" script instead, uninstall the module.";

    private final Supplier<JMenuBar> menuBar;
    private final Supplier<Object> project;
    private final Consumer<String> notify;

    private final List<Timer> timers = new ArrayList<>();
    private AWTEventListener menuWatcher;
    private boolean noticed;

    /**
     * @param menuBar the Designer frame's menu bar, resolved on every use
     *                because the frame does not exist when the module starts
     * @param project the open project, a {@code ResourceCollection}, resolved
     *                the same way
     * @param notify  where the one notice goes (status bar and log)
     */
    ExchangeScript(Supplier<JMenuBar> menuBar, Supplier<Object> project, Consumer<String> notify) {
        this.menuBar = menuBar;
        this.project = project;
        this.notify = notify;
    }

    // --- lifecycle -----------------------------------------------------------

    /** Start the timers and the menu watch. Event dispatch thread. */
    void install() {
        for (int delay : CHECK_DELAYS_MS) {
            Timer timer = new Timer(delay, e -> check());
            timer.setRepeats(false);
            timer.start();
            timers.add(timer);
        }
        if (menuWatcher == null) {
            menuWatcher = event -> {
                if (event.getID() == ContainerEvent.COMPONENT_ADDED
                        && isTheCheckbox(((ContainerEvent) event).getChild())) {
                    // After the script has finished inserting it.
                    SwingUtilities.invokeLater(this::check);
                }
            };
            Toolkit.getDefaultToolkit().addAWTEventListener(menuWatcher, AWTEvent.CONTAINER_EVENT_MASK);
        }
    }

    void uninstall() {
        timers.forEach(Timer::stop);
        timers.clear();
        if (menuWatcher != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(menuWatcher);
            menuWatcher = null;
        }
    }

    // --- the check -----------------------------------------------------------

    /**
     * Look once: neutralise the checkbox if it is there, notice the script
     * if only its module is. Never throws.
     *
     * @return true when the checkbox was found (whether or not it was already
     *         neutralised)
     */
    boolean check() {
        try {
            JCheckBoxMenuItem checkbox = findCheckbox();
            if (checkbox != null) {
                // Enabled: the script just added it. Selected while disabled:
                // the script's setDarkMode(True) was called from a console,
                // which sets the box regardless. Either way, take it down.
                if (checkbox.isEnabled() || checkbox.isSelected()) {
                    boolean wasTicked = neutralise(checkbox);
                    notice(checkboxNotice(wasTicked));
                }
                return true;
            }
            if (hasScriptModule()) {
                notice(presenceNotice());
            }
            return false;
        } catch (Throwable t) {
            DebugLog.detail("ExchangeScript: the check failed; the script's checkbox may still be live.", t);
            return false;
        }
    }

    /**
     * Untick, disable and label the script's checkbox. Unticking through
     * {@code setSelected} fires no action event, so the script does not
     * paint its light branch in response.
     *
     * @return whether it was ticked, i.e. whether the script's dark paint is
     *         on the Designer now
     */
    static boolean neutralise(JCheckBoxMenuItem checkbox) {
        boolean wasTicked = checkbox.isSelected();
        if (wasTicked) {
            checkbox.setSelected(false);
        }
        checkbox.setEnabled(false);
        checkbox.setToolTipText(TOOLTIP);
        return wasTicked;
    }

    private void notice(String message) {
        if (noticed) {
            return;
        }
        noticed = true;
        notify.accept(message);
    }

    // --- messages ------------------------------------------------------------

    static String checkboxNotice(boolean wasTicked) {
        return "This project contains the \"" + SCRIPT_NAME + "\" Exchange script. Its View → Dark Mode "
            + "has been disabled; use Tools → Dark Mode. "
            + (wasTicked ? "It was on: relaunch the Designer to clear its colours. " : "")
            + removalHint();
    }

    static String presenceNotice() {
        return "This project contains the \"" + SCRIPT_NAME + "\" Exchange script (" + SCRIPT_MODULE_PATH
            + "). Do not turn on its View → Dark Mode alongside this module. " + removalHint();
    }

    private static String removalHint() {
        return "To silence this, remove the project's \"" + CLIENT_TAG + "\" Vision client tag; the "
            + SCRIPT_MODULE_PATH.replace('/', '.') + " script library can stay.";
    }

    // --- detection -----------------------------------------------------------

    JCheckBoxMenuItem findCheckbox() {
        return findCheckbox(menuBar.get());
    }

    static JCheckBoxMenuItem findCheckbox(JMenuBar menuBar) {
        if (menuBar == null) {
            return null;
        }
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu == null || !VIEW_MENU_TEXT.equals(menu.getText())) {
                continue;
            }
            for (Component item : menu.getMenuComponents()) {
                if (isTheCheckbox(item)) {
                    return (JCheckBoxMenuItem) item;
                }
            }
        }
        return null;
    }

    /** A "Dark Mode" check box menu item — in a View menu when we can tell. */
    static boolean isTheCheckbox(Component component) {
        if (!(component instanceof JCheckBoxMenuItem)
                || !CHECKBOX_TEXT.equals(((JCheckBoxMenuItem) component).getText())) {
            return false;
        }
        Component parent = component.getParent();
        if (parent instanceof JPopupMenu) {
            Component invoker = ((JPopupMenu) parent).getInvoker();
            return !(invoker instanceof JMenu) || VIEW_MENU_TEXT.equals(((JMenu) invoker).getText());
        }
        return true;
    }

    boolean hasScriptModule() {
        try {
            Object open = project.get();
            if (!(open instanceof ResourceCollection)) {
                return false;
            }
            // Built here, not in a static field: the unit tests run without
            // the platform jars, and the catch below is what keeps a missing
            // class from taking the whole check down.
            ResourcePath path = new ResourcePath(
                new ResourceType(SCRIPT_LIBRARY_MODULE, SCRIPT_LIBRARY_TYPE), SCRIPT_MODULE_PATH);
            return ((ResourceCollection) open).getResource(path).isPresent();
        } catch (Throwable t) {
            DebugLog.detail("ExchangeScript: the project's resources are unavailable.", t);
            return false;
        }
    }
}
