package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ThemeManager#updateComponentTreeUiResiliently} — that one component
 * throwing out of {@code updateUI()} costs only that component.
 *
 * <p>Swing's own {@code updateComponentTreeUI} is an unguarded recursion, so
 * the first throw abandons the rest of the tree. On the light restore the tree
 * that aborts is the main Designer frame's, and everything below the throwing
 * component keeps the outgoing look and feel's delegates. Two Ignition classes
 * are known to throw there and neither is fixable from this module, so the
 * blast radius is what this covers.
 */
class ResilientTreeUpdateTest {

    /** Counts the {@code updateUI()} calls that arrive after construction. */
    private static class CountingPanel extends JPanel {

        transient int updates;
        private transient boolean armed;

        CountingPanel() {
            armed = true;
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (armed) {
                updates++;
            }
        }
    }

    /** Throws the way {@code DockingInternalFrameUI.installDefaults} does. */
    private static class ThrowingPanel extends JPanel {

        private transient boolean armed;

        ThrowingPanel() {
            armed = true;
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (armed) {
                throw new NullPointerException(
                    "Cannot invoke \"java.awt.Color.getAlpha()\" because \"newColor\" is null");
            }
        }
    }

    @Test
    @DisplayName("a component that throws does not cost its siblings")
    void siblingsAfterAThrowAreStillUpdated() {
        JPanel root = new JPanel();
        CountingPanel before = new CountingPanel();
        CountingPanel after = new CountingPanel();
        root.add(before);
        root.add(new ThrowingPanel());
        root.add(after);

        Set<String> failed = new LinkedHashSet<>();
        int failures = ThemeManager.updateComponentTreeUiResiliently(root, failed);

        assertEquals(1, failures);
        assertEquals(1, before.updates, "the sibling before the throw");
        assertEquals(1, after.updates,
            "the sibling after the throw — this is the one Swing loses");
    }

    @Test
    @DisplayName("a component that throws does not cost its own children")
    void theSubtreeUnderAThrowIsStillWalked() {
        JPanel root = new JPanel();
        ThrowingPanel throwing = new ThrowingPanel();
        CountingPanel nested = new CountingPanel();
        throwing.add(nested);
        root.add(throwing);

        Set<String> failed = new LinkedHashSet<>();
        ThemeManager.updateComponentTreeUiResiliently(root, failed);

        assertEquals(1, nested.updates,
            "a broken parent does not make its children unthemeable");
    }

    @Test
    @DisplayName("repeated failures of one class are collected once")
    void distinctFailingClassesAreCollected() {
        JPanel root = new JPanel();
        for (int i = 0; i < 5; i++) {
            root.add(new ThrowingPanel());
        }

        Set<String> failed = new LinkedHashSet<>();
        int failures = ThemeManager.updateComponentTreeUiResiliently(root, failed);

        assertEquals(5, failures, "every failure is counted");
        assertEquals(1, failed.size(), "but one class is named once, not five times");
        assertTrue(failed.iterator().next().endsWith("ThrowingPanel"), failed.toString());
    }

    @Test
    @DisplayName("menu items are reached — they hang off the popup, not getComponents()")
    void menuItemsAreWalked() {
        JMenu menu = new JMenu("Tools");
        CountingPanel inMenu = new CountingPanel();
        menu.add(new JMenuItem("Dark Mode"));
        menu.add(inMenu);

        Set<String> failed = new LinkedHashSet<>();
        ThemeManager.updateComponentTreeUiResiliently(menu, failed);

        assertEquals(0, failed.size());
        assertEquals(1, inMenu.updates);
    }

    @Test
    @DisplayName("Swing's own walk really does lose the sibling (this test is not vacuous)")
    void swingsOwnWalkAbandonsTheRestOfTheTree() {
        JPanel root = new JPanel();
        CountingPanel before = new CountingPanel();
        CountingPanel after = new CountingPanel();
        root.add(before);
        root.add(new ThrowingPanel());
        root.add(after);

        assertThrows(NullPointerException.class,
            () -> javax.swing.SwingUtilities.updateComponentTreeUI(root),
            "Swing propagates the throw rather than containing it");
        assertEquals(1, before.updates, "everything before the throw is updated");
        assertEquals(0, after.updates,
            "and everything after it is not — the loss this class exists to prevent");
    }

    @Test
    @DisplayName("a clean tree reports nothing")
    void aCleanTreeReportsNoFailures() {
        JPanel root = new JPanel();
        CountingPanel child = new CountingPanel();
        root.add(child);

        Set<String> failed = new LinkedHashSet<>();

        assertEquals(0, ThemeManager.updateComponentTreeUiResiliently(root, failed));
        assertEquals(0, failed.size());
        assertEquals(1, child.updates);
    }

    // --- Vision internal frames -------------------------------------------

    /** A content pane that cannot take null, the way Vision's BasicContainer cannot. */
    private static class NullHostileContentPane extends JPanel {

        @Override
        public void setBackground(java.awt.Color bg) {
            if (bg == null) {
                throw new NullPointerException(
                    "Cannot invoke \"java.awt.Color.getAlpha()\" because \"newColor\" is null");
            }
            super.setBackground(bg);
        }
    }

    /**
     * A stand-in for a Vision window that models the two things that matter.
     *
     * <p>First, {@code DockingInternalFrameUI.installDefaults} in the order the
     * bytecode of {@code vision-client-12.3.6.jar} has it: null the content
     * pane's background if it is a {@code UIResource} (offset 58-60), and only
     * then install the layout (offset 76) — so the layout is lost when the
     * first step throws.
     *
     * <p>Second, that a UI delegate installs the layout with root-pane
     * checking off, which is what {@code JInternalFrame.setUI} arranges. With
     * it on, {@code setLayout} forwards to the content pane and the frame's own
     * layout is never touched — a detail that made the first version of these
     * tests assert nothing.
     */
    private static class VisionLikeFrame extends javax.swing.JInternalFrame {

        VisionLikeFrame() {
            super("Main Window");
            setContentPane(new NullHostileContentPane());
            getContentPane().setBackground(new javax.swing.plaf.ColorUIResource(0xEEEEEE));
            withoutRootPaneChecking(() -> setLayout(null));
        }

        void installDefaultsLikeVision() {
            withoutRootPaneChecking(() -> {
                java.awt.Container contentPane = getContentPane();
                if (contentPane.getBackground() instanceof javax.swing.plaf.UIResource) {
                    contentPane.setBackground(null);
                }
                setLayout(new java.awt.BorderLayout());
            });
        }

        private void withoutRootPaneChecking(Runnable task) {
            boolean checking = isRootPaneCheckingEnabled();
            try {
                setRootPaneCheckingEnabled(false);
                task.run();
            } finally {
                setRootPaneCheckingEnabled(checking);
            }
        }
    }

    @Test
    @DisplayName("the shape being guarded: a throw before setLayout leaves a null layout")
    void theUnpreventedShapeLosesTheLayout() {
        VisionLikeFrame frame = new VisionLikeFrame();

        assertThrows(NullPointerException.class, frame::installDefaultsLikeVision);
        assertNull(frame.getLayout(),
            "this is the state that makes every later getMinimumSize() throw");
    }

    @Test
    @DisplayName("a plain-Color background lets installDefaults run to completion")
    void neutralisingTheBackgroundPreventsTheThrow() {
        VisionLikeFrame frame = new VisionLikeFrame();
        java.awt.Color original = frame.getContentPane().getBackground();

        // What the walk now does before updateUI().
        frame.getContentPane().setBackground(new java.awt.Color(original.getRGB(), true));
        frame.installDefaultsLikeVision();

        assertNotNull(frame.getLayout(), "installDefaults should have reached setLayout");
        assertEquals(original.getRGB(), frame.getContentPane().getBackground().getRGB(),
            "the colour must not change, only its type");
    }

    @Test
    @DisplayName("the walk leaves the content pane tracking the look and feel again")
    void theBackgroundIsRestoredAsAUiResource() {
        VisionLikeFrame frame = new VisionLikeFrame();
        java.awt.Color before = frame.getContentPane().getBackground();
        JPanel root = new JPanel();
        root.add(frame);

        ThemeManager.updateComponentTreeUiResiliently(root, new LinkedHashSet<>());

        java.awt.Color after = frame.getContentPane().getBackground();
        assertTrue(after instanceof javax.swing.plaf.UIResource,
            "left as " + after.getClass().getName() + "; a plain Color would be pinned"
                + " forever, since installColorsAndFont only replaces UIResources");
        assertEquals(before.getRGB(), after.getRGB());
    }


    /** The FilterablePalette shape: reading the children throws. */
    private static class HostileContainer extends JPanel {

        @Override
        public java.awt.Component[] getComponents() {
            throw new UnsupportedOperationException("components is write-only");
        }
    }

    @Test
    @DisplayName("a container that throws when read costs only its own subtree")
    void aContainerThatCannotBeReadDoesNotAbortTheWalk() {
        JPanel root = new JPanel();
        CountingPanel before = new CountingPanel();
        CountingPanel after = new CountingPanel();
        root.add(before);
        root.add(new HostileContainer());
        root.add(after);

        Set<String> failed = new LinkedHashSet<>();
        ThemeManager.updateComponentTreeUiResiliently(root, failed);

        assertEquals(1, before.updates);
        assertEquals(1, after.updates,
            "the Exchange script records FilterablePalette as throwing on component"
                + " access, and this walk reaches the Perspective palette");
    }

}
