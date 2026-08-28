package com.mustrysolutions.designerdarkmode.designer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Best-effort append-only debug log at ~/.ignition/designer-dark-mode.log.
 * The Designer keeps its own logs in memory only, which makes theming issues
 * hard to diagnose from the outside — this file gives the dev loop something
 * to read. Failures to write are ignored.
 *
 * <p>Two levels, because the same file has two audiences. {@link #log} is for
 * what a user or a maintainer reading a bug report needs: the switches and
 * the failures. {@link #detail} is for developing the module — counts,
 * per-event traces, the diagnostic dumps — and is written only under
 * {@code -Ddesignerdarkmode.debug=true}. The split is not cosmetic: the
 * component watcher re-runs the theming passes for the whole session, so the
 * detail lines are unbounded, and every one of them used to cost a file open,
 * a write and a close on the event dispatch thread.
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
     * Opened on the first write and held for the session. Nothing is created
     * until something is actually logged, so a Designer that never switches
     * theme leaves no file behind.
     */
    private static Writer writer;

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

    /** Where the log lives, for pointing a user at it. */
    static String path() {
        return LOG_FILE.getPath();
    }

    /** A theme switch, a failure — something worth keeping in every log. */
    public static void log(String message) {
        write(message);
    }

    public static void log(String message, Throwable error) {
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        write(message + System.lineSeparator() + stack);
    }

    /** Development detail: written only under {@code -Ddesignerdarkmode.debug=true}. */
    public static void detail(String message) {
        if (verbose()) {
            write(message);
        }
    }

    /**
     * A detail that carries a stack trace — a class the Designer does not have,
     * a pass that skipped itself. These repeat on every rescan for the whole
     * session, so they are only worth writing while developing.
     */
    public static void detail(String message, Throwable error) {
        if (verbose()) {
            log(message, error);
        }
    }

    /** Release the log file. Called when the module shuts down. */
    public static synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
                // Best effort only.
            }
            writer = null;
        }
    }

    private static synchronized void write(String message) {
        try {
            if (writer == null) {
                writer = new BufferedWriter(new FileWriter(LOG_FILE, true));
            }
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
            writer.write(stamp + " " + message + System.lineSeparator());
            // Flushed per line rather than left to the buffer: the log's whole
            // purpose is to survive a Designer that hangs or is killed.
            writer.flush();
        } catch (IOException e) {
            // Drop the handle so the next call reopens rather than writing
            // into a stream that has already failed.
            close();
        }
    }
}
