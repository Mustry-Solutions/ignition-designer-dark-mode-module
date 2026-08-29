package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two log levels.
 *
 * <p>{@link DebugLog#detail} exists because the component watcher re-runs the
 * theming passes for the whole session: every count and every per-event trace
 * repeats indefinitely, and each one used to cost a file open, a write and a
 * close on the event dispatch thread. What a user needs from this file is the
 * switches and the failures; everything else is for developing the module.
 */
class DebugLogLevelsTest {

    private static final String VERBOSE = "designerdarkmode.debug";

    @AfterEach
    void tearDown() {
        System.clearProperty(VERBOSE);
    }

    @Test
    @DisplayName("a null throwable logs the message instead of throwing")
    void aNullThrowableIsTolerated() throws IOException {
        long before = size();

        // Not a hypothetical: a summary line that passed null here escaped to
        // the event dispatch thread out of the rescan timer and killed the
        // tick. A logger that throws turns a contained failure into an
        // uncaught one, which is the opposite of its job.
        DebugLog.log("a failure with no throwable to attach", null);

        assertTrue(size() > before, "the message should still have been written");
        assertTrue(tail().contains("a failure with no throwable to attach"), tail());
    }

    @Test
    @DisplayName("detail writes nothing unless the debug flag is set")
    void detailIsSilentByDefault() throws IOException {
        System.clearProperty(VERBOSE);
        long before = size();

        DebugLog.detail("this line must not reach an ordinary user's log");

        assertEquals(before, size(), "detail() wrote while the debug flag was off");
    }

    @Test
    @DisplayName("detail writes when the debug flag is set")
    void detailWritesWhenVerbose() throws IOException {
        System.setProperty(VERBOSE, "true");

        DebugLog.detail("a detail line under the debug flag");

        assertTrue(contents().contains("a detail line under the debug flag"));
    }

    @Test
    @DisplayName("log writes whether or not the debug flag is set")
    void logAlwaysWrites() throws IOException {
        System.clearProperty(VERBOSE);

        DebugLog.log("a failure a user would need to see");

        assertTrue(contents().contains("a failure a user would need to see"));
    }

    /** Each line is flushed as it is written: a hung Designer must still leave a log. */
    @Test
    @DisplayName("a written line is on disk before the writer is closed")
    void writesAreFlushedImmediately() throws IOException {
        System.clearProperty(VERBOSE);

        DebugLog.log("flushed without closing");

        assertTrue(contents().contains("flushed without closing"));
    }

    private static long size() {
        return new File(DebugLog.path()).length();
    }

    private static String contents() throws IOException {
        return new String(Files.readAllBytes(new File(DebugLog.path()).toPath()),
            StandardCharsets.UTF_8);
    }

    /** The last of the log, for asserting a line reached it. */
    private static String tail() throws IOException {
        File file = new File(DebugLog.path());
        if (!file.isFile()) {
            return "";
        }
        String all = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return all.length() > 2000 ? all.substring(all.length() - 2000) : all;
    }

}
