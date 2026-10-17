package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
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
 * A renderer component the renderer keeps, built under dark and off screen
 * during the light restore, is back on the stock look and feel when the
 * restore is over (#188).
 *
 * <p>Found live in a Linux 8.3.6 Designer: dark, the window's slider selected,
 * the Vision Property Editor scrolled down to its colour rows and back up,
 * light. Vision's {@code PropertyValueEditor} builds one editor panel per
 * property type the first time a paint shows that type, and keeps it, so the
 * colour editors were built under FlatLaf. After the restore ten of their
 * components were still on FlatLaf's delegates ({@code ColorEditor},
 * {@code IconButton}, {@code EditorTextField}, their panels), until a paint
 * showed each one and the light watcher refreshed it, after that paint.
 *
 * <p>The restore could not know them. The light tree update reaches no kept
 * renderer component, no renderer pane stamped them during it, as they were
 * below the fold, and the sanitizer's own restore refreshed only what it had
 * refreshed while dark: components built under the light theme. A component
 * built under dark needed no refresh while dark, so it was in no record.
 *
 * <p>The renderer here has the property editor's shape: a lazily built panel
 * per type, kept in a map, returned as-is, and not itself a component, so
 * {@code JTable.updateUI} does not reach it either. The colour type is used
 * only by the last row. Windowed, as the whole switch has to run over a real
 * window for the sanitizer to intercept the table's renderer pane; the frame
 * is never shown, so nothing paints but the test.
 */
class DarkBuiltRendererTest {

    private static final int ROWS = 40;
    private static final int COLOUR_ROW = ROWS - 1;

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
    @DisplayName("an editor built under dark and off screen during the light restore is on stock delegates when it ends")
    void aDarkBuiltEditorOffScreenIsRestored() throws Exception {
        TypedEditors renderer = new TypedEditors();
        JTable[] table = new JTable[1];
        SwingUtilities.invokeAndWait(() -> {
            table[0] = table(renderer);
            frame(table[0]);
        });

        // Dark, and every row painted: the user scrolling the property editor
        // through, which builds the colour editor under FlatLaf.
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            assertEquals(List.of(), manager.failedPhases());
            paintEveryRow(table[0]);
        });
        JPanel colour = renderer.editor("colour");
        SwingUtilities.invokeAndWait(() -> assertTrue(ThemeManager.hasStaleUi(colour, false),
            "the colour editor built under dark is not on FlatLaf's delegates, so this test "
                + "reproduces nothing: " + colour.getUI().getClass().getName()));

        // Scrolled back to the top for the switch, so the light restore paints
        // nothing of the colour row. Checked in the same event as the restore:
        // a later paint would let the light watcher refresh it, which is the
        // first light paint drawn with FlatLaf's delegates.
        SwingUtilities.invokeAndWait(() -> {
            table[0].scrollRectToVisible(table[0].getCellRect(0, 0, true));
            assertFalse(table[0].getCellRect(COLOUR_ROW, 1, true)
                    .intersects(table[0].getVisibleRect()),
                "the colour row is on screen, so this is not the case of #188");

            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases());

            JTextField field = (JTextField) colour.getComponent(0);
            assertFalse(ThemeManager.hasStaleUi(colour, false),
                "the colour editor built under dark is still on FlatLaf's delegates after the "
                    + "light restore: " + colour.getUI().getClass().getName() + ", its field on "
                    + field.getUI().getClass().getName());
            assertEquals(rgb(UIManager.getColor("TextField.foreground")), rgb(field.getForeground()),
                "the dark-built field kept FlatLaf's near-white text into the light theme");
            // The refresh's installBorder treats the field's null as unset;
            // a never-dark Designer draws no box round the value.
            assertNull(field.getBorder(),
                "the restore gave a field that clears its own border the look and feel's "
                    + "border: " + field.getBorder());
        });
    }

    /**
     * Vision's {@code PropertyValueEditor}: one editor per type, built the
     * first time a cell of that type is rendered, then handed back as-is. Not
     * a component itself, like the real one ({@code AbstractCellEditor}).
     */
    private static final class TypedEditors implements TableCellRenderer {
        private final Map<String, JPanel> editors = new HashMap<>();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focused, int row, int column) {
            JPanel panel = editors.computeIfAbsent(row == COLOUR_ROW ? "colour" : "text", type -> {
                JPanel editor = new JPanel(new BorderLayout());
                editor.add(new CachedEditorAfterRestoreTest.BorderlessField(), BorderLayout.CENTER);
                editor.add(new JLabel("..."), BorderLayout.EAST);
                return editor;
            });
            ((JTextField) panel.getComponent(0)).setText(String.valueOf(value));
            return panel;
        }

        JPanel editor(String type) {
            return editors.get(type);
        }
    }

    private static JTable table(TableCellRenderer renderer) {
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Property", "Value"}, 0);
        for (int row = 0; row < ROWS; row++) {
            model.addRow(new Object[] {"Property " + row, row == COLOUR_ROW ? "250,250,251" : "Slider"});
        }
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(1).setCellRenderer(renderer);
        table.setRowHeight(24);
        return table;
    }

    /** A packed frame, never shown: the switch reaches it, and nothing paints it. */
    private void frame(JTable table) {
        JFrame frame = new JFrame("dark-built renderer");
        frames.add(frame);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(320, 200));
        frame.getContentPane().add(scroll);
        frame.pack();
    }

    /** A real paint of the whole table, below the fold too, through its renderer pane. */
    private static void paintEveryRow(JTable table) {
        BufferedImage image = new BufferedImage(
            Math.max(1, table.getWidth()), Math.max(1, table.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            table.paint(g);
        } finally {
            g.dispose();
        }
    }

    private static int rgb(Color color) {
        return color.getRGB() & 0xFFFFFF;
    }
}
