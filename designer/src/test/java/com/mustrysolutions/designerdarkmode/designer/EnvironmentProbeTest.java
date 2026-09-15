package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The environment block a bug report from another platform is read against.
 *
 * <p>The interesting assertion is the module-access line. This test JVM is
 * launched with exactly one of the Designer's openings
 * ({@code --add-opens java.desktop/java.awt}, see {@code designer/build.gradle.kts}),
 * so the probe has to report that one as reachable and the others as
 * missing — which is the both-ways check that the detection is real and
 * not a constant.
 */
class EnvironmentProbeTest {

    @Test
    @DisplayName("every fact is one line, and none of them throws")
    void describesTheHostWithoutThrowing() {
        List<String> lines = EnvironmentProbe.describe();

        assertEquals(8, lines.size(), lines.toString());
        assertTrue(lines.get(0).startsWith("os "), lines.get(0));
        assertTrue(lines.get(1).startsWith("java "), lines.get(1));
        assertTrue(lines.get(2).startsWith("headless "), lines.get(2));
        assertTrue(lines.get(3).startsWith("jvm args "), lines.get(3));
        assertTrue(lines.get(4).startsWith("java.desktop access: "), lines.get(4));
        assertTrue(lines.get(5).startsWith("scaling: "), lines.get(5));
        assertTrue(lines.get(6).startsWith("synthetica "), lines.get(6));
        assertTrue(lines.get(7).startsWith("font "), lines.get(7));
    }

    @Test
    @DisplayName("the opening this JVM has is reported reachable, the rest missing")
    void reportsModuleAccessBothWays() {
        String access = EnvironmentProbe.describe().get(4);

        // java.awt is opened by the test task; javax.swing is not.
        assertTrue(access.contains("MISSING"), access);
        assertTrue(access.contains("javax.swing"), access);
        assertFalse(access.matches(".*MISSING \\[[^\\]]*\\bjava\\.awt\\b[^\\]]*\\].*"),
            "java.awt is opened to this JVM and must not be listed as missing: " + access);
    }

    @Test
    @DisplayName("the JVM arguments shown are only the theming-relevant ones")
    void showsOnlyInterestingJvmArguments() {
        String args = EnvironmentProbe.describe().get(3);

        // The test task passes --add-opens java.desktop/java.awt; it must
        // appear. The count of what was left out must be reported, so a
        // reader knows the list is filtered rather than complete.
        assertTrue(args.contains("--add-opens"), args);
        assertTrue(args.matches(".*\\(\\+\\d+ not shown\\)$"), args);
    }

    @Test
    @DisplayName("a font is described by family, size and class")
    void describesAFont() {
        String described = EnvironmentProbe.describe(
            new java.awt.Font(java.awt.Font.DIALOG, java.awt.Font.BOLD, 12));

        assertTrue(described.contains(" 12pt bold (Font)"), described);
    }
}
