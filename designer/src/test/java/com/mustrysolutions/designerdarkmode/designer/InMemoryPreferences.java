package com.mustrysolutions.designerdarkmode.designer;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;

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
 */
final class InMemoryPreferences extends AbstractPreferences {

    private final Map<String, String> values = new HashMap<>();

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
    protected void flushSpi() {
    }
}
