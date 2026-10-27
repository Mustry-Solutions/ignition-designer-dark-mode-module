package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one piece of state this module keeps between launches: which theme the
 * user chose.
 *
 * <p>The rule worth the tests is that the saved value follows the look and feel
 * actually INSTALLED, not the one the user asked for. A theme switch is a long
 * sequence that can fail halfway through, and a failure that still saved "dark"
 * would come back at every launch — the same broken switch, against a Designer
 * the user can no longer read, with no obvious way out.
 *
 * <p>The other rule is that every write is flushed, not left for a timer. On
 * Linux the backing store is {@code FileSystemPreferences}, which writes
 * through only on a 30-second sync timer or a shutdown hook; a Designer
 * force-quit, killed or crashed inside that window came back in the theme the
 * user had just changed away from. Windows (registry) and macOS (cfprefsd)
 * persist out of process and hid it. The value is in the node either way, so
 * those tests assert the flush count rather than the value.
 *
 * <p>Everything here runs against an {@link InMemoryPreferences} node rather
 * than the developer's own. What the switch DOES to the Designer is the
 * headless harness's job ({@code src/lafHarness}); this covers the wiring
 * around it.
 */
class ThemePreferencePersistenceTest {

    /**
     * The stored key. Renaming it does not migrate anyone — it silently resets
     * every existing install to light — so it is pinned here as a value, not
     * read back off the class under test.
     */
    private static final String KEY = "darkMode";

    /** Match System's key, pinned for the same reason. */
    private static final String FOLLOW_SYSTEM = "followSystem";

    private InMemoryPreferences prefs;
    private ThemeManager manager;
    private RecordingThemeStateListener listener;
    private LookAndFeel original;

    @BeforeEach
    void setUp() throws Exception {
        prefs = new InMemoryPreferences();
        manager = new ThemeManager(prefs);
        listener = new RecordingThemeStateListener();
        manager.setThemeStateListener(listener);

        original = UIManager.getLookAndFeel();
        // A known not-dark look and feel: the assertions below turn on FlatLaf
        // being absent or present, and inheriting whatever an earlier test left
        // installed would make them mean nothing. Standing in for the Designer's
        // Synthetica, which is not on this source set's classpath by design.
        UIManager.setLookAndFeel(new MetalLookAndFeel());
        manager.captureStockLaf();
    }

    @AfterEach
    void tearDown() throws Exception {
        // One test drives a real switch, and dark mode writes ~200 UIManager
        // developer defaults. The light half of the switch is what clears them
        // again, so unwind through it rather than dropping the look and feel
        // back and leaving them behind for the next test.
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        if (original != null) {
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    @DisplayName("setDark writes the preference and isDarkModeEnabled reads it back")
    void setDarkRoundTrips() {
        assertFalse(manager.isDarkModeEnabled(),
            "a Designer that has never been told otherwise launches light");

        manager.setDark(true);

        assertTrue(manager.isDarkModeEnabled());
        assertTrue(prefs.getBoolean(KEY, false),
            "the choice must land under \"" + KEY + "\"; a Designer restarted "
                + "tomorrow reads that key and nothing else");

        manager.setDark(false);

        assertFalse(manager.isDarkModeEnabled());
        assertFalse(prefs.getBoolean(KEY, true));
    }

    /**
     * The write happens before the "is the UI up yet?" gate, not after it:
     * {@code startup} was never called here, so {@code setDark} defers the
     * switch itself. If the save were deferred with it, a click that arrived
     * early would be forgotten entirely on a force-quit.
     */
    @Test
    @DisplayName("setDark flushes the preference, on and off, even when the switch is deferred")
    void setDarkFlushesEveryWrite() {
        manager.setDark(true);
        assertEquals(1, prefs.flushes, "the choice should be flushed, not left to a timer");

        manager.setDark(false);
        assertEquals(2, prefs.flushes, "turning it back off must be as durable as turning it on");
    }

    @Test
    @DisplayName("finishSwitch flushes the value it reconciles")
    void finishSwitchFlushes() {
        manager.finishSwitch();

        assertEquals(1, prefs.flushes,
            "the reconciled value is the one the next launch reads, so it must reach disk too");
    }

    /**
     * A backing store that cannot be written is not a reason to break the
     * toggle. {@code setDark} runs off the Tools menu click, so an exception
     * escaping it would leave the menu item and the Designer disagreeing —
     * a worse outcome than a preference that may not outlive the session.
     */
    @Test
    @DisplayName("a failing backing store does not break the toggle")
    void toleratesAFailingBackingStore() {
        prefs.failFlush = true;

        manager.setDark(true);

        assertTrue(manager.isDarkModeEnabled(),
            "the in-memory node is still correct, so the session is unaffected");
        assertEquals(1, prefs.flushes, "the flush should have been attempted");
    }

    @Test
    @DisplayName("a switch that failed to install dark does not leave dark saved")
    void finishSwitchFollowsWhatIsInstalledNotWhatWasAsked() {
        // What setDark(true) wrote when the user ticked the menu item, before
        // the switch went on to fail.
        prefs.putBoolean(KEY, true);

        manager.finishSwitch();

        assertFalse(manager.isDarkModeEnabled(),
            "dark was never installed, so the next launch must not try again");
        assertEquals(List.of(false), listener.darkActive,
            "the Tools menu must not carry a checkmark for a theme the Designer is not in");
    }

    @Test
    @DisplayName("a switch that did install dark saves dark")
    void finishSwitchSavesDarkWhenDarkIsInstalled() throws Exception {
        UIManager.setLookAndFeel(new FlatDarkLaf());

        manager.finishSwitch();

        assertTrue(manager.isDarkModeEnabled());
        assertEquals(List.of(true), listener.darkActive);
    }

    @Test
    @DisplayName("the startup apply leaves a light Designer alone")
    void startupAppliesNothingWhenThePreferenceIsLight() {
        manager.applyStartupPreference();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf,
            "every launch runs this; a Designer that never chose dark must come up untouched");
        assertFalse(manager.isDarkModeEnabled());
    }

    /**
     * The switch itself is the harness's subject, not this one's — what is
     * asserted here is only that the saved preference is what reaches it.
     */
    @Test
    @DisplayName("the startup apply applies dark when the preference says dark")
    void startupAppliesDarkWhenThePreferenceIsSet() {
        prefs.putBoolean(KEY, true);

        manager.applyStartupPreference();

        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf,
            "the saved choice is applied at launch, or dark mode lasts one session");
        assertTrue(manager.isDarkModeEnabled(),
            "the switch worked, so the preference it is squared with must survive it");
        assertEquals(List.of(true), listener.darkActive);
    }

    // ---- Match System ----------------------------------------------------

    /** A manager whose system reads as {@code appearance}, sharing this test's node. */
    private ThemeManager systemManager(SystemTheme.Appearance appearance) {
        ThemeManager system = new ThemeManager(prefs, () -> appearance);
        system.setThemeStateListener(listener);
        system.captureStockLaf();
        manager = system;
        return system;
    }

    @Test
    @DisplayName("a Designer that has never been told otherwise is Light")
    void lightIsTheDefault() {
        assertEquals(ThemeMode.LIGHT, manager.getThemeMode());
    }

    @Test
    @DisplayName("Match System is saved under its own key, flushed, and read back")
    void matchSystemRoundTrips() {
        manager.setThemeMode(ThemeMode.SYSTEM);

        assertEquals(ThemeMode.SYSTEM, manager.getThemeMode());
        assertTrue(prefs.getBoolean(FOLLOW_SYSTEM, false));
        assertEquals(1, prefs.flushes, "as durable as Light and Dark");
    }

    @Test
    @DisplayName("choosing Light or Dark leaves Match System")
    void anExplicitChoiceLeavesMatchSystem() {
        manager.setThemeMode(ThemeMode.SYSTEM);

        manager.setDark(true);

        assertEquals(ThemeMode.DARK, manager.getThemeMode());
        assertFalse(prefs.getBoolean(FOLLOW_SYSTEM, true));

        manager.setThemeMode(ThemeMode.SYSTEM);
        manager.setThemeMode(ThemeMode.LIGHT);

        assertEquals(ThemeMode.LIGHT, manager.getThemeMode());
    }

    @Test
    @DisplayName("the startup apply under Match System applies dark when the system is dark")
    void startupFollowsADarkSystem() {
        prefs.putBoolean(FOLLOW_SYSTEM, true);
        ThemeManager system = systemManager(SystemTheme.Appearance.DARK);

        system.applyStartupPreference();

        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertEquals(ThemeMode.SYSTEM, system.getThemeMode(),
            "a switch that worked must not take the mode out of Match System");
        assertTrue(prefs.getBoolean(KEY, false),
            "the dark value follows the installed theme, for a module version "
                + "that predates Match System");
        assertEquals(List.of(true), listener.darkActive);
    }

    /**
     * Last session the system was dark, so dark was saved. Today it is
     * light: the Designer stays stock, and the saved dark value follows, so a
     * downgrade to a module without Match System does not come up dark.
     */
    @Test
    @DisplayName("the startup apply under Match System leaves the Designer stock when the system is light")
    void startupFollowsALightSystem() {
        prefs.putBoolean(FOLLOW_SYSTEM, true);
        prefs.putBoolean(KEY, true);
        ThemeManager system = systemManager(SystemTheme.Appearance.LIGHT);

        system.applyStartupPreference();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf);
        assertEquals(ThemeMode.SYSTEM, system.getThemeMode());
        assertFalse(prefs.getBoolean(KEY, true));
    }

    @Test
    @DisplayName("a system that cannot be read is treated as light")
    void unknownIsLight() {
        prefs.putBoolean(FOLLOW_SYSTEM, true);
        ThemeManager system = systemManager(SystemTheme.Appearance.UNKNOWN);

        system.applyStartupPreference();

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf,
            "the stock Designer is the safe answer to a question nobody could answer");
        assertEquals(ThemeMode.SYSTEM, system.getThemeMode(),
            "the choice stays: tomorrow the system may be readable");
    }

    /**
     * The same rule as an explicit choice: the saved value follows the theme
     * installed. A Match System switch that did not reach the system's theme
     * would otherwise be retried at every launch — for a dark system, the
     * same broken dark switch the rule exists to stop.
     */
    @Test
    @DisplayName("a Match System switch that did not complete leaves Match System for the installed theme")
    void aFailedMatchSystemSwitchSettlesOnWhatIsInstalled() throws Exception {
        prefs.putBoolean(FOLLOW_SYSTEM, true);
        ThemeManager system = systemManager(SystemTheme.Appearance.LIGHT);
        system.applyStartupPreference();
        // The light switch the system asked for, failed: dark is still installed.
        UIManager.setLookAndFeel(new FlatDarkLaf());

        system.finishSwitch();

        assertEquals(ThemeMode.DARK, system.getThemeMode(),
            "the Tools menu must show the theme the Designer is in");
        assertEquals(List.of(true), listener.darkActive);
    }

}
