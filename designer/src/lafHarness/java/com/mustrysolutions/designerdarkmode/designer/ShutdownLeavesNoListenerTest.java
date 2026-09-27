package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Toolkit;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Module shutdown takes down every AWT listener the switch installed (#147).
 *
 * <p>The light restore INSTALLS a watcher for subtrees attached after it, and
 * {@code shutdown()} used to end with {@code apply(false)} alone: shut down
 * while dark, the restore put that watcher in on the way out; shut down while
 * light after a dark session, {@code apply(false)} returned early and the
 * last restore's watcher stayed. Either way a listener outlived the module,
 * pinning its classloader and refreshing the Designer from unloaded code.
 *
 * <p>Counted as the toolkit's listeners before and after, so listeners other
 * harness tests leave behind do not matter.
 */
class ShutdownLeavesNoListenerTest {

    private ThemeManager manager;
    private int before;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        before = listeners();
    }

    @AfterEach
    void leaveTheJvmLight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
                manager.apply(false);
            }
        });
    }

    @Test
    @DisplayName("shutting down while dark leaves no listener of ours behind")
    void shutdownWhileDark() throws Exception {
        SwingUtilities.invokeAndWait(() -> manager.apply(true));

        SwingUtilities.invokeAndWait(manager::shutdown);

        assertFalse(UIManager.getLookAndFeel() instanceof FlatDarkLaf,
            "shutdown did not put the stock look and feel back");
        assertEquals(before, listeners(),
            "shutdown left an AWT listener installed; the restore it ran installed the "
                + "light-leftover watcher on the way out");
    }

    @Test
    @DisplayName("shutting down while light after a dark session leaves no listener behind")
    void shutdownWhileLightAfterDark() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            manager.apply(false);
        });
        assertEquals(before + 1, listeners(),
            "the light restore no longer installs its watcher, so this test proves nothing");

        SwingUtilities.invokeAndWait(manager::shutdown);

        assertEquals(before, listeners(),
            "shutdown left the watcher the last light restore installed");
    }

    private static int listeners() {
        return Toolkit.getDefaultToolkit().getAWTEventListeners().length;
    }
}
