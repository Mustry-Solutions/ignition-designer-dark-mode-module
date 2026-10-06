package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import javax.swing.CellRendererPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The light watcher's tick, when a component and its ancestor were attached
 * in the same burst and the ancestor's refresh detaches the child.
 *
 * <p>The tick refreshes from the top: a child is held back for its pending
 * ancestor, whose refresh covers it. A combo box whose renderer is not a
 * component breaks that: its new UI swaps in a new renderer pane, and the label
 * it keeps leaves with the old one before the walk gets to it
 * ({@link KeptComboRendererTest}, which needs a window). What such a refresh
 * detaches goes round again here, and these tests pin how: still from the top,
 * with the child's own context, and never a second try for a child the
 * ancestor's refresh did reach.
 *
 * <p>{@link SwappingHost} stands in for the combo box: its {@code updateUI}
 * replaces its renderer pane, as {@code BasicComboBoxUI} does. Headless: the
 * tick is run directly, right after the components are attached, so the order
 * the watcher saw them in is the only thing left to chance.
 */
class HeldBackChildTest {

    /**
     * Groups attached in one burst. The tick's map iterates in identity hash
     * order; with this many, a run where every group comes out in the order
     * that hides a bug is too rare to matter.
     */
    private static final int GROUPS = 16;

    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                DesignerLookAndFeel.installStock();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            manager = ManagerCleanup.newManager();
            manager.captureStockLaf();
        });
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
    @DisplayName("a renderer panel detached with its field is refreshed before the field, with its renderer context")
    void aDetachedSubtreeIsRefreshedFromTheTop() throws Exception {
        List<SwappingHost> hosts = new ArrayList<>();
        List<JPanel> panels = new ArrayList<>();
        List<BorderlessField> fields = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            for (int i = 0; i < GROUPS; i++) {
                hosts.add(new SwappingHost());
                panels.add(new JPanel());
                fields.add(new BorderlessField());
            }
            manager.apply(false);
            assertTrue(ThemeManager.hasStaleOwnUi(fields.get(0), false),
                "a field built under dark is not on a FlatLaf delegate, so this test "
                    + "reproduces nothing: " + fields.get(0).getUI().getClass().getName());

            // One burst: the field into its panel, the panel into the host's
            // renderer pane (a renderer), the host into a plain container.
            JPanel root = new JPanel();
            for (int i = 0; i < GROUPS; i++) {
                panels.get(i).add(fields.get(i));
                hosts.get(i).pane.add(panels.get(i));
                root.add(hosts.get(i));
            }
            manager.lightWatcherTick();
        });

        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < GROUPS; i++) {
                assertFalse(SwingUtilities.isDescendingFrom(panels.get(i), hosts.get(i)),
                    "the host's refresh did not detach its renderer panel, so this test "
                        + "reproduces nothing");
                assertFalse(ThemeManager.hasStaleUi(panels.get(i), false),
                    "group " + i + ": the detached renderer panel is still on FlatLaf");
                // Refreshed on its own first, the field got the look and
                // feel's border, and the panel's renderer step, which keeps a
                // null border null, no longer saw it as borderless.
                assertNull(fields.get(i).getBorder(),
                    "group " + i + ": the field was refreshed before the renderer panel it "
                        + "sits in, without the renderer context that keeps its own null "
                        + "border, and is drawn boxed: " + fields.get(i).getBorder());
            }
        });
    }

    @Test
    @DisplayName("a held-back child whose updateUI throws is tried once, not again on its own")
    void aChildTheRefreshReachedIsNotTriedAgain() throws Exception {
        List<ThrowingPanel> children = new ArrayList<>();
        List<JPanel> parents = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            for (int i = 0; i < GROUPS; i++) {
                parents.add(new JPanel());
                children.add(new ThrowingPanel());
            }
            manager.apply(false);
            JPanel root = new JPanel();
            for (int i = 0; i < GROUPS; i++) {
                children.get(i).failing = true;
                parents.get(i).add(children.get(i));
                root.add(parents.get(i));
            }
            manager.lightWatcherTick();
        });

        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < GROUPS; i++) {
                assertTrue(SwingUtilities.isDescendingFrom(children.get(i), parents.get(i)),
                    "the child left its parent, so this test checks nothing");
                assertEquals(1, children.get(i).attempts,
                    "group " + i + ": the parent's refresh reached the child, whose updateUI "
                        + "threw, and the tick tried it again on its own: one more failure "
                        + "logged, and the windows walked for nothing");
            }
        });
    }

    @Test
    @DisplayName("a detached renderer label on a stock delegate still gets a dark session's colours reset")
    void aDetachedRendererLabelHasItsDarkColoursReset() throws Exception {
        List<SwappingHost> hosts = new ArrayList<>();
        List<DefaultTableCellRenderer> labels = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            for (int i = 0; i < GROUPS; i++) {
                hosts.add(new SwappingHost());
            }
            manager.apply(false);
            JPanel root = new JPanel();
            for (int i = 0; i < GROUPS; i++) {
                // #156: back on a stock delegate, with a dark session's
                // UIResource background still set.
                DefaultTableCellRenderer label = new DefaultTableCellRenderer();
                label.setBackground(new ColorUIResource(new Color(0x2B2B2B)));
                labels.add(label);
                hosts.get(i).pane.add(label);
                root.add(hosts.get(i));
            }
            manager.lightWatcherTick();
        });

        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < GROUPS; i++) {
                assertFalse(SwingUtilities.isDescendingFrom(labels.get(i), hosts.get(i)),
                    "the host's refresh did not detach the label, so this test reproduces "
                        + "nothing");
                assertFalse(labels.get(i).isBackgroundSet(),
                    "group " + i + ": a renderer label the host's refresh detached kept a dark "
                        + "session's background: its delegate is stock, and the tick looked "
                        + "only for a stale delegate before the renderer step that resets it");
            }
        });
    }

    /** A combo box's shape: each new UI comes with a new renderer pane. */
    static final class SwappingHost extends JPanel {
        CellRendererPane pane;

        @Override
        public void updateUI() {
            super.updateUI();
            if (pane != null) {
                remove(pane);
            }
            pane = new CellRendererPane();
            add(pane);
        }
    }

    /** Vision's EditorTextField: clears its own border in its constructor. */
    static final class BorderlessField extends JTextField {
        BorderlessField() {
            setBorder(null);
        }
    }

    /** Throws before setUI, so it stays on the delegate it had: Ignition's #12 NPE. */
    static final class ThrowingPanel extends JPanel {
        boolean failing;
        int attempts;

        @Override
        public void updateUI() {
            if (failing) {
                attempts++;
                throw new IllegalStateException("updateUI fails, as Ignition's #12 NPE does");
            }
            super.updateUI();
        }
    }
}
