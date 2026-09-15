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
 * by Vision keeps the preference for a later launch, and leaving dark mode
 * for Vision is a complete switch back — not a look-and-feel swap on its own.
 */
class VisionGateSwitchTest {

    private static final String KEY = "darkMode";

    private Preferences prefs;
    private DictatedGate gate;
    private ThemeManager manager;
    private RecordingListener listener;
    private LookAndFeel original;

    @BeforeEach
    void setUp() throws Exception {
        prefs = new InMemoryPreferences();
        gate = new DictatedGate();
        manager = new ThemeManager(prefs, gate);
        listener = new RecordingListener();
        manager.setThemeStateListener(listener);
        original = UIManager.getLookAndFeel();
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        manager.captureStockLaf();
    }

    @AfterEach
    void tearDown() throws Exception {
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
    @DisplayName("leaving dark mode for Vision is a full switch back, squared with the preference")
    void leavingForVisionIsACompleteRestore() {
        prefs.putBoolean(KEY, true);
        manager.applyStartupPreference();
        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf, "precondition");

        manager.leaveDarkForVision("the Vision workspace was opened", false);

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertFalse(prefs.getBoolean(KEY, true),
            "the Designer is light now; the preference must say so");
        assertEquals(List.of(true, false), listener.darkActive);
    }

    @Test
    @DisplayName("leaving dark mode when the Designer is already light does nothing")
    void leavingWhenLightIsANoOp() {
        manager.applyStartupPreference();

        manager.leaveDarkForVision("the Vision workspace was opened", false);

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertEquals(List.of(), listener.darkActive, "no switch happened, so nothing was reported");
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

    private static final class RecordingListener implements ThemeManager.ThemeStateListener {

        private final List<Boolean> darkActive = new ArrayList<>();

        @Override
        public void switchStarted() {
        }

        @Override
        public void switchFinished(boolean dark) {
            darkActive.add(dark);
        }
    }
}
