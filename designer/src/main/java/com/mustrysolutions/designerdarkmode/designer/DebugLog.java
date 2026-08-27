package com.mustrysolutions.designerdarkmode.designer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Best-effort append-only debug log at ~/.ignition/designer-dark-mode.log.
 * The Designer keeps its own logs in memory only, which makes theming issues
 * hard to diagnose from the outside — this file gives the dev loop something
 * to read. Failures to write are ignored.
 */
public final class DebugLog {

    /**
     * Overridable so unit tests do not append to the developer's real
     * ~/.ignition log — a test exercising a failure path wrote a stack trace
     * there that then looked like a live Designer fault.
     */
    private static final File LOG_FILE = new File(
        System.getProperty("designerdarkmode.logFile",
            new File(System.getProperty("user.home"), ".ignition/designer-dark-mode.log")
                .getPath()));

    /**
     * Whether the noisy diagnostic dumps should run.
     *
     * <p>These exist for developing the module — enumerating every dock title
     * pane, every light {@code UIManager} default — and each costs a hundred
     * or more lines per theme switch. Useful while chasing a mistyped surface;
     * pure noise in an ordinary user's log, where the interesting lines are
     * the warnings. Enable with {@code -Ddesignerdarkmode.debug=true} on the
     * Designer's command line.
     */
    public static boolean verbose() {
        return Boolean.getBoolean("designerdarkmode.debug");
    }

    private DebugLog() {
    }

    public static synchronized void log(String message) {
        try (FileWriter writer = new FileWriter(LOG_FILE, true)) {
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            writer.write(stamp + " " + message + System.lineSeparator());
        } catch (IOException ignored) {
            // Best effort only.
        }
    }

    public static void log(String message, Throwable error) {
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        log(message + System.lineSeparator() + stack);
    }
}
