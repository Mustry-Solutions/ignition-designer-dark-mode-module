package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link ThemeManager}'s own switch sequence against the real Designer
 * look and feels, and asserts the invariants this module's bugs have violated.
 *
 * <p>Both halves of a switch are covered, and they fail differently: the dark
 * state is asserted directly (the standard Swing colours have to beat JIDE's
 * light mapping), and the light restore is asserted as a diff against the state
 * before the cycle. A harness that only checked the restore would pass with
 * dark mode thoroughly broken.
 *
 * <p>Nothing here is mocked. The stock Synthetica look and feel is installed,
 * JIDE's extension goes on top of it, and then {@link ThemeManager#apply} runs
 * exactly as it does in a Designer. What is missing is a Designer: there are no
 * windows, so the component walks run and find nothing, and everything asserted
 * below lives in {@code UIManager} or in the module's own state.
 *
 * <h2>What this cannot see</h2>
 *
 * <p>Pixels, and anything that needs a live component tree. The painters
 * <em>cached on components</em> (#14, #19) are half-covered here: the
 * {@code Theme.painter} map is asserted, the private fields JIDE caches from it
 * are not, because with no windows there is nothing holding one. A Designer and
 * a pair of eyes still settle "does this look right".
 */
class ThemeSwitchCycleTest {

    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        // The one piece of startup() state apply() needs, and the reason it
        // needs no DesignerContext.
        manager.captureStockLaf();
    }

    @AfterEach
    void leaveTheJvmLight() {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("dark mode actually changes the defaults (the cycle test is not vacuous)")
    void darkModeChangesTheDefaults() {
        UiDefaultsSnapshot light = UiDefaultsSnapshot.take();
        manager.apply(true);
        UiDefaultsSnapshot dark = UiDefaultsSnapshot.take();

        assertTrue(UIManager.getLookAndFeel() instanceof FlatDarkLaf,
            "the harness must actually install FlatLaf, or every other assertion here is empty");
        assertNotEquals(0, light.diff(dark).size(),
            "a switch to dark that changed no default would make the restore test pass trivially");
        assertEquals(List.of(), manager.failedPhases(),
            "every phase must actually run here. A phase that throws in this environment "
                + "would quietly reduce the sequence under test to the part that survives.");
        assertTrue(light.size() > 1000,
            "the snapshot covers " + light.size() + " defaults; a stock Designer has on the "
                + "order of 1,700. Far fewer means the real Synthetica/JIDE jars are not on "
                + "the classpath and every assertion in this class is measuring plain Swing.");
    }

    /**
     * The standard Swing colour keys that {@code installJideExtension()}
     * overwrites with a light-theme mapping, and that
     * {@code applyMenuDefaults(true)} exists to re-assert on top of it.
     *
     * <p>Three of them — {@code Menu}, {@code MenuBar} and {@code PopupMenu} —
     * resolve to {@code null} in a stock Designer and exist only because JIDE
     * writes them into the developer defaults. Those are the invisible context
     * menus.
     */
    private static final List<String> BACKGROUNDS_THAT_MUST_GO_DARK = List.of(
        "TextField.background", "TextArea.background", "Table.background",
        "TableHeader.background", "List.background", "Tree.background",
        "ComboBox.background", "Panel.background",
        "Menu.background", "MenuBar.background", "MenuItem.background",
        "PopupMenu.background");

    private static final List<String> FOREGROUNDS_THAT_MUST_GO_LIGHT = List.of(
        "Label.foreground", "TextField.foreground", "Table.foreground",
        "List.foreground", "Tree.foreground", "MenuItem.foreground");

    /**
     * Midpoint of the luminance scale {@link ThemeManager#luminance} produces.
     * Nothing here is near it: under dark these backgrounds land at 49-72 and
     * the foregrounds at 221, against 220-255 and 46 in the stock theme.
     */
    private static final int MID_LUMINANCE = 128;

    /**
     * <p>Weaker than it looks, and worth knowing why. Headlessly,
     * {@code installJideExtension(VSNET_STYLE)} really does derive its colours
     * from FlatLaf's dark palette the way it is supposed to: skipping the
     * module's FlatLaf re-assert entirely changes exactly ONE value in this
     * environment ({@code MenuBar.border}) and no colour at all. So the light
     * mapping this test is named for does not reproduce here, and this cannot
     * catch its loss.
     *
     * <p>It is kept because the assertion is still true and still the thing
     * that matters — these keys must be dark, and a null one is #23's amber
     * property editor — and because it covers the dark half at all, which
     * nothing else here does. But the dark state is the harness's weak side:
     * a Designer is still the instrument for it.
     */
    @Test
    @DisplayName("dark mode wins over JIDE's light mapping for the standard Swing colours")
    void theStandardSwingColoursActuallyGoDark() {
        manager.apply(true);

        for (String key : BACKGROUNDS_THAT_MUST_GO_DARK) {
            Color background = UIManager.getColor(key);
            assertNotNull(background, key + " resolves to nothing under dark mode. Swing "
                + "falls back to the parent's colour when a background is unset, which is "
                + "how #23 lit up a whole property editor in filter-match amber.");
            assertTrue(ThemeManager.luminance(background) < MID_LUMINANCE,
                key + " is still light under dark mode (" + background + "). "
                    + "installJideExtension() overwrites these standard Swing keys with a "
                    + "light-theme mapping, which is where white search fields, white table "
                    + "cells and invisible context menus come from; the FlatLaf re-assert "
                    + "after it is what puts them back.");
        }

        for (String key : FOREGROUNDS_THAT_MUST_GO_LIGHT) {
            Color foreground = UIManager.getColor(key);
            assertNotNull(foreground, key + " resolves to nothing under dark mode");
            assertTrue(ThemeManager.luminance(foreground) > MID_LUMINANCE,
                key + " is still dark under dark mode (" + foreground + "): dark text on a "
                    + "dark background is the same bug as a light background, and reads as "
                    + "an empty panel rather than a mistyped one.");
        }
    }

    @Test
    @DisplayName("a light -> dark -> light cycle puts every resolvable default back (#23)")
    void aFullCycleLeavesEveryDefaultAsItFoundIt() {
        UiDefaultsSnapshot before = UiDefaultsSnapshot.take();

        manager.apply(true);
        manager.apply(false);

        UiDefaultsSnapshot after = UiDefaultsSnapshot.take();
        List<UiDefaultsSnapshot.Difference> drift = unexplained(before.diff(after));
        assertTrue(drift.isEmpty(),
            "a completed restore must leave no trace in UIManager. "
                + UiDefaultsSnapshot.describe(drift));
    }

    @Test
    @DisplayName("the FlatLaf overrides are cleared while FlatLaf is still installed (#23)")
    void theOverridesAreClearedBeforeTheStockLookAndFeelReturns() {
        manager.apply(true);
        manager.apply(false);

        List<String> phases = manager.phaseTrace();
        int cleared = phases.indexOf("flatDefaults");
        int swapped = phases.indexOf("lookAndFeel");

        assertTrue(cleared >= 0, "the light restore must clear the overrides at all: " + phases);
        assertTrue(cleared < swapped,
            "UIManager.put(key, null) DELETES an entry rather than reverting it, so the "
                + "clear has to happen while FlatLaf is still serving those keys. Run after "
                + "the stock look and feel is back, it deletes the values the restore just "
                + "put there — which is #23. Order was: " + phases);
    }

    /**
     * Unlike its neighbours, this one has never been seen to fail. Fifteen
     * mutations of the switch sequence were tried against it — an untracked
     * look-and-feel instance, a painter snapshot that is never cleared, a
     * defaults snapshot that accumulates across cycles — and none produced
     * drift, because the module holds almost no state that survives a cycle
     * and what it does hold is rebuilt from the look and feel each time.
     *
     * <p>So treat it as a guard against a class of bug rather than as a
     * detector with a known catch: a per-cycle leak is invisible on the toggle
     * that introduces it and only shows up after a session's worth of them,
     * which is exactly the kind nobody finds by looking. It is nearly free to
     * run. If you make it fail, that is a real finding — write down what did
     * it.
     */
    @Test
    @DisplayName("repeated cycles converge rather than drifting")
    void repeatedCyclesConverge() {
        manager.apply(true);
        manager.apply(false);
        UiDefaultsSnapshot afterFirst = UiDefaultsSnapshot.take();

        for (int cycle = 0; cycle < 2; cycle++) {
            manager.apply(true);
            manager.apply(false);
        }

        UiDefaultsSnapshot afterThird = UiDefaultsSnapshot.take();
        List<UiDefaultsSnapshot.Difference> drift = unexplained(afterFirst.diff(afterThird));
        assertTrue(drift.isEmpty(),
            "each cycle must be a no-op on the last. A per-cycle leak is invisible on the "
                + "toggle that introduces it and only shows up after a session's worth of "
                + "them. " + UiDefaultsSnapshot.describe(drift));
    }

    /**
     * Note what this does and does not prove. A mutation sweep found that
     * removing the module's own painter restore ({@code
     * overrideThemePainters(false)}) leaves this test PASSING, while removing
     * {@code installJideExtension()} from the light path fails it — so what
     * actually repopulates the map with the Synthetica entries here is JIDE's
     * own extension reinstall, and the module's restore is belt-and-braces on
     * top of it.
     *
     * <p>Kept as an outcome check, which is what it is: the map must be right
     * after a restore, however it got there. Do not read a pass as evidence
     * that the painter-restore code works, and do not delete that code on the
     * strength of this test — a live Designer resolves painters for more than
     * one classloader, and this JVM has exactly one.
     */
    @Test
    @DisplayName("Theme.painter comes back to the stock mapping on the restore (#14, #19)")
    void theThemePaintersComeBackToTheStockMapping() {
        Map<String, String> stock = DesignerLookAndFeel.themePainters();
        assertTrue(stock.values().stream().anyMatch(painter -> painter.contains("Synthetica")),
            "sanity: the stock Designer maps its dock painters to a Synthetica painter, "
                + "and that is the thing the restore has to put back. Found: " + stock);

        manager.apply(true);
        Map<String, String> underDark = DesignerLookAndFeel.themePainters();
        assertTrue(underDark.values().stream().allMatch(
                painter -> painter.equals("com.jidesoft.plaf.basic.BasicPainter")),
            "under dark every entry must point at the style-neutral BasicPainter: a "
                + "SyntheticaJidePainter casts the active look and feel to "
                + "SyntheticaLookAndFeel and throws on every repaint under FlatLaf. "
                + "Found: " + underDark);

        manager.apply(false);
        assertEquals(stock, DesignerLookAndFeel.themePainters(),
            "the restore must put the original painters back, not leave BasicPainter "
                + "drawing the light theme's dock chrome");
    }

    /**
     * The differences the module is answerable for.
     *
     * <p>Exactly one kind is excluded, and it is Swing's rather than ours:
     * {@code UIDefaults} caches each UI delegate class it resolves back into
     * the developer defaults under the class's own name, where it survives a
     * look-and-feel swap. A first dark cycle therefore leaves
     * {@code com.formdev.flatlaf.ui.FlatLabelUI -> Class[...]} and one for
     * {@code FlatPanelUI} behind for good. The stock Designer carries three of
     * the Synthetica equivalents before this module has done anything at all,
     * and the second cycle adds none — so it is a cache warming up, not a leak.
     *
     * <p>Deliberately narrow: {@link UiDefaultsSnapshot.Difference} only counts
     * an entry as this if the value is the class the KEY names. Anything else
     * the module leaves behind still fails the test.
     */
    private static List<UiDefaultsSnapshot.Difference> unexplained(
            List<UiDefaultsSnapshot.Difference> differences) {
        return differences.stream()
            .filter(difference -> !difference.isUiDelegateClassCache())
            .toList();
    }
}
