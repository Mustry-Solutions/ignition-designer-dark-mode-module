package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

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

        // And it looks like a stock-built one: the refresh's installBorder
        // treats the field's null as unset, which drew a box round the value.
        assertNull(field.getBorder(),
            "the refresh gave a field that clears its own border the look and feel's "
                + "border, a box a never-dark Designer does not draw: " + field.getBorder());
        assertEquals(new JLabel().getBorder() == null, renderer.cachedLabel().getBorder() == null,
            "a plain label in the same editor did not end up with the border a fresh "
                + "label has under the stock look and feel");
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

        assertEquals(0, manager.refreshStaleAttached(renderer.cachedPanel(), true),
            "a component that was never on FlatLaf was counted as stale");
        assertTrue(stockUi == field.getUI(),
            "a component already on stock delegates was given new ones anyway");
    }

    @Test
    @DisplayName("a late-attached panel outside a renderer pane is refreshed without constructing its classes")
    void aLatePanelIsRefreshedWithoutFreshInstances() throws Exception {
        JPanel host = new JPanel(new BorderLayout());
        JPanel[] late = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            late[0] = new JPanel(new BorderLayout());
            late[0].add(new CountingField(), BorderLayout.CENTER);
            manager.apply(false);
        });
        assertTrue(ThemeManager.hasStaleUi(late[0], false),
            "the panel built under dark mode is not on FlatLaf delegates, so this test "
                + "reproduces nothing");
        int constructedBefore = CountingField.constructed;

        // Attached after the restore, the way a dock panel detached during it is.
        SwingUtilities.invokeAndWait(() -> host.add(late[0], BorderLayout.CENTER));
        waitForWatcherTick();

        assertFalse(ThemeManager.hasStaleUi(late[0], false),
            "the late-attached panel still has a FlatLaf delegate in a light Designer");
        assertEquals(constructedBefore, CountingField.constructed,
            "the refresh built a fresh instance of a class outside a renderer pane: "
                + "Designer chrome whose constructor may have side effects");
    }

    @Test
    @DisplayName("an editor built under stock keeps its own null border through a dark → light cycle")
    void aStockBuiltEditorKeepsItsNullBorderThroughACycle() throws Exception {
        CachingRenderer renderer = new CachingRenderer();
        JTable table = table(renderer);
        JPanel host = host(table);

        // Stock first: the editor is built now, border cleared by its constructor.
        SwingUtilities.invokeAndWait(() -> paint(table));
        JTextField field = renderer.cachedField();
        assertNull(field.getBorder(), "the stock-built field has a border, so this test reproduces nothing");

        // Dark: the renderer pane refreshes the stock editor on the paint that shows it.
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            SwingUtilities.updateComponentTreeUI(host);
            renderers.installIn(host);
            paint(table);
        });
        assertFalse(ThemeManager.hasStaleUi(field, true),
            "the dark paint did not refresh the stock-built editor, so this test reproduces nothing: "
                + field.getUI().getClass().getName());
        assertNull(field.getBorder(),
            "the dark refresh gave a field that clears its own border FlatLaf's border, the box "
                + "that then follows it back to light: " + field.getBorder());

        // And back to light.
        SwingUtilities.invokeAndWait(() -> {
            renderers.unwrap();
            manager.apply(false);
            SwingUtilities.updateComponentTreeUI(host);
            renderers.uninstall();
            paint(table);
        });
        waitForWatcherTick();
        assertFalse(ThemeManager.hasStaleUi(field, false),
            "the editor is not back on stock delegates: " + field.getUI().getClass().getName());
        assertNull(field.getBorder(),
            "after a dark → light cycle the field sits in a box a never-dark Designer does not "
                + "draw: " + field.getBorder());
    }

    @Test
    @DisplayName("a field with no no-arg constructor that clears its border keeps it cleared")
    void aFieldWithoutANoArgConstructorKeepsItsNullBorder() throws Exception {
        CachingRenderer renderer = new CachingRenderer(() -> new BorderlessFormattedField(20));
        JTable table = table(renderer);
        JPanel host = host(table);

        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            SwingUtilities.updateComponentTreeUI(host);
            renderers.installIn(host);
            paint(table);
        });
        JTextField field = renderer.cachedField();
        SwingUtilities.invokeAndWait(() -> {
            renderers.unwrap();
            manager.apply(false);
            SwingUtilities.updateComponentTreeUI(host);
            renderers.uninstall();
            paint(table);
        });
        waitForWatcherTick();

        assertFalse(ThemeManager.hasStaleUi(field, false),
            "the editor is not back on stock delegates: " + field.getUI().getClass().getName());
        assertNull(field.getBorder(),
            "a formatted field whose constructors all take an argument sits in a box after the "
                + "restore (Vision's Titlebar Height, Width, Height): " + field.getBorder());
    }

    /** Counts its constructions; clears its border like Vision's EditorTextField. */
    static final class CountingField extends JTextField {
        static int constructed;

        CountingField() {
            constructed++;
            setBorder(null);
        }
    }

    // --- the shape of Vision's PropertyValueEditor ---------------------------

    /** One editor panel per value type, built on first use and handed back as-is. */
    private static final class CachingRenderer implements TableCellRenderer {
        private final Map<Class<?>, JPanel> editors = new HashMap<>();
        private final java.util.function.Supplier<JTextField> fields;

        CachingRenderer() {
            this(BorderlessField::new);
        }

        CachingRenderer(java.util.function.Supplier<JTextField> fields) {
            this.fields = fields;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JPanel panel = editors.computeIfAbsent(String.class, type -> {
                JPanel editor = new JPanel(new BorderLayout());
                JTextField text = fields.get();
                text.setOpaque(false);
                editor.add(text, BorderLayout.CENTER);
                editor.add(new JLabel("..."), BorderLayout.EAST);
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

        JLabel cachedLabel() {
            return (JLabel) cachedPanel().getComponent(1);
        }
    }

    /**
     * Vision's {@code EditorTextField}: clears its border in its constructor,
     * so a stock-built one has none. Package-private with a no-arg
     * constructor, as the fresh-instance comparison needs.
     */
    static final class BorderlessField extends JTextField {
        BorderlessField() {
            setBorder(null);
        }
    }

    /**
     * Vision's {@code EditorFormattedField}: every constructor takes an
     * argument and clears the border (through {@code init()}), so there is no
     * no-arg constructor to build a fresh one with.
     */
    public static final class BorderlessFormattedField extends javax.swing.JFormattedTextField {
        public BorderlessFormattedField(Object value) {
            super(value);
            setBorder(null);
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
