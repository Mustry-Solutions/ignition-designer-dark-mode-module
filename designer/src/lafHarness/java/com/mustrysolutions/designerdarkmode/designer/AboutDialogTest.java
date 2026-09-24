package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Tools → About Designer Dark Mode, built the way {@link AboutDialog#show}
 * builds it, under the stock Designer theme and under dark mode.
 *
 * <p>The links carry their own foreground, so they are the part of the dialog
 * that does not simply follow the theme: each must stay readable against the
 * dialog behind it in both. The two renders are written next to the harness
 * reports as {@code about-light.png} and {@code about-dark.png}.
 */
class AboutDialogTest {

    private ThemeManager manager;
    private final List<JDialog> dialogs = new ArrayList<>();

    @BeforeAll
    static void requireADisplay() {
        boolean headless = GraphicsEnvironment.isHeadless();
        if (Boolean.getBoolean(WindowedCycleTest.WINDOWED_PROPERTY)) {
            assertFalse(headless, "-Pharness.windowed=true was given, but this JVM is headless.");
        }
        Assumptions.assumeFalse(headless, "no display: Synthetica cannot paint headlessly.");
    }

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Throwable {
        onEdt(() -> {
            DesignerLookAndFeel.installStock();
            manager = new ThemeManager();
            manager.captureStockLaf();
        });
    }

    @AfterEach
    void leaveTheJvmLight() throws Throwable {
        onEdt(() -> {
            dialogs.forEach(JDialog::dispose);
            dialogs.clear();
            manager.apply(false);
        });
    }

    @Test
    void linksStayReadableInBothThemes() throws Throwable {
        File dir = new File(System.getProperty("designerdarkmode.logFile", "build/x"))
            .getParentFile();
        onEdt(() -> {
            JDialog light = build();
            assertReadableLinks(light, "light");
            ImageIO.write(render(light), "png", new File(dir, "about-light.png"));

            manager.apply(true);
            JDialog dark = build();
            assertReadableLinks(dark, "dark");
            ImageIO.write(render(dark), "png", new File(dir, "about-dark.png"));
        });
    }

    private JDialog build() {
        JOptionPane pane = new JOptionPane(AboutDialog.content(), JOptionPane.PLAIN_MESSAGE,
            JOptionPane.DEFAULT_OPTION, new MoonIcon(16));
        JDialog dialog = pane.createDialog(null, "About Designer Dark Mode");
        dialog.pack();
        dialogs.add(dialog);
        return dialog;
    }

    private static void assertReadableLinks(JDialog dialog, String theme) {
        List<JLabel> links = new ArrayList<>();
        collectLinks(dialog.getContentPane(), links);
        assertTrue(links.size() == 3, theme + ": expected three links, found " + links.size());
        for (JLabel link : links) {
            Color bg = effectiveBackground(link);
            double ratio = contrast(link.getForeground(), bg);
            assertTrue(ratio >= 4.5, theme + ": link " + link.getText() + " is "
                + String.format("%.1f", ratio) + ":1 against " + bg + ", below WCAG AA");
        }
    }

    private static void collectLinks(Container c, List<JLabel> out) {
        for (Component child : c.getComponents()) {
            if (child instanceof JLabel label && label.getToolTipText() != null) {
                out.add(label);
            }
            if (child instanceof Container container) {
                collectLinks(container, out);
            }
        }
    }

    private static Color effectiveBackground(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (p.isOpaque() && p.getBackground() != null) {
                return p.getBackground();
            }
        }
        throw new AssertionError("no opaque ancestor behind " + c);
    }

    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        double[] rgb = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) {
            rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    /** The dialog's content at twice its size, which is what a Retina screen shows. */
    private static BufferedImage render(JDialog dialog) {
        Container root = dialog.getRootPane();
        BufferedImage image = new BufferedImage(root.getWidth() * 2, root.getHeight() * 2,
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(2, 2);
        root.paint(g);
        g.dispose();
        return image;
    }

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
