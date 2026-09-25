package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A renderer component the renderer CACHES, built while the Designer was dark,
 * and painted again after the light restore.
 *
 * <p>This is the Vision Property Editor after a dark → light switch: the value
 * column read #DDDDDD on #F4F4F4 (1.23:1) where a Designer that was never dark
 * shows #2E2E2E on white. Vision's {@code PropertyValueEditor} keeps one editor
 * panel per property type in its own tables and hands the same panel back for
 * every paint of that type. Probed live on 8.1.50 after the restore, the panel
 * and its {@code EditorTextField} still carried {@code FlatPanelUI} /
 * {@code FlatTextFieldUI} and FlatLaf's foreground, under Synthetica, with no
 * parent: a cached renderer component is never in the hierarchy when the
 * restore's {@code updateComponentTreeUI} walks it.
 *
 * <p>Going the other way already worked, because while dark every table paints
 * through {@code SanitizingCellRendererPane}, which refreshes a stale delegate
 * on the paint that shows it. Nothing did that in light mode. The light-mode
 * watcher now does: {@code CellRendererPane} ADDS each renderer component to
 * itself to paint it, and the watcher already hears every addition.
 *
 * <p>The renderer here has Vision's shape: a lazily built panel per type, kept
 * in a map, returned as-is. Nothing in it refers to Vision.
 */
class CachedEditorAfterRestoreTest {

    private ThemeManager manager;
    private CellRendererSanitizer renderers;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        renderers = new CellRendererSanitizer();
    }

    @AfterEach
    void leaveTheJvmLight() {
        renderers.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("an editor panel cached while dark is on stock delegates once a light paint shows it")
    void aCachedEditorIsRefreshedWhenPainted() throws Exception {
        CachingRenderer renderer = new CachingRenderer();
        JTable table = table(renderer);
        JPanel host = host(table);

        // Dark first, and the editor for this row's type is built now, the
        // way a Designer that starts dark builds the property editor.
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            SwingUtilities.updateComponentTreeUI(host);
            renderers.installIn(host);
            paint(table);
        });
        JTextField field = renderer.cachedField();
        assertTrue(ThemeManager.hasStaleUi(field, false),
            "the editor built under dark mode is not on FlatLaf delegates, so this test "
                + "reproduces nothing: " + field.getUI().getClass().getName());

        // The light restore, in ThemeManager.apply's order, over the host.
        SwingUtilities.invokeAndWait(() -> {
            renderers.unwrap();
            manager.apply(false);
            SwingUtilities.updateComponentTreeUI(host);
            renderers.uninstall();
        });
        assertTrue(ThemeManager.hasStaleUi(field, false),
            "the restore's tree walk reached the cached editor after all; the gap this "
                + "test pins is no longer there, so re-check what it guards");

        // A light paint shows the cached editor again. The watcher hears the
        // renderer pane adding it and refreshes it on its next tick.
        SwingUtilities.invokeAndWait(() -> paint(table));
        waitForWatcherTick();

        assertFalse(ThemeManager.hasStaleUi(field, false),
            "the cached editor still paints with a FlatLaf delegate in a light Designer: "
                + field.getUI().getClass().getName());
        assertEquals(stock("TextField.foreground"), rgb(field.getForeground()),
            "the cached editor's text kept FlatLaf's dark-theme foreground, near-white "
                + "on a light row");
    }

    @Test
    @DisplayName("the refresh leaves an editor on stock delegates alone")
    void aStockEditorIsLeftAlone() throws Exception {
        CachingRenderer renderer = new CachingRenderer();
        JTable table = table(renderer);
        host(table);
        SwingUtilities.invokeAndWait(() -> paint(table));
        JTextField field = renderer.cachedField();
        Object stockUi = field.getUI();

        assertEquals(0, manager.refreshStaleAttached(renderer.cachedPanel()),
            "a component that was never on FlatLaf was counted as stale");
        assertTrue(stockUi == field.getUI(),
            "a component already on stock delegates was given new ones anyway");
    }

    // --- the shape of Vision's PropertyValueEditor ---------------------------

    /** One editor panel per value type, built on first use and handed back as-is. */
    private static final class CachingRenderer implements TableCellRenderer {
        private final Map<Class<?>, JPanel> editors = new HashMap<>();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JPanel panel = editors.computeIfAbsent(String.class, type -> {
                JPanel editor = new JPanel(new BorderLayout());
                JTextField text = new JTextField();
                text.setOpaque(false);
                text.setBorder(null);
                editor.add(text, BorderLayout.CENTER);
                return editor;
            });
            ((JTextField) panel.getComponent(0)).setText(String.valueOf(value));
            return panel;
        }

        JPanel cachedPanel() {
            return editors.get(String.class);
        }

        JTextField cachedField() {
            return (JTextField) cachedPanel().getComponent(0);
        }
    }

    private static JTable table(TableCellRenderer renderer) {
        JTable table = new JTable(new DefaultTableModel(
            new Object[][] {{"Name", "Table"}}, new Object[] {"Property", "Value"}));
        table.getColumnModel().getColumn(1).setCellRenderer(renderer);
        table.setSize(300, 60);
        return table;
    }

    private static JPanel host(JTable table) {
        JPanel host = new JPanel(new BorderLayout());
        host.add(new JScrollPane(table), BorderLayout.CENTER);
        host.setSize(320, 120);
        host.doLayout();
        return host;
    }

    /** A real paint: BasicTableUI hands every cell to its CellRendererPane. */
    private static void paint(JTable table) {
        BufferedImage image = new BufferedImage(300, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            table.paint(g);
        } finally {
            g.dispose();
        }
    }

    /** The light watcher debounces on a 150 ms Swing timer. */
    private static void waitForWatcherTick() throws Exception {
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static int stock(String key) {
        return rgb(UIManager.getColor(key));
    }

    private static int rgb(Color color) {
        return color.getRGB() & 0xFFFFFF;
    }
}
