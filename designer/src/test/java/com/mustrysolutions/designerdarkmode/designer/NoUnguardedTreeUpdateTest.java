package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No source file in this module may call
 * {@code SwingUtilities.updateComponentTreeUI} directly. Every walk goes
 * through {@link ThemeManager#updateComponentTreeUiResiliently}, which contains
 * a throwing component instead of losing the rest of the tree.
 *
 * <p>This is a structural test because the bug it guards is a structural one.
 * The resilient walk was written for the phase-6 call site, and the other two
 * were missed — so the very next Designer run threw out of the rescan timer,
 * uncaught, on exactly the Ignition NPE the fix had been written for. Fixing
 * three call sites is worth nothing if a fourth is added later.
 *
 * <p>The check is on source rather than bytecode because the method name also
 * appears in log-message literals, which land in the constant pool and are not
 * calls. Matching the trailing {@code (} separates a call from a mention, and
 * from the {@code @code} references in the surrounding javadoc.
 *
 * <p>There is deliberately no companion test asserting the wrapper exists:
 * {@code ThemeManager} cannot be reflected over from this source set, which
 * runs without the Designer and JIDE jars, and
 * {@link ResilientTreeUpdateTest} would not compile if the wrapper were
 * removed — a stronger guarantee than a reflective check.
 */
class NoUnguardedTreeUpdateTest {

    /**
     * The unguarded walk, as it appears at a call site. Note the trailing
     * {@code UI(}: the guarded wrapper is
     * {@code updateComponentTreeUiResiliently}, with a lowercase {@code Ui}.
     */
    private static final String FORBIDDEN_CALL = ".updateComponentTreeUI(";

    @Test
    @DisplayName("nothing calls Swing's unguarded updateComponentTreeUI")
    void noSourceFileCallsTheUnguardedWalk() throws Exception {
        Path source = mainSource();
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> tree = Files.walk(source)) {
            for (Path file : (Iterable<Path>) tree
                    .filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                if (text.contains(FORBIDDEN_CALL)) {
                    offenders.add(source.relativize(file).toString());
                }
            }
        }

        assertEquals(List.of(), offenders,
            "these files call SwingUtilities" + FORBIDDEN_CALL + ") directly; route them"
                + " through ThemeManager.updateComponentTreeUiResiliently instead, or one"
                + " throwing component takes the rest of the tree with it");
    }

    /**
     * Gradle runs a {@code Test} task with the subproject directory as its
     * working directory, so this resolves against {@code designer/}.
     */
    private static Path mainSource() {
        Path source = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(source),
            "expected the main sources at " + source.toAbsolutePath()
                + "; if the layout moved, point this test at the new one rather"
                + " than deleting it");
        return source;
    }
}
