package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.AWTEventListenerProxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Shuts down every {@link ThemeManager} a harness test made, and fails the
 * test if it still leaves an AWT listener behind (#179).
 *
 * <p>A light restore installs the light-leftover watcher: a toolkit-wide AWT
 * listener and a 150 ms timer that refreshes components on the EDT. Only
 * {@code shutdown()} takes them down, which a Designer calls once at module
 * shutdown and the tests never did. Every test that ran a restore left one
 * more watcher live for the rest of the JVM, and the next test's
 * {@code installStock()} attached components that woke all of them. Their
 * ticks then ran tree updates on the EDT while the test thread went on
 * setting up, and on macOS CI one of them stalled in {@code UIDefaults} past
 * the 60 s timeout and took six later tests with it.
 *
 * <p>Registered for every harness test by JUnit's extension autodetection
 * (see {@code designer/build.gradle.kts}), so a test only has to make its
 * managers through {@link #newManager()}. The after-each callback runs after
 * the test's own {@code @AfterEach}, so a test that ends dark is restored
 * there first, as before.
 */
public final class ManagerCleanup implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(ManagerCleanup.class);

    private static final List<ThemeManager> MANAGERS = new ArrayList<>();

    /** A new manager that is shut down when the current test ends. */
    static ThemeManager newManager() {
        ThemeManager manager = new ThemeManager();
        synchronized (MANAGERS) {
            MANAGERS.add(manager);
        }
        return manager;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getStore(NAMESPACE).put("listeners", moduleListeners());
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        List<ThemeManager> made;
        synchronized (MANAGERS) {
            made = new ArrayList<>(MANAGERS);
            MANAGERS.clear();
        }
        SwingUtilities.invokeAndWait(() -> made.forEach(ThemeManager::shutdown));

        List<AWTEventListener> left = moduleListeners();
        left.removeAll(context.getStore(NAMESPACE).get("listeners", List.class));
        if (!left.isEmpty()) {
            throw new AssertionError("the test left " + left.size() + " of the module's AWT "
                + "listeners installed (" + left.stream().map(l -> l.getClass().getName())
                    .collect(Collectors.joining(", "))
                + "). Make its ThemeManager with ManagerCleanup.newManager(), or take the "
                + "listener down in @AfterEach.");
        }
    }

    /**
     * The module's own toolkit listeners. Swing's are left out: it adds one
     * lazily, the first time a look and feel builds a popup menu UI. The
     * toolkit wraps each listener in a fresh proxy on every call, hence the
     * unwrap.
     */
    private static List<AWTEventListener> moduleListeners() {
        return Arrays.stream(Toolkit.getDefaultToolkit().getAWTEventListeners())
            .map(l -> l instanceof AWTEventListenerProxy ? ((AWTEventListenerProxy) l).getListener() : l)
            .filter(l -> l.getClass().getName().startsWith("com.mustrysolutions."))
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
