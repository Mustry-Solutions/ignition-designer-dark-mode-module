package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JOptionPane;

/**
 * Keeps dark mode and Vision resources apart.
 *
 * <p>A Vision window is saved by comparing every component property against a
 * "clean copy" of its class, and the platform caches that clean copy in a
 * static map for the life of the Designer, built under whatever look and feel
 * was installed the first time the class was saved. Once FlatLaf has been in
 * and out, the live components and the cached copy disagree, and the save
 * writes the difference into the window: FlatLaf's font, its colours, and its
 * border classes by name. A Vision client has no FlatLaf, so a window saved
 * that way fails to open at all
 * ({@code ClassNotFoundException: com.formdev.flatlaf.ui.FlatButtonBorder}).
 * That was reproduced headlessly against the real Vision jars before this
 * class was written; the mechanism is in the platform, not in this module,
 * and no amount of restyling on our side reaches it.
 *
 * <p>So the module does not try. Dark mode is refused while any Vision window
 * or template is open, or while the Vision workspace is the selected one, and
 * a Designer that is already dark drops back to the stock theme the moment
 * the user navigates to Vision — <em>synchronously</em>, from the workspace
 * manager's own navigation listener, so it runs before the window a double
 * click is about to open gets deserialized under FlatLaf. A window that
 * reaches the component tree under dark mode anyway (opened by some path that
 * did not select the workspace first) is caught as it is attached, and the
 * user is told to close and reopen it.
 *
 * <p>Everything Vision-side is reached by class name: neither Vision nor the
 * Designer's {@code WorkspaceManager} is SDK surface, and a Designer without
 * Vision installed must lose this gate, not the module.
 */
class VisionGate {

    /** Implemented by Vision windows AND templates — the two things a save serializes. */
    static final String TOP_LEVEL_CONTAINER =
        "com.inductiveautomation.vision.api.client.components.model.TopLevelContainer";

    /** The Designer's workspace switcher; {@code IgnitionDesigner.getWorkspace()} returns it. */
    static final String WORKSPACE_MANAGER =
        "com.inductiveautomation.ignition.designer.WorkspaceManager";

    /** Told, synchronously, when a workspace is selected. */
    static final String NAVIGATION_LISTENER =
        WORKSPACE_MANAGER + "$WorkspaceNavigationListener";

    /** {@code WindowWorkspace.getKey()} — the workspace both windows and templates open in. */
    static final String VISION_WORKSPACE_KEY = "windows";

    /**
     * How far below an attached component to look for a Vision window.
     *
     * <p>The window or template is normally the attached component itself
     * (an {@code FPMIWindow} goes straight onto the desktop pane, a template
     * into its holder); the margin covers a wrapper or two, such as a scroll
     * pane's viewport and view. Bounded because this runs for every container
     * attached anywhere in the Designer while dark mode is on.
     */
    static final int ATTACH_SEARCH_DEPTH = 4;

    private final Supplier<Object> frame;

    /** Removes the navigation listener {@link #watchNavigation} added, or {@code null}. */
    private Runnable unwatch;

    /** The gate over a frame that is not there yet: nothing to check, nothing to watch. */
    VisionGate() {
        this(() -> null);
    }

    /**
     * @param frame the Designer's main frame, resolved on every use because
     *              it does not exist when the module starts up.
     */
    VisionGate(Supplier<Object> frame) {
        this.frame = frame;
    }

    // --- the decision ------------------------------------------------------

    /**
     * Why dark mode must not be entered right now, or {@code null} when it may.
     *
     * <p>Open windows are checked before the selected workspace: a window left
     * open in a hidden Vision workspace is still in the component tree, and
     * the switch's tree update would still reach it.
     */
    String blockingReason() {
        for (Container root : roots()) {
            if (findVisionTopLevel(root, Integer.MAX_VALUE) != null) {
                return "a Vision window or template is open";
            }
        }
        if (VISION_WORKSPACE_KEY.equalsIgnoreCase(selectedWorkspaceKey())) {
            return "the Vision workspace is selected";
        }
        return null;
    }

    /** The containers a Vision window could be attached under. */
    List<Container> roots() {
        List<Container> roots = new ArrayList<>();
        Object designer = frame.get();
        if (designer instanceof Container) {
            roots.add((Container) designer);
        }
        for (Window window : Window.getWindows()) {
            if (window != designer) {
                roots.add(window);
            }
        }
        return roots;
    }

    // --- messages ----------------------------------------------------------

    /** The status-bar line for a refused switch. */
    static String refusalMessage(String reason) {
        return "Dark mode was not applied: " + reason + ". "
            + "Vision resources saved under dark mode pick up FlatLaf fonts, colours and "
            + "borders that Vision clients cannot load.";
    }

    /** The status-bar line after dark mode gave way to Vision. */
    static String dropOutMessage(String reason, boolean windowAlreadyOpen) {
        return "Dark mode was turned off because " + reason + ". "
            + "Vision resources cannot be edited safely under dark mode."
            + (windowAlreadyOpen
                ? " Close and reopen the window before editing it."
                : "");
    }

    /**
     * Put the explanation in front of the user as a dialog.
     *
     * <p>A status-bar line is easy to miss, and a Designer that has just
     * changed theme on its own, or refused a click, owes the user a reason.
     * Never throws and does nothing headless.
     */
    void explain(String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            Object designer = frame.get();
            JOptionPane.showMessageDialog(
                designer instanceof Component ? (Component) designer : null,
                "<html><body style='width: 380px'>" + message
                    + "<br><br>Close the Vision windows and leave the Vision workspace "
                    + "before turning dark mode on again.</body></html>",
                "Designer Dark Mode",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Throwable t) {
            DebugLog.detail("Could not show the Vision notice.", t);
        }
    }

    // --- the navigation watch ----------------------------------------------

    /**
     * Run {@code onVisionActivated} whenever the Vision workspace is selected.
     *
     * <p>The callback runs on the event dispatch thread, inside the workspace
     * manager's own selection, which is the point: selecting a Vision window
     * in the project browser activates the workspace on the first click, and
     * the window itself is deserialized on the second. Leaving dark mode in
     * between is what keeps FlatLaf out of the window.
     */
    void watchNavigation(Runnable onVisionActivated) {
        if (unwatch != null) {
            return;
        }
        try {
            Object manager = workspaceManager();
            if (manager == null) {
                DebugLog.log("Vision gate: no workspace manager on the Designer frame; "
                    + "dark mode will only leave Vision when a window is attached.");
                return;
            }
            Class<?> listenerType = Class.forName(NAVIGATION_LISTENER, true,
                manager.getClass().getClassLoader());
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "workspaceActivated":
                        if (args != null && args.length == 1
                                && VISION_WORKSPACE_KEY.equalsIgnoreCase(String.valueOf(args[0]))) {
                            onVisionActivated.run();
                        }
                        return null;
                    case "workspaceDeactivated":
                        return null;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "VisionGate navigation listener";
                    default:
                        return null;
                }
            };
            Object listener = Proxy.newProxyInstance(
                listenerType.getClassLoader(), new Class<?>[] {listenerType}, handler);
            manager.getClass().getMethod("addNavigationListener", listenerType)
                .invoke(manager, listener);
            Method remove = manager.getClass().getMethod("removeNavigationListener", listenerType);
            unwatch = () -> {
                try {
                    remove.invoke(manager, listener);
                } catch (Exception e) {
                    DebugLog.detail("Vision gate: could not remove the navigation listener.", e);
                }
            };
            DebugLog.detail("Vision gate: watching workspace navigation.");
        } catch (Throwable t) {
            DebugLog.log("Vision gate: could not watch workspace navigation; "
                + "dark mode will only leave Vision when a window is attached.", t);
        }
    }

    void unwatchNavigation() {
        if (unwatch != null) {
            unwatch.run();
            unwatch = null;
        }
    }

    // --- reflection over the Designer ---------------------------------------

    /** The key of the workspace on screen, or {@code null} when it cannot be read. */
    String selectedWorkspaceKey() {
        try {
            Object manager = workspaceManager();
            if (manager == null) {
                return null;
            }
            Object workspace = manager.getClass().getMethod("getSelectedWorkspace").invoke(manager);
            if (workspace == null) {
                return null;
            }
            Method getKey = workspace.getClass().getMethod("getKey");
            getKey.setAccessible(true);
            Object key = getKey.invoke(workspace);
            return key == null ? null : key.toString();
        } catch (Throwable t) {
            DebugLog.detail("Vision gate: the selected workspace is unavailable.", t);
            return null;
        }
    }

    private Object workspaceManager() throws Exception {
        Object designer = frame.get();
        if (designer == null) {
            return null;
        }
        Method getWorkspace = designer.getClass().getMethod("getWorkspace");
        getWorkspace.setAccessible(true);
        Object manager = getWorkspace.invoke(designer);
        return manager != null && ClassNames.extendsNamed(manager.getClass(), WORKSPACE_MANAGER)
            ? manager : null;
    }

    // --- detection -----------------------------------------------------------

    /** True for a Vision window or template: anything Vision serializes as a resource. */
    static boolean isVisionTopLevel(Component component) {
        return component != null
            && ClassNames.implementsNamed(component.getClass(), TOP_LEVEL_CONTAINER);
    }

    /**
     * The first Vision window or template at or under {@code root}, looking at
     * most {@code maxDepth} levels down, or {@code null}. Does not descend into
     * one. Bounded for the attach-time check, which runs for every container
     * attached under dark mode; unbounded for a blocking check over the whole
     * Designer.
     */
    static Component findVisionTopLevel(Component root, int maxDepth) {
        if (isVisionTopLevel(root)) {
            return root;
        }
        if (maxDepth <= 0 || !(root instanceof Container)) {
            return null;
        }
        for (Component child : ((Container) root).getComponents()) {
            Component found = findVisionTopLevel(child, maxDepth - 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

}
