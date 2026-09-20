package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jidesoft.swing.JideTabbedPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The workspace tab strip — the row of open-resource tabs along the bottom of
 * the Perspective, script, named query, report and Web Dev editors — on
 * Windows (#81).
 *
 * <p>{@code TabbedResourceWorkspace} is a {@link JideTabbedPane}. Under dark
 * mode the module reinstalls the JIDE extension with {@code VSNET_STYLE}, and
 * because JIDE does not recognise FlatLaf, {@code LookAndFeelFactory} picks
 * the table of defaults for that style <em>by operating system</em>:
 * {@code VsnetMetalUtils} everywhere but Windows, which derives the tab colours
 * from the look and feel's {@code control*} colours and so comes out dark; and
 * {@code VsnetWindowsUtils} on Windows, which reads the OS's own 3D colours
 * through {@code WindowsDesktopProperty} — {@code win.3d.lightColor}
 * (#E3E3E3), {@code win.3d.shadowColor} (#A0A0A0), {@code win.button.textColor}
 * (black) — and never consults FlatLaf at all. The selected tab is filled from
 * {@code JideTabbedPane.light}, so on Windows it stayed #E3E3E3 under a dark
 * strip, while the label on it was lifted to light along with every other
 * dark foreground. The reporter's screenshots are exactly those two colours.
 *
 * <h2>Simulating Windows headlessly</h2>
 *
 * <p>The OS branch is {@code SystemInfo.isWindows()}, a plain static boolean,
 * and the colour lookups go through {@code Toolkit.getDesktopProperty}, which
 * reads the default toolkit's {@code desktopProperties} map first. Flipping the
 * boolean and seeding that map with the standard Windows light-theme 3D
 * colours reproduces the Windows table on any OS, byte for byte: before the
 * fix this test rendered the selected tab at #E3E3E3 on this Mac. Both are
 * put back in {@link #leaveTheJvmLight()} (restoring any real desktop
 * properties the spoof displaced) so the rest of the harness is unaffected.
 *
 * <p>The pane overrides {@code isDragOverDisabled()} because
 * {@code BasicJideTabbedPaneUI.installListeners} otherwise registers a
 * {@code DropTarget}, whose constructor throws {@code HeadlessException}. Only
 * the dark half is rendered: the stock Synthetica delegate needs a screen
 * device to paint.
 */
class WorkspaceTabStripTest {

    /**
     * What {@code Toolkit.getDesktopProperty} answers on a Windows 11 machine
     * with the default light theme (Win32 {@code GetSysColor}: 3DFACE, 3DLIGHT,
     * 3DHILIGHT, 3DSHADOW, 3DDKSHADOW, BTNTEXT and friends). Only the ones
     * {@code VsnetWindowsUtils} reads.
     */
    private static final Map<String, Object> WINDOWS_DESKTOP = new LinkedHashMap<>();
    static {
        WINDOWS_DESKTOP.put("win.3d.backgroundColor", new Color(0xF0F0F0));
        WINDOWS_DESKTOP.put("win.3d.lightColor", new Color(0xE3E3E3));
        WINDOWS_DESKTOP.put("win.3d.highlightColor", new Color(0xFFFFFF));
        WINDOWS_DESKTOP.put("win.3d.shadowColor", new Color(0xA0A0A0));
        WINDOWS_DESKTOP.put("win.3d.darkShadowColor", new Color(0x696969));
        WINDOWS_DESKTOP.put("win.button.textColor", new Color(0x000000));
        WINDOWS_DESKTOP.put("win.frame.captionTextColor", new Color(0x000000));
        WINDOWS_DESKTOP.put("win.frame.activeCaptionColor", new Color(0x99B4D1));
        WINDOWS_DESKTOP.put("win.mdi.backgroundColor", new Color(0xABABAB));
        WINDOWS_DESKTOP.put("win.menu.textColor", new Color(0x000000));
        WINDOWS_DESKTOP.put("win.item.highlightColor", new Color(0x0078D7));
        WINDOWS_DESKTOP.put("win.highContrast.on", Boolean.FALSE);
    }

    private static final String SYSTEM_INFO = "com.jidesoft.utils.SystemInfo";

    /** The keys the fix pins; restore is checked against their stock text. */
    private static final String[] PINNED_KEYS = {
        "JideTabbedPane.light",
        "JideTabbedPane.highlight",
        "JideTabbedPane.shadow",
        "JideTabbedPane.selectedTabBackground",
        "JideTabbedPane.tabAreaBackgroundLt",
        "JideTabbedPane.tabAreaBackgroundDk",
        "JideTabbedPane.foreground",
        "JideTabbedPane.selectedTabTextForeground",
        "JideTabbedPane.unselectedTabTextForeground",
    };

    private static final double WCAG_AA_TEXT = 4.5;

    private ThemeManager manager;
    private Boolean windowsBefore;
    /** Real Toolkit values displaced by {@link #spoofWindows}, restored by unspoof. */
    private Map<String, Object> desktopBefore;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
    }

    @AfterEach
    void leaveTheJvmLight() throws Exception {
        // The OS flag goes back BEFORE the light restore: the stock path must
        // not see a spoofed platform either.
        unspoofWindows();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("Windows: the selected tab paints dark from JideTabbedPane.light, with legible text")
    void windowsSelectedTabIsDarkWithLegibleText() throws Exception {
        JideTabbedPane pane = workspacePane();
        JPanel host = host(pane);

        spoofWindows();
        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(host);

        assertStripIsLegible(pane, host);
    }

    @Test
    @DisplayName("Everywhere else: the same strip, same expectations")
    void otherPlatformsSelectedTabIsDarkWithLegibleText() throws Exception {
        JideTabbedPane pane = workspacePane();
        JPanel host = host(pane);

        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(host);

        assertStripIsLegible(pane, host);
    }

    @Test
    @DisplayName("Switching back returns every pinned key to its stock value")
    void restoreReturnsTheStockTabColours() throws Exception {
        Map<String, String> stock = new HashMap<>();
        for (String key : PINNED_KEYS) {
            stock.put(key, String.valueOf(UIManager.get(key)));
        }

        manager.apply(true);
        manager.apply(false);

        for (String key : PINNED_KEYS) {
            assertEquals(stock.get(key), String.valueOf(UIManager.get(key)), key);
        }
    }

    private static void assertStripIsLegible(JideTabbedPane pane, JPanel host) {
        BufferedImage image = render(host);
        int selected = pane.getSelectedIndex();
        int unselected = selected == 0 ? 1 : 0;

        // Pin the fill to the key it must come from, so the render is proven
        // to exercise the paint path and not some stale delegate.
        Color light = UIManager.getColor("JideTabbedPane.light");
        Color selectedFill = modal(image, tabBounds(pane, host, selected));
        assertEquals(hex(light), hex(selectedFill),
            "the selected tab must be filled from JideTabbedPane.light");
        assertTrue(ThemeManager.luminance(selectedFill) < 100,
            "the selected tab is light (" + hex(selectedFill) + ") under a dark strip — #81");

        Color unselectedFill = modal(image, tabBounds(pane, host, unselected));
        assertTrue(ThemeManager.luminance(unselectedFill) < 100,
            "an unselected tab is light (" + hex(unselectedFill) + ") under a dark strip");

        // The label colours JIDE paints with (BasicJideTabbedPaneUI.paintText
        // falls through to these when the pane's own foreground is a
        // UIResource, and the pane's foreground is installed from
        // JideTabbedPane.foreground).
        assertContrast(UIManager.getColor("JideTabbedPane.selectedTabTextForeground"), selectedFill,
            "selected tab text");
        assertContrast(UIManager.getColor("JideTabbedPane.unselectedTabTextForeground"), unselectedFill,
            "unselected tab text");
        assertContrast(pane.getForeground(), selectedFill, "the pane's installed foreground");
        assertTrue(pane.getForeground() instanceof UIResource,
            "the pane's foreground should still be the look and feel's, not a component-level stamp");
    }

    private static void assertContrast(Color text, Color fill, String what) {
        double ratio = contrastRatio(text, fill);
        assertTrue(ratio >= WCAG_AA_TEXT, String.format(
            "%s: %s on %s is %.1f:1, below the %.1f:1 WCAG AA minimum",
            what, hex(text), hex(fill), ratio, WCAG_AA_TEXT));
    }

    /** Built the way {@code TabbedResourceWorkspace}'s constructor builds it. */
    private static JideTabbedPane workspacePane() {
        JideTabbedPane pane = new JideTabbedPane(SwingConstants.BOTTOM, JideTabbedPane.SCROLL_TAB_LAYOUT) {
            @Override
            public boolean isDragOverDisabled() {
                return true; // no DropTarget: see the class comment
            }
        };
        pane.setShowCloseButton(true);
        pane.setShowCloseButtonOnTab(true);
        pane.setShowCloseButtonOnSelectedTab(true);
        pane.setBoldActiveTab(true);
        pane.setBackground(UIManager.getColor("InternalFrame.inactiveTitleBackground"));
        pane.addTab("Perspective", new JPanel());
        pane.addTab("runControl", new JPanel());
        pane.setSelectedIndex(1);
        return pane;
    }

    private static JPanel host(JideTabbedPane pane) {
        JPanel host = new JPanel(new BorderLayout());
        host.add(pane, BorderLayout.CENTER);
        host.setSize(360, 120);
        return host;
    }

    private void spoofWindows() throws Exception {
        Field isWindows = Class.forName(SYSTEM_INFO).getDeclaredField("_isWindows");
        isWindows.setAccessible(true);
        windowsBefore = isWindows.getBoolean(null);
        isWindows.setBoolean(null, true);

        // On a real Windows CI host these keys already have values. Remember
        // them so unspoof can put them back: removing the keys left the next
        // test ("Everywhere else") on a Toolkit with no 3D colours, and
        // JIDE's tab scroll layout then AIOOBE'd under the support-floor SDK
        // ("No such child: 6") while the current SDK happened to survive.
        desktopBefore = new LinkedHashMap<>();
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        for (String key : WINDOWS_DESKTOP.keySet()) {
            desktopBefore.put(key, toolkit.getDesktopProperty(key));
        }

        Method set = Toolkit.class.getDeclaredMethod("setDesktopProperty", String.class, Object.class);
        set.setAccessible(true);
        for (Map.Entry<String, Object> entry : WINDOWS_DESKTOP.entrySet()) {
            set.invoke(toolkit, entry.getKey(), entry.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private void unspoofWindows() throws Exception {
        if (windowsBefore == null) {
            return;
        }
        Field isWindows = Class.forName(SYSTEM_INFO).getDeclaredField("_isWindows");
        isWindows.setAccessible(true);
        isWindows.setBoolean(null, windowsBefore);
        windowsBefore = null;

        Method set = Toolkit.class.getDeclaredMethod("setDesktopProperty", String.class, Object.class);
        set.setAccessible(true);
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Field props = Toolkit.class.getDeclaredField("desktopProperties");
        props.setAccessible(true);
        Map<String, Object> map = (Map<String, Object>) props.get(toolkit);
        for (String key : WINDOWS_DESKTOP.keySet()) {
            Object previous = desktopBefore == null ? null : desktopBefore.get(key);
            if (previous != null) {
                set.invoke(toolkit, key, previous);
            } else {
                map.remove(key);
            }
        }
        desktopBefore = null;
    }

    /**
     * Paint the host into an image. {@code validate()} is a no-op on a tree
     * with no peer, so the layout is done by hand — twice, because the JIDE
     * tab layout sizes its scroll viewport off the first pass.
     */
    private static BufferedImage render(JPanel host) {
        layout(host);
        layout(host);
        BufferedImage image = new BufferedImage(
            host.getWidth(), host.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        host.paint(graphics);
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

    private static Rectangle tabBounds(JideTabbedPane pane, JPanel host, int index) {
        return SwingUtilities.convertRectangle(pane, pane.getUI().getTabBounds(pane, index), host);
    }

    /** The most common colour inside the rectangle — the fill, not the text or the edge. */
    private static Color modal(BufferedImage image, Rectangle area) {
        Map<Integer, Integer> tally = new HashMap<>();
        for (int y = Math.max(0, area.y); y < Math.min(image.getHeight(), area.y + area.height); y++) {
            for (int x = Math.max(0, area.x); x < Math.min(image.getWidth(), area.x + area.width); x++) {
                tally.merge(image.getRGB(x, y) & 0xFFFFFF, 1, Integer::sum);
            }
        }
        assertTrue(!tally.isEmpty(), "tab bounds " + area + " fall outside the render");
        int best = tally.entrySet().stream()
            .max(Map.Entry.comparingByValue()).get().getKey();
        return new Color(best);
    }

    /** WCAG 2.x contrast ratio. */
    private static double contrastRatio(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double relativeLuminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int value) {
        double s = value / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static String hex(Color color) {
        return color == null ? "null" : String.format("#%06X", color.getRGB() & 0xFFFFFF);
    }
}
