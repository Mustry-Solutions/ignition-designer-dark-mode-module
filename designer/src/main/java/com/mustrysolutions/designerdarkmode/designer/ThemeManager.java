package com.mustrysolutions.designerdarkmode.designer;

import java.awt.AWTEvent;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.lang.reflect.Field;
import java.util.prefs.Preferences;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies and persists the Designer theme choice.
 *
 * The Designer's stock look and feel is Synthetica-based
 * (IgnitionLookAndFeel$LaF) and the Designer calls
 * SyntheticaLookAndFeel.getInstance() at runtime (e.g. for UI scaling) even
 * when another look and feel is active. Installing FlatLaf triggers
 * Synthetica's uninitialize(), which nulls that singleton and turns every such
 * call into an NPE — so after switching to dark we reflectively re-point the
 * singleton at the stock instance to keep those calls working.
 *
 * Restoring the stock theme must go through Synthetica's own entry point
 * (mirroring the Designer's startup), and the JIDE docking framework needs
 * LookAndFeelFactory.installJideExtension() after every switch. All of these
 * classes exist only at Designer runtime, so they are invoked reflectively.
 *
 * The saved preference is applied only once the main Designer window is
 * showing: applying during startup breaks Synthetica-dependent init code and
 * can prevent the Designer from opening at all.
 */
public class ThemeManager {

    private static final String PREF_DARK_MODE = "darkMode";
    private static final String STOCK_LAF_CLASS =
        "com.inductiveautomation.ignition.client.IgnitionLookAndFeel$LaF";
    private static final String SYNTHETICA_LAF =
        "de.javasoft.plaf.synthetica.SyntheticaLookAndFeel";
    private static final String JIDE_LAF_FACTORY = "com.jidesoft.plaf.LookAndFeelFactory";

    /** Give up waiting for the main window after ~2 minutes (250ms ticks). */
    /** 250ms per poll. A silent two-minute wait was far worse than applying early. */
    private static final int MAX_STARTUP_POLLS = 120;

    private final Logger log = LoggerFactory.getLogger(ThemeManager.class);
    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
    private final TreeIconRecolorer treeIcons = new TreeIconRecolorer();
    private final IaColorTokens tokens = new IaColorTokens();
    private final CellRendererSanitizer cellRenderers = new CellRendererSanitizer();

    private DesignerContext context;
    private LookAndFeel stockLaf;

    private final DesignerStatus status = new DesignerStatus();

    /**
     * Told when a switch starts and when it ends, so the Tools menu can follow
     * the theme actually in effect rather than the one that was asked for.
     */
    public interface ThemeStateListener {

        /** A switch has started; the menu item should not take another click. */
        void switchStarted();

        /** A switch has ended; {@code darkActive} is the theme now installed. */
        void switchFinished(boolean darkActive);
    }

    private ThemeStateListener stateListener = new ThemeStateListener() {
        @Override
        public void switchStarted() {
        }

        @Override
        public void switchFinished(boolean darkActive) {
        }
    };

    /**
     * Phases that failed during the current {@link #apply(boolean)}, and how
     * many were attempted — the material for the one-line summary a user gets
     * when the switch only partly worked.
     */
    private final java.util.List<String> failedPhases = new java.util.ArrayList<>();
    private int attemptedPhases;

    /**
     * Every phase of the last switch, in the order it ran.
     *
     * <p>Ordering is not a detail here, it is where the bugs are. #23 was a
     * correct set of steps in the wrong sequence: the FlatLaf overrides were
     * cleared after the stock look and feel had come back, and since the clear
     * is a delete rather than a revert it took the stock values with it. The
     * result on screen — an amber property editor — is several inferential
     * steps away from the cause, and nothing asserted at the time could see
     * the order.
     *
     * <p>So the order is recorded and asserted directly. Kept even though the
     * headless harness also checks the CONSEQUENCE (every default back to its
     * stock value): the consequence check says something regressed, this says
     * which step moved.
     */
    private final java.util.List<String> phaseTrace = new java.util.ArrayList<>();

    /**
     * Theme changes are only allowed once the main Designer window is fully
     * built. The Tools menu checkbox calls setDark() during module startup
     * (setSelected fires itemStateChanged), which used to apply FlatLaf to a
     * half-built Designer and leave the first launch as an unreadable dark
     * screen — while the later ready-time apply no-oped because dark was
     * already active.
     */
    private boolean uiReady = false;

    private final ComponentInspector inspector = new ComponentInspector();
    private final ScriptEditorTheme scriptEditors = new ScriptEditorTheme();
    private final ConsoleTextTheme consoleText = new ConsoleTextTheme();
    private final BlockWorkspaceTheme blockWorkspaces = new BlockWorkspaceTheme();

    /** Register the menu's listener before {@link #startup}. */
    public void setThemeStateListener(ThemeStateListener listener) {
        this.stateListener = listener;
    }

    /** Called once on Designer startup; re-applies dark mode once the UI is up. */
    public void startup(DesignerContext context) {
        this.context = context;
        status.attach(context);
        // Must be set before FlatLaf ever initializes: with user scaling on,
        // FlatLaf registers a permanent UIScale listener on the UI defaults
        // that NPEs (null defaultFont) when Synthetica's uninitialize fires
        // during a later light->dark switch, aborting the switch. macOS is
        // system-scaled, so user scaling adds nothing here.
        System.setProperty("flatlaf.uiScale.enabled", "false");
        onEdt(() -> {
            captureStockLaf();
            inspector.install();
            applyWhenDesignerVisible();
        });
    }

    /** Called on module shutdown; puts the Designer back the way we found it. */
    public void shutdown() {
        onEdt(() -> {
            inspector.uninstall();
            apply(false);
        });
    }

    public boolean isDarkModeEnabled() {
        return prefs.getBoolean(PREF_DARK_MODE, false);
    }

    public void setDark(boolean dark) {
        prefs.putBoolean(PREF_DARK_MODE, dark);
        onEdt(() -> {
            if (!uiReady) {
                DebugLog.detail("setDark(" + dark + ") before the UI is ready; "
                    + "deferred to the startup apply.");
                return;
            }
            beginSwitch(dark);
        });
    }

    /**
     * Start a switch, one event-queue turn late.
     *
     * <p>{@link #apply} blocks the event dispatch thread for as long as the
     * switch takes — seconds, on a Designer with several windows open — and
     * the click that asked for it has not finished being processed yet.
     * Deferring one turn lets the menu close and the "applying" message paint
     * first, so the Designer looks busy instead of hung. Without it the user
     * sees a frozen, still-light Designer with the box already ticked, and
     * reasonably concludes the click did not register.
     */
    private void beginSwitch(boolean dark) {
        stateListener.switchStarted();
        status.message(dark
            ? "Applying dark mode\u2026"
            : "Restoring the stock Designer theme\u2026");
        SwingUtilities.invokeLater(() -> {
            try {
                apply(dark);
            } finally {
                finishSwitch();
            }
        });
    }

    /**
     * Reconcile what the user can see with the theme that is actually
     * installed. The preference follows reality rather than the request: a
     * switch that failed must not be retried at every launch, and the Tools
     * menu must not carry a checkmark for a theme the Designer is not in.
     */
    private void finishSwitch() {
        boolean darkActive = isDarkActive();
        prefs.putBoolean(PREF_DARK_MODE, darkActive);
        stateListener.switchFinished(darkActive);
    }

    private static boolean isDarkActive() {
        return UIManager.getLookAndFeel() instanceof FlatDarkLaf;
    }

    /**
     * Defer the saved dark preference until the main Designer window is
     * actually showing. Applying earlier — during module startup — swaps the
     * look and feel out from under the Designer's own initialization and kills
     * the launch (NPEs from Synthetica statics).
     */
    private void applyWhenDesignerVisible() {
        Timer timer = new Timer(250, null);
        final int[] polls = {0};
        final int[] readyTicks = {0};
        timer.addActionListener(e -> {
            Frame frame = context.getFrame();
            // "Showing" is not enough: at that point the docked panels are
            // still being built, and theming a half-built UI leaves it
            // blank/mistyled. Wait until the dock panels exist and the UI has
            // been stable for a few ticks.
            //
            // Readiness is measured in DOCKABLE FRAMES, not trees. Counting
            // trees made this depend on which workspace the Designer happened
            // to restore: open on Sequential Function Charts and only the
            // Project Browser has one (the Tag Browser shows a table), so the
            // count never reached 2, the probe never succeeded, and dark mode
            // arrived via the timeout — two minutes after launch, looking for
            // all the world like the module had failed. Dock panels are
            // present in every workspace.
            int docks = frame instanceof java.awt.Container
                ? countDockableFrames((java.awt.Container) frame) : 0;
            int trees = frame instanceof java.awt.Container
                ? TreeIconRecolorer.countTrees((java.awt.Container) frame) : 0;
            boolean ready = frame != null && frame.isShowing() && (docks >= 2 || trees >= 2);
            if (ready) {
                if (++readyTicks[0] >= 4) {
                    timer.stop();
                    DebugLog.detail("Startup apply: designer UI ready after "
                        + polls[0] + " polls (" + docks + " dock frame(s), "
                        + trees + " tree(s)).");
                    uiReady = true;
                    if (isDarkModeEnabled()) {
                        apply(true);
                        finishSwitch();
                    }
                    return;
                }
            } else {
                readyTicks[0] = 0;
            }
            if (++polls[0] > MAX_STARTUP_POLLS) {
                timer.stop();
                DebugLog.log("Startup apply: readiness never detected after " + polls[0]
                    + " polls (" + docks + " dock frame(s), " + trees + " tree(s)); "
                    + "applying anyway. If dark mode looked delayed at launch, this is why.");
                uiReady = true;
                if (isDarkModeEnabled()) {
                    apply(true);
                    finishSwitch();
                }
            }
        });
        timer.start();
    }

    /**
     * How many JIDE dockable frames live under this container — the readiness
     * probe for {@link #applyWhenDesignerVisible()}.
     *
     * <p>Matched on class name rather than an imported type: the docking
     * classes are not SDK surface, and a missing class must cost us this
     * heuristic, not the whole startup path.
     */
    private static int countDockableFrames(java.awt.Container container) {
        int count = 0;
        for (java.awt.Component child : container.getComponents()) {
            for (Class<?> type = child.getClass(); type != null; type = type.getSuperclass()) {
                if ("com.jidesoft.docking.DockableFrame".equals(type.getName())) {
                    count++;
                    break;
                }
            }
            if (child instanceof java.awt.Container) {
                count += countDockableFrames((java.awt.Container) child);
            }
        }
        return count;
    }

    /**
     * Run one theme switch, start to finish.
     *
     * <p>Package-visible, and deliberately free of {@link DesignerContext}:
     * every step either works on {@code UIManager} or walks
     * {@code Window.getWindows()}, both of which are perfectly happy with no
     * Designer around. That is what lets the headless harness
     * ({@code src/lafHarness}) drive this exact sequence against the real
     * Synthetica/JIDE/FlatLaf jars — the interaction where every bug in this
     * module's history has lived — instead of against stubs.
     */
    void apply(boolean dark) {
        boolean darkActive = UIManager.getLookAndFeel() instanceof FlatDarkLaf;
        if (dark == darkActive) {
            // Already there. Take down the "applying" message anyway, or it
            // sits in the status bar describing work that never happened.
            status.clear();
            return;
        }
        DebugLog.log("ThemeManager: switching to " + (dark ? "dark" : "light") + " mode.");
        failedPhases.clear();
        attemptedPhases = 0;
        phaseTrace.clear();
        // Held only for the abort path below: the exact overrides phase 0 drops,
        // so a failed swap can put them back rather than guessing at them.
        final java.util.Map<String, Object> clearedDefaults = new java.util.HashMap<>();
        if (!dark) {
            // Phase 0, light only — drop our UIManager overrides FIRST, while
            // FlatLaf is still installed and still serving the same values from
            // its own defaults table, so nothing renders differently in between.
            //
            // These clears are UIManager.put(key, null), which does not revert a
            // key: it DELETES the entry from the developer defaults. That is the
            // only place most standard Swing colours live in a stock Designer —
            // Synthetica is Synth-based and its look-and-feel table defines none
            // of them; TextField.background, Table.background, List.background
            // and ~190 others are written into the developer defaults by
            // Synthetica's own compatibility defaults and by
            // installJideExtension(). Clearing AFTER the restore therefore
            // deleted the values the restore had just put back, and left them
            // resolving to null for the rest of the session.
            //
            // A null TextField.background is exactly what #23 looked like.
            // BasicTextUI.installDefaults sets a field's background from that
            // key, so the phase-6 updateComponentTreeUI below left every text
            // field with NO background of its own — and Component.getBackground()
            // falls through to the parent when unset. In the Perspective
            // property editor the parent is NodeEditor$FilterWrapper, which
            // permanently holds the filter-match amber (#F7901E), so every
            // property name lit up at once.
            clearedDefaults.putAll(flatLafDefaults);
            safely("flatDefaults", () -> applyMenuDefaults(false));
            safely("jideOverrides", () -> applyJideDarkOverrides(false));
        }
        // Phase 1 — the look-and-feel swap itself. If THIS fails the switch is
        // genuinely off and we stop (reverting the pref so a broken dark state
        // never persists into the next launch).
        try {
            if (dark) {
                trace("painterSnapshot");
                snapshotThemePainters();
                trace("lookAndFeel");
                try {
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                } catch (Throwable first) {
                    // A stale FlatLaf UIScale listener can NPE out of
                    // Synthetica's uninitialize and abort the switch halfway;
                    // the retry starts from that half-uninitialized state and
                    // goes through.
                    DebugLog.log("setLookAndFeel(FlatDarkLaf) failed once; retrying.", first);
                    UIManager.setLookAndFeel(new FlatDarkLaf());
                }
            } else {
                trace("lookAndFeel");
                try {
                    restoreStockLaf();
                } catch (Throwable first) {
                    DebugLog.log("restoreStockLaf failed once; retrying.", first);
                    restoreStockLaf();
                }
            }
        } catch (Throwable t) {
            log.error("Failed to switch the Designer theme.", t);
            DebugLog.log("Theme switch FAILED in the look-and-feel phase.", t);
            // Say so where the user is looking. The preference is squared with
            // what is actually installed in finishSwitch(), so a switch that
            // failed neither persists to the next launch nor leaves the menu
            // claiming a theme the Designer is not in.
            status.message(dark
                ? "Dark mode could not be applied. Details in " + DebugLog.path()
                : "The stock theme could not be restored. Details in " + DebugLog.path());
            if (!dark && UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
                // The restore failed and we are still dark — but phase 0 has
                // already dropped the overrides dark mode depends on. FlatLaf's
                // own defaults table still covers the keys it defines; the JIDE
                // ones (DockableFrame.*, CommandBar.*, JideButton.*, ...) it does
                // not, so leaving them cleared strands the Designer dark with
                // light dock chrome. Put both sets back, so the state we abort
                // into is the one we started from and the next toggle-off retries
                // from something coherent.
                //
                // Re-applying the stashed copy rather than re-snapshotting: by
                // now installJideExtension has overwritten some of these keys in
                // the look-and-feel table, so a fresh snapshot would capture
                // JIDE's values for them (MenuBar.border, CheckBoxMenuItem
                // .borderPainted) instead of FlatLaf's. It also puts
                // flatLafDefaults back, so the next toggle-off has something to
                // clear and cannot relapse into #23.
                DebugLog.log("Restore failed with FlatLaf still active; "
                    + "re-asserting the dark overrides.");
                safely("flatDefaults", () -> {
                    flatLafDefaults.putAll(clearedDefaults);
                    applyMenuDefaults(true);
                });
                safely("jideOverrides", () -> applyJideDarkOverrides(true));
            }
            return;
        }
        // Phase 2 — styling passes. Each is isolated: one failure is logged
        // with its stack and must not strand the rest of the switch.
        if (dark) {
            // First, and before anything else can call into Synthetica: the
            // FlatLaf install above has just nulled its singleton.
            safely("syntheticaSingleton", this::keepSyntheticaAlive);
            safely("snapshotDefaults", this::snapshotMenuDefaults);
            safely("tokens", tokens::install);
        } else {
            safely("tokens", tokens::uninstall);
        }
        safely("jideExtension", () -> installJideExtension(dark));
        safely("painters", () -> overrideThemePainters(dark));
        if (dark) {
            // FlatLaf re-assert first, then the JIDE-specific keys on top so
            // the dock/collapsible overrides win where both define a key. The
            // light direction clears both in phase 0 instead — see there.
            safely("flatDefaults", () -> applyMenuDefaults(true));
            safely("jideOverrides", () -> applyJideDarkOverrides(true));
        }
        safely("updateComponentTrees", () -> {
            java.util.Set<String> failed = new java.util.LinkedHashSet<>();
            int failures = 0;
            for (Window window : Window.getWindows()) {
                // Isolate per WINDOW, not per phase. Synthetica can NPE out of
                // updateComponentTreeUI on a window holding a stale delegate
                // ("Cannot invoke java.awt.Font.getFamily() because font is
                // null"); with one guard around the whole loop that aborted
                // every window after it, leaving the light restore visibly
                // half-applied — some panels light, others still dark.
                try {
                    failures += updateComponentTreeUiResiliently(window, failed);
                } catch (Throwable t) {
                    DebugLog.log("updateComponentTreeUI failed for "
                        + window.getClass().getName() + "; continuing with the rest.", t);
                    // The stack above names the delegate that threw; it cannot
                    // name the component, and it cannot say how much of the tree
                    // the aborted update never reached — which is the part #12
                    // is actually about. Only runs when a window has genuinely
                    // failed, so it is not gated on the verbose flag: this is
                    // exactly the moment nobody has debug logging turned on.
                    TreeUpdateDiagnostic.report(window, t);
                }
            }
            if (failures > 0) {
                DebugLog.log("updateUI failed on " + failures + " component(s) across "
                    + failed.size() + " class(es): " + failed
                    + ". Their subtrees were still walked.");
            }
        });
        safely("cachedPopups", this::refreshCachedPopups);
        // The macOS native title bar follows this root pane property; without
        // the explicit reset it stays dark after a switch back to light.
        safely("macTitleBars", () -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof javax.swing.RootPaneContainer) {
                    ((javax.swing.RootPaneContainer) window).getRootPane().putClientProperty(
                        "apple.awt.windowAppearance",
                        dark ? "NSAppearanceNameDarkAqua" : null);
                }
            }
        });
        if (dark) {
            safely("treeIcons", treeIcons::install);
            safely("buttonIcons", treeIcons::recolorButtonIcons);
            safely("cellRenderers", cellRenderers::install);
            safely("collapsibles", () -> recolorCollapsibleTitlePanes(true));
            safely("whiteSwap", () -> swapWhiteTokenBackgrounds(true));
            safely("scriptEditors", scriptEditors::install);
            safely("consoleText", consoleText::install);
            safely("blockWorkspaces", blockWorkspaces::install);
            safely("statusBar", status::install);
            safely("cachedPainters", () -> repointCachedThemePainters(true));
            installComponentWatcher();
            if (DebugLog.verbose()) {
                debugDumpDockState();
                safely("lightDefaults", this::debugDumpLightDefaults);
            }
        } else {
            uninstallComponentWatcher();
            // After the light theme is back, so restored colors are light.
            safely("treeIcons", treeIcons::uninstall);
            safely("cellRenderers", cellRenderers::uninstall);
            safely("collapsibles", () -> recolorCollapsibleTitlePanes(false));
            safely("whiteSwap", () -> swapWhiteTokenBackgrounds(false));
            safely("scriptEditors", scriptEditors::uninstall);
            safely("consoleText", consoleText::uninstall);
            safely("blockWorkspaces", blockWorkspaces::uninstall);
            safely("statusBar", status::uninstall);
            safely("cachedPainters", () -> repointCachedThemePainters(false));
        }
        log.info(dark ? "Dark mode applied." : "Stock Designer theme restored.");
        reportOutcome(dark);
    }

    /**
     * Tell the user when the switch only partly worked.
     *
     * <p>Every pass after the look-and-feel swap runs under {@link #safely},
     * so one that fails leaves a Designer that works but is visibly wrong
     * somewhere: a light tree, stock icons, an unthemed console. The only
     * record of that used to be the debug log, which nobody knows to look
     * for — the visible result was a half-dark Designer and no explanation.
     */
    private void reportOutcome(boolean dark) {
        if (failedPhases.isEmpty()) {
            status.clear();
            return;
        }
        String message = degradedMessage(dark, failedPhases, attemptedPhases);
        log.warn(message);
        DebugLog.log(message);
        status.message(message);
    }

    /** One line: what worked, what did not, and where to read about it. */
    static String degradedMessage(boolean dark, java.util.List<String> failed, int attempted) {
        return (dark ? "Dark mode applied" : "Stock theme restored")
            + " with " + failed.size() + " of " + attempted + " steps failing ("
            + String.join(", ", failed) + "). Details in " + DebugLog.path();
    }

    /** Note a phase that does not run under {@link #safely} (it has its own guard). */
    private void trace(String phase) {
        phaseTrace.add(phase);
    }

    /** The phases of the last switch, in order. */
    java.util.List<String> phaseTrace() {
        return java.util.Collections.unmodifiableList(phaseTrace);
    }

    /** The phases of the last switch that threw. */
    java.util.List<String> failedPhases() {
        return java.util.Collections.unmodifiableList(failedPhases);
    }

    private void safely(String phase, Runnable task) {
        attemptedPhases++;
        trace(phase);
        try {
            task.run();
        } catch (Throwable t) {
            failedPhases.add(phase);
            log.warn("Theme phase '" + phase + "' failed.", t);
            DebugLog.log("Theme phase " + phase + " FAILED.", t);
        }
    }

    /**
     * Installing another look and feel ran Synthetica's uninitialize(), which
     * nulled its activeInstance singleton — but the Designer keeps calling
     * SyntheticaLookAndFeel.getInstance() while dark mode is active. Point the
     * singleton back at the stock instance so those calls keep working.
     *
     * <p>Runs under {@link #safely} as the first phase-2 pass on the dark
     * switch: first because nothing else may call into Synthetica before the
     * singleton is back, and under {@code safely} because a failure here has to
     * be reported rather than logged (#35).
     */
    private void keepSyntheticaAlive() {
        try {
            Class<?> synthetica = Class.forName(SYNTHETICA_LAF);
            if (synthetica.isInstance(stockLaf)) {
                Field active = synthetica.getDeclaredField("activeInstance");
                active.setAccessible(true);
                if (active.get(null) == null) {
                    active.set(null, stockLaf);
                }
            }
        } catch (Exception e) {
            // Rethrown rather than logged, which is the whole point of #35.
            // This reaches into a PRIVATE STATIC FIELD OF A THIRD-PARTY JAR
            // ("activeInstance", by name, in a jar we do not control), so the
            // way it breaks is a Synthetica upgrade renaming it — and it breaks
            // for everyone at once, on the next release, silently.
            //
            // Swallowed here, the switch reported complete success while every
            // Designer call to SyntheticaLookAndFeel.getInstance() NPE'd. Under
            // safely() the phase lands in failedPhases instead, so the user gets
            // the same status-bar line as any other partial failure and the log
            // gets the stack.
            throw new IllegalStateException(
                "Could not keep the Synthetica singleton alive under dark mode.", e);
        }
    }

    /**
     * Remember the look and feel the Designer came up in, so the light restore
     * has something to put back.
     *
     * <p>Its own method because {@link #startup} is not the only caller that
     * matters: the headless harness has no Designer to start up, and this is
     * the one piece of {@link #startup}'s state that {@link #apply} needs.
     */
    void captureStockLaf() {
        stockLaf = UIManager.getLookAndFeel();
    }

    /**
     * Reinstall the stock theme the same way the Designer does at startup:
     * SyntheticaLookAndFeel.setLookAndFeel(IgnitionLookAndFeel$LaF, true, true)
     * followed by SyntheticaLookAndFeel.setFont("Dialog", 12). Falls back to a
     * plain UIManager restore if the stock theme was not Synthetica-based.
     */
    private void restoreStockLaf() throws Exception {
        if (stockLaf != null && STOCK_LAF_CLASS.equals(stockLaf.getClass().getName())) {
            Class<?> synthetica = Class.forName(SYNTHETICA_LAF);
            synthetica.getMethod("setLookAndFeel", String.class, boolean.class, boolean.class)
                .invoke(null, STOCK_LAF_CLASS, true, true);
            synthetica.getMethod("setFont", String.class, int.class)
                .invoke(null, "Dialog", 12);
        } else if (stockLaf != null) {
            UIManager.setLookAndFeel(stockLaf.getClass().getName());
        }
        // The restore created a fresh look-and-feel instance; track it so the
        // next dark/restore cycle works with the live one.
        stockLaf = UIManager.getLookAndFeel();
    }

    /** UIManager keys behind the JIDE dock title bars ("Project Browser", ...) and toolbars. */
    private static final String[] JIDE_DARK_KEYS = {
        "DockableFrame.background",
        "DockableFrame.activeTitleBackground",
        "DockableFrame.activeTitleForeground",
        "DockableFrame.activeTitleBorderColor",
        "DockableFrame.inactiveTitleBackground",
        "DockableFrame.inactiveTitleForeground",
        "DockableFrame.inactiveTitleBorderColor",
        "CommandBar.background",
        "CommandBar.titleBarBackground",
        // Splitters / grippers between docked panels.
        "Gripper.foreground",
        "Gripper.background",
        "SplitPane.background",
        "SplitPaneDivider.draggingColor",
        "JideSplitPane.background",
        "JideSplitPaneDivider.background",
        // Section headers ("SESSION PROPS", ...) — JIDE CollapsiblePane title
        // panes paint from these keys via the ThemePainter.
        "CollapsiblePane.background",
        "CollapsiblePane.contentBackground",
        "CollapsiblePane.foreground",
        "CollapsiblePane.emphasizedForeground",
        "CollapsiblePanes.backgroundLt",
        "CollapsiblePanes.backgroundDk",
        // JIDE dock/toolbar containers re-assert these light values in their
        // own updateUI (#E5E8ED strips around docked frames and toolbars).
        "JideTabbedPane.background",
        "JideTabbedPane.tabAreaBackground",
        "ContentContainer.background",
        "MainContainer.background",
        "DockableBarContainer.background",
        "DockableBar.background",
        "Workspace.background",
        // JIDE toolbar buttons: BasicPainter.paintButtonBackground resolves
        // these through UIDefaultsLookup, so neither a component walk nor the
        // token mutation reaches them. Without them a SELECTED toggle button
        // (the layout and pointer tools) keeps the stock near-white highlight
        // against the dark toolbar.
        "JideButton.background",
        "JideButton.selectedBackground",
        "JideButton.selectedAndFocusedBackground",
        "JideButton.focusedBackground",
        "JideButton.borderColor",
        "JideButton.highlight",
        "JideButton.shadow",
        "JideButton.darkShadow",
        // Found by dumping every UIManager colour default still LIGHT under
        // dark mode (#21). Both are thin strips painted by a JIDE delegate
        // from a shared default, so no component-level pass could reach them:
        // SidePane.background was #E5E8ED — the very colour the comment above
        // calls out — and CommandBarSeparator.background #DBD8D1.
        "SidePane.background",
        "SidePane.foreground",
        "CommandBarSeparator.background",
        // A key named "darkShadow" holding #DDDDDD. Whatever it draws, a light
        // shadow under a dark theme is wrong on its face.
        "JideTabbedPane.darkShadow",
    };

    /**
     * installJideExtension() derives the dock/toolbar colors from a light-theme
     * mapping, so the "Project Browser"-style title bars stay light under dark
     * mode. Override them with the FlatLaf dark palette; clearing the keys
     * (null) on restore falls back to the stock values.
     */
    private void applyJideDarkOverrides(boolean dark) {
        if (!dark) {
            for (String key : JIDE_DARK_KEYS) {
                UIManager.put(key, null);
            }
            return;
        }
        java.awt.Color background = orDefault(
            UIManager.getColor("Panel.background"), new java.awt.Color(0x3C3F41));
        java.awt.Color titleBackground = background.brighter();
        java.awt.Color activeTitleBackground = orDefault(
            UIManager.getColor("List.selectionBackground"), new java.awt.Color(0x4B6EAF));
        java.awt.Color foreground = orDefault(
            UIManager.getColor("Label.foreground"), new java.awt.Color(0xBBBBBB));
        java.awt.Color border = orDefault(
            UIManager.getColor("Component.borderColor"), background.darker());

        UIManager.put("DockableFrame.background", background);
        UIManager.put("DockableFrame.activeTitleBackground", activeTitleBackground);
        UIManager.put("DockableFrame.activeTitleForeground", foreground);
        UIManager.put("DockableFrame.activeTitleBorderColor", border);
        UIManager.put("DockableFrame.inactiveTitleBackground", titleBackground);
        UIManager.put("DockableFrame.inactiveTitleForeground", foreground);
        UIManager.put("DockableFrame.inactiveTitleBorderColor", border);
        UIManager.put("CommandBar.background", background);
        UIManager.put("CommandBar.titleBarBackground", titleBackground);
        // Make the panel-resize splitters/grippers visible against the dark UI.
        java.awt.Color gripper = new java.awt.Color(0x9DA2A6);
        UIManager.put("Gripper.foreground", gripper);
        UIManager.put("Gripper.background", titleBackground);
        UIManager.put("SplitPane.background", background);
        UIManager.put("SplitPaneDivider.draggingColor", gripper);
        UIManager.put("JideSplitPane.background", background);
        UIManager.put("JideSplitPaneDivider.background", titleBackground);
        UIManager.put("CollapsiblePane.background", titleBackground);
        UIManager.put("CollapsiblePane.contentBackground", background);
        UIManager.put("CollapsiblePane.foreground", foreground);
        UIManager.put("CollapsiblePane.emphasizedForeground", foreground);
        UIManager.put("CollapsiblePanes.backgroundLt", background);
        UIManager.put("CollapsiblePanes.backgroundDk", background);
        UIManager.put("JideTabbedPane.background", background);
        UIManager.put("JideTabbedPane.tabAreaBackground", background);
        UIManager.put("ContentContainer.background", background);
        UIManager.put("MainContainer.background", background);
        UIManager.put("DockableBarContainer.background", background);
        UIManager.put("DockableBar.background", background);
        UIManager.put("Workspace.background", background);

        // Toolbar button states. `selected` has to stay clearly distinguishable
        // from the toolbar behind it without shouting — a toggle that is merely
        // active should not out-compete the content, which is exactly what the
        // stock near-white highlight does on a dark bar.
        java.awt.Color selected = titleBackground;
        UIManager.put("JideButton.background", background);
        UIManager.put("JideButton.selectedBackground", selected);
        UIManager.put("JideButton.selectedAndFocusedBackground", selected.brighter());
        UIManager.put("JideButton.focusedBackground", selected);
        UIManager.put("JideButton.borderColor", border);
        UIManager.put("JideButton.highlight", selected.brighter());
        UIManager.put("JideButton.shadow", border);
        UIManager.put("JideButton.darkShadow", background.darker());
        UIManager.put("SidePane.background", background);
        UIManager.put("SidePane.foreground", foreground);
        UIManager.put("CommandBarSeparator.background", border);
        UIManager.put("JideTabbedPane.darkShadow", background.darker());
    }

    /** Log which UI/painter actually drives the dock title bars right now. */
    /**
     * Log every {@code UIManager} colour default that is still LIGHT while
     * dark mode is active.
     *
     * <p>Diagnostic for #21: a pale band that no component owns. Every
     * component in that area reports a dark background and no border, and the
     * viewport's view has zero height — so nothing in the component-property
     * model explains it. The remaining candidate is a UI delegate painting
     * from a shared default that our passes never re-asserted, and a light
     * value under a dark look and feel is by definition suspect.
     *
     * <p>Kept rather than deleted after use: "which defaults are still light?"
     * is the first question for any future band of this kind, and rederiving
     * it costs a deploy cycle.
     */
    private void debugDumpLightDefaults() {
        try {
            java.util.List<String> light = new java.util.ArrayList<>();
            javax.swing.UIDefaults defaults = UIManager.getDefaults();
            for (Object key : new java.util.ArrayList<>(defaults.keySet())) {
                Object value;
                try {
                    value = defaults.get(key);
                } catch (Throwable t) {
                    continue;
                }
                if (value instanceof java.awt.Color
                        && luminance((java.awt.Color) value) > 200) {
                    light.add(key + "=" + String.format("#%06X",
                        ((java.awt.Color) value).getRGB() & 0xFFFFFF));
                }
            }
            java.util.Collections.sort(light);
            DebugLog.detail("Light UIManager colour defaults under dark ("
                + light.size() + "): " + light);
        } catch (Throwable t) {
            DebugLog.log("Could not dump light UIManager defaults.", t);
        }
    }

    private void debugDumpDockState() {
        DebugLog.detail("DockableFrameUI -> " + UIManager.get("DockableFrameUI"));
        Object painter = UIManager.get("Theme.painter");
        DebugLog.detail("Theme.painter -> "
            + (painter == null ? "null" : painter.getClass().getName()));
        DebugLog.detail("DockableFrame.inactiveTitleBackground -> "
            + UIManager.get("DockableFrame.inactiveTitleBackground"));
        for (Window window : Window.getWindows()) {
            if (window instanceof java.awt.Container) {
                dumpTitlePanes((java.awt.Container) window);
            }
        }
    }

    private void dumpTitlePanes(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            String className = child.getClass().getName();
            if (className.contains("TitlePane") || className.endsWith("DockableFrame")) {
                String ui = "?";
                try {
                    ui = String.valueOf(child.getClass().getMethod("getUI").invoke(child));
                } catch (Exception ignored) {
                    // Not all candidates expose getUI.
                }
                DebugLog.detail("Dock component: " + className
                    + " bg=" + child.getBackground() + " ui=" + ui);
            }
            if (child instanceof java.awt.Container) {
                dumpTitlePanes((java.awt.Container) child);
            }
        }
    }

    private static java.awt.Color orDefault(java.awt.Color color, java.awt.Color fallback) {
        return color != null ? color : fallback;
    }

    /**
     * Rebuild JIDE's UI defaults for the active look and feel. The no-arg
     * installJideExtension() only knows how to map recognized look and feels
     * (Synthetica among them) — under FlatLaf it must be told a style
     * explicitly. VSNET (1) is JIDE's flat style and derives its colors from
     * the active look and feel's palette, i.e. FlatLaf's dark colors.
     */
    private void installJideExtension(boolean dark) {
        try {
            Class<?> factory = Class.forName(JIDE_LAF_FACTORY);
            if (dark) {
                factory.getMethod("installJideExtension", int.class).invoke(null, 1);
                DebugLog.detail("installJideExtension(VSNET_STYLE) succeeded.");
            } else {
                factory.getMethod("installJideExtension").invoke(null);
                DebugLog.detail("installJideExtension() succeeded.");
            }
        } catch (Exception e) {
            log.warn("Could not reinstall the JIDE UI defaults after the theme change.", e);
            DebugLog.log("installJideExtension FAILED.", e);
        }
    }

    private static final String THEME_PAINTER_KEY = "Theme.painter";
    private static final String BASIC_PAINTER = "com.jidesoft.plaf.basic.BasicPainter";
    private static final String THEME_PAINTER_TYPE = "com.jidesoft.plaf.basic.ThemePainter";

    private final java.util.Map<Object, Object> painterSnapshot = new java.util.HashMap<>();
    /** Resolved once per dark switch so the synchronous add-hook stays cheap. */
    private Class<?> themePainterType;
    private Object basicPainterInstance;
    /** Component -> the ThemePainter fields we repointed, and what they held. */
    private final java.util.Map<java.awt.Component, java.util.Map<java.lang.reflect.Field, Object>>
        cachedPainterFields = new java.util.WeakHashMap<>();

    /**
     * JIDE resolves the painter behind dock title bars, grippers, and split
     * dividers through a per-classloader map stored under "Theme.painter" —
     * not through the UIManager color keys directly. The Designer's Synthetica
     * startup leaves the Synthetica painter registered for the classloader the
     * title panes resolve to, so they keep painting the light Synthetica
     * artwork even when every DockableFrame.* color is dark. Repoint every
     * entry at the style-neutral BasicPainter (which reads the UIManager
     * colors at paint time) and restore the original mapping on light mode.
     */
    /**
     * Capture the stock (Synthetica-era) painter mapping BEFORE any JIDE
     * reinstall touches it — snapshotting later would capture our own
     * BasicPainter entries and restore the wrong painters into light mode.
     */
    private void snapshotThemePainters() {
        Object value = UIManager.get(THEME_PAINTER_KEY);
        if (value instanceof java.util.Map && painterSnapshot.isEmpty()) {
            painterSnapshot.putAll((java.util.Map<?, ?>) value);
        }
    }

    private void overrideThemePainters(boolean dark) {
        Object value = UIManager.get(THEME_PAINTER_KEY);
        if (!(value instanceof java.util.Map)) {
            DebugLog.detail("Theme.painter is " + (value == null ? "null" : value.getClass().getName())
                + "; painter override skipped.");
            return;
        }
        @SuppressWarnings("unchecked")
        java.util.Map<Object, Object> painters = (java.util.Map<Object, Object>) value;
        try {
            if (dark) {
                Object basicPainter = Class.forName(BASIC_PAINTER)
                    .getMethod("getInstance").invoke(null);
                // Re-assert rather than set once. JIDE repopulates this map
                // when it installs its extension for a newly created dockable
                // frame, which puts SyntheticaJidePainter back and hands it to
                // every pane built from then on — the pane then casts the
                // active look and feel to SyntheticaLookAndFeel and throws on
                // each repaint. This runs on every rescan for that reason.
                java.util.List<Object> reverted = new java.util.ArrayList<>();
                for (Object key : new java.util.ArrayList<>(painters.keySet())) {
                    Object current = painters.get(key);
                    if (current != basicPainter) {
                        reverted.add(current);
                        painters.put(key, basicPainter);
                    }
                }
                if (!reverted.isEmpty()) {
                    DebugLog.detail("Theme.painter: repointed " + reverted.size() + " of "
                        + painters.size() + " classloader entr(ies) at BasicPainter; "
                        + "displaced: " + reverted);
                }
            } else {
                painters.putAll(painterSnapshot);
                painterSnapshot.clear();
            }
        } catch (Exception e) {
            log.warn("Could not override the JIDE theme painter.", e);
            DebugLog.log("overrideThemePainters FAILED.", e);
        }
    }

    /**
     * Ignition sets an explicit light background (not a UIResource) on the
     * JIDE CollapsiblePane title panes, so updateComponentTreeUI leaves them
     * light under dark mode. Force them dark and remember the originals; keys
     * are weak so recreated panes don't accumulate.
     */
    private final java.util.Map<java.awt.Component, java.awt.Color> collapsibleOriginals =
        new java.util.WeakHashMap<>();

    /**
     * Restores iterate the tracked components directly — never the window
     * hierarchy. A component that is detached at restore time (a closed
     * dialog, a hidden section) would be missed by a hierarchy walk and come
     * back later stuck in the old theme.
     */
    /**
     * Repoint {@code ThemePainter} references that JIDE components cached
     * before the switch.
     *
     * <p>Rewriting the {@code Theme.painter} map is not enough on its own.
     * {@code BasicDockableFrameTitlePane.installDefaults} reads
     * {@code UIDefaultsLookup.get("Theme.painter")} <em>once</em> and keeps the
     * result in a private field, so every dock title pane built before dark
     * mode was applied still holds {@code SyntheticaJidePainter}. That painter
     * casts the active look and feel to {@code SyntheticaLookAndFeel}
     * unconditionally, so under FlatLaf it throws {@code ClassCastException}
     * on every repaint of every docked panel's title bar.
     *
     * <p>Fields are located by TYPE rather than by name: JIDE ships obfuscated,
     * the field is currently called {@code a}, and matching on that would be a
     * silent no-op the moment they rebuild. Matching on the field type also
     * catches any other JIDE component caching a painter the same way.
     */
    private void repointCachedThemePainters(boolean dark) {
        Class<?> painterType;
        try {
            painterType = Class.forName(THEME_PAINTER_TYPE);
        } catch (Throwable t) {
            DebugLog.detail("ThemePainter type unavailable; cached-painter repoint skipped.", t);
            return;
        }
        if (!dark) {
            int restored = 0;
            for (java.util.Map.Entry<java.awt.Component,
                    java.util.Map<java.lang.reflect.Field, Object>> entry
                    : cachedPainterFields.entrySet()) {
                for (java.util.Map.Entry<java.lang.reflect.Field, Object> field
                        : entry.getValue().entrySet()) {
                    try {
                        field.getKey().set(entry.getKey(), field.getValue());
                        restored++;
                    } catch (Throwable t) {
                        DebugLog.log("Could not restore a cached ThemePainter.", t);
                    }
                }
                entry.getKey().repaint();
            }
            cachedPainterFields.clear();
            themePainterType = null;
            basicPainterInstance = null;
            if (restored > 0) {
                DebugLog.detail("Restored " + restored + " cached ThemePainter field(s).");
            }
            return;
        }
        Object basicPainter;
        try {
            basicPainter = Class.forName(BASIC_PAINTER).getMethod("getInstance").invoke(null);
        } catch (Throwable t) {
            DebugLog.detail("BasicPainter unavailable; cached-painter repoint skipped.", t);
            return;
        }
        themePainterType = painterType;
        basicPainterInstance = basicPainter;
        int repointed = 0;
        for (Window window : Window.getWindows()) {
            repointed += repointCachedThemePainters(window, painterType, basicPainter);
        }
        if (repointed > 0) {
            DebugLog.detail("Repointed " + repointed + " cached ThemePainter field(s) at BasicPainter.");
        }
    }

    private int repointCachedThemePainters(java.awt.Container container,
            Class<?> painterType, Object basicPainter) {
        int repointed = 0;
        for (java.awt.Component child : container.getComponents()) {
            for (Class<?> type = child.getClass(); type != null; type = type.getSuperclass()) {
                if (!type.getName().startsWith("com.jidesoft.")) {
                    continue;
                }
                for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                    if (!painterType.isAssignableFrom(field.getType())
                            || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        Object current = field.get(child);
                        if (current == null || current == basicPainter) {
                            continue;
                        }
                        cachedPainterFields
                            .computeIfAbsent(child, key -> new java.util.HashMap<>())
                            .putIfAbsent(field, current);
                        field.set(child, basicPainter);
                        child.repaint();
                        repointed++;
                    } catch (Throwable t) {
                        DebugLog.log("Could not repoint a cached ThemePainter on "
                            + child.getClass().getName(), t);
                    }
                }
            }
            if (child instanceof java.awt.Container) {
                repointed += repointCachedThemePainters(
                    (java.awt.Container) child, painterType, basicPainter);
            }
        }
        return repointed;
    }

    private void recolorCollapsibleTitlePanes(boolean dark) {
        if (!dark) {
            collapsibleOriginals.forEach((pane, original) -> {
                pane.setBackground(original);
                pane.repaint();
            });
            collapsibleOriginals.clear();
            return;
        }
        java.awt.Color darkTitle = orDefault(
            UIManager.getColor("DockableFrame.inactiveTitleBackground"),
            new java.awt.Color(0x4E5254));
        for (Window window : Window.getWindows()) {
            recolorCollapsibleTitlePanes(window, darkTitle);
        }
    }

    private void recolorCollapsibleTitlePanes(java.awt.Container container,
            java.awt.Color darkTitle) {
        for (java.awt.Component child : container.getComponents()) {
            if (child.getClass().getName().contains("CollapsiblePaneTitlePane")
                    && child.getBackground() != darkTitle) {
                collapsibleOriginals.putIfAbsent(child, child.getBackground());
                child.setBackground(darkTitle);
                child.repaint();
            }
            if (child instanceof java.awt.Container) {
                recolorCollapsibleTitlePanes((java.awt.Container) child, darkTitle);
            }
        }
    }

    /**
     * IA hands some components literally the Colors.Base000 token — which is
     * java.awt.Color.WHITE, an instance IaColorTokens must never mutate. Swap
     * those backgrounds per-component instead. The identity comparison is the
     * point: a user's own white (a deserialized Vision window color, say) is a
     * different instance and stays untouched.
     */
    private final java.util.Set<java.awt.Component> whiteSwapped =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final java.awt.Color DARK_CONTENT_BACKGROUND = new java.awt.Color(0x3A3D3F);
    /** Line colour substituted for a white matte/line border under dark mode. */
    static final java.awt.Color DARK_BORDER_LINE = new java.awt.Color(0x55595B);
    /** Component -> its stock border, for the light restore. */
    private final java.util.Map<javax.swing.JComponent, javax.swing.border.Border>
        swappedBorders = new java.util.WeakHashMap<>();
    /** Scroll pane -> its stock viewport border, a separate property from getBorder(). */
    private final java.util.Map<javax.swing.JScrollPane, javax.swing.border.Border>
        swappedViewportBorders = new java.util.WeakHashMap<>();

    /**
     * IA code re-sets Color.WHITE at runtime (NodeEditor's hover exit path
     * calls setBackground(WHITE) on every state change), which would undo a
     * one-time swap. This listener re-darkens a component the moment its
     * background is set back to the WHITE instance while dark mode is active;
     * setting a non-WHITE color re-fires the event once and then stops.
     */
    private final java.beans.PropertyChangeListener whiteEnforcer = e -> {
        if (e.getNewValue() == java.awt.Color.WHITE
                && UIManager.getLookAndFeel() instanceof FlatDarkLaf
                && e.getSource() instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) e.getSource()).setBackground(DARK_CONTENT_BACKGROUND);
        }
    };

    /**
     * Stale LaF-owned colors: a LIGHT UIResource background (or a DARK
     * UIResource foreground) under the dark look and feel means some
     * component's own updateUI re-asserted it from a light-theme key (JIDE's
     * QuickFilterField panels, for instance). UIResource means LaF-owned by
     * definition, so this can never touch user-content colors.
     */
    private final java.util.Map<java.awt.Component, java.awt.Color> staleUiresBackgrounds =
        new java.util.WeakHashMap<>();
    private final java.util.Map<java.awt.Component, java.awt.Color> staleUiresForegrounds =
        new java.util.WeakHashMap<>();

    private void swapWhiteTokenBackgrounds(boolean dark) {
        if (!dark) {
            // Restore from the tracked sets, not the hierarchy — a component
            // detached at restore time (closed dialog, hidden section) would
            // be missed by a walk and come back stuck dark.
            for (java.awt.Component component : new java.util.ArrayList<>(whiteSwapped)) {
                component.removePropertyChangeListener("background", whiteEnforcer);
                component.setBackground(java.awt.Color.WHITE);
                java.awt.Color originalForeground = liftedForegrounds.remove(component);
                if (originalForeground != null) {
                    component.setForeground(originalForeground);
                }
            }
            whiteSwapped.clear();
            swappedBorders.forEach(javax.swing.JComponent::setBorder);
            swappedBorders.clear();
            swappedViewportBorders.forEach(javax.swing.JScrollPane::setViewportBorder);
            swappedViewportBorders.clear();
            staleUiresBackgrounds.forEach(java.awt.Component::setBackground);
            staleUiresBackgrounds.clear();
            staleUiresForegrounds.forEach(java.awt.Component::setForeground);
            staleUiresForegrounds.clear();
            return;
        }
        for (Window window : Window.getWindows()) {
            swapWhiteTokenBackgrounds(window);
        }
    }

    /** Originals for foregrounds lifted alongside a white-background swap. */
    private final java.util.Map<java.awt.Component, java.awt.Color> liftedForegrounds =
        new java.util.WeakHashMap<>();
    private static final java.awt.Color LIGHT_FOREGROUND = new java.awt.Color(0xDDE0E3);

    /**
     * Replace a border drawn in the {@code Color.WHITE} instance with the same
     * border in a dark line colour.
     *
     * <p>{@code Base000} is {@code java.awt.Color.WHITE} itself, so
     * {@link IaColorTokens} refuses to mutate it — rewriting that instance
     * would change white for the whole JVM. The compensating pass corrects
     * components handed it by identity, but only ever looked at BACKGROUNDS.
     * A component handed the same instance as a BORDER colour was never
     * covered, which is why the Project Browser tree and the tag table's
     * header kept a white band across two dozen clean inspections: their
     * backgrounds really were dark, and nothing examined their borders.
     *
     * <p>The border is <em>replaced</em>, never recoloured — {@code
     * MatteBorder} is immutable, and mutating the shared colour is precisely
     * the thing that must not happen. The original object is tracked so the
     * light restore puts it back rather than reconstructing an approximation.
     */
    private void swapWhiteBorder(javax.swing.JComponent component) {
        if (!swappedBorders.containsKey(component)) {
            javax.swing.border.Border border = component.getBorder();
            javax.swing.border.Border darkened = darkenWhiteBorder(border);
            if (darkened != null) {
                swappedBorders.put(component, border);
                component.setBorder(darkened);
            }
        }
        // A JScrollPane paints a SECOND border around its viewport. It is a
        // separate property that getBorder() does not cover, so a white one
        // survives every pass above and draws a band along the viewport edge.
        if (component instanceof javax.swing.JScrollPane
                && !swappedViewportBorders.containsKey(component)) {
            javax.swing.JScrollPane pane = (javax.swing.JScrollPane) component;
            javax.swing.border.Border darkened =
                darkenWhiteBorder(pane.getViewportBorder());
            if (darkened != null) {
                swappedViewportBorders.put(pane, pane.getViewportBorder());
                pane.setViewportBorder(darkened);
            }
        }
    }

    /** The border with white lines darkened, or null if it has none. */
    static javax.swing.border.Border darkenWhiteBorder(javax.swing.border.Border border) {
        if (border instanceof javax.swing.border.MatteBorder) {
            javax.swing.border.MatteBorder matte = (javax.swing.border.MatteBorder) border;
            if (matte.getMatteColor() != java.awt.Color.WHITE) {
                return null;
            }
            java.awt.Insets insets = matte.getBorderInsets();
            return javax.swing.BorderFactory.createMatteBorder(
                insets.top, insets.left, insets.bottom, insets.right, DARK_BORDER_LINE);
        }
        if (border instanceof javax.swing.border.LineBorder) {
            javax.swing.border.LineBorder line = (javax.swing.border.LineBorder) border;
            if (line.getLineColor() != java.awt.Color.WHITE) {
                return null;
            }
            return javax.swing.BorderFactory.createLineBorder(
                DARK_BORDER_LINE, line.getThickness(), line.getRoundedCorners());
        }
        if (border instanceof javax.swing.border.CompoundBorder) {
            // The tag table header nests one inside another; darken whichever
            // halves qualify and keep the rest as they are.
            javax.swing.border.CompoundBorder compound =
                (javax.swing.border.CompoundBorder) border;
            javax.swing.border.Border outside = compound.getOutsideBorder();
            javax.swing.border.Border inside = compound.getInsideBorder();
            javax.swing.border.Border darkOutside = darkenWhiteBorder(outside);
            javax.swing.border.Border darkInside = darkenWhiteBorder(inside);
            if (darkOutside == null && darkInside == null) {
                return null;
            }
            return javax.swing.BorderFactory.createCompoundBorder(
                darkOutside != null ? darkOutside : outside,
                darkInside != null ? darkInside : inside);
        }
        return null;
    }

    private void swapWhiteTokenBackgrounds(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JComponent) {
                javax.swing.JComponent component = (javax.swing.JComponent) child;
                java.awt.Color background = component.isBackgroundSet()
                    ? component.getBackground() : null;
                if (background == java.awt.Color.WHITE && whiteSwapped.add(component)) {
                    component.addPropertyChangeListener("background", whiteEnforcer);
                    component.setBackground(DARK_CONTENT_BACKGROUND);
                    liftDarkForeground(component);
                } else if (background instanceof javax.swing.plaf.UIResource
                        && luminance(background) > 170
                        && !staleUiresBackgrounds.containsKey(component)) {
                    staleUiresBackgrounds.put(component, background);
                    component.setBackground(DARK_CONTENT_BACKGROUND);
                } else if (background != null
                        && !(background instanceof javax.swing.plaf.UIResource)
                        && luminance(background) > 200
                        && isNeutral(background)
                        && !staleUiresBackgrounds.containsKey(component)
                        && !insideVisionWorkspace(component)) {
                    // Explicit light-NEUTRAL chrome ("SESSION PROPS" header is
                    // a JLabel with a literal #eeeeef). Saturated backgrounds
                    // are left alone — they may be data (color swatches) — and
                    // the Vision workspace shows user content, never touched.
                    staleUiresBackgrounds.put(component, background);
                    component.setBackground(DARK_CONTENT_BACKGROUND);
                    liftDarkForeground(component);
                }
                swapWhiteBorder(component);
                java.awt.Color foreground = component.isForegroundSet()
                    ? component.getForeground() : null;
                if (foreground instanceof javax.swing.plaf.UIResource
                        && luminance(foreground) < 90
                        && !staleUiresForegrounds.containsKey(component)) {
                    staleUiresForegrounds.put(component, foreground);
                    component.setForeground(LIGHT_FOREGROUND);
                }
            }
            if (child instanceof java.awt.Container) {
                swapWhiteTokenBackgrounds((java.awt.Container) child);
            }
        }
    }

    static int luminance(java.awt.Color color) {
        return (color.getRed() * 299 + color.getGreen() * 587 + color.getBlue() * 114) / 1000;
    }

    /** Grayish — the channel spread is too small to be a deliberate color. */
    static boolean isNeutral(java.awt.Color color) {
        int max = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
        int min = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
        return max - min < 24;
    }

    /** Vision design canvases render user content; never restyle inside them. */
    private static boolean insideVisionWorkspace(java.awt.Component component) {
        for (java.awt.Component p = component; p != null; p = p.getParent()) {
            String name = p.getClass().getName();
            if (name.contains("factorypmi") || name.contains("VisionDesign")) {
                return true;
            }
        }
        return false;
    }

    /**
     * A darkened background under an explicitly-set dark foreground (a filter
     * field's near-black text, say) would be unreadable — lift the foreground
     * to a light one and remember the original for the light restore.
     * UIResource foregrounds are left to the look and feel.
     */
    private void liftDarkForeground(javax.swing.JComponent component) {
        java.awt.Color foreground = component.getForeground();
        if (foreground == null || foreground instanceof javax.swing.plaf.UIResource) {
            return;
        }
        if (luminance(foreground) < 90 && !liftedForegrounds.containsKey(component)) {
            liftedForegrounds.put(component, foreground);
            component.setForeground(LIGHT_FOREGROUND);
        }
    }

    /**
     * Trees and panels created after dark mode was applied (an opened
     * Perspective view, new dock panels, dialogs) come up unthemed: the tree
     * renderers are unwrapped, IA-token whites light, and — for subtrees the
     * Designer built before dark mode and attached later on a section switch —
     * the UI delegates stale. Watch for components being added anywhere,
     * refresh the added subtrees, and re-run the (idempotent) theming passes,
     * debounced so layout storms trigger one pass instead of hundreds.
     */
    private AWTEventListener componentWatcher;
    private Timer rescanTimer;
    private final java.util.List<java.lang.ref.WeakReference<java.awt.Component>> pendingAdded =
        new java.util.ArrayList<>();

    /**
     * Correct a subtree that has just been attached, before it can paint.
     *
     * <p>Ignition builds a workspace's dock panels early — under the stock
     * Synthetica look and feel — and only attaches them when you first open
     * that workspace. Until then they are invisible to any walk of the window
     * hierarchy, so none of the theming passes have ever seen them. On attach
     * they paint immediately, well inside the rescan's 150 ms debounce, and
     * fail in two different ways:
     *
     * <ul>
     *   <li>a title pane holding {@code SyntheticaJidePainter} casts the
     *       active look and feel to {@code SyntheticaLookAndFeel} and throws
     *       {@code ClassCastException};</li>
     *   <li>a component still on a Synthetica UI delegate paints a Synthetica
     *       border and throws {@code NullPointerException} out of
     *       {@code ImagePainter} ("dInsets is null").</li>
     * </ul>
     *
     * <p>Both are the same underlying problem — built under one look and feel,
     * painted under another — so both are corrected here, in order: refreshing
     * the delegates re-runs {@code installDefaults}, which re-reads
     * {@code Theme.painter} and usually fixes the painter as a side effect;
     * the repoint then catches whatever that missed.
     *
     * <p>This is the same shape as the {@code JPopupMenu} handling below, and
     * for the same reason: some things must be corrected before their first
     * paint, not on the next sweep. {@code hasStaleUi} short-circuits on the
     * first stale descendant, so the common case (nothing stale) is cheap.
     */
    private void correctBeforeFirstPaint(java.awt.Component added) {
        if (!(added instanceof java.awt.Container)) {
            return;
        }
        try {
            if (added instanceof javax.swing.JComponent) {
                refreshStaleUiDelegates((javax.swing.JComponent) added);
            }
            if (themePainterType != null && basicPainterInstance != null) {
                int repointed = repointCachedThemePainters(
                    (java.awt.Container) added, themePainterType, basicPainterInstance);
                if (repointed > 0) {
                    DebugLog.detail("Repointed " + repointed + " cached ThemePainter field(s) on "
                        + added.getClass().getName() + " as it was attached.");
                }
            }
        } catch (Throwable t) {
            DebugLog.log("Pre-paint correction failed for "
                + added.getClass().getName(), t);
        }
    }

    private void installComponentWatcher() {
        if (componentWatcher != null) {
            return;
        }
        rescanTimer = new Timer(150, e -> {
            // The ENTIRE body is guarded, not just the passes. This is a Timer
            // callback: anything that escapes reaches the EDT's default handler
            // and the tick dies where it stands. It has happened twice — once
            // on an Ignition NPE out of the tree walk, once on a logging call
            // of ours in the summary that reports it.
            try {
                rescanTick();
            } catch (Throwable t) {
                DebugLog.log("Rescan tick failed.", t);
            }
        });
        rescanTimer.setRepeats(false);
        installAwtEventListener();
    }

    /**
     * One debounced rescan: refresh anything attached with a wrong-look-and-feel
     * delegate, then re-run the idempotent passes over the new components.
     *
     * <p>Extracted from the timer so the callback is nothing but a guard. Note
     * the early return when dark mode is no longer active — it must skip the
     * passes too, which is why this is a method and not an inlined try block.
     */
    private void rescanTick() {
        java.util.List<java.lang.ref.WeakReference<java.awt.Component>> added =
            new java.util.ArrayList<>(pendingAdded);
        pendingAdded.clear();
        if (!(UIManager.getLookAndFeel() instanceof FlatDarkLaf)) {
            return;
        }
        java.util.Set<String> failed = new java.util.LinkedHashSet<>();
        int failures = 0;
        for (java.lang.ref.WeakReference<java.awt.Component> ref : added) {
            java.awt.Component component = ref.get();
            if (component == null || !component.isDisplayable()) {
                continue;
            }
            // Stale (wrong-LaF) delegates always warrant a refresh — a
            // Synthetica delegate under FlatLaf paints broken or throws —
            // regardless of the size heuristic below.
            if (hasStaleUi(component, true) || worthUiRefresh(component)) {
                failures += updateComponentTreeUiResiliently(component, failed);
            }
        }
        if (failures > 0) {
            DebugLog.log("Rescan: updateUI failed on " + failures
                + " component(s) across " + failed.size() + " class(es): " + failed
                + ". Their subtrees were still walked.");
        }
        runThemingPasses();
    }

    private void installAwtEventListener() {
        componentWatcher = event -> {
            if (event.getID() == ContainerEvent.COMPONENT_ADDED) {
                java.awt.Component child = ((ContainerEvent) event).getChild();
                if (child instanceof java.awt.Container) {
                    // Before the debounce: a workspace's dock panels are built
                    // early under Synthetica and attached only when that
                    // workspace is first opened, so they paint (and throw)
                    // long before the rescan would reach them.
                    correctBeforeFirstPaint(child);
                    pendingAdded.add(new java.lang.ref.WeakReference<>(child));
                }
                if (child instanceof javax.swing.JPopupMenu) {
                    // Cached menus created under the other theme keep stale UI
                    // delegates (SyntheticaMenuItemUI cannot paint under
                    // FlatLaf -> blank context menus). Refresh before paint.
                    refreshStaleUiDelegates((javax.swing.JPopupMenu) child);
                    if (DebugLog.verbose()) {
                        logPopupState((javax.swing.JPopupMenu) child);
                    }
                }
                rescanTimer.restart();
            } else if ((event.getID() == java.awt.event.WindowEvent.WINDOW_OPENED
                    || event.getID() == java.awt.event.WindowEvent.WINDOW_ACTIVATED)
                    && event instanceof java.awt.event.WindowEvent) {
                Window window = ((java.awt.event.WindowEvent) event).getWindow();
                boolean opened = event.getID() == java.awt.event.WindowEvent.WINDOW_OPENED;
                if (DebugLog.verbose() && opened
                        && window.getClass().getName().toLowerCase().contains("popup")) {
                    logWindowContents(window);
                }
                if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
                    // Windows built from panels cached under the light theme
                    // (the Diagnostics dialog) carry Synthetica UI delegates
                    // that NPE-loop when painted under FlatLaf — refresh them
                    // before anything paints. Cheap when nothing is stale;
                    // skipped for the main frame, which is never stale and too
                    // big to scan on every activation.
                    if (window instanceof javax.swing.RootPaneContainer
                            && (context == null || window != context.getFrame())) {
                        refreshStaleUiDelegates(
                            ((javax.swing.RootPaneContainer) window).getRootPane());
                    }
                    if (opened) {
                        // Color passes deferred one event-queue turn: mutating
                        // components synchronously inside WINDOW_OPENED can
                        // swallow the window's first paint.
                        SwingUtilities.invokeLater(() -> {
                            if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
                                runThemingPasses();
                                window.repaint();
                            }
                        });
                    }
                }
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(componentWatcher,
            AWTEvent.CONTAINER_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK);
        DebugLog.detail("Component watcher installed.");
    }

    /**
     * updateComponentTreeUI on every addition makes the Designer sluggish and
     * can glitch live interactions. It is only needed for the big pre-built
     * subtrees the Designer attaches on a section switch — popup internals,
     * tooltips, and table cell editors are created under the current look and
     * feel and small additions self-style; skip them.
     */
    private static boolean worthUiRefresh(java.awt.Component component) {
        for (java.awt.Component p = component; p != null; p = p.getParent()) {
            if (p instanceof javax.swing.JPopupMenu
                    || p instanceof javax.swing.CellRendererPane
                    || p instanceof javax.swing.JTable
                    || p.getClass().getName().contains("Popup")
                    || p.getClass().getName().contains("ToolTip")) {
                return false;
            }
        }
        return component instanceof java.awt.Container
            && countComponents(component, 12) >= 12;
    }

    /** Component count with an early exit once {@code enough} is reached. */
    private static int countComponents(java.awt.Component component, int enough) {
        int count = 1;
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child
                    : ((java.awt.Container) component).getComponents()) {
                count += countComponents(child, enough - count);
                if (count >= enough) {
                    return count;
                }
            }
        }
        return count;
    }

    /** The idempotent dark-mode passes; each is guarded so re-runs are cheap. */
    private void runThemingPasses() {
        treeIcons.install();
        treeIcons.recolorButtonIcons();
        cellRenderers.install();
        recolorCollapsibleTitlePanes(true);
        swapWhiteTokenBackgrounds(true);
        scriptEditors.install();
        consoleText.install();
        // Blocks are built when a pipeline or chart workspace is first
        // opened, which is long after the switch — so this pass earns its
        // place in the rescan rather than only in apply().
        blockWorkspaces.install();
        // Before the cached-field pass: if JIDE has put its own painter back
        // in the map, fix the map first so anything built next reads the right
        // one, then correct the instances that already read the wrong one.
        overrideThemePainters(true);
        repointCachedThemePainters(true);
        refreshStaleInSecondaryWindows();
    }


    /**
     * Catch stale (wrong-LaF) UI delegates in secondary windows on every
     * rescan. The tag editor builds its category panels lazily — combo cells
     * created when you click "Numeric" keep Synthetica delegates (the light
     * rounded dropdowns) that the window-open refresh already missed. The main
     * frame is excluded: it is rebuilt at apply time and too large to rescan.
     * hasStaleUi short-circuits on the first stale delegate, so this is cheap
     * when nothing is stale.
     */
    /**
     * {@code SwingUtilities.updateComponentTreeUI}, but a component that throws
     * loses only itself.
     *
     * <p>Swing's own walk is one recursion with no guard, so the first
     * {@code updateUI()} that throws abandons every component after it. That is
     * a real loss and not a hypothetical one: isolating per window (#11) was
     * not enough, because the window that aborts is usually the MAIN one, and
     * everything below the throwing component in its tree keeps the delegates
     * of the outgoing look and feel.
     *
     * <p>Two Ignition classes are known to throw here, both on the light
     * restore and both for reasons outside this module:
     *
     * <ul>
     *   <li>Vision's {@code DockingInternalFrameUI.installDefaults} nulls the
     *       content pane's background when it is a {@code UIResource} — the
     *       state the preceding walk leaves behind — and a Vision content pane
     *       is a {@code BasicContainer}, whose {@code setBackground} override
     *       dereferences its argument;</li>
     *   <li>the {@code Font.getFamily()} NPE recorded in #12.</li>
     * </ul>
     *
     * <p>Neither is fixable from here. What is fixable is the blast radius.
     *
     * <p>The failures are logged but deliberately do <em>not</em> mark the phase
     * degraded in the status bar: a Designer with a Vision window open would
     * then warn on every single switch, and a warning that always fires stops
     * being read. If a future failure turns out to be ours rather than
     * Ignition's, that is the line to reconsider.
     *
     * @param failed collects the distinct classes that threw, so a tree with
     *     two hundred of one broken component logs one line rather than two
     *     hundred
     * @return how many components threw
     */
    static int updateComponentTreeUiResiliently(
            java.awt.Component component, java.util.Set<String> failed) {
        int failures = updateSubtree(component, failed);
        // What SwingUtilities does after its own walk.
        component.invalidate();
        component.validate();
        component.repaint();
        return failures;
    }

    /**
     * Stop Vision's {@code DockingInternalFrameUI.installDefaults} from throwing
     * part-way through, by removing the condition it throws on.
     *
     * <p>Containing the throw is not enough here, and the reason is in the
     * bytecode. {@code installDefaults} does, in order:
     *
     * <pre>
     * offset 58-60  if (contentPane.getBackground() instanceof UIResource)
     *                   contentPane.setBackground(null);      // throws
     * offset 63-76      frame.setLayout(createLayoutManager());
     * offset 79-91      frame.setBackground(...);
     * offset 94-105     frame.setBorder(new CustomBorder());
     * </pre>
     *
     * <p>A Vision content pane is a {@code BasicContainer}, whose
     * {@code setBackground} override dereferences its argument, so the null at
     * offset 60 throws and the frame never gets a layout. Catching that leaves
     * a {@code JInternalFrame} with {@code getLayout() == null}, and every
     * later {@code BasicInternalFrameUI.getMinimumSize()} NPEs — once per
     * paint, per validate, and once per read of the {@code minimumSize}
     * property in the Vision property table. Containment turned one abort into
     * a storm.
     *
     * <p>The condition is ours to remove. IA's block only fires when the
     * content pane's background is a {@code UIResource}, and it is a
     * {@code UIResource} because a previous walk of ours put one there —
     * {@code LookAndFeel.installColorsAndFont} replaces a null or
     * {@code UIResource} background with the new look and feel's. In a stock
     * Designer this code runs once, at construction, against whatever
     * background the window's own design carries, so it never fires.
     *
     * <p>So: swap the same colour in as a plain {@code Color} before
     * {@code updateUI()}, and put a {@code UIResource} back afterwards. IA's
     * block sees a non-{@code UIResource} and skips itself; the layout, the
     * background and the border all get installed; and the content pane is
     * left tracking the look and feel again, so the walk into its own subtree
     * recolours it normally.
     *
     * @return the background to hand to {@link #restoreInternalFrameBackground},
     *     or null when nothing was changed
     */
    private static java.awt.Color neutraliseInternalFrameBackground(
            javax.swing.JComponent component) {
        if (!(component instanceof javax.swing.JInternalFrame)) {
            return null;
        }
        try {
            java.awt.Container contentPane =
                ((javax.swing.JInternalFrame) component).getContentPane();
            if (!(contentPane instanceof javax.swing.JComponent)) {
                return null;
            }
            java.awt.Color background = contentPane.getBackground();
            if (!(background instanceof javax.swing.plaf.UIResource)) {
                return null;
            }
            // Same colour, plain type. Alpha carried explicitly: the int
            // constructor drops it.
            contentPane.setBackground(new java.awt.Color(background.getRGB(), true));
            return background;
        } catch (Throwable t) {
            DebugLog.log("Could not neutralise the content-pane background on "
                + component.getClass().getName(), t);
            return null;
        }
    }

    /** Put the {@code UIResource} background back, so it tracks the theme again. */
    private static void restoreInternalFrameBackground(
            javax.swing.JComponent component, java.awt.Color background) {
        if (background == null) {
            return;
        }
        try {
            java.awt.Container contentPane =
                ((javax.swing.JInternalFrame) component).getContentPane();
            contentPane.setBackground(background);
        } catch (Throwable t) {
            DebugLog.log("Could not restore the content-pane background on "
                + component.getClass().getName(), t);
        }
    }

    private static int updateSubtree(
            java.awt.Component component, java.util.Set<String> failed) {
        int failures = 0;
        if (component instanceof javax.swing.JComponent) {
            javax.swing.JComponent child = (javax.swing.JComponent) component;
            // Prevention, not containment — see the method's javadoc.
            java.awt.Color pinned = neutraliseInternalFrameBackground(child);
            try {
                child.updateUI();
            } catch (Throwable t) {
                failures++;
                if (failed.add(child.getClass().getName())) {
                    DebugLog.log("updateUI failed for " + child.getClass().getName()
                        + "; walking its subtree anyway.", t);
                }
            } finally {
                restoreInternalFrameBackground(child, pinned);
            }
            javax.swing.JPopupMenu popup = child.getComponentPopupMenu();
            if (popup != null && popup.isVisible() && popup.getInvoker() == child) {
                failures += updateSubtree(popup, failed);
            }
        }
        // A JMenu's items hang off its popup, not off getComponents().
        java.awt.Component[] children =
            component instanceof javax.swing.JMenu
                ? ((javax.swing.JMenu) component).getMenuComponents()
                : component instanceof java.awt.Container
                    ? ((java.awt.Container) component).getComponents()
                    : null;
        if (children != null) {
            for (java.awt.Component each : children) {
                failures += updateSubtree(each, failed);
            }
        }
        return failures;
    }

    private void refreshStaleInSecondaryWindows() {
        for (Window window : Window.getWindows()) {
            if (!window.isShowing() || (context != null && window == context.getFrame())) {
                continue;
            }
            if (window instanceof javax.swing.RootPaneContainer) {
                refreshStaleUiDelegates(
                    ((javax.swing.RootPaneContainer) window).getRootPane());
            }
        }
    }

    /**
     * Cached popup menus (componentPopupMenu properties, menu bar submenus)
     * live detached from any window and would flash their stale style on
     * first open — refresh them proactively at theme-apply time.
     */
    private void refreshCachedPopups() {
        for (Window window : Window.getWindows()) {
            refreshCachedPopups(window);
        }
    }

    private void refreshCachedPopups(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JComponent) {
                javax.swing.JPopupMenu popup =
                    ((javax.swing.JComponent) child).getComponentPopupMenu();
                if (popup != null) {
                    refreshStaleUiDelegates(popup);
                }
                if (child instanceof javax.swing.JMenu) {
                    refreshStaleUiDelegates(((javax.swing.JMenu) child).getPopupMenu());
                }
            }
            if (child instanceof java.awt.Container) {
                refreshCachedPopups((java.awt.Container) child);
            }
        }
    }

    private void uninstallComponentWatcher() {
        if (componentWatcher != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(componentWatcher);
            componentWatcher = null;
        }
        if (rescanTimer != null) {
            rescanTimer.stop();
            rescanTimer = null;
        }
        pendingAdded.clear();
    }

    /**
     * If any descendant carries a UI delegate from the wrong look and feel
     * (Synthetica under FlatDark or vice versa — cached components created
     * under the other theme), refresh the whole subtree's delegates.
     */
    static void refreshStaleUiDelegates(javax.swing.JComponent root) {
        boolean darkActive = UIManager.getLookAndFeel() instanceof FlatDarkLaf;
        if (hasStaleUi(root, darkActive)) {
            java.util.Set<String> failed = new java.util.LinkedHashSet<>();
            int failures = updateComponentTreeUiResiliently(root, failed);
            DebugLog.detail("Refreshed stale UI delegates under "
                + root.getClass().getName());
            if (failures > 0) {
                DebugLog.log("Stale-delegate refresh under " + root.getClass().getName()
                    + ": updateUI failed on " + failures + " component(s): " + failed
                    + ". Their subtrees were still walked.");
            }
        }
    }

    static boolean hasStaleUi(java.awt.Component component, boolean darkActive) {
        if (component instanceof javax.swing.JComponent) {
            try {
                Object ui = component.getClass().getMethod("getUI").invoke(component);
                if (ui != null) {
                    String name = ui.getClass().getName();
                    if (darkActive
                            ? (name.contains("Synthetica") || name.contains("synth"))
                            : name.contains("flatlaf")) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // Component without getUI.
            }
        }
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child
                    : ((java.awt.Container) component).getComponents()) {
                if (hasStaleUi(child, darkActive)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Log a popup window's content tree (3 levels) after it lays out. */
    private void logWindowContents(Window window) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder("PopupWindow ")
                .append(window.getClass().getName()).append(" ")
                .append(window.getWidth()).append("x").append(window.getHeight()).append(":");
            appendChildren(sb, window, 0);
            DebugLog.detail(sb.toString());
        });
    }

    private void appendChildren(StringBuilder sb, java.awt.Container container, int depth) {
        if (depth > 3) {
            return;
        }
        for (java.awt.Component child : container.getComponents()) {
            sb.append(" [").append(child.getClass().getSimpleName())
                .append(" ").append(child.getWidth()).append("x").append(child.getHeight())
                .append(child.isVisible() ? "" : " HIDDEN");
            if (child instanceof java.awt.Container) {
                appendChildren(sb, (java.awt.Container) child, depth + 1);
            }
            sb.append("]");
        }
    }

    /** Log a shown popup's items — sizes, UI delegates — after it lays out. */
    private void logPopupState(javax.swing.JPopupMenu popup) {
        SwingUtilities.invokeLater(() -> {
            StringBuilder sb = new StringBuilder("Popup: ")
                .append(popup.getComponentCount()).append(" item(s), ")
                .append(popup.getWidth()).append("x").append(popup.getHeight()).append(";");
            for (java.awt.Component item : popup.getComponents()) {
                sb.append(" [").append(item.getClass().getSimpleName())
                    .append(" ").append(item.getWidth()).append("x").append(item.getHeight());
                try {
                    Object ui = item.getClass().getMethod("getUI").invoke(item);
                    sb.append(" ui=").append(ui == null ? "NULL" : ui.getClass().getSimpleName());
                } catch (Exception ignored) {
                    // Not all children expose getUI.
                }
                sb.append("]");
            }
            DebugLog.detail(sb.toString());
        });
    }

    /** Keys that must never be re-asserted from the FlatLaf snapshot. */
    private static final java.util.Set<String> DEFAULTS_SNAPSHOT_SKIP =
        java.util.Set.of("ClassLoader", "Theme.painter");

    private final java.util.Map<String, Object> flatLafDefaults = new java.util.HashMap<>();

    /** Capture ALL of FlatLaf's resolved defaults right after it is installed. */
    void snapshotMenuDefaults() {
        flatLafDefaults.clear();
        java.util.Enumeration<Object> keys = UIManager.getLookAndFeelDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (key instanceof String && !DEFAULTS_SNAPSHOT_SKIP.contains(key)) {
                Object value = UIManager.get(key);
                if (value != null) {
                    flatLafDefaults.put((String) key, value);
                }
            }
        }
        DebugLog.detail("Captured " + flatLafDefaults.size() + " FlatLaf defaults.");
    }

    /**
     * installJideExtension clobbers far more than JIDE's own keys: it replaces
     * standard Swing defaults (Menu*, TextField.background, Table.background,
     * ComboBox colors, ...) with a light-theme mapping — the source of white
     * search fields, white table cells, and invisible context menus under dark
     * mode. Re-assert every FlatLaf default on top, leaving only the
     * JIDE-specific keys (which FlatLaf does not define) to the extension;
     * clear the overrides again when light mode returns.
     *
     * <p>The clear is destructive, not a revert: {@code UIManager.put(key,
     * null)} REMOVES the developer-defaults entry, and it cannot distinguish
     * ours from the one that was there before. It must therefore run while
     * FlatLaf is still installed — before the stock look and feel and
     * {@code installJideExtension()} repopulate those same keys (#23).
     */
    void applyMenuDefaults(boolean dark) {
        if (dark) {
            flatLafDefaults.forEach(UIManager::put);
        } else {
            for (String key : flatLafDefaults.keySet()) {
                UIManager.put(key, null);
            }
            flatLafDefaults.clear();
        }
    }

    private void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
