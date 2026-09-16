package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.function.Supplier;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;

import com.inductiveautomation.ignition.common.resourcecollection.ResourceCollection;
import com.inductiveautomation.ignition.common.resourcecollection.ResourcePath;
import com.inductiveautomation.ignition.common.script.ScriptConfig;

/**
 * Keeps this module and the Exchange's "Dark Mode for the Designer" script
 * from running over each other.
 *
 * <p>The Exchange resource (Justin Edwards, MIT) is a project-library script
 * plus a Vision client tag, so it lives inside a project rather than on the
 * gateway. Two seconds after such a project opens, the tag inserts a
 * {@code JCheckBoxMenuItem("Dark Mode")} at index 0 of the <b>View</b> menu,
 * and that checkbox <em>is</em> the script's state. Ticked, it paints several
 * hundred component classes with explicit, non-{@code UIResource} colours;
 * unticked, it paints the same components explicit white and black rather
 * than restoring anything. Neither reaches our light restore, which only
 * tracks what this module changed, and ticked on top of our dark theme the
 * script's light branch produces white panels with black text inside a dark
 * Designer. That looks like a module bug, and the cure (remove the project's
 * {@code designerPatch} client tag) is not something a user would guess.
 *
 * <p>So, the same shape as {@link VisionGate}: Tools → Dark Mode is refused
 * while the script's checkbox is ticked, a Designer that is already dark
 * drops back to the stock theme the moment the script's checkbox is ticked,
 * and a project that merely carries the script gets one warning that the two
 * should not be used together. The script's own paints are its state, not
 * ours, and are never undone here.
 *
 * <p>Detection is plain Swing: a {@code JMenu} titled View on the frame's
 * menu bar, holding a {@code JCheckBoxMenuItem} titled Dark Mode. The
 * script's presence is also read from the project, through the SDK's
 * {@code ResourceCollection}: a script module at
 * {@value #SCRIPT_MODULE_PATH} is the script whether or not its tag has
 * fired yet.
 */
class ExchangeScriptGate {

    /** The script module the Exchange project import creates. */
    static final String SCRIPT_MODULE_PATH = "designer/darkModePatch";

    /** The Vision client tag that adds the checkbox; removing it is the fix. */
    static final String CLIENT_TAG = "designerPatch";

    /** The menu the script inserts its checkbox into. */
    static final String VIEW_MENU_TEXT = "View";

    /** The checkbox's text, which the script itself uses as its identity. */
    static final String CHECKBOX_TEXT = "Dark Mode";

    static final String SCRIPT_NAME = "Dark Mode for the Designer";

    private final Supplier<Object> frame;
    private final Supplier<Object> project;

    /** Removes the item listener {@link #watchCheckbox} added, or {@code null}. */
    private Runnable unwatch;

    /** The gate over a Designer that is not there yet: nothing to check. */
    ExchangeScriptGate() {
        this(() -> null, () -> null);
    }

    /**
     * @param frame   the Designer's main frame, resolved on every use because
     *                it does not exist when the module starts up
     * @param project the open project, a {@code ResourceCollection}, resolved
     *                the same way
     */
    ExchangeScriptGate(Supplier<Object> frame, Supplier<Object> project) {
        this.frame = frame;
        this.project = project;
    }

    // --- the decision ------------------------------------------------------

    /**
     * Why dark mode must not be entered right now, or {@code null} when it may.
     *
     * <p>Only a <em>ticked</em> checkbox blocks: an unticked one has painted
     * nothing yet, and a user who keeps the script around but never ticks it
     * loses nothing by using the module.
     */
    String blockingReason() {
        JCheckBoxMenuItem checkbox = findCheckbox();
        if (checkbox != null && checkbox.isSelected()) {
            return "the Exchange script's View → Dark Mode is ticked";
        }
        return null;
    }

    /** True when the script is in this project, ticked or not. */
    boolean scriptPresent() {
        return findCheckbox() != null || hasScriptModule();
    }

    // --- messages ----------------------------------------------------------

    /** The status-bar line for a refused switch. */
    static String refusalMessage(String reason) {
        return "Dark mode was not applied: " + reason + ". The \"" + SCRIPT_NAME
            + "\" Exchange script and this module cannot be used together. Untick it, or "
            + removalHint();
    }

    /** The status-bar line after dark mode gave way to the script. */
    static String dropOutMessage(String reason) {
        return "Dark mode was turned off because " + reason + ". The \"" + SCRIPT_NAME
            + "\" Exchange script and this module cannot be used together: "
            + removalHint();
    }

    /** The one-time warning for a project that carries the script. */
    static String coexistenceWarning() {
        return "This project contains the \"" + SCRIPT_NAME + "\" Exchange script "
            + "(View → Dark Mode). Use one or the other: its light mode paints panels "
            + "white under this module's dark theme. To keep the module, "
            + removalHint();
    }

    private static String removalHint() {
        return "remove the project's \"" + CLIENT_TAG + "\" Vision client tag (the "
            + SCRIPT_MODULE_PATH.replace('/', '.') + " script library can stay).";
    }

    /**
     * Put the explanation in front of the user as a dialog. Never throws and
     * does nothing headless.
     */
    void explain(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            Object designer = frame.get();
            JOptionPane.showMessageDialog(
                designer instanceof Component ? (Component) designer : null,
                "<html><body style='width: 380px'>" + message + "</body></html>",
                "Designer Dark Mode",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Throwable t) {
            DebugLog.detail("Could not show the Exchange script notice.", t);
        }
    }

    // --- the checkbox watch --------------------------------------------------

    /**
     * Run {@code onScriptDarkOn} whenever the script's checkbox becomes ticked.
     *
     * <p>The callback runs on the event dispatch thread, from the checkbox's
     * own item event, which fires before the script's action listener has
     * painted anything. Leaving dark mode there hands the script a stock
     * Designer to paint, which is the only state it knows how to handle.
     *
     * @return true when a checkbox was found and is now watched
     */
    boolean watchCheckbox(Runnable onScriptDarkOn) {
        if (unwatch != null) {
            return true;
        }
        JCheckBoxMenuItem checkbox = findCheckbox();
        if (checkbox == null) {
            return false;
        }
        ItemListener listener = event -> {
            if (event.getStateChange() == ItemEvent.SELECTED) {
                onScriptDarkOn.run();
            }
        };
        checkbox.addItemListener(listener);
        unwatch = () -> checkbox.removeItemListener(listener);
        DebugLog.detail("Exchange script gate: watching the script's View → Dark Mode checkbox.");
        return true;
    }

    void unwatchCheckbox() {
        if (unwatch != null) {
            unwatch.run();
            unwatch = null;
        }
    }

    // --- detection -----------------------------------------------------------

    /** The script's checkbox on the Designer's menu bar, or {@code null}. */
    JCheckBoxMenuItem findCheckbox() {
        try {
            Object designer = frame.get();
            if (!(designer instanceof JFrame)) {
                return null;
            }
            return findCheckbox(((JFrame) designer).getJMenuBar());
        } catch (Throwable t) {
            DebugLog.detail("Exchange script gate: the menu bar is unavailable.", t);
            return null;
        }
    }

    /**
     * The script's checkbox in {@code menuBar}: a {@code JCheckBoxMenuItem}
     * titled Dark Mode directly under a menu titled View. The script inserts
     * it at index 0, but the whole View menu is scanned so that another
     * module's View entries do not hide it. Nothing outside View is looked
     * at: this module's own Dark Mode item lives under Tools.
     */
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
                if (item instanceof JCheckBoxMenuItem
                        && CHECKBOX_TEXT.equals(((JCheckBoxMenuItem) item).getText())) {
                    return (JCheckBoxMenuItem) item;
                }
            }
        }
        return null;
    }

    /** True when the open project (parents included) carries the script module. */
    boolean hasScriptModule() {
        try {
            Object open = project.get();
            if (!(open instanceof ResourceCollection)) {
                return false;
            }
            ResourcePath path = new ResourcePath(ScriptConfig.RESOURCE_TYPE, SCRIPT_MODULE_PATH);
            return ((ResourceCollection) open).getResource(path).isPresent();
        } catch (Throwable t) {
            DebugLog.detail("Exchange script gate: the project's resources are unavailable.", t);
            return false;
        }
    }
}
