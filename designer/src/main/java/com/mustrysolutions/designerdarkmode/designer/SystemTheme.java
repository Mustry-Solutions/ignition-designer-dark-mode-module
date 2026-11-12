package com.mustrysolutions.designerdarkmode.designer;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whether the operating system is set to a dark appearance, for the
 * Match System mode.
 *
 * <p>Neither the JDK nor FlatLaf can answer this: FlatLaf's native libraries
 * do window decorations and file choosers, and nothing in AWT exposes the
 * setting. So each platform is asked the way its own tools ask, with one short
 * command and no new dependency:
 *
 * <ul>
 *   <li><b>Windows 10/11</b> — the {@code AppsUseLightTheme} value under
 *       {@code HKCU\...\Themes\Personalize}, through {@code reg.exe}. It is
 *       Settings → Personalization → Colors → "Choose your app mode".</li>
 *   <li><b>macOS</b> — {@code defaults read -g AppleInterfaceStyle}, which
 *       prints {@code Dark} in dark mode and fails because the key is absent
 *       in light mode. Under Appearance "Auto" it follows the current
 *       appearance.</li>
 *   <li><b>Linux</b> — the XDG desktop portal's
 *       {@code org.freedesktop.appearance color-scheme}, which GNOME and KDE
 *       both serve, then GNOME's own {@code color-scheme} and
 *       {@code gtk-theme} settings for desktops without the portal.</li>
 * </ul>
 *
 * <p>Read once per decision, never on a timer: Match System follows the
 * system when the Designer launches and when the mode is chosen, not while
 * the Designer is in use, because a switch blocks the Designer for seconds.
 *
 * <p>Runs a process, so it is never called on the event dispatch thread, and
 * it never throws: anything it cannot read is {@link Appearance#UNKNOWN},
 * which the caller treats as light — the stock Designer.
 */
final class SystemTheme {

    /** What the system is set to, or that it could not be read. */
    enum Appearance {
        DARK, LIGHT, UNKNOWN
    }

    /**
     * Long enough for a cold {@code reg.exe} on a busy Windows machine, short
     * enough that a hung D-Bus does not hold up the startup apply for long.
     */
    static final long TIMEOUT_MS = 3000;

    /** What a command returned. {@code exitCode} is -1 when it did not run to completion. */
    static final class Result {
        static final Result FAILED = new Result(-1, "");

        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }

    /** Runs one command; a seam so tests can hand back canned output. Never throws. */
    interface CommandRunner {
        Result run(List<String> command);
    }

    static final String WINDOWS_KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize";

    private static final Pattern REG_DWORD =
        Pattern.compile("AppsUseLightTheme\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)");
    private static final Pattern PORTAL_UINT = Pattern.compile("uint32\\s+(\\d+)");

    /**
     * The last detection's reasoning, one line, for the debug log. Per
     * thread: reads can overlap (the launch read and one from the menu), and
     * a shared value could pair one read's answer with another's reasoning.
     */
    private static final ThreadLocal<String> lastSource =
        ThreadLocal.withInitial(() -> "not read yet");

    private SystemTheme() {
    }

    /** Read this machine's setting. Blocks for up to a few seconds; never throws. */
    static Appearance detect() {
        try {
            return detect(System.getProperty("os.name", ""), System::getenv, SystemTheme::run);
        } catch (Throwable t) {
            lastSource.set("detection failed: " + t);
            return Appearance.UNKNOWN;
        }
    }

    static Appearance detect(String osName, Function<String, String> env, CommandRunner runner) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return windows(env, runner);
        }
        if (os.startsWith("mac")) {
            return mac(runner);
        }
        if (os.contains("linux") || os.contains("bsd")) {
            return linux(runner);
        }
        lastSource.set("no detection for os \"" + osName + "\"");
        return Appearance.UNKNOWN;
    }

    /** How the last {@link #detect} on this thread reached its answer, for the debug log. */
    static String lastSource() {
        return lastSource.get();
    }

    private static Appearance windows(Function<String, String> env, CommandRunner runner) {
        // By full path when Windows says where it lives, so a reg.exe in the
        // Designer's working directory or on a user's PATH is never the one run.
        String root = env.apply("SystemRoot");
        String reg = root == null || root.isEmpty()
            ? "reg"
            : root + File.separator + "System32" + File.separator + "reg.exe";
        Result result = runner.run(
            Arrays.asList(reg, "query", WINDOWS_KEY, "/v", "AppsUseLightTheme"));
        Appearance appearance = parseWindows(result);
        lastSource.set("reg AppsUseLightTheme (exit " + result.exitCode + "): " + appearance);
        return appearance;
    }

    /**
     * {@code 0x0} is dark, anything else light. A missing value (a Windows
     * older than 1809, or one that was never switched) makes reg.exe fail,
     * and a Windows that has never been switched is light.
     */
    static Appearance parseWindows(Result result) {
        if (result.exitCode == -1) {
            return Appearance.UNKNOWN;
        }
        Matcher m = REG_DWORD.matcher(result.output);
        if (result.exitCode != 0 || !m.find()) {
            return Appearance.LIGHT;
        }
        return Long.parseLong(m.group(1), 16) == 0 ? Appearance.DARK : Appearance.LIGHT;
    }

    private static Appearance mac(CommandRunner runner) {
        Result result = runner.run(
            Arrays.asList("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle"));
        Appearance appearance = parseMac(result);
        lastSource.set("defaults AppleInterfaceStyle (exit " + result.exitCode + "): " + appearance);
        return appearance;
    }

    /**
     * {@code Dark} is dark. The key only exists in dark mode, so
     * {@code defaults} exiting non-zero is light, not a failure.
     */
    static Appearance parseMac(Result result) {
        if (result.exitCode == -1) {
            return Appearance.UNKNOWN;
        }
        return result.exitCode == 0 && "dark".equalsIgnoreCase(result.output.trim())
            ? Appearance.DARK : Appearance.LIGHT;
    }

    private static Appearance linux(CommandRunner runner) {
        Result portal = runner.run(Arrays.asList(
            "gdbus", "call", "--session", "--timeout", "2",
            "--dest", "org.freedesktop.portal.Desktop",
            "--object-path", "/org/freedesktop/portal/desktop",
            "--method", "org.freedesktop.portal.Settings.Read",
            "org.freedesktop.appearance", "color-scheme"));
        // Each command is asked only when the one before gave no answer: on a
        // hung D-Bus each can take the full TIMEOUT_MS.
        Result colorScheme = Result.FAILED;
        Result gtkTheme = Result.FAILED;
        String source = "portal color-scheme (exit " + portal.exitCode + ")";
        if (parsePortal(portal) == Appearance.UNKNOWN) {
            colorScheme = runner.run(Arrays.asList(
                "gsettings", "get", "org.gnome.desktop.interface", "color-scheme"));
            source += ", gsettings color-scheme (exit " + colorScheme.exitCode + ")";
            if (parseColorScheme(colorScheme) == Appearance.UNKNOWN) {
                gtkTheme = runner.run(Arrays.asList(
                    "gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"));
                source += ", gtk-theme (exit " + gtkTheme.exitCode + ")";
            }
        }
        Appearance appearance = parseLinux(portal, colorScheme, gtkTheme);
        lastSource.set(source + ": " + appearance);
        return appearance;
    }

    /**
     * The portal answers {@code 1} for prefer-dark, {@code 2} for
     * prefer-light and {@code 0} for no preference. Only the first two are an
     * answer; "no preference" is what GNOME reports for its light default but
     * also what a desktop reports that has never heard of the setting, so it
     * falls through to the theme name.
     */
    static Appearance parsePortal(Result result) {
        if (result.exitCode != 0) {
            return Appearance.UNKNOWN;
        }
        Matcher m = PORTAL_UINT.matcher(result.output);
        if (!m.find()) {
            return Appearance.UNKNOWN;
        }
        switch (m.group(1)) {
            case "1":
                return Appearance.DARK;
            case "2":
                return Appearance.LIGHT;
            default:
                return Appearance.UNKNOWN;
        }
    }

    /**
     * The portal first. Then GNOME 42's {@code color-scheme}, then a theme
     * named dark (Adwaita-dark, Yaru-dark — how a desktop said "dark" before
     * there was a setting for it). Anything that answered but did not say
     * dark is light.
     */
    static Appearance parseLinux(Result portal, Result colorScheme, Result gtkTheme) {
        Appearance fromPortal = parsePortal(portal);
        if (fromPortal != Appearance.UNKNOWN) {
            return fromPortal;
        }
        Appearance fromColorScheme = parseColorScheme(colorScheme);
        if (fromColorScheme != Appearance.UNKNOWN) {
            return fromColorScheme;
        }
        if (gtkTheme.exitCode == 0 && gtkTheme.output.toLowerCase(Locale.ROOT).contains("dark")) {
            return Appearance.DARK;
        }
        return portal.exitCode == 0 || colorScheme.exitCode == 0 || gtkTheme.exitCode == 0
            ? Appearance.LIGHT : Appearance.UNKNOWN;
    }

    /** GNOME 42's {@code color-scheme}: prefer-dark or prefer-light, else no answer. */
    static Appearance parseColorScheme(Result colorScheme) {
        if (colorScheme.exitCode != 0) {
            return Appearance.UNKNOWN;
        }
        String scheme = colorScheme.output.toLowerCase(Locale.ROOT);
        if (scheme.contains("prefer-dark")) {
            return Appearance.DARK;
        }
        if (scheme.contains("prefer-light")) {
            return Appearance.LIGHT;
        }
        return Appearance.UNKNOWN;
    }

    /**
     * Run a command with nothing on stdin and stderr thrown away, for at
     * most {@link #TIMEOUT_MS}.
     *
     * <p>Its output goes to a file, not a pipe. A helper the command leaves
     * running — a session bus that {@code gdbus} or {@code gsettings}
     * autolaunched — inherits the pipe and holds it open after the command
     * itself has exited. The JDK's reaper drains the pipe when the command
     * exits, but a read that starts first waits for the end of the pipe, as
     * long as the helper lives (2 runs in 300 on JDK 21). A file is read to
     * its end whoever still has it open.
     */
    static Result run(List<String> command) {
        Process process = null;
        Path output = null;
        try {
            output = Files.createTempFile("designer-dark-mode-", ".out");
            process = new ProcessBuilder(command)
                .redirectOutput(output.toFile())
                .redirectError(Redirect.DISCARD)
                .start();
            process.getOutputStream().close();
            if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return Result.FAILED;
            }
            return new Result(process.exitValue(),
                new String(Files.readAllBytes(output), Charset.defaultCharset()));
        } catch (IOException e) {
            // Not installed (gdbus, gsettings) or not runnable: no answer.
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return Result.FAILED;
        } finally {
            if (output != null) {
                deleteQuietly(output);
            }
        }
    }

    /** A helper still holding the file open stops Windows deleting it; then it goes at exit. */
    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            file.toFile().deleteOnExit();
        }
    }
}
