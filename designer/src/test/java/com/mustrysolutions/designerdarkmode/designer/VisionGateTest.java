package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Container;
import java.util.List;

import javax.swing.JInternalFrame;
import javax.swing.JPanel;

import com.inductiveautomation.vision.api.client.components.model.TopLevelContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How the gate recognises Vision, and what it says when it does.
 *
 * <p>Vision is recognised by name — the {@code TopLevelContainer} interface
 * both windows and templates implement — because the Vision jars are not on
 * this classpath. The stand-in under that name lives in {@code src/test}.
 */
class VisionGateTest {

    /** A Vision window, as far as the gate is concerned. */
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
        assertTrue(VisionGate.isVisionTopLevel(new StandInWindow()));
        assertTrue(VisionGate.isVisionTopLevel(new StandInTemplate()));
        assertTrue(VisionGate.isVisionTopLevel(new StandInSubclass()));
        assertFalse(VisionGate.isVisionTopLevel(new JPanel()));
        assertFalse(VisionGate.isVisionTopLevel(new JInternalFrame()));
        assertFalse(VisionGate.isVisionTopLevel(null));
    }

    @Test
    @DisplayName("the attach-time search finds a window within its depth and not beyond it")
    void attachSearchIsBounded() {
        StandInWindow window = new StandInWindow();
        JPanel level3 = new JPanel();
        level3.add(window);
        JPanel level2 = new JPanel();
        level2.add(level3);
        JPanel level1 = new JPanel();
        level1.add(level2);
        JPanel root = new JPanel();
        root.add(level1);

        assertSame(window, VisionGate.findVisionTopLevel(root, 4));
        assertNull(VisionGate.findVisionTopLevel(root, 3),
            "four levels down is out of a three-level search");
        assertSame(window, VisionGate.findVisionTopLevel(window, 0),
            "the attached component itself is always checked");
    }

    @Test
    @DisplayName("counting sees every open window and template, wherever they are docked")
    void countsAcrossTheTree() {
        JPanel desktop = new JPanel();
        desktop.add(new StandInWindow());
        desktop.add(new StandInWindow());
        JPanel elsewhere = new JPanel();
        elsewhere.add(new StandInTemplate());
        JPanel root = new JPanel();
        root.add(desktop);
        root.add(elsewhere);
        root.add(new JPanel());

        assertEquals(3, VisionGate.countVisionTopLevels(root));
        assertEquals(0, VisionGate.countVisionTopLevels(new JPanel()));
    }

    @Test
    @DisplayName("open windows block, and the reason counts them")
    void openWindowsBlock() {
        JPanel root = new JPanel();
        root.add(new StandInWindow());
        VisionGate gate = gateOver(root, null);

        assertEquals("a Vision window or template is open", gate.blockingReason());

        root.add(new StandInTemplate());
        assertEquals("2 Vision windows or templates are open", gate.blockingReason());
    }

    @Test
    @DisplayName("the Vision workspace being selected blocks even with nothing open")
    void selectedWorkspaceBlocks() {
        assertEquals("the Vision workspace is selected",
            gateOver(new JPanel(), "windows").blockingReason());
        assertEquals("the Vision workspace is selected",
            gateOver(new JPanel(), "WINDOWS").blockingReason(),
            "the Designer compares workspace keys case-insensitively, so must we");
    }

    @Test
    @DisplayName("nothing Vision in play: no reason to refuse")
    void nothingVisionDoesNotBlock() {
        assertNull(gateOver(new JPanel(), "perspective").blockingReason());
        assertNull(gateOver(new JPanel(), null).blockingReason());
        assertNull(new VisionGate().blockingReason(), "no frame yet: nothing to refuse over");
    }

    @Test
    @DisplayName("the messages name the reason and, after the fact, the reopen advice")
    void messagesCarryTheReason() {
        String refused = VisionGate.refusalMessage("the Vision workspace is selected");
        assertTrue(refused.startsWith("Dark mode was not applied: the Vision workspace is selected."), refused);

        String left = VisionGate.dropOutMessage("the Vision workspace was opened", false);
        assertTrue(left.startsWith("Dark mode was turned off because the Vision workspace was opened."), left);
        assertFalse(left.contains("reopen"), "nothing was opened under dark mode; no reopen advice");

        String late = VisionGate.dropOutMessage("a Vision window or template was opened", true);
        assertTrue(late.contains("Close and reopen the window"), late);
    }

    /** A gate over a fixed tree and a fixed selected-workspace answer. */
    private static VisionGate gateOver(Container root, String selectedWorkspace) {
        return new VisionGate() {
            @Override
            List<Container> roots() {
                return List.of(root);
            }

            @Override
            String selectedWorkspaceKey() {
                return selectedWorkspace;
            }
        };
    }
}
