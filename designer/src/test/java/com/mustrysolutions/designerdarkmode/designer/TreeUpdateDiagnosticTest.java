package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The diagnostic that runs when {@code updateComponentTreeUI} fails on a window
 * (#12).
 *
 * <p>Its one hard requirement is that it cannot make things worse. It is called
 * from inside the {@code catch} for a theme switch that has already failed, so a
 * diagnostic that throws there would bury the real failure under a second one —
 * which is why the awkward inputs below are tested at least as carefully as the
 * useful output.
 */
class TreeUpdateDiagnosticTest {

    @Test
    @DisplayName("names a component that has no font, and the path to it")
    void reportsComponentsWithNoFont() {
        JPanel root = new JPanel();
        JPanel inner = new JPanel();
        JLabel orphan = new JLabel("no font");
        // A component with no font of its own AND no ancestor to inherit one
        // from is the only way getFont() returns null — which is why the report
        // prints the chain rather than just the component.
        orphan.setFont(null);
        inner.setFont(null);
        root.setFont(null);
        inner.add(orphan);
        root.add(inner);

        String report = TreeUpdateDiagnostic.describe(root, new NullPointerException(
            "Cannot invoke \"java.awt.Font.getFamily()\" because \"font\" is null"));

        assertTrue(report.contains("JLabel"), "the offending component must be named: " + report);
        assertTrue(report.contains("JPanel > JPanel > JLabel"),
            "the ancestor chain places it in the tree: " + report);
        assertTrue(report.contains("Font.getFamily()"),
            "the original failure is quoted so the report stands alone: " + report);
    }

    @Test
    @DisplayName("says how much of the tree still holds a wrong-theme delegate")
    void reportsStaleDelegates() {
        JPanel root = new JPanel();
        root.add(new JLabel("a"));

        String report = TreeUpdateDiagnostic.describe(root, new RuntimeException("boom"));

        assertTrue(report.contains("the subtree the update never reached"),
            "the count of stale delegates is the actual damage #12 describes: " + report);
        assertTrue(report.contains("component(s) walked"),
            "a count of what was examined, so an empty result is distinguishable from "
                + "a walk that did not happen: " + report);
    }

    @Test
    @DisplayName("counts every affected component, not just the ones it lists")
    void countsAreNotCappedWithTheDetail() {
        // 40 components, well past the reporting cap. "the update skipped 12"
        // and "the update skipped 40, here are 12" are different bug reports,
        // and only the second one is true.
        JPanel root = new JPanel();
        root.setFont(null);
        for (int i = 0; i < 40; i++) {
            JLabel label = new JLabel("l" + i);
            label.setFont(null);
            root.add(label);
        }

        String report = TreeUpdateDiagnostic.describe(root, new RuntimeException("boom"));

        assertTrue(report.contains("getFont() == null): 41"),
            "the count must be of everything found, not of what was printed: " + report);
        assertTrue(report.contains("and 29 more"),
            "and the detail is capped, saying so explicitly: " + report);
    }

    @Test
    @DisplayName("never throws, whatever it is handed")
    void neverThrows() {
        // Every awkward input at once: an empty container, a null throwable, and
        // a component whose getUI() does not exist.
        assertDoesNotThrow(() -> TreeUpdateDiagnostic.describe(new JPanel(), null));
        assertDoesNotThrow(() -> TreeUpdateDiagnostic.report(new JPanel(), null));
        assertDoesNotThrow(() -> TreeUpdateDiagnostic.report(
            new java.awt.Panel(), new OutOfMemoryError("simulated")));
    }
}
