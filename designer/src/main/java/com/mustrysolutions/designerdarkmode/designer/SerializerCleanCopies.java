package com.mustrysolutions.designerdarkmode.designer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Drops the XML serializer's clean-copy cache when the look and feel changes
 * (#92, part 1).
 *
 * <p>Vision saves a window by serializing every component property that
 * differs from a <em>clean copy</em> of the component's class: a fresh
 * instance, constructed under whatever look and feel is installed at the time
 * and then kept in a static map for the life of the Designer. A clean copy
 * built under Synthetica has a Synthetica border, font and colours; a button
 * saved under FlatLaf differs from it in all of those, so the save writes
 * {@code setFont}, {@code setForeground} and
 * {@code <o cls="com.formdev.flatlaf.ui.FlatButtonBorder"/>} into the window,
 * and a Vision client, which has no FlatLaf, fails to open it. The same
 * happens in the other direction: a copy built under FlatLaf makes every
 * light save carry Synthetica's values. The cache is the mechanism; the look
 * and feel that built it is the bug.
 *
 * <p>The cache is lazy, so the cure is to throw it away at every switch: the
 * next save rebuilds each entry under the look and feel that is then current,
 * which is the one the saved components were also dressed by, and the diff is
 * empty again. Verified headlessly against the real Vision 12.3.8 jars, nine
 * scenarios over a stock &rarr; dark &rarr; stock cycle: with the refresh, no
 * save carried a FlatLaf class name, a {@code setFont} or a look-and-feel
 * colour, while hand-set values still round-tripped. (The write-up is in
 * {@code docs/ARCHITECTURE.md}, under Vision.)
 *
 * <p>Why here and not in {@code DesignerModuleHook.configureSerializer}: that
 * runs on every save, and Vision seeds its own {@code PathBasedVisionShape}
 * entry through {@code setCleanCopy} at the same point, so a clear there would
 * wipe Vision's entry on every save. A switch is the only moment the cache
 * actually goes stale.
 *
 * <p>The map is replaced rather than cleared. A save that is somehow in
 * flight on another thread keeps its own reference and finishes against the
 * copies it started with; its late puts land in the map being discarded, which
 * is harmless. Clearing in place would race that save's {@code HashMap}.
 *
 * <p>This is not the whole of dark mode inside Vision. Nine Vision component
 * classes copy static {@code IgnitionLookAndFeel$Colors} objects into
 * themselves at construction, and the module rewrites those under dark, so a
 * window <em>loaded</em> under dark still writes the dark button foreground
 * into its save — wrong on a light client, not fatal. That and the font
 * staleness after a light restore are parts 2 and 3 of #92, handled by
 * {@link TokenColorDelegate} and the restore's style primer.
 */
final class SerializerCleanCopies {

    /** The serializer every Designer save goes through; in ignition-common. */
    static final String SERIALIZER_CLASS =
        "com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializer";

    /**
     * Its clean-copy cache: {@code private static Map<Class, Object>}, a plain
     * {@code HashMap} built in the static initializer, looked up with
     * {@code get} and seeded with {@code newInstance} on a miss. Pinned by
     * {@code ReflectiveSurfaceTest}.
     */
    static final String CLEAN_MAP_FIELD = "cleanMap";

    private SerializerCleanCopies() {
    }

    /**
     * Replace the cache with an empty one.
     *
     * @return the simple names of the classes whose copies were dropped, for
     *         the log; empty when nothing had been saved yet
     * @throws ReflectiveOperationException when the field is gone or sealed
     *         off: the phase fails visibly rather than leaving a stale cache
     *         behind a passing switch
     */
    static Set<String> refresh() throws ReflectiveOperationException {
        Field field;
        try {
            field = cleanMapField();
        } catch (ClassNotFoundException noSerializer) {
            // Not a Designer: nothing can have cached a clean copy, so there
            // is nothing to refresh. The unit tests run without the platform
            // jars; a real Designer always has this class.
            DebugLog.detail("SerializerCleanCopies: " + SERIALIZER_CLASS
                + " is not on this classpath; nothing to refresh.");
            return Set.of();
        }
        Map<?, ?> stale = (Map<?, ?>) field.get(null);
        field.set(null, new HashMap<>());
        Set<String> dropped = new TreeSet<>();
        if (stale != null) {
            for (Object key : stale.keySet()) {
                dropped.add(key instanceof Class ? ((Class<?>) key).getSimpleName() : String.valueOf(key));
            }
        }
        DebugLog.log("SerializerCleanCopies: dropped " + dropped.size()
            + " clean cop" + (dropped.size() == 1 ? "y" : "ies")
            + (dropped.isEmpty() ? "." : ": " + dropped + "."));
        return dropped;
    }

    /**
     * How many classes the cache currently holds a copy for. A test seam: the
     * harness seeds the cache with a save and asserts a switch empties it.
     */
    static int size() throws ReflectiveOperationException {
        Map<?, ?> map = (Map<?, ?>) cleanMapField().get(null);
        return map == null ? 0 : map.size();
    }

    private static Field cleanMapField() throws ReflectiveOperationException {
        Class<?> serializer = Class.forName(SERIALIZER_CLASS, true,
            SerializerCleanCopies.class.getClassLoader());
        Field field = serializer.getDeclaredField(CLEAN_MAP_FIELD);
        field.setAccessible(true);
        return field;
    }
}
