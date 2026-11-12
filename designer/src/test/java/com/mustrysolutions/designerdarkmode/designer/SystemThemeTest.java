package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mustrysolutions.designerdarkmode.designer.SystemTheme.Appearance;
import com.mustrysolutions.designerdarkmode.designer.SystemTheme.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading the system's appearance, against the output each platform's tool
 * really prints. The commands themselves are run for real by the harness's
 * {@code SystemThemeLiveTest}, on each CI platform; here the parsing and the
 * choice of command are checked without running anything.
 */
class SystemThemeTest {

    private static Result ok(String output) {
        return new Result(0, output);
    }

    private static final String REG_HEADER = "\r\n"
        + "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\r\n";

    @Test
    @DisplayName("Windows: AppsUseLightTheme 0x0 is dark, 0x1 is light")
    void windowsAppMode() {
        assertEquals(Appearance.DARK, SystemTheme.parseWindows(
            ok(REG_HEADER + "    AppsUseLightTheme    REG_DWORD    0x0\r\n\r\n")));
        assertEquals(Appearance.LIGHT, SystemTheme.parseWindows(
            ok(REG_HEADER + "    AppsUseLightTheme    REG_DWORD    0x1\r\n\r\n")));
    }

    /**
     * reg.exe exits 1 when the value does not exist: a Windows that has never
     * been switched to dark, or one older than 1809. Both are light.
     */
    @Test
    @DisplayName("Windows: a missing value is light, a reg.exe that did not run is unknown")
    void windowsMissingValue() {
        assertEquals(Appearance.LIGHT, SystemTheme.parseWindows(new Result(1, "")));
        assertEquals(Appearance.UNKNOWN, SystemTheme.parseWindows(Result.FAILED));
    }

    @Test
    @DisplayName("Windows: reg.exe is run from System32, not from the PATH")
    void windowsRunsRegFromSystemRoot() {
        List<List<String>> ran = new ArrayList<>();
        Map<String, String> env = new HashMap<>();
        env.put("SystemRoot", "C:\\Windows");

        Appearance appearance = SystemTheme.detect("Windows 11", env::get, command -> {
            ran.add(command);
            return ok(REG_HEADER + "    AppsUseLightTheme    REG_DWORD    0x0\r\n");
        });

        assertEquals(Appearance.DARK, appearance);
        assertEquals(1, ran.size());
        assertEquals("C:\\Windows" + java.io.File.separator + "System32"
            + java.io.File.separator + "reg.exe", ran.get(0).get(0));
        assertTrue(ran.get(0).contains(SystemTheme.WINDOWS_KEY), ran.toString());
    }

    @Test
    @DisplayName("macOS: Dark is dark; the key's absence is light")
    void macAppearance() {
        assertEquals(Appearance.DARK, SystemTheme.parseMac(ok("Dark\n")));
        // `defaults read -g AppleInterfaceStyle` in light mode: "The domain/default
        // pair ... does not exist" on stderr, exit 1.
        assertEquals(Appearance.LIGHT, SystemTheme.parseMac(new Result(1, "")));
        assertEquals(Appearance.UNKNOWN, SystemTheme.parseMac(Result.FAILED));
    }

    @Test
    @DisplayName("macOS: defaults is run by its full path")
    void macRunsDefaults() {
        List<List<String>> ran = new ArrayList<>();

        Appearance appearance = SystemTheme.detect("Mac OS X", name -> null, command -> {
            ran.add(command);
            return ok("Dark\n");
        });

        assertEquals(Appearance.DARK, appearance);
        assertEquals(List.of("/usr/bin/defaults", "read", "-g", "AppleInterfaceStyle"), ran.get(0));
    }

    @Test
    @DisplayName("Linux: the portal's 1 is dark and 2 is light, without asking gsettings")
    void linuxPortal() {
        List<List<String>> ran = new ArrayList<>();

        Appearance appearance = SystemTheme.detect("Linux", name -> null, command -> {
            ran.add(command);
            return ok("(<<uint32 1>>,)\n");
        });

        assertEquals(Appearance.DARK, appearance);
        assertEquals(1, ran.size(), "a portal answer needs no fallback: " + ran);
        assertEquals("gdbus", ran.get(0).get(0));
        assertEquals(Appearance.LIGHT, SystemTheme.parsePortal(ok("(<<uint32 2>>,)\n")));
    }

    /**
     * "No preference" is GNOME's light default, but also every desktop that
     * has not heard of the setting, so the theme name is asked next.
     */
    @Test
    @DisplayName("Linux: the portal's 0 falls through to GNOME's settings")
    void linuxNoPreferenceFallsThrough() {
        Result noPreference = ok("(<<uint32 0>>,)\n");

        assertEquals(Appearance.DARK, SystemTheme.parseLinux(noPreference,
            ok("'default'\n"), ok("'Yaru-dark'\n")));
        assertEquals(Appearance.LIGHT, SystemTheme.parseLinux(noPreference,
            ok("'default'\n"), ok("'Adwaita'\n")));
    }

    @Test
    @DisplayName("Linux without the portal: GNOME's color-scheme, then a dark theme name")
    void linuxGsettings() {
        assertEquals(Appearance.DARK, SystemTheme.parseLinux(Result.FAILED,
            ok("'prefer-dark'\n"), ok("'Adwaita'\n")));
        assertEquals(Appearance.LIGHT, SystemTheme.parseLinux(Result.FAILED,
            ok("'prefer-light'\n"), ok("'Adwaita-dark'\n")));
        assertEquals(Appearance.DARK, SystemTheme.parseLinux(Result.FAILED,
            Result.FAILED, ok("'Adwaita-dark'\n")));
    }

    /** Each command can take the full timeout on a hung D-Bus, so none is asked needlessly. */
    @Test
    @DisplayName("Linux: GNOME's color-scheme answer is not followed by the theme name")
    void linuxColorSchemeAnswerStops() {
        List<List<String>> ran = new ArrayList<>();

        Appearance appearance = SystemTheme.detect("Linux", name -> null, command -> {
            ran.add(command);
            return command.contains("color-scheme") && command.get(0).equals("gsettings")
                ? ok("'prefer-dark'\n") : Result.FAILED;
        });

        assertEquals(Appearance.DARK, appearance);
        assertEquals(2, ran.size(), "the portal, then color-scheme: " + ran);
    }

    @Test
    @DisplayName("Linux: a color-scheme of 'default' still asks the theme name")
    void linuxDefaultColorSchemeAsksTheTheme() {
        List<List<String>> ran = new ArrayList<>();

        Appearance appearance = SystemTheme.detect("Linux", name -> null, command -> {
            ran.add(command);
            if (command.contains("color-scheme") && command.get(0).equals("gsettings")) {
                return ok("'default'\n");
            }
            return command.contains("gtk-theme") ? ok("'Adwaita-dark'\n") : Result.FAILED;
        });

        assertEquals(Appearance.DARK, appearance);
        assertEquals(3, ran.size(), ran.toString());
    }

    @Test
    @DisplayName("Linux with nothing to ask is unknown")
    void linuxNothingAnswers() {
        Appearance appearance = SystemTheme.detect("Linux", name -> null, command -> Result.FAILED);

        assertEquals(Appearance.UNKNOWN, appearance);
    }

    @Test
    @DisplayName("an OS without a detection is unknown and runs nothing")
    void unknownOs() {
        List<List<String>> ran = new ArrayList<>();

        assertEquals(Appearance.UNKNOWN, SystemTheme.detect("SunOS", name -> null, command -> {
            ran.add(command);
            return ok("");
        }));
        assertTrue(ran.isEmpty(), ran.toString());
    }

    /**
     * A helper the command leaves behind (a session bus gdbus autolaunched)
     * can keep its stdout open after the command has exited. Read from a
     * pipe, that output ends only when the JDK's reaper thread drains it, and
     * a read that gets there first blocks for as long as the helper lives: 2
     * runs in 300 on JDK 21. Too rare to catch here every time; this pins
     * that the output is read whole and in time with a child still holding it.
     */
    @Test
    @DisplayName("a command that leaves a child holding its output open still returns")
    void childHoldingStdoutDoesNotHang() {
        assumeTrue(new File("/bin/sh").canExecute(), "needs a POSIX shell");

        Result result = assertTimeoutPreemptively(Duration.ofSeconds(10),
            () -> SystemTheme.run(List.of("/bin/sh", "-c", "sleep 20 & echo Dark")));

        assertEquals(0, result.exitCode);
        assertEquals(Appearance.DARK, SystemTheme.parseMac(result));
    }

    @Test
    @DisplayName("how a read reached its answer is kept per thread")
    void lastSourceIsPerThread() throws Exception {
        SystemTheme.detect("SunOS", name -> null, command -> ok(""));
        String here = SystemTheme.lastSource();

        Thread other = new Thread(() -> SystemTheme.detect("Mac OS X", name -> null,
            command -> ok("Dark\n")));
        other.start();
        other.join();

        assertEquals(here, SystemTheme.lastSource());
    }

    @Test
    @DisplayName("a command that does not exist is a failed result, not an exception")
    void missingCommand() {
        Result result = SystemTheme.run(List.of("designer-dark-mode-no-such-command"));

        assertEquals(-1, result.exitCode);
    }
}
