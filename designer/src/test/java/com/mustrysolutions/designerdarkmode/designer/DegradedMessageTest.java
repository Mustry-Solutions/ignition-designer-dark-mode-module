package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The line a user gets when the switch only partly worked.
 *
 * <p>Every pass after the look-and-feel swap is isolated, so a failing one
 * leaves a Designer that works and is visibly wrong somewhere. The summary has
 * to say which passes failed and where the detail lives, because a half-dark
 * Designer with no explanation is indistinguishable from a broken module.
 */
class DegradedMessageTest {

    @Test
    @DisplayName("the summary names every failed phase")
    void namesEveryFailedPhase() {
        String message = ThemeManager.degradedMessage(
            true, Arrays.asList("treeIcons", "cellRenderers"), 14);

        assertTrue(message.contains("treeIcons"), message);
        assertTrue(message.contains("cellRenderers"), message);
    }

    @Test
    @DisplayName("the summary counts the failures against what was attempted")
    void countsFailuresAgainstAttempts() {
        String message = ThemeManager.degradedMessage(
            true, Arrays.asList("treeIcons", "cellRenderers"), 14);

        assertTrue(message.contains("2 of 14"), message);
    }

    @Test
    @DisplayName("the summary points at the log that holds the stack traces")
    void pointsAtTheLog() {
        String message = ThemeManager.degradedMessage(
            false, Collections.singletonList("scriptEditors"), 9);

        assertTrue(message.contains(DebugLog.path()), message);
    }

    @Test
    @DisplayName("the summary says which direction the switch was going")
    void namesTheDirection() {
        assertTrue(ThemeManager.degradedMessage(
            true, Collections.singletonList("treeIcons"), 14).startsWith("Dark mode applied"));
        assertTrue(ThemeManager.degradedMessage(
            false, Collections.singletonList("treeIcons"), 9).startsWith("Stock theme restored"));
    }
}
