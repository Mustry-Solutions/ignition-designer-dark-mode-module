package com.mustrysolutions.designerdarkmode.designer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.UIManager;

/**
 * Tools → About Designer Dark Mode: the version, who makes the module, and
 * where to go next. Built from plain Swing components on every open, so it
 * follows whichever theme is installed at the time.
 */
final class AboutDialog {

    static final String SITE = "https://mustrysolutions.com";
    static final String MODULES = SITE + "/ignition-modules";
    static final String CONTACT = SITE + "/contact-us";
    static final String ISSUES =
        "https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues";

    /** Written by the build; see processResources in designer/build.gradle.kts. */
    private static final String BUILD_INFO = "designerdarkmode-build.properties";

    private AboutDialog() {
    }

    static void show(Component parent) {
        JOptionPane.showMessageDialog(parent, content(), "About Designer Dark Mode",
            JOptionPane.PLAIN_MESSAGE, new MoonIcon(16));
    }

    static JPanel content() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Designer Dark Mode");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3f));
        panel.add(title);
        panel.add(new JLabel("Version " + version()));
        panel.add(Box.createVerticalStrut(12));

        panel.add(new JLabel("<html><body style='width: 300px'>Free and open source under the "
            + "Apache License 2.0. Made by Mustry Solutions, an IT/OT consultancy that "
            + "builds Ignition systems and modules.</body></html>"));
        panel.add(Box.createVerticalStrut(12));

        panel.add(link("Our other Ignition modules", MODULES));
        panel.add(link("Work with us", CONTACT));
        panel.add(link("Report a problem with this module", ISSUES));
        panel.add(Box.createVerticalStrut(4));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        panel.setOpaque(false);
        wrapper.add(panel, BorderLayout.CENTER);
        return wrapper;
    }

    /** The module version the build stamped, or "development build" when run from source. */
    static String version() {
        try (InputStream in = AboutDialog.class.getResourceAsStream(BUILD_INFO)) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty("version");
                if (v != null && !v.isBlank() && !v.contains("${")) {
                    return v;
                }
            }
        } catch (IOException e) {
            DebugLog.log("about: could not read " + BUILD_INFO + ": " + e);
        }
        return "development build";
    }

    private static JLabel link(String text, String url) {
        JLabel label = new JLabel("<html><u>" + text + "</u></html>");
        label.setForeground(linkColor());
        label.setToolTipText(url);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                open(label, url);
            }
        });
        return label;
    }

    /**
     * The theme's link colour, moved toward white or black until it reads at
     * WCAG AA (4.5:1) against the dialog. FlatLaf defines a link colour and
     * Synthetica does not; FlatLaf Dark's own blue is only 3.8:1 against its
     * panel background.
     */
    static Color linkColor() {
        Color link = UIManager.getColor("Component.linkColor");
        if (link == null) {
            link = new Color(0x1F5FBF);
        }
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) {
            return link;
        }
        Color target = luminance(bg) < 0.5 ? Color.WHITE : Color.BLACK;
        for (int step = 0; step < 20 && contrast(link, bg) < 4.5; step++) {
            link = blend(link, target, 0.1);
        }
        return link;
    }

    private static Color blend(Color from, Color to, double amount) {
        return new Color(
            (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
            (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
            (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** WCAG relative luminance. */
    private static double luminance(Color c) {
        double[] rgb = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) {
            rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    /**
     * Opens the page in the default browser. Where the JVM has no browser to
     * hand it to (some Linux desktops), the address goes to the clipboard
     * instead and the user is told so.
     */
    private static void open(Component parent, String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception e) {
            DebugLog.log("about: could not open " + url + ": " + e);
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
        JOptionPane.showMessageDialog(parent,
            "No browser could be opened. The address was copied to the clipboard:\n" + url,
            "About Designer Dark Mode", JOptionPane.INFORMATION_MESSAGE);
    }
}
