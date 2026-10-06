package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.swing.CellRendererPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The label a kept combo box paints its value with comes back to the stock look
 * and feel after a dark → light switch, including its text anti-aliasing.
 *
 * <p>Found live in a Linux 8.3.6 Designer: after one dark → light switch the
 * Vision Property Editor drew the window's Titlebar Font value ("Dialog, Bold,
 * 12") with FlatLaf's grayscale text anti-aliasing, where every other cell, and
 * a Designer that was never dark, draws crisp text. The cell is a JIDE
 * {@code ExComboBox} the property table keeps between paints. Its renderer,
 * JIDE's {@code ExComboBoxRenderer}, is not a component: it hands back a label
 * it keeps (Ignition's {@code ComboListCellRenderer}), which the combo box
 * stamps through its own {@code CellRendererPane}. Probed in that Designer, the
 * label still had {@code FlatLabelUI} and FlatLaf's text anti-aliasing hint,
 * which {@code JComponent.setUI} reads from the look and feel and only the next
 * {@code setUI} replaces.
 *
 * <p>Nothing that refreshes the combo box reaches that label.
 * {@code JComboBox.updateUI} refreshes its renderer only when the renderer is
 * itself a component, and the new UI replaces the combo box's renderer pane, so
 * the label leaves the tree with the old pane before the walk gets to it. The
 * light watcher had held the label back for that refresh, as the combo box was
 * attached in the same paint, and does not look at a component twice in a
 * light session: the next paint stamped it into the new pane unnoticed.
 *
 * <p>The combo box here is JIDE's {@code FontExComboBox}, which Vision's font
 * cell extends; Synthetica's UI for it leaves the label in the pane between
 * paints, which is what makes the combo box look stale and get refreshed. A
 * plain {@code JComboBox} under Synthetica does not, and passes either way.
 * Windowed, as the whole switch has to run over a real window.
 */
class KeptComboRendererTest {

    /**
     * Kept combo boxes in the table. One went wrong only in some runs: the
     * watcher looks at what a paint attached in its map's order, which is
     * identity hash order, and when it came to the combo box before the label
     * the refresh had already detached the label, so the label was looked at on
     * its own and came back. With eight, a run where every one comes out right
     * by that luck is too rare to hide the bug.
     */
    private static final int CELLS = 8;

    private ThemeManager manager;
    private final List<Window> frames = new ArrayList<>();

    @BeforeAll
    static void requireADisplay() {
        boolean headless = GraphicsEnvironment.isHeadless();
        if (Boolean.getBoolean(WindowedCycleTest.WINDOWED_PROPERTY)) {
            assertFalse(headless, "-Pharness.windowed=true was given, but this JVM is headless");
        }
        Assumptions.assumeFalse(headless,
            "no display: the switch reaches no window. Run with -Pharness.windowed=true on a "
                + "machine with a display.");
    }

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
    void leaveTheJvmLightAndWindowless() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
                    manager.apply(false);
                }
            } finally {
                frames.forEach(Window::dispose);
                frames.clear();
            }
        });
    }

    @Test
    @DisplayName("the label a kept combo box stamps its value with is back on the stock look and feel after a cycle")
    void keptComboValueLabelIsRestored() throws Exception {
        List<KeptCombo> cells = new ArrayList<>();
        JTable[] table = new JTable[1];
        Object[] stockHint = new Object[1];
        SwingUtilities.invokeAndWait(() -> {
            for (int row = 0; row < CELLS; row++) {
                KeptCombo cell = new KeptCombo();
                cell.build();
                cells.add(cell);
            }
            table[0] = table(cells);
            frame(table[0]);
            paint(table[0]);
            stockHint[0] = new JLabel().getClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING);
            for (KeptCombo cell : cells) {
                cell.label = cell.stamped();
                assertNotNull(cell.label, "painting the table under stock left no label in the "
                    + "combo box's renderer pane, so this test has nothing to follow");
                assertFalse(cell.combo.getRenderer() instanceof Component, "the combo box's "
                    + "renderer is a component, which JComboBox.updateUI refreshes itself; this "
                    + "test is about one that is not");
                assertEquals(stockHint[0], textHint(cell.label), "under stock the kept label "
                    + "does not have a fresh label's text anti-aliasing to begin with");
            }
        });

        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            assertEquals(List.of(), manager.failedPhases());
            paint(table[0]);
        });
        waitForWatcherTicks();
        SwingUtilities.invokeAndWait(() -> {
            paint(table[0]);
            for (KeptCombo cell : cells) {
                assertTrue(ThemeManager.hasStaleOwnUi(cell.label, false),
                    "the kept label is not on a FlatLaf delegate under dark, so this test "
                        + "reproduces nothing: " + cell.label.getUI().getClass().getName());
            }
        });

        // The light restore, then the property table repainting while the
        // watcher works through what each paint attached. Each paint is
        // followed straight away by the watcher's tick, in the same event, so
        // nothing else on the queue decides what the tick finds: left to the
        // timer, this failed in some runs and passed in others, on main and on
        // the commits before #182 and #183 alike.
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases());
            for (int i = 0; i < 3; i++) {
                paint(table[0]);
                lightWatcherTick();
            }
        });

        SwingUtilities.invokeAndWait(() -> {
            for (int row = 0; row < CELLS; row++) {
                JLabel label = cells.get(row).label;
                assertFalse(ThemeManager.hasStaleOwnUi(label, false),
                    "row " + row + ": the label the kept combo box stamps its value with is "
                        + "still on " + label.getUI().getClass().getName()
                        + " after the light restore");
                assertEquals(stockHint[0], textHint(label),
                    "row " + row + ": the kept combo box's value is still drawn with "
                        + "FlatLaf's text anti-aliasing after the light restore, where a "
                        + "never-dark Designer draws it crisp");
            }
        });
    }

    /**
     * The property editor's font cell: JIDE's {@code FontExComboBox}, the class
     * Vision's {@code SimpleFontComboBox} extends, kept by the cell renderer.
     * Its renderer is JIDE's {@code ExComboBoxRenderer}, which is not a
     * component; the label it hands back is found in the combo box's renderer
     * pane once a paint has stamped it.
     */
    private static final class KeptCombo {
        com.jidesoft.combobox.FontExComboBox combo;
        JLabel label;

        void build() {
            combo = new com.jidesoft.combobox.FontExComboBox();
            combo.setSelectedFont(new Font("Dialog", Font.BOLD, 12));
        }

        /** The label the last paint stamped, still in the combo box's pane. */
        JLabel stamped() {
            for (Component child : combo.getComponents()) {
                if (child instanceof CellRendererPane) {
                    for (Component stamp : ((CellRendererPane) child).getComponents()) {
                        if (stamp instanceof JLabel) {
                            return (JLabel) stamp;
                        }
                    }
                }
            }
            return null;
        }
    }

    private static Object textHint(JLabel label) {
        return label.getClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING);
    }

    /** One row per cell, each value painted by that cell's own combo box. */
    private static JTable table(List<KeptCombo> cells) {
        TableCellRenderer keeps =
            (table, value, selected, focused, row, column) -> cells.get(row).combo;
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Property", "Value"}, 0);
        for (int row = 0; row < cells.size(); row++) {
            model.addRow(new Object[] {"Font " + row, "Dialog, Bold, 12"});
        }
        JTable table = new JTable(model) {
            @Override
            public TableCellRenderer getCellRenderer(int row, int column) {
                return column == 1 ? keeps : super.getCellRenderer(row, column);
            }
        };
        table.setRowHeight(24);
        return table;
    }

    private void frame(JTable table) {
        JFrame frame = new JFrame("kept combo renderer");
        frames.add(frame);
        frame.getContentPane().add(new JScrollPane(table));
        frame.pack();
    }

    /** A real paint: the table stamps the combo box, which stamps its label. */
    private static void paint(JTable table) {
        BufferedImage image = new BufferedImage(
            Math.max(1, table.getWidth()), Math.max(1, table.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            table.paint(g);
        } finally {
            g.dispose();
        }
    }

    /** What the light watcher's timer runs when it fires. */
    private void lightWatcherTick() {
        try {
            Field field = ThemeManager.class.getDeclaredField("lightWatcherTimer");
            field.setAccessible(true);
            Timer timer = (Timer) field.get(manager);
            assertNotNull(timer, "the light restore installed no watcher");
            for (ActionListener tick : timer.getActionListeners()) {
                tick.actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, null));
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("the light watcher's timer is out of reach", e);
        }
    }

    /** The watchers debounce on Swing timers of a few hundred milliseconds at most. */
    private static void waitForWatcherTicks() throws Exception {
        Thread.sleep(700);
        SwingUtilities.invokeAndWait(() -> { });
    }
}
