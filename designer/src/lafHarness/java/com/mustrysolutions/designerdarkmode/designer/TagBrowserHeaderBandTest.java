package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.client.util.gui.SimpleTreeTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pale band under the Tag Browser's {@code Tag | Value} header (#21), read
 * off the rendered pixels of the real {@link SimpleTreeTable}.
 *
 * <h2>Why this one needs pixels</h2>
 *
 * <p>#21 sat open through roughly thirty component inspections that all came
 * back dark, because the band is painted by something that is not in the
 * component hierarchy at all. {@code SimpleTreeTable$SimpleHeaderRenderer}
 * gives every header cell a compound border whose bottom edge is 8px of {@code
 * Color.WHITE} over 1px of {@code Table.gridColor} — and {@code GRID_COLOR} is
 * a {@code static final} read once at class-init, so it holds the light
 * theme's #C0C5CA for the life of the Designer. A header cell is a rubber
 * stamp: it is configured, stamped through a {@code CellRendererPane}, and
 * never added to anything. Walking components cannot see it, which is exactly
 * why {@code UiDefaultsSnapshot} and the hierarchy walks were both blind here.
 *
 * <p>So this test renders instead. It builds the real scroll pane, drives the
 * module's dark passes over it, paints it into a {@code BufferedImage} and
 * looks for a light horizontal run. That is a capability the rest of the
 * harness deliberately lacks; it is worth the weight only for bugs of this
 * shape, where the wrong colour reaches the screen without passing through any
 * state an assertion could read.
 *
 * <h2>What it is not</h2>
 *
 * <p>Not a screenshot test. Nothing here compares against a reference image, so
 * it cannot fail on a font, a platform or a one-pixel shift: it asserts one
 * property — no long light run in a dark panel — over a component tree it
 * builds itself. A Designer is still the instrument for "does this look right".
 */
class TagBrowserHeaderBandTest {

    /** Light enough to read as a band against #3A3D3F-ish chrome. */
    private static final int LIGHT = 160;

    /**
     * A run this wide cannot be text. The band under the header spans the
     * whole column-header viewport (250px in the tree built below); glyph
     * antialiasing tops out around a dozen pixels of one colour, and the
     * widest run in a passing render is the 8px tree indent guide.
     */
    private static final int BAND = 32;

    private ThemeManager manager;
    private CellRendererSanitizer renderers;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        // Class-init SimpleTreeTable while the LIGHT theme is installed, the
        // way a Designer does at launch. Its GRID_COLOR is captured here and
        // never re-read, and that staleness is half of #21 — a test that first
        // touched the class under dark would quietly test nothing.
        Class.forName(SimpleTreeTable.class.getName());
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
    @DisplayName("no pale band under the Tag Browser header once dark mode is applied")
    void theHeaderBandGoesDark() {
        JPanel panel = tagBrowserPanel();

        assertTrue(ThemeManager.luminance(SimpleTreeTable.GRID_COLOR) > LIGHT,
            "SimpleTreeTable.GRID_COLOR is " + SimpleTreeTable.GRID_COLOR + ", already dark. "
                + "It is captured from Table.gridColor at class-init, so this test is only "
                + "meaningful when the class was first loaded under the light theme.");

        goDark(panel);

        List<String> bands = lightBands(render(panel));
        assertEquals(List.of(), bands,
            "a light horizontal run of " + BAND + "px or more survives dark mode:\n  "
                + String.join("\n  ", bands));
    }

    /**
     * The counterpart the mutation sweep asks for: with the module's passes
     * skipped, the band must be there. Without this, a fix that quietly
     * stopped rendering anything — a layout that collapses to nothing, a paint
     * that throws and is swallowed — would leave the test above green and
     * empty.
     */
    @Test
    @DisplayName("the band IS present when the module does not run (the test is not vacuous)")
    void theBandExistsWithoutTheModule() {
        JPanel panel = tagBrowserPanel();
        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(panel);

        List<String> bands = lightBands(render(panel));
        assertNotEquals(List.of(), bands,
            "FlatDarkLaf alone should leave the header band light. Finding none means the "
                + "render is not exercising SimpleHeaderRenderer, and the passing test above "
                + "proves nothing.");
    }

    /**
     * The other half of the bargain: the borders have to come back.
     *
     * <p>Both swaps replace a {@code Border} on an object that outlives the
     * theme — the tree-header corner is a live component, and the header cell
     * renderer is a singleton the Designer keeps for the session — so a swap
     * without a restore is a dark hairline stuck in the light theme. Asserted
     * by identity: the ORIGINAL instance has to be back, not an equal one.
     */
    @Test
    @DisplayName("the light restore puts the original borders back, by identity")
    void theBordersComeBack() {
        JPanel panel = tagBrowserPanel();
        javax.swing.JComponent corner = corner(panel);
        javax.swing.border.Border stockCorner = corner.getBorder();
        javax.swing.table.TableCellRenderer stockHeader = header(panel).getDefaultRenderer();

        goDark(panel);
        render(panel);

        assertNotEquals(stockCorner, corner.getBorder(),
            "the corner's border was never swapped, so restoring it proves nothing");

        renderers.uninstall();
        manager.apply(false);
        SwingUtilities.updateComponentTreeUI(panel);

        assertSame(stockCorner, corner.getBorder(),
            "the tree-header corner kept a dark border through the light restore");
        assertSame(stockHeader, header(panel).getDefaultRenderer(),
            "the header renderer wrapper survived the light restore");
        // Not re-rendered under light: Synthetica's painters need a screen
        // device, so the light half of a cycle cannot be read off pixels in a
        // headless run. The identity checks above are what this covers.
        assertTrue(ThemeManager.luminance(SimpleTreeTable.GRID_COLOR) > LIGHT,
            "GRID_COLOR itself must never be mutated — it is a shared ColorUIResource, "
                + "and rewriting it would change every grid line in the Designer.");
    }

    private static javax.swing.JComponent corner(JPanel panel) {
        SimpleTreeTable pane = (SimpleTreeTable) panel.getComponent(0);
        return (javax.swing.JComponent) pane.getCorner(
            javax.swing.ScrollPaneConstants.UPPER_LEFT_CORNER);
    }

    private static javax.swing.table.JTableHeader header(JPanel panel) {
        SimpleTreeTable pane = (SimpleTreeTable) panel.getComponent(0);
        return (javax.swing.table.JTableHeader) pane.getColumnHeader().getView();
    }

    /** Everything {@code runThemingPasses} does that can reach a detached tree. */
    private void goDark(JPanel panel) {
        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(panel);
        manager.swapWhiteTokenBackgrounds(panel);
        renderers.install();
        renderers.installIn(panel);
    }

    /**
     * The Tag Browser's scroll pane, as {@code TagTabbedPanel} builds it: a
     * tree in the row header, a values table in the viewport, a shared column
     * header. The models are stand-ins — nothing here depends on tag data —
     * but {@link SimpleTreeTable} itself is the real class, and it is the one
     * that installs the borders #21 is about.
     */
    private static JPanel tagBrowserPanel() {
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Value"}, 0);
        for (int row = 0; row < 6; row++) {
            model.addRow(new Object[] {"value " + row});
        }
        JTable table = new JTable(model);
        SimpleTreeTable pane = new SimpleTreeTable(new JTree(), table, "Tag");
        // What JTable.configureEnclosingScrollPane() does once the Designer's
        // hierarchy is realized. Nothing is realized here, so do it by hand —
        // without it the scroll pane has no column header and the band under
        // test is never painted.
        pane.setColumnHeaderView(table.getTableHeader());

        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(new Color(0x3C3F41));
        panel.add(pane, java.awt.BorderLayout.CENTER);
        panel.setSize(336, 200);
        return panel;
    }

    /**
     * Paint the panel into an image.
     *
     * <p>{@code validate()} is a no-op on a tree with no peer, so the layout is
     * driven by hand. Twice: the scroll pane sizes its row header from the
     * tree's width, which is only known after the first pass.
     */
    private static BufferedImage render(JPanel panel) {
        layout(panel);
        layout(panel);
        BufferedImage image = new BufferedImage(
            panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static void layout(Component component) {
        if (component instanceof Container) {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }

    /** Every horizontal run of one light colour at least {@link #BAND} wide. */
    private static List<String> lightBands(BufferedImage image) {
        List<String> bands = new ArrayList<>();
        for (int y = 0; y < image.getHeight(); y++) {
            int runStart = 0;
            int previous = image.getRGB(0, y);
            for (int x = 1; x <= image.getWidth(); x++) {
                int rgb = x == image.getWidth() ? ~previous : image.getRGB(x, y);
                if (rgb != previous) {
                    if (x - runStart >= BAND
                            && ThemeManager.luminance(new Color(previous)) > LIGHT) {
                        bands.add(String.format("y=%d x=%d..%d #%06X",
                            y, runStart, x - 1, previous & 0xFFFFFF));
                    }
                    runStart = x;
                    previous = rgb;
                }
            }
        }
        return bands;
    }
}
