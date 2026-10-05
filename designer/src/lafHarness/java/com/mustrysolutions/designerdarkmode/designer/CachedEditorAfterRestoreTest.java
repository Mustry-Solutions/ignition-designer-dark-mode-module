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
    @DisplayName("a value field built while dark takes the light colours back, in a renderer that is itself a component (#156)")
    void aFieldBuiltWhileDarkTakesTheLightColoursBack() throws Exception {
        JTable[] table = new JTable[1];
        JPanel[] host = new JPanel[1];
        ComponentRenderer[] renderer = new ComponentRenderer[1];
        // The Tag Editor's shape: the renderer holds the editor panel it hands
        // out, the whole of it is built the first time a dark dialog opens, and
        // it outlives that dialog.
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            renderer[0] = new ComponentRenderer();
            table[0] = table(renderer[0]);
            host[0] = host(table[0]);
            SwingUtilities.updateComponentTreeUI(host[0]);
            renderers.install();
            renderers.installIn(host[0]);
            paint(table[0]);
        });
        JTextField field = renderer[0].field;
        assertFalse(ThemeManager.hasStaleUi(field, true),
            "the field built under dark mode is not on FlatLaf, so this test reproduces nothing");

        SwingUtilities.invokeAndWait(() -> {
            renderers.unwrap();
            manager.apply(false);
            SwingUtilities.updateComponentTreeUI(host[0]);
            renderers.uninstall();
            paint(table[0]);
        });
        waitForWatcherTick();

        assertFalse(ThemeManager.hasStaleUi(field, false),
            "the field is not back on stock delegates: " + field.getUI().getClass().getName());
        assertEquals(stock("TextField.background"), rgb(field.getBackground()),
            "the value field kept FlatLaf's dark background in a light Designer");
        assertEquals(stock("TextField.foreground"), rgb(field.getForeground()),
            "the value field kept FlatLaf's light text in a light Designer");
    }

    @Test
    @DisplayName("a value field built light, refreshed in dark, takes the light colours back on the restore (#156)")
    void aFieldRefreshedInDarkTakesTheLightColoursBack() throws Exception {
        OwnerHost[] owner = new OwnerHost[1];
        JTable[] table = new JTable[1];
        JPanel[] host = new JPanel[1];
        // The Tag Editor opened once in a light Designer: the editor is built
        // on stock delegates and then cached. Its renderer is declared by the
        // panel that owns the table, so it is never wrapped; the dark paint
        // goes through the renderer pane instead.
        SwingUtilities.invokeAndWait(() -> {
            owner[0] = new OwnerHost();
            table[0] = table(owner[0].renderer);
            owner[0].add(new JScrollPane(table[0]), BorderLayout.CENTER);
            owner[0].setSize(320, 120);
            owner[0].doLayout();
            host[0] = owner[0];
            paint(table[0]);
        });
        JTextField field = owner[0].renderer.field;
        assertTrue(ThemeManager.hasStaleUi(field, true),
            "the field is not on stock delegates, so this test reproduces nothing");

        // Dark: the renderer pane refreshes the stock editor on the paint that shows it.
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            SwingUtilities.updateComponentTreeUI(host[0]);
            renderers.install();
            renderers.installIn(host[0]);
            paint(table[0]);
        });
        assertFalse(ThemeManager.hasStaleUi(field, true),
            "the dark paint did not refresh the field onto FlatLaf, so this test reproduces nothing");
        // No stock record was taken: the dialog was closed when Dark Mode went on,
        // as in the live run, so the capture phase saw no table.
        SwingUtilities.invokeAndWait(() -> {
            renderers.unwrap();
            manager.apply(false);
            SwingUtilities.updateComponentTreeUI(host[0]);
            renderers.uninstall();
            paint(table[0]);
        });
        waitForWatcherTick();

        assertFalse(ThemeManager.hasStaleUi(field, false),
            "the field is not back on stock delegates: " + field.getUI().getClass().getName());
        assertEquals(stock("TextField.background"), rgb(field.getBackground()),
            "the value field kept FlatLaf's dark background in a light Designer");
        assertEquals(stock("TextField.foreground"), rgb(field.getForeground()),
            "the value field kept FlatLaf's light text in a light Designer");
    }

    @Test
    @DisplayName("refreshing a stale delegate does not pin the outgoing look and feel's colours (#156)")
    void theRefreshDoesNotPinTheOutgoingColours() throws Exception {
        JPanel[] editor = new JPanel[1];
        javax.swing.JFormattedTextField[] field = new javax.swing.JFormattedTextField[1];
        // A cached editor that spent a dark session on FlatLaf: its delegate
        // and the UIResource colours that delegate installed.
        SwingUtilities.invokeAndWait(() -> {
            editor[0] = new JPanel(new BorderLayout());
            field[0] = new javax.swing.JFormattedTextField("0.0");
            editor[0].add(field[0], BorderLayout.CENTER);
            manager.apply(true);
            SwingUtilities.updateComponentTreeUI(editor[0]);
        });
        assertFalse(ThemeManager.hasStaleUi(field[0], true),
            "the field is not on FlatLaf, so this test reproduces nothing");

        SwingUtilities.invokeAndWait(() -> {
            manager.apply(false);
            // The restore's tree walk never reaches a cached editor; the
            // sanitizer's refresh does, from uninstall() and from a paint.
            renderers.refreshDelegatePreservingColors(editor[0]);
        });

        assertFalse(ThemeManager.hasStaleUi(field[0], false),
            "the refresh left a FlatLaf delegate: " + field[0].getUI().getClass().getName());
        assertEquals(stock("TextField.background"), rgb(field[0].getBackground()),
            "the refresh pinned FlatLaf's dark background onto the field");
        assertEquals(stock("TextField.foreground"), rgb(field[0].getForeground()),
            "the refresh pinned FlatLaf's light text onto the field");
    }

    @Test
    @DisplayName("a cached table-cell label left with a dark UIResource colour is reset when a light paint shows it (#156)")
    void aCachedCellLabelWithDarkColoursIsResetOnALightPaint() throws Exception {
        // JIDE's property table keeps a couple of default-renderer labels inside
        // its name-cell panel and reuses them for every other row. One that went
        // through a dark session kept FlatLaf's colours as UIResources even
        // though its delegate was back on stock: every second label cell dark.
        javax.swing.table.DefaultTableCellRenderer.UIResource label =
            new javax.swing.table.DefaultTableCellRenderer.UIResource();
        JPanel cell = new JPanel(new BorderLayout());
        cell.add(label, BorderLayout.CENTER);
        TableCellRenderer renderer = (table, value, selected, focus, row, column) -> {
            label.setText(String.valueOf(value));
            return cell;
        };
        JTable[] table = new JTable[1];
        JPanel[] host = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            // A restore has happened, so the watcher is running.
            manager.apply(true);
            manager.apply(false);
            table[0] = table(renderer);
            host[0] = host(table[0]);
            label.setBackground(new javax.swing.plaf.ColorUIResource(0x46494B));
            label.setForeground(new javax.swing.plaf.ColorUIResource(0xDDDDDD));
        });
        assertFalse(ThemeManager.hasStaleUi(label, false), "the label is on FlatLaf, so the test reproduces nothing");

        SwingUtilities.invokeAndWait(() -> paint(table[0]));
        waitForWatcherTick();

        SwingUtilities.invokeAndWait(() -> paint(table[0]));
        assertTrue(ThemeManager.luminance(label.getBackground()) > 200,
            "the cached label still paints FlatLaf's dark background in a light Designer: "
                + Integer.toHexString(rgb(label.getBackground())));
        assertTrue(ThemeManager.luminance(label.getForeground()) < 120,
            "the cached label kept FlatLaf's light text in a light Designer: "
                + Integer.toHexString(rgb(label.getForeground())));
    }

    /** Declares its table's renderer, as ConfigPropertyEditPanel declares its EditorRenderer. */
    private static final class OwnerHost extends JPanel {
        final Renderer renderer = new Renderer();

        OwnerHost() {
            super(new BorderLayout());
        }

        private static final class Renderer extends JPanel implements TableCellRenderer {
            final javax.swing.JFormattedTextField field = new javax.swing.JFormattedTextField("0.0");

            Renderer() {
                super(new BorderLayout());
                add(field, BorderLayout.CENTER);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                field.setText(String.valueOf(value));
                return this;
            }
        }
    }

    /** A renderer that is a component and carries its editor, like Ignition's EditorRenderer. */
    private static final class ComponentRenderer extends JPanel implements TableCellRenderer {
        final javax.swing.JFormattedTextField field = new javax.swing.JFormattedTextField("0.0");

        ComponentRenderer() {
            super(new BorderLayout());
            add(field, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            field.setText(String.valueOf(value));
            return this;
        }
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
        });
        // The restore swapped the dark renderer pane out, and the watcher
        // holds containers weakly. Collect it before the light paint: whether
        // the editor was a renderer must not depend on that pane still being
        // reachable at the tick. The macOS CI runner hit exactly this.
        collectGarbage();
        SwingUtilities.invokeAndWait(() -> paint(table));
        waitForWatcherTick();

        assertFalse(ThemeManager.hasStaleUi(field, false),
            "the editor is not back on stock delegates: " + field.getUI().getClass().getName());
        assertNull(field.getBorder(),
            "a formatted field whose constructors all take an argument sits in a box after the "
                + "restore (Vision's Titlebar Height, Width, Height): " + field.getBorder());
    }

    @Test
    @DisplayName("a table repainting faster than the debounce does not postpone the refresh")
    void aRepaintingTableDoesNotStarveTheWatcher() throws Exception {
        CachingRenderer renderer = new CachingRenderer();
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
        });

        // Every paint re-attaches the cached editor. When each attach
        // restarted the 150 ms countdown, a table painting every 50 ms kept
        // the tick from ever firing, and the editor stayed on FlatLaf for as
        // long as the table kept painting (#151 finding 1).
        boolean[] stale = new boolean[1];
        for (int i = 0; i < 20; i++) {
            SwingUtilities.invokeAndWait(() -> paint(table));
            Thread.sleep(50);
        }
        SwingUtilities.invokeAndWait(() -> {
            paint(table);
            stale[0] = ThemeManager.hasStaleUi(field, false);
        });

        assertFalse(stale[0],
            "a second of repaints every 50 ms, and the cached editor is still on a FlatLaf "
                + "delegate: repaints are postponing the watcher's tick again");
    }

    @Test
    @DisplayName("a tick that only saw stock renderers does not walk the windows")
    void aRendererOnlyTickDoesNotWalkTheWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            manager.apply(true);
            manager.apply(false);
        });
        // Built under light, so the table's renderers are on stock delegates.
        // Building it attaches a renderer pane, viewport and so on: not
        // renderers, so that tick walks.
        JTable[] table = new JTable[1];
        JPanel[] host = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            table[0] = new JTable(new DefaultTableModel(
                new Object[][] {{"Name", "Table"}}, new Object[] {"Property", "Value"}));
            table[0].setSize(300, 60);
            host[0] = host(table[0]);
        });
        waitForWatcherTick();
        int walks = manager.lightLeftoverWalks();

        // Painting attaches only renderer components, none of them stale.
        SwingUtilities.invokeAndWait(() -> paint(table[0]));
        waitForWatcherTick();
        assertEquals(walks, manager.lightLeftoverWalks(),
            "a tick that saw only stock renderer components walked every window for dark "
                + "leftovers, which every table paint in a light Designer would then pay for "
                + "(#151 finding 2)");

        // Control: a non-renderer attached does walk, so the count is live.
        SwingUtilities.invokeAndWait(() -> host[0].add(new JPanel(), BorderLayout.SOUTH));
        waitForWatcherTick();
        assertTrue(manager.lightLeftoverWalks() > walks,
            "a non-renderer attached after the restore did not walk the windows, so the "
                + "assertion above proves nothing");
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

    /** A best-effort collection; enough to clear a weakly held, unreachable pane. */
    private static void collectGarbage() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            System.gc();
            Thread.sleep(50);
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
