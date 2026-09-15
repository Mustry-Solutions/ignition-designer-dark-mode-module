package com.mustrysolutions.designerdarkmode.designer;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * A {@link java.util.prefs.Preferences} node that lives and dies with the test.
 *
 * <p>{@link ThemeManager}'s production node is
 * {@code Preferences.userNodeForPackage} — the very node the Designer on this
 * machine reads at launch — so a test that wrote to it would flip the dark mode
 * of whoever ran the build. Nothing here touches the user preference store, and
 * on a CI box with no writable one there is nothing to warn about either.
 *
 * <p>Flat by design: the module stores exactly one key.
 *
 * <p>It also counts flushes, because on Linux a value that is only PUT is not
 * yet on disk: {@code FileSystemPreferences} writes through on a 30-second
 * timer or a shutdown hook, and a force-quit inside that window loses it. A
 * test that only read the value back could not tell the difference.
 */
final class InMemoryPreferences extends AbstractPreferences {

    private final Map<String, String> values = new HashMap<>();

    /** How many times the code under test asked for a write-through. */
    int flushes;

    /** Makes the backing store unwritable, as a full disk or bad ACL would. */
    boolean failFlush;

    InMemoryPreferences() {
        super(null, "");
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

    @Override
    protected String[] childrenNamesSpi() {
        return new String[0];
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        throw new UnsupportedOperationException("the theme preference has no child nodes");
    }

    @Override
    protected void removeNodeSpi() {
        values.clear();
    }

    @Override
    protected void syncSpi() {
    }

    @Override
    protected void flushSpi() throws BackingStoreException {
        flushes++;
        if (failFlush) {
            throw new BackingStoreException("backing store unavailable");
        }
    }
}
