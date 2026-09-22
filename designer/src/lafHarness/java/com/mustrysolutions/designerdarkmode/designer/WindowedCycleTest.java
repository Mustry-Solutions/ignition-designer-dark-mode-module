package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;
import javax.swing.table.DefaultTableModel;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.client.util.gui.SimpleTreeTable;
import com.jidesoft.docking.DockableFrame;
import com.jidesoft.pane.CollapsiblePane;
import com.jidesoft.pane.CollapsiblePanes;
import com.jidesoft.status.LabelStatusBarItem;
import com.jidesoft.status.StatusBar;
import com.jidesoft.swing.JideTabbedPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The passes that walk {@code Window.getWindows()}, driven against a real
 * {@link JFrame} (#42).
 *
 * <p>Everything else in this harness runs with {@code java.awt.headless=true},
 * and under that flag {@code Window.getWindows()} is always empty. Every pass
 * in the module that starts from it — the white-background swaps and their
 * restore, the cached-painter repoint, the {@code JInternalFrame}
 * neutralisation, the resilient tree walk itself — ran in the headless harness
 * and found nothing, and their tests drove the per-container walks by hand
 * instead. That is a different sequence from {@link ThemeManager#apply}'s
 * own, and the light restore in particular has steps that only exist at the
 * window level.
 *
 * <p>Only <em>forced</em> headless mode blocks windows. With a display present
 * a frame can be built, packed and driven without ever being shown, so this
 * class does exactly that: one frame holding the component shapes the passes
 * exist for, put through {@code apply(true)} and {@code apply(false)} the way
 * a Designer does it, with the assertions on what the components hold
 * afterwards — and, for the restore, on the pixels.
 *
 * <h2>When it runs</h2>
 *
 * <p>With a display. {@code -Pharness.windowed=true} turns the headless flag
 * off; without it this class skips itself. With the property given, a JVM
 * that is still headless is a misconfigured runner (no Xvfb, say), and the
 * class fails rather than skipping, so CI cannot quietly stop covering it.
 *
 * <h2>Pixels</h2>
 *
 * <p>The light theme rendered before a cycle and after it must be the same
 * image. That is the most direct evidence there is that a restore works, and
 * it is the one instrument headless mode structurally lacked: Synthetica's
 * painters ask for a screen device and throw {@code HeadlessException}, so
 * only the dark half could ever be rendered before. It is still not a
 * screenshot test — the reference is the same JVM's own render a moment
 * earlier, so a font, a platform or a one-pixel layout shift cannot fail it.
 */
class WindowedCycleTest {

    /** Set by the lafHarness task when {@code -Pharness.windowed=true}. */
    static final String WINDOWED_PROPERTY = "designerdarkmode.harness.windowed";

    private ThemeManager manager;
    private final List<Window> frames = new ArrayList<>();

    @BeforeAll
    static void requireADisplay() {
        boolean headless = GraphicsEnvironment.isHeadless();
        if (Boolean.getBoolean(WINDOWED_PROPERTY)) {
            assertFalse(headless, "-Pharness.windowed=true was given, but this JVM is headless. "
                + "On Linux the task needs a display (xvfb-run); anywhere else the "
                + "lafHarness task's java.awt.headless setting has been broken.");
        }
        Assumptions.assumeFalse(headless,
            "no display: the window-level passes are not reachable. Run with "
                + "-Pharness.windowed=true on a machine with a display.");
    }

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Throwable {
        onEdt(() -> {
            DesignerLookAndFeel.installStock();
            // Class-init under the light theme, as a Designer does — see
            // TagBrowserHeaderBandTest for why the order matters.
            Class.forName(SimpleTreeTable.class.getName());
            manager = new ThemeManager();
            manager.captureStockLaf();
        });
    }

    @AfterEach
    void leaveTheJvmLightAndWindowless() throws Throwable {
        onEdt(() -> {
            if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
                manager.apply(false);
            }
            for (Window frame : frames) {
                frame.dispose();
            }
            frames.clear();
        });
    }

    @Test
    @DisplayName("a packed, unshown frame is reached by the window walks (the class is not vacuous)")
    void theWalksReachAnUnshownFrame() throws Throwable {
        onEdt(() -> {
            JPanel white = whitePanel();
            JFrame frame = frame(white);

            assertTrue(List.of(Window.getWindows()).contains(frame),
                "Window.getWindows() does not list the frame; nothing below can see it either");
            assertFalse(frame.isShowing(), "the frame must never be shown — a CI runner has "
                + "no one to dismiss it, and a developer's machine should not flash windows");

            manager.apply(true);

            assertEquals(List.of(), manager.failedPhases(),
                "every phase must run here: with windows present the walks do real work, "
                    + "and a phase that throws would quietly reduce the sequence under test");
            assertNotSame(Color.WHITE, white.getBackground(),
                "the white-token swap did not reach a panel inside a packed frame. That pass "
                    + "is the one every other assertion in this class depends on being able "
                    + "to reach a window.");
            assertTrue(ThemeManager.luminance(white.getBackground()) < 128,
                "swapped, but not to a dark colour: " + white.getBackground());
        });
    }

    /**
     * IA hands some components literally {@code Colors.Base000}, which is
     * {@code java.awt.Color.WHITE} itself, and others a literal light neutral
     * (the "SESSION PROPS" header is a {@code JLabel} with {@code #EEEEEF}).
     * The module swaps both per component and puts the ORIGINAL back on the
     * restore — the same instance, not an equal colour. Identity is the
     * contract: a later pass tells IA's white from a user's white by it.
     */
    @Test
    @DisplayName("a swapped white or light-neutral background comes back as the same instance")
    void swappedBackgroundsComeBackAsTheSameInstance() throws Throwable {
        onEdt(() -> {
            JPanel white = whitePanel();
            Color sessionPropsGrey = new Color(0xEEEEEF);
            JLabel header = new JLabel("SESSION PROPS");
            header.setOpaque(true);
            header.setBackground(sessionPropsGrey);
            JPanel row = new JPanel(new GridLayout(1, 0));
            row.add(white);
            row.add(header);
            frame(row);

            manager.apply(true);
            assertNotSame(Color.WHITE, white.getBackground(), "WHITE was not swapped under dark");
            assertNotSame(sessionPropsGrey, header.getBackground(),
                "the explicit light-neutral background was not swapped under dark");

            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases());
            assertSame(Color.WHITE, white.getBackground(),
                "the restore put back a white, but not THE white: " + white.getBackground()
                    + ". The swap tracks the Color.WHITE instance by identity, and so does "
                    + "everything downstream of it.");
            assertSame(sessionPropsGrey, header.getBackground(),
                "the restore put back an equal colour rather than the tracked instance: "
                    + header.getBackground());
        });
    }

    /**
     * JIDE components read {@code Theme.painter} into a private field and paint
     * through it from then on. The module repoints every such field at
     * {@code BasicPainter} under dark and back at the Synthetica entry on the
     * restore (#14, #19), because a {@code SyntheticaJidePainter} left in one
     * casts the active look and feel to Synthetica on every paint.
     *
     * <p>Two things are asserted, and it matters which is which. Every JIDE
     * component in the frame — a dock title pane, a {@code CollapsiblePanes},
     * a {@code StatusBar} — ends up holding the right painter on both sides
     * of the cycle. But a mutation sweep shows those heal without the module:
     * {@code updateComponentTreeUI} rebuilds the title panes and the others
     * re-read the map in {@code updateUI()}. So the outcome holds here for a
     * component the tree update reaches, and says nothing about the pass.
     *
     * <p>The pass's own contract is the second assertion: a JIDE component
     * that does NOT re-read the map is repointed anyway. The stand-in is a
     * {@code StatusBar} whose {@code updateUI()} is a no-op — a component
     * that caches the painter once and is never told again, which is what
     * the dock title panes were in #14 and #19. Break the pass and only that
     * one stays on {@code SyntheticaJidePainter} under dark.
     */
    @Test
    @DisplayName("every cached ThemePainter field is BasicPainter under dark and Synthetica's after the restore")
    void cachedThemePaintersAreRepointedAndRestored() throws Throwable {
        onEdt(() -> {
            JPanel content = new JPanel(new BorderLayout());
            DockableFrame dock = new DockableFrame("dock");
            dock.getContentPane().add(new JLabel("docked"));
            content.add(dock, BorderLayout.WEST);
            CollapsiblePanes panes = new CollapsiblePanes();
            CollapsiblePane pane = new CollapsiblePane("section");
            pane.setContentPane(new JLabel("content"));
            panes.add(pane);
            panes.addExpansion();
            content.add(panes, BorderLayout.CENTER);
            StatusBar status = new StatusBar();
            status.add(new LabelStatusBarItem("status"));
            content.add(status, BorderLayout.SOUTH);
            StatusBar cachesOnce = new StatusBar() {
                @Override
                public void updateUI() {
                    // Read Theme.painter once, at construction, and never
                    // again — the shape the repoint pass exists for.
                    if (getUI() == null) {
                        super.updateUI();
                    }
                }
            };
            cachesOnce.add(new LabelStatusBarItem("cached once"));
            content.add(cachesOnce, BorderLayout.NORTH);
            frame(content);

            Map<Component, Object> stock = painterFields(content);
            assertTrue(stock.size() >= 4, "expected a title pane, the collapsible panes and "
                + "two status bars to cache a painter; found " + stock.keySet());
            String syntheticaPainter = "com.jidesoft.plaf.synthetica.SyntheticaJidePainter";
            stock.forEach((component, painter) -> assertEquals(syntheticaPainter,
                painter.getClass().getName(), "stock painter on " + describe(component)));
            Object cachedOnceStock = stock.get(cachesOnce);
            assertNotNull(cachedOnceStock, "the stand-in never read Theme.painter at all");

            manager.apply(true);
            Map<Component, Object> dark = painterFields(content);
            dark.forEach((component, painter) -> assertEquals(ThemeManager.BASIC_PAINTER,
                painter.getClass().getName(), "under dark, " + describe(component)
                    + " still paints through " + painter.getClass().getSimpleName()
                    + ", which casts the look and feel to Synthetica on every paint"));
            assertEquals(ThemeManager.BASIC_PAINTER, dark.get(cachesOnce).getClass().getName(),
                "the component that never re-reads Theme.painter was not repointed — that is "
                    + "the repoint pass's job, and nothing else does it");

            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases());
            Map<Component, Object> light = painterFields(content);
            light.forEach((component, painter) -> assertEquals(syntheticaPainter,
                painter.getClass().getName(), "after the restore, " + describe(component)
                    + " is still on " + painter.getClass().getSimpleName()));
            assertSame(cachedOnceStock, light.get(cachesOnce),
                "the stand-in got a Synthetica painter back, but not the instance it had");
        });
    }

    /**
     * Vision's {@code DockingInternalFrameUI.installDefaults} — inherited from
     * {@code BasicInternalFrameUI}, so plain Swing reproduces it — does
     * {@code if (contentPane.getBackground() instanceof UIResource)
     * contentPane.setBackground(null)} before installing the layout. A Vision
     * content pane is a {@code BasicContainer} whose {@code setBackground}
     * dereferences its argument, so the null throws, the frame never gets a
     * layout, and every {@code getMinimumSize()} after that NPEs — once per
     * paint. The module removes the condition around {@code updateUI()}
     * rather than catching the throw (see {@code
     * ThemeManager.neutraliseInternalFrameBackground}).
     *
     * <p>The content pane here is the same shape without Vision's jars: a
     * {@code JPanel} that throws on {@code setBackground(null)}. Its background
     * is a {@code UIResource} because the previous theme switch's walk left
     * one there, exactly as in a Designer; the first switch installs it.
     */
    @Test
    @DisplayName("an internal frame whose content pane holds a UIResource background survives the tree update")
    void internalFrameWithUiResourceContentSurvivesTheTreeUpdate() throws Throwable {
        onEdt(() -> {
            JDesktopPane desktop = new JDesktopPane();
            desktop.setPreferredSize(new Dimension(320, 240));
            JInternalFrame inner = new JInternalFrame("window");
            JPanel content = new JPanel() {
                @Override
                public void setBackground(Color background) {
                    if (background == null) {
                        // BasicContainer.setBackground dereferences its argument.
                        throw new NullPointerException("BasicContainer stand-in: null background");
                    }
                    super.setBackground(background);
                }
            };
            inner.setContentPane(content);
            inner.setSize(200, 120);
            inner.setVisible(true);
            desktop.add(inner);
            frame(desktop);
            assertTrue(content.getBackground() instanceof UIResource,
                "the content pane starts with a look-and-feel background: " + content.getBackground());

            manager.apply(true);
            assertEquals(List.of(), manager.failedPhases());
            assertNotNull(inner.getLayout(), "the internal frame lost its layout: installDefaults "
                + "threw part-way through, which is the Vision crash (#39) reproduced in plain Swing");
            assertTrue(content.getBackground() instanceof UIResource,
                "the content pane no longer tracks the look and feel: " + content.getBackground()
                    + ". The neutralisation swaps a plain colour in for the update and must put "
                    + "a UIResource back afterwards, or the pane stays whatever colour it was.");
            assertTrue(ThemeManager.luminance(content.getBackground()) < 128,
                "the content pane did not go dark: " + content.getBackground());
            inner.getMinimumSize(); // NPEs without a layout

            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases());
            assertNotNull(inner.getLayout(), "the internal frame lost its layout on the restore");
            assertTrue(content.getBackground() instanceof UIResource, "after the restore: "
                + content.getBackground());
            assertTrue(ThemeManager.luminance(content.getBackground()) > 128,
                "the content pane did not come back light: " + content.getBackground());
            inner.getMinimumSize();
        });
    }

    /**
     * The light theme, rendered before the cycle and after it, must be the
     * same image. This is the assertion the headless harness could never
     * make, and the one that says the restore is complete rather than
     * merely clean in {@code UIManager}.
     *
     * <p>The baseline is the light theme after one plain
     * {@code SwingUtilities.updateComponentTreeUI}, not the pristine tree.
     * A tree update is not a no-op even with the same look and feel on both
     * sides: JIDE rebuilds a dock frame's title pane inside it, and the
     * title label built that way ends up with Synthetica's {@code
     * Label.font} set on it where the startup-built one inherited the
     * pane's — 137 pixels of a slightly larger "Project Browser" (measured
     * with no module involved at all). What the module owes the Designer is
     * a tree that looks as if the stock look and feel had simply been
     * re-applied, and that is what is compared.
     *
     * <p>Two shapes found on the way to zero, both fixed under this test:
     * the light tree update runs while our renderer wrappers are still on,
     * so the look and feel never reinstalled its own tree and table
     * renderers ({@code TreeIconRecolorer.unwrap}, {@code
     * CellRendererSanitizer.unwrap}). One known residue is kept out of the
     * tree: JIDE's {@code StatusBar} hands its items {@code
     * StatusBarItem.border}, which FlatLaf's extension defines as a plain
     * {@code EtchedBorder} rather than a {@code UIResource}, so Synthetica's
     * reinstall leaves the dark-era border in place. The Designer's status
     * bar is IA's own {@code JPanel}, not JIDE's, so nothing depends on it.
     */
    @Test
    @DisplayName("the light theme renders pixel-identical after a dark cycle")
    void theLightThemeRendersIdenticallyAfterACycle() throws Throwable {
        onEdt(() -> {
            JPanel content = designerLikeContent();
            JFrame frame = frame(content);
            SwingUtilities.updateComponentTreeUI(frame);
            frame.validate();

            BufferedImage before = render(frame);
            manager.apply(true);
            frame.validate();
            BufferedImage dark = render(frame);
            manager.apply(false);
            frame.validate();
            BufferedImage after = render(frame);
            assertEquals(List.of(), manager.failedPhases());

            int changedByDark = differingPixels(before, dark);
            assertTrue(changedByDark > before.getWidth() * before.getHeight() / 2,
                "dark mode changed only " + changedByDark + " pixel(s) of the frame; the cycle "
                    + "did not render both themes, so the identity below would be trivial");

            int leftBehind = differingPixels(before, after);
            if (leftBehind > 0) {
                File dir = saveRenders(before, dark, after);
                assertEquals(0, leftBehind, leftBehind + " pixel(s) differ between the light "
                    + "theme before the cycle and after it, within " + differingBounds(before, after)
                    + ". Renders saved under " + dir + " (light-before, dark, light-after).");
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    /** A panel handed the {@code Color.WHITE} instance, as IA's Base000 token is. */
    private static JPanel whitePanel() {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setPreferredSize(new Dimension(80, 40));
        return panel;
    }

    /**
     * The shapes a Designer window is made of, without a Designer: the Tag
     * Browser's tree-table, a plain table, a tree, a list, text and choice
     * fields, a JIDE dock frame with its title pane, a JIDE tab strip, and
     * the white and light-neutral panels the swap pass is for. Nothing is
     * selected, focused or hovered, so nothing animates.
     */
    private static JPanel designerLikeContent() {
        JPanel content = new JPanel(new BorderLayout());

        JPanel top = new JPanel(new GridLayout(1, 0, 4, 0));
        top.add(whitePanel());
        JLabel header = new JLabel("SESSION PROPS");
        header.setOpaque(true);
        header.setBackground(new Color(0xEEEEEF));
        top.add(header);
        top.add(new JTextField("filter"));
        top.add(new JComboBox<>(new String[] {"one", "two"}));
        top.add(new JCheckBox("Persistent", true));
        content.add(top, BorderLayout.NORTH);

        DockableFrame dock = new DockableFrame("Project Browser");
        dock.getContentPane().setLayout(new BorderLayout());
        dock.getContentPane().add(new JScrollPane(new JTree()));
        dock.setPreferredSize(new Dimension(180, 220));
        content.add(dock, BorderLayout.WEST);

        JideTabbedPane tabs = new JideTabbedPane();
        tabs.addTab("Tags", tagBrowser());
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Name", "Value"}, 0);
        for (int row = 0; row < 5; row++) {
            model.addRow(new Object[] {"row " + row, row});
        }
        tabs.addTab("Query", new JScrollPane(new JTable(model)));
        tabs.setPreferredSize(new Dimension(360, 220));
        content.add(tabs, BorderLayout.CENTER);

        content.add(new JScrollPane(new JList<>(new String[] {"alpha", "beta", "gamma"})),
            BorderLayout.EAST);
        return content;
    }

    /** The Tag Browser's scroll pane, as TagBrowserHeaderBandTest builds it. */
    private static SimpleTreeTable tagBrowser() {
        DefaultTableModel model = new DefaultTableModel(new Object[] {"Value"}, 0);
        for (int row = 0; row < 6; row++) {
            model.addRow(new Object[] {"value " + row});
        }
        JTable table = new JTable(model);
        SimpleTreeTable pane = new SimpleTreeTable(new JTree(), table, "Tag");
        pane.setColumnHeaderView(table.getTableHeader());
        return pane;
    }

    /** A packed frame around the content, never shown, disposed after the test. */
    private JFrame frame(Component content) {
        JFrame frame = new JFrame("WindowedCycleTest");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(content instanceof Container ? (Container) content : wrap(content));
        frame.pack();
        frames.add(frame);
        return frame;
    }

    private static Container wrap(Component component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(component);
        return panel;
    }

    /** Paint the frame's content pane into an image — a real layout, since the frame has a peer. */
    private static BufferedImage render(JFrame frame) {
        Container content = frame.getContentPane();
        BufferedImage image = new BufferedImage(
            Math.max(1, content.getWidth()), Math.max(1, content.getHeight()),
            BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        content.paint(graphics);
        graphics.dispose();
        return image;
    }

    private static int differingPixels(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return Math.max(a.getWidth() * a.getHeight(), b.getWidth() * b.getHeight());
        }
        int count = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String differingBounds(BufferedImage a, BufferedImage b) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < Math.min(a.getHeight(), b.getHeight()); y++) {
            for (int x = 0; x < Math.min(a.getWidth(), b.getWidth()); x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? "(size differs)"
            : "x " + minX + ".." + maxX + ", y " + minY + ".." + maxY;
    }

    /** Next to the harness log, so CI's artifact upload picks them up. */
    private static File saveRenders(BufferedImage before, BufferedImage dark, BufferedImage after) {
        String log = System.getProperty("designerdarkmode.logFile");
        File dir = new File(log != null ? new File(log).getParentFile() : new File("build"),
            "laf-harness-renders");
        dir.mkdirs();
        try {
            ImageIO.write(before, "png", new File(dir, "light-before.png"));
            ImageIO.write(dark, "png", new File(dir, "dark.png"));
            ImageIO.write(after, "png", new File(dir, "light-after.png"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("could not save the renders under " + dir, e);
        }
        return dir;
    }

    /**
     * Every JIDE component under {@code root} that caches a {@code ThemePainter}
     * in a field, mapped to what the field holds — found the way the module
     * finds them, by field type, since JIDE ships obfuscated.
     */
    private static Map<Component, Object> painterFields(Container root) throws Exception {
        Map<Component, Object> found = new LinkedHashMap<>();
        collectPainterFields(root, found);
        return found;
    }

    private static void collectPainterFields(Container root, Map<Component, Object> into)
            throws Exception {
        for (Component child : root.getComponents()) {
            for (Class<?> type = child.getClass(); type != null; type = type.getSuperclass()) {
                if (!type.getName().startsWith("com.jidesoft.")) {
                    continue;
                }
                for (Field field : type.getDeclaredFields()) {
                    if (field.getType().getName().equals(ThemeManager.THEME_PAINTER_TYPE)
                            && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        Object painter = field.get(child);
                        if (painter != null) {
                            into.put(child, painter);
                        }
                    }
                }
            }
            if (child instanceof Container) {
                collectPainterFields((Container) child, into);
            }
        }
    }

    private static String describe(Component component) {
        return component.getClass().getSimpleName().isEmpty()
            ? component.getClass().getSuperclass().getSimpleName() + " (stand-in)"
            : component.getClass().getSimpleName();
    }

    /** Run on the dispatch thread and rethrow whatever it threw, assertions included. */
    private static void onEdt(Executable task) throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                task.execute();
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
    }
}
