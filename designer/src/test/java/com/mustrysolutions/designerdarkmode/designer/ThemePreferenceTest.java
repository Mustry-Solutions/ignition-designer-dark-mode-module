package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The saved theme choice: that it is written, and that it is pushed to the
 * backing store rather than left for a timer.
 *
 * <p>The flush is the point. `putBoolean` alone puts the value in an in-memory
 * node and nothing more; on Linux the backing store is
 * {@code FileSystemPreferences}, which writes through only on a 30-second sync
 * timer or a shutdown hook. A Designer force-quit, killed or crashed inside
 * that window lost the toggle, and came back in the theme the user had just
 * changed away from. Windows (registry) and macOS (cfprefsd) persist out of
 * process and hid it.
 *
 * <p>So an assertion on the VALUE alone cannot fail for the bug this covers —
 * the value is in the node either way. These tests assert the flush.
 *
 * <p>Every test runs against {@link RecordingPreferences}, never the real node:
 * the production node is per OS user, so a test using it would toggle the
 * developer's own Designer as a side effect.
 */
class ThemePreferenceTest {

    @Test
    @DisplayName("turning dark mode on saves it and pushes it to the backing store")
    void savesAndFlushesOn() {
        RecordingPreferences prefs = new RecordingPreferences();
        ThemeManager manager = new ThemeManager(prefs);

        manager.setDark(true);

        assertTrue(manager.isDarkModeEnabled(), "the choice should be readable back");
        assertEquals(1, prefs.flushes, "the choice should be flushed, not left to a timer");
    }

    @Test
    @DisplayName("turning dark mode off saves it and pushes it to the backing store")
    void savesAndFlushesOff() {
        RecordingPreferences prefs = new RecordingPreferences();
        ThemeManager manager = new ThemeManager(prefs);

        manager.setDark(true);
        manager.setDark(false);

        assertFalse(manager.isDarkModeEnabled(), "the choice should be readable back");
        assertEquals(2, prefs.flushes, "turning it back off must be as durable as turning it on");
    }

    /**
     * The write happens before the "is the UI up yet?" gate, not after it.
     *
     * <p>{@code setDark} defers the actual switch until the Designer is built,
     * and a click that arrives early is applied by the startup path instead.
     * If the save were deferred with it, that click would be forgotten
     * entirely on a force-quit.
     */
    @Test
    @DisplayName("the choice is saved even when the switch itself is deferred")
    void savesBeforeTheUiReadyGate() {
        RecordingPreferences prefs = new RecordingPreferences();
        // No startup() call, so uiReady is false and the switch is deferred.
        ThemeManager manager = new ThemeManager(prefs);

        manager.setDark(true);

        assertTrue(manager.isDarkModeEnabled());
        assertEquals(1, prefs.flushes);
    }

    @Test
    @DisplayName("an unset preference reads as light")
    void defaultsToLight() {
        assertFalse(new ThemeManager(new RecordingPreferences()).isDarkModeEnabled());
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
        RecordingPreferences prefs = new RecordingPreferences();
        prefs.failFlush = true;
        ThemeManager manager = new ThemeManager(prefs);

        manager.setDark(true);

        assertTrue(manager.isDarkModeEnabled(),
            "the in-memory node is still correct, so the session is unaffected");
        assertEquals(1, prefs.flushes, "the flush should have been attempted");
    }

    /**
     * An in-memory {@link java.util.prefs.Preferences} that counts flushes.
     *
     * <p>Hand-rolled rather than mocked: the project has no mocking framework,
     * and the contract under test is small enough that a real subclass is
     * clearer than a stubbed one.
     */
    private static final class RecordingPreferences extends AbstractPreferences {

        private final Map<String, String> values = new HashMap<>();

        /** How many times the code under test asked for a write-through. */
        int flushes;

        /** Makes the backing store unwritable, as a full disk or bad ACL would. */
        boolean failFlush;

        RecordingPreferences() {
            super(null, "");
        }

        @Override
        public void flush() throws BackingStoreException {
            flushes++;
            if (failFlush) {
                throw new BackingStoreException("backing store unavailable");
            }
            super.flush();
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(new String[0]);
        }

        // No child nodes: ThemeManager only ever uses the one it is handed.
        @Override
        protected String[] childrenNamesSpi() {
            return new String[0];
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            throw new UnsupportedOperationException("child nodes are not used");
        }

        @Override
        protected void removeNodeSpi() {
        }

        @Override
        protected void syncSpi() {
        }

        @Override
        protected void flushSpi() {
        }
    }
}
