package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the gate's verdict does to a switch.
 *
 * <p>The verdict itself is {@link VisionGateTest}'s subject; here it is
 * dictated, and what is asserted is the wiring: a refused switch leaves the
 * Designer light with the preference and the menu agreeing, a startup blocked
 * by Vision keeps the preference for a later launch, leaving dark mode for
 * Vision is a complete switch back that also keeps the preference, and the
 * gate is consulted again at the moment the theme is actually installed.
 */
class VisionGateSwitchTest {

    private static final String KEY = "darkMode";

    private Preferences prefs;
    private DictatedGate gate;
    private ThemeManager manager;
    private RecordingThemeStateListener listener;
    private LookAndFeel original;

    @BeforeEach
    void setUp() throws Exception {
        prefs = new InMemoryPreferences();
        gate = new DictatedGate();
        manager = new ThemeManager(prefs, gate);
        listener = new RecordingThemeStateListener();
        manager.setThemeStateListener(listener);
        original = UIManager.getLookAndFeel();
        // A known not-dark look and feel, standing in for the Designer's
        // Synthetica (not on this classpath by design): the assertions turn on
        // FlatLaf being absent or present, so inheriting whatever an earlier
        // test left installed would make them mean nothing.
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        manager.captureStockLaf();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Dark mode writes ~200 UIManager developer defaults; the light half
        // of the switch is what clears them, so unwind through it rather than
        // leaving them behind for the next test.
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        if (original != null) {
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    @DisplayName("a blocked click leaves the Designer light, unticks the menu and resets the preference")
    void refusedSwitchLeavesEverythingLight() throws Exception {
        manager.applyStartupPreference();
        gate.reason = "a Vision window or template is open";

        manager.setDark(true);
        drainEventQueue();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertFalse(prefs.getBoolean(KEY, true),
            "setDark wrote dark before the switch was refused; the refusal must undo that "
                + "or the next launch tries again with the same windows open");
        assertEquals(List.of(false), listener.darkActive,
            "the menu must not stay ticked for a theme that was refused");
        assertEquals(List.of(VisionGate.refusalMessage("a Vision window or template is open")),
            gate.explained, "the user clicked, so the user is told why nothing happened");
    }

    @Test
    @DisplayName("the gate is asked again when the theme is actually installed, one turn after the click")
    void gateIsAskedAgainWhenTheSwitchActuallyRuns() throws Exception {
        manager.applyStartupPreference();
        gate.reason = null;

        // Both from ONE event-thread turn, in this order, because the order is
        // the test. A click on a Vision window is already queued behind the
        // menu click; the menu click's handler (setDark, synchronous on the
        // event thread) then queues the install behind it. Posting them from
        // the test thread instead races the event thread: on an idle machine
        // it handles the menu click before the Vision click is queued, the
        // install runs first, and dark mode lands — which is exactly the
        // sequence a real Designer never produces.
        SwingUtilities.invokeAndWait(() -> {
            SwingUtilities.invokeLater(() -> gate.reason = "the Vision workspace is selected");
            manager.setDark(true);
        });
        drainEventQueue();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf,
            "the verdict from the click was stale by the time the theme was installed");
        assertFalse(prefs.getBoolean(KEY, true));
        assertEquals(List.of(false), listener.darkActive, "the menu was disabled for the switch and must come back unticked");
        assertEquals(List.of(VisionGate.refusalMessage("the Vision workspace is selected")), gate.explained);
    }

    @Test
    @DisplayName("a blocked startup keeps the preference for a launch without Vision open")
    void blockedStartupKeepsThePreference() {
        prefs.putBoolean(KEY, true);
        gate.reason = "the Vision workspace is selected";

        manager.applyStartupPreference();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertTrue(prefs.getBoolean(KEY, false),
            "nothing failed; the Designer just came up on Vision. Forgetting the choice "
                + "would punish the user for where they left off");
        assertEquals(List.of(false), listener.darkActive,
            "but the menu follows the theme on screen, which is light");
    }

    @Test
    @DisplayName("an unblocked startup and click still apply dark")
    void unblockedSwitchApplies() throws Exception {
        prefs.putBoolean(KEY, true);
        gate.reason = null;

        manager.applyStartupPreference();

        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertEquals(List.of(true), listener.darkActive);
    }

    @Test
    @DisplayName("leaving dark mode for Vision is a full switch back that keeps the preference")
    void leavingForVisionRestoresTheThemeButKeepsThePreference() {
        prefs.putBoolean(KEY, true);
        manager.applyStartupPreference();
        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf, "precondition");

        manager.leaveDarkForVision("the Vision workspace was opened", false);

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertTrue(prefs.getBoolean(KEY, false),
            "nothing failed and the user still prefers dark: one visit to Vision must not "
                + "turn every future launch light, the same rule a blocked startup follows");
        assertEquals(List.of(true, false), listener.darkActive,
            "the menu follows the screen, which is light");
    }

    @Test
    @DisplayName("dark already on with Vision in play: a re-asserted dark request drops out instead of claiming a refusal")
    void reassertedDarkWhileDarkOnVisionDropsOut() throws Exception {
        prefs.putBoolean(KEY, true);
        gate.reason = null;
        manager.applyStartupPreference();
        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf, "precondition");

        // Vision came into play without the navigation watch seeing it, and
        // something re-asserts the preference (a rebuilt menu, say).
        gate.reason = "the Vision workspace is selected";
        manager.setDark(true);
        drainEventQueue();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf,
            "Vision wins: dark mode must end, not be reported as never applied");
        assertTrue(prefs.getBoolean(KEY, false), "a drop-out keeps the preference");
        assertEquals(List.of(true, false), listener.darkActive);
        assertEquals(List.of(VisionGate.dropOutMessage("the Vision workspace is selected", false)),
            gate.explained, "the user is told dark mode was turned off, not that it was refused");
    }

    @Test
    @DisplayName("leaving dark mode when the Designer is already light does nothing")
    void leavingWhenLightIsANoOp() {
        manager.applyStartupPreference();

        manager.leaveDarkForVision("the Vision workspace was opened", false);

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertEquals(List.of(), listener.darkActive, "no switch happened, so nothing was reported");
    }

    @Test
    @DisplayName("a dark request after shutdown is ignored, preference included")
    void setDarkAfterShutdownIsIgnored() throws Exception {
        manager.applyStartupPreference();
        manager.shutdown();
        drainEventQueue();

        // The Designer rebuilds module menus during teardown; a rebuilt menu
        // re-asserting a kept preference must not start a switch on a module
        // that has already put the Designer back.
        manager.setDark(true);
        drainEventQueue();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertFalse(prefs.getBoolean(KEY, false), "nothing may be written after shutdown");
        assertEquals(List.of(), listener.darkActive);
    }

    /** Two turns: setDark defers to the EDT, and beginSwitch defers apply one more turn. */
    private static void drainEventQueue() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** A gate that says what the test tells it to, and records what it was asked to explain. */
    private static final class DictatedGate extends VisionGate {

        private String reason;
        private final List<String> explained = new ArrayList<>();

        @Override
        String blockingReason() {
            return reason;
        }

        @Override
        void explain(String message) {
            explained.add(message);
        }

        @Override
        void watchNavigation(Runnable onVisionActivated) {
        }
    }
}
