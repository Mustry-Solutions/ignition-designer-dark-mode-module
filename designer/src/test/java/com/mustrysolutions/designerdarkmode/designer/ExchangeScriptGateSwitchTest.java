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
 * What the Exchange script gate's verdict does to a switch.
 *
 * <p>The verdict itself is {@link ExchangeScriptGateTest}'s subject; here it
 * is dictated, and what is asserted is the wiring: a click with the script's
 * checkbox ticked is refused and leaves the preference and the menu agreeing
 * with the light theme, a startup with it ticked keeps the preference, the
 * delayed check drops dark mode when the script is dark and only warns when
 * it is merely present, and a tick on the script's checkbox after the check
 * drops dark mode too. Same shape as {@link VisionGateSwitchTest}.
 */
class ExchangeScriptGateSwitchTest {

    private static final String KEY = "darkMode";

    private Preferences prefs;
    private DictatedScriptGate gate;
    private ThemeManager manager;
    private RecordingThemeStateListener listener;
    private LookAndFeel original;

    @BeforeEach
    void setUp() throws Exception {
        prefs = new InMemoryPreferences();
        gate = new DictatedScriptGate();
        manager = new ThemeManager(prefs, new SilentVisionGate(), gate);
        listener = new RecordingThemeStateListener();
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
    @DisplayName("a click with the script's checkbox ticked leaves the Designer light, unticks the menu and resets the preference")
    void refusedSwitchLeavesEverythingLight() throws Exception {
        manager.applyStartupPreference();
        gate.ticked = true;

        manager.setDark(true);
        drainEventQueue();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertFalse(prefs.getBoolean(KEY, true),
            "setDark wrote dark before the switch was refused; the refusal must undo that");
        assertEquals(List.of(false), listener.darkActive,
            "the menu must not stay ticked for a theme that was refused");
        assertEquals(List.of(ExchangeScriptGate.refusalMessage(DictatedScriptGate.REASON)),
            gate.explained, "the user clicked, so the user is told why nothing happened");
    }

    @Test
    @DisplayName("the script merely present, unticked: the switch goes ahead")
    void presentButUntickedDoesNotBlock() throws Exception {
        manager.applyStartupPreference();
        gate.present = true;
        gate.ticked = false;

        manager.setDark(true);
        drainEventQueue();

        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertTrue(prefs.getBoolean(KEY, false));
        assertEquals(List.of(true), listener.darkActive);
        assertEquals(List.of(), gate.explained);
    }

    @Test
    @DisplayName("a startup with the script's checkbox ticked keeps the preference for a project without it")
    void blockedStartupKeepsThePreference() {
        prefs.putBoolean(KEY, true);
        gate.ticked = true;

        manager.applyStartupPreference();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertTrue(prefs.getBoolean(KEY, false),
            "nothing failed; this project happens to carry the script ticked");
        assertEquals(List.of(false), listener.darkActive, "the menu follows the theme on screen");
    }

    @Test
    @DisplayName("the delayed check finds the script ticked under dark mode: dark mode ends, preference kept")
    void delayedCheckDropsOutWhenScriptIsDark() throws Exception {
        prefs.putBoolean(KEY, true);
        manager.applyStartupPreference();
        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf, "precondition");

        // The script's tag fired after our startup apply, with startupInDarkMode edited to True.
        gate.present = true;
        gate.ticked = true;
        SwingUtilities.invokeAndWait(manager::checkForExchangeScript);
        drainEventQueue();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertTrue(prefs.getBoolean(KEY, false), "a drop-out keeps the preference");
        assertEquals(List.of(true, false), listener.darkActive);
        assertEquals(List.of(ExchangeScriptGate.dropOutMessage(DictatedScriptGate.REASON)), gate.explained,
            "told that dark mode was turned off, not that it was refused");
        assertTrue(gate.watching, "and the checkbox is watched from here on");
    }

    @Test
    @DisplayName("the delayed check finds the script present but unticked: dark mode stays, the checkbox is watched")
    void delayedCheckOnlyWarnsWhenScriptIsLight() throws Exception {
        prefs.putBoolean(KEY, true);
        manager.applyStartupPreference();
        gate.present = true;
        gate.ticked = false;

        SwingUtilities.invokeAndWait(manager::checkForExchangeScript);
        drainEventQueue();

        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf, "a warning is not a switch");
        assertEquals(List.of(true), listener.darkActive);
        assertEquals(List.of(), gate.explained, "a warning gets the status bar and the log, not a dialog");
        assertTrue(gate.watching);
    }

    @Test
    @DisplayName("ticking the script's checkbox after the check drops dark mode")
    void tickAfterCheckDropsOut() throws Exception {
        prefs.putBoolean(KEY, true);
        manager.applyStartupPreference();
        gate.present = true;
        SwingUtilities.invokeAndWait(manager::checkForExchangeScript);
        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf, "precondition");

        SwingUtilities.invokeAndWait(gate::tick);
        drainEventQueue();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertTrue(prefs.getBoolean(KEY, false));
        assertEquals(List.of(true, false), listener.darkActive);
        assertEquals(1, gate.explained.size());
    }

    @Test
    @DisplayName("no script at all: the delayed check does nothing")
    void delayedCheckWithNoScriptIsANoOp() throws Exception {
        prefs.putBoolean(KEY, true);
        manager.applyStartupPreference();

        SwingUtilities.invokeAndWait(manager::checkForExchangeScript);

        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertEquals(List.of(true), listener.darkActive);
        assertFalse(gate.watching, "nothing to watch");
    }

    @Test
    @DisplayName("a Vision block outranks the script block: the user gets the Vision explanation")
    void visionExplanationWinsWhenBothBlock() throws Exception {
        VisionGateStub vision = new VisionGateStub();
        vision.reason = "the Vision workspace is selected";
        manager = new ThemeManager(prefs, vision, gate);
        manager.setThemeStateListener(listener);
        manager.captureStockLaf();
        manager.applyStartupPreference();
        gate.ticked = true;

        manager.setDark(true);
        drainEventQueue();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertEquals(List.of(VisionGate.refusalMessage(vision.reason)), vision.explained);
        assertEquals(List.of(), gate.explained, "one explanation, the one that protects saved resources");
    }

    /** Two turns: setDark defers to the EDT, and beginSwitch defers apply one more turn. */
    private static void drainEventQueue() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** A Vision gate that never blocks and never watches. */
    private static class SilentVisionGate extends VisionGate {

        @Override
        String blockingReason() {
            return null;
        }

        @Override
        void watchNavigation(Runnable onVisionActivated) {
        }
    }

    /** A Vision gate that says what the test tells it to. */
    private static final class VisionGateStub extends SilentVisionGate {

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
    }

    /** A script gate that says what the test tells it to, and records what it was asked to explain. */
    private static final class DictatedScriptGate extends ExchangeScriptGate {

        static final String REASON = "the Exchange script's View → Dark Mode is ticked";

        private boolean present;
        private boolean ticked;
        private boolean watching;
        private Runnable onTick;
        private final List<String> explained = new ArrayList<>();

        @Override
        String blockingReason() {
            return ticked ? REASON : null;
        }

        @Override
        boolean scriptPresent() {
            return present || ticked;
        }

        @Override
        void explain(String message) {
            explained.add(message);
        }

        @Override
        boolean watchCheckbox(Runnable onScriptDarkOn) {
            if (!scriptPresent()) {
                return false;
            }
            watching = true;
            onTick = onScriptDarkOn;
            return true;
        }

        /** The user ticks View → Dark Mode. */
        void tick() {
            ticked = true;
            if (onTick != null) {
                onTick.run();
            }
        }
    }
}
