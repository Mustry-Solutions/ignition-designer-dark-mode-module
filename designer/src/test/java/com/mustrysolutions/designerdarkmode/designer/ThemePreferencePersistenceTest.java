package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

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

    private Preferences prefs;
    private ThemeManager manager;
    private RecordingListener listener;
    private LookAndFeel original;

    @BeforeEach
    void setUp() throws Exception {
        prefs = new InMemoryPreferences();
        manager = new ThemeManager(prefs);
        listener = new RecordingListener();
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

    /** Records what the Tools menu is told the theme ended up being. */
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
