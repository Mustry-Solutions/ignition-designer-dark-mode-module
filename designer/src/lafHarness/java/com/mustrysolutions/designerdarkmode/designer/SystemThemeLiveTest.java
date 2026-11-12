package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Match System's commands, run for real on the machine running the harness.
 *
 * <p>{@code SystemThemeTest} checks the parsing against captured output; only
 * a real run shows that the command exists where it is looked for and prints
 * what was captured. CI runs the harness on Windows, macOS and Linux, so each
 * platform's command is run on that platform. Whether the runner is dark or
 * light is not the point — that it gave an answer is.
 */
class SystemThemeLiveTest {

    @Test
    @DisplayName("this platform's appearance is read in time, and Windows and macOS give an answer")
    void readsThisPlatform() {
        long start = System.nanoTime();
        SystemTheme.Appearance appearance = SystemTheme.detect();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String seen = appearance + " via " + SystemTheme.lastSource() + " in " + elapsedMs + " ms";
        System.out.println("SystemThemeLiveTest: " + seen);

        // Linux tries up to three commands, each bounded by the timeout.
        assertTrue(elapsedMs < 3 * SystemTheme.TIMEOUT_MS + 1000, seen);
        if (os.startsWith("windows") || os.startsWith("mac")) {
            // Both platforms always have the tool, and every outcome of it
            // is an answer; unknown here means the command was not found.
            assertNotEquals(SystemTheme.Appearance.UNKNOWN, appearance, seen);
        }
    }
}
