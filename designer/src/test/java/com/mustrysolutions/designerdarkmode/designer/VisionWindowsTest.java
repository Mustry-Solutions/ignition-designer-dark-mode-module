package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JInternalFrame;
import javax.swing.JPanel;

import com.inductiveautomation.vision.api.client.components.model.TopLevelContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the module recognises Vision content.
 *
 * <p>By name — the {@code TopLevelContainer} interface both windows and
 * templates implement — because the Vision jars are not on this classpath.
 * The stand-in under that name lives in {@code src/test}.
 */
class VisionWindowsTest {

    /** A Vision window, as far as the module is concerned. */
    private static class StandInWindow extends JInternalFrame implements TopLevelContainer {
    }

    /** A Vision template: not a frame at all, just a container that serializes. */
    private static final class StandInTemplate extends JPanel implements TopLevelContainer {
    }

    /** A subclass — Vision's own windows are subclasses of the class that implements it. */
    private static final class StandInSubclass extends StandInWindow {
    }

    @Test
    @DisplayName("windows, templates and their subclasses are Vision; plain Swing is not")
    void recognisesVisionByInterfaceName() {
        assertTrue(VisionWindows.isVisionTopLevel(new StandInWindow()));
        assertTrue(VisionWindows.isVisionTopLevel(new StandInTemplate()));
        assertTrue(VisionWindows.isVisionTopLevel(new StandInSubclass()));
        assertFalse(VisionWindows.isVisionTopLevel(new JPanel()));
        assertFalse(VisionWindows.isVisionTopLevel(new JInternalFrame()));
        assertFalse(VisionWindows.isVisionTopLevel(null));
    }
}
