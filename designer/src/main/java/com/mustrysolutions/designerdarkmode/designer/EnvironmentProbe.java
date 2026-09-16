package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import com.formdev.flatlaf.util.UIScale;

/**
 * One block of facts about the JVM the Designer is running in, written to the
 * debug log the first time a theme switch happens.
 *
 * <p>This module has only ever been watched on macOS. Everything it does that
 * could go differently elsewhere comes down to a handful of questions about
 * the host JVM — is {@code java.desktop/java.awt} opened to us (the token
 * mutation in {@link IaColorTokens} needs it), which font FlatLaf resolved
 * against which font Synthetica had, and whether system scaling is in play
 * (the reason {@code flatlaf.uiScale.enabled} is off at all). None of those
 * can be answered from here for a machine we do not have; all of them can be
 * answered by a Windows or Linux user pasting their log. So the log answers
 * them up front, and a bug report from another platform carries the evidence
 * instead of the guesswork.
 *
 * <p>Written at {@link DebugLog#log} level, not {@link DebugLog#detail}: it is
 * the reader of a bug report who needs this, and they will not have had the
 * debug flag on. It is a dozen lines, once per session, so it costs nothing.
 *
 * <p>Diagnostic only. Nothing here may affect a switch, so every step is
 * guarded and the whole thing is a no-op on failure.
 */
final class EnvironmentProbe {

    /**
     * The JDK module openings and exports the Designer JVM has to be launched
     * with. This is the set the headless harness declares
     * ({@code designer/build.gradle.kts}), which was read off a running
     * Designer's command line — on macOS. Whether the launcher passes the
     * same set elsewhere is exactly what the log is for.
     */
    private static final String[][] REQUIRED_OPENS = {
        {"java.awt", "opens"},
        {"javax.swing", "opens"},
        {"javax.swing.plaf.synth", "opens"},
        {"javax.swing.plaf.basic", "opens"},
        {"sun.swing", "exports"},
        {"sun.swing.table", "exports"},
        {"sun.swing.plaf.synth", "exports"},
        {"sun.awt", "exports"},
    };

    /**
     * The JVM argument prefixes worth reproducing. The full list can carry a
     * gateway address and whatever else the launcher was told; the module
     * system, Java2D and look-and-feel flags are all that bear on theming.
     */
    private static final String[] INTERESTING_ARG_PREFIXES = {
        "--add-opens", "--add-exports",
        "-Dsun.java2d.", "-Dflatlaf.", "-Dapple.", "-Dawt.", "-Dswing.",
        "-Dignition.laf", "-Ddesignerdarkmode.",
    };

    private static boolean logged;

    private EnvironmentProbe() {
    }

    /** Write the block, the first time only. Never throws. */
    static void logOnce() {
        if (logged) {
            return;
        }
        logged = true;
        try {
            for (String line : describe()) {
                DebugLog.log("env: " + line);
            }
        } catch (Throwable t) {
            DebugLog.log("env: probe failed.", t);
        }
    }

    /**
     * The lines, one fact each, so a test can read them and so a failure in
     * one leaves the rest.
     */
    static List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("os " + prop("os.name") + " " + prop("os.version") + " " + prop("os.arch"));
        lines.add("java " + prop("java.version") + " (" + prop("java.vendor") + ") at "
            + prop("java.home"));
        lines.add("headless " + GraphicsEnvironment.isHeadless());
        lines.add(jvmArguments());
        lines.add(moduleAccess());
        lines.add(scaling());
        lines.add("synthetica " + synthetica());
        lines.add("font " + describe(UIManager.getFont("Label.font")));
        return lines;
    }

    /** One line: the current UIManager label font, for bracketing a switch. */
    static String fontLine() {
        return "font " + describe(UIManager.getFont("Label.font"));
    }

    private static String jvmArguments() {
        List<String> shown = new ArrayList<>();
        int hidden = 0;
        // "--add-opens java.desktop/java.awt=ALL-UNNAMED" arrives as TWO
        // arguments when the launcher passes it that way (Gradle's jvmArgs
        // does, and so may the Designer Launcher); the value is meaningless
        // without the flag, so it is carried along with it.
        boolean carryNext = false;
        for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (carryNext || interesting(arg)) {
                shown.add(arg);
                carryNext = "--add-opens".equals(arg) || "--add-exports".equals(arg);
            } else {
                hidden++;
            }
        }
        return "jvm args " + shown + " (+" + hidden + " not shown)";
    }

    private static boolean interesting(String arg) {
        for (String prefix : INTERESTING_ARG_PREFIXES) {
            if (arg.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether each required package is open (or exported) to this module's
     * classes. Asked of the module system directly rather than by trying a
     * {@code setAccessible} and catching, so the probe has no side effects.
     */
    private static String moduleAccess() {
        Module desktop = Color.class.getModule();
        Module us = EnvironmentProbe.class.getModule();
        List<String> ok = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String[] required : REQUIRED_OPENS) {
            String pkg = required[0];
            boolean has = "opens".equals(required[1])
                ? desktop.isOpen(pkg, us)
                : desktop.isExported(pkg, us);
            (has ? ok : missing).add(pkg);
        }
        return "java.desktop access: " + ok.size() + " of " + REQUIRED_OPENS.length
            + " required packages reachable"
            + (missing.isEmpty() ? "" : "; MISSING " + missing);
    }

    private static String scaling() {
        StringBuilder line = new StringBuilder("scaling:");
        line.append(" flatlaf.uiScale.enabled=").append(prop("flatlaf.uiScale.enabled"));
        line.append(" sun.java2d.uiScale=").append(prop("sun.java2d.uiScale"));
        line.append(" GDK_SCALE=").append(env("GDK_SCALE"));
        line.append(" GDK_DPI_SCALE=").append(env("GDK_DPI_SCALE"));
        line.append(" flatlaf.systemScaling=").append(UIScale.isSystemScalingEnabled());
        line.append(" flatlaf.userScale=").append(UIScale.getUserScaleFactor());
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                java.awt.GraphicsConfiguration gc = GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
                line.append(" screen.transform=").append(gc.getDefaultTransform().getScaleX());
                line.append(" screen.dpi=")
                    .append(java.awt.Toolkit.getDefaultToolkit().getScreenResolution());
            } catch (Throwable t) {
                line.append(" screen=unavailable(").append(t.getClass().getSimpleName()).append(')');
            }
        }
        return line.toString();
    }

    /**
     * Synthetica's own scale factor and font, reflectively: neither is SDK
     * surface, and the probe must not fail to load on a Designer that has
     * moved them.
     */
    private static String synthetica() {
        LookAndFeel laf = UIManager.getLookAndFeel();
        if (laf == null) {
            return "no look and feel";
        }
        StringBuilder line = new StringBuilder(laf.getClass().getName());
        try {
            Object scale = laf.getClass().getMethod("getScaleFactor").invoke(laf);
            line.append(" scaleFactor=").append(scale);
        } catch (Throwable absent) {
            line.append(" scaleFactor=n/a");
        }
        try {
            Object font = laf.getClass().getMethod("getFont").invoke(null);
            line.append(" font=").append(describe((Font) font));
        } catch (Throwable absent) {
            line.append(" font=n/a");
        }
        return line.toString();
    }

    static String describe(Font font) {
        if (font == null) {
            return "null";
        }
        return font.getFamily(Locale.ROOT) + " " + font.getSize() + "pt"
            + (font.isBold() ? " bold" : "") + (font.isItalic() ? " italic" : "")
            + " (" + font.getClass().getSimpleName() + ")";
    }

    private static String prop(String key) {
        return String.valueOf(System.getProperty(key));
    }

    private static String env(String key) {
        return String.valueOf(System.getenv(key));
    }
}
