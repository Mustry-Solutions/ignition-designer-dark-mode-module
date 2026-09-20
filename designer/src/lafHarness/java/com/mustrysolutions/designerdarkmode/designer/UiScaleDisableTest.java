package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.util.List;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.util.UIScale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Answers [#76][76]: does disabling FlatLaf <em>user</em> scaling undersize
 * dark mode on a HiDPI Windows or Linux Designer?
 *
 * <p>FlatLaf has two scaling modes ({@link UIScale}). System scaling is the
 * JDK HiDPI transform (Java 9+, every platform). User scaling is FlatLaf's
 * own font-derived factor, used mainly for Java 8 and as a Linux
 * compensation path. {@link ThemeManager#startup} turns user scaling off
 * before FlatLaf loads, because the listener it installs outlives FlatLaf
 * and NPEs on a later Synthetica {@code uninitialize}. That is
 * OS-independent and non-negotiable
 * ({@code theFlatLafScalingListenerIsNeverRegistered}).
 *
 * <p>What this test pins is the cost of that defence on a scaled display.
 * Headless CI has no real screen transform, so the HiDPI case is simulated
 * the way a live Designer presents it to the module: Synthetica has already
 * enlarged {@code Label.font} (its own scale factor), and that is the font
 * {@code keepStockFont} reads. Under the shipped property:
 *
 * <ul>
 *   <li>system scaling stays on ({@link UIScale#isSystemScalingEnabled});
 *   <li>user scale stays 1.0, so FlatLaf does not additionally stretch
 *       insets via {@link UIScale#scale(int)};
 *   <li>the stock (already scaled) font size is what dark mode paints —
 *       colour only, which is the whole point of the pin in #93.
 * </ul>
 *
 * <p>Re-enabling user scaling is not a fix for the Linux concern in #76.
 * With an already-scaled stock font, FlatLaf would derive a user factor
 * {@code > 1} from that font and scale insets on top of it — and the
 * permanent listener would come back. The font pin is the portable answer;
 * the comment in {@code startup} now says so for all three platforms.
 *
 * <p>[76]: https://github.com/Mustry-Solutions/ignition-designer-dark-mode-module/issues/76
 */
class UiScaleDisableTest {

    private ThemeManager manager;
    private Font labelFontBefore;
    private Object defaultFontBefore;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        labelFontBefore = UIManager.getFont("Label.font");
        defaultFontBefore = UIManager.get("defaultFont");
    }

    @AfterEach
    void leaveTheJvmLight() throws Exception {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        // The scaled-font put is a developer-defaults stamp; put the stock
        // values back so a later test does not inherit Dialog 24.
        UIManager.put("Label.font", labelFontBefore);
        UIManager.put("defaultFont", defaultFontBefore);
        DesignerLookAndFeel.installStock();
    }

    @Test
    @DisplayName("Java 9+ system scaling stays on; only FlatLaf user scaling is off (#76)")
    void systemScalingStaysOnWhileUserScalingIsOff() {
        assertEquals("false", System.getProperty("flatlaf.uiScale.enabled"),
            "the harness must ship the same property ThemeManager.startup sets, "
                + "or this is measuring a different configuration");
        assertTrue(UIScale.isSystemScalingEnabled(),
            "FlatLaf's system-scaling flag is true on Java 9+ on every platform. "
                + "Disabling user scaling must not turn that off — it is what "
                + "Windows and macOS HiDPI (and a Linux JDK that honours "
                + "sun.java2d.uiScale / GDK_SCALE) actually use.");
        assertEquals(1.0f, UIScale.getUserScaleFactor(), 0.001f,
            "with user scaling disabled the factor stays 1.0; FlatLaf.scale(n) "
                + "is then a no-op and the JDK transform does the work");
        assertEquals(12, UIScale.scale(12),
            "UIScale.scale must not enlarge under the shipped property");
    }

    @Test
    @DisplayName("a Synthetica-scaled stock font is kept under dark; user scale stays 1 (#76)")
    void aScaledStockFontIsKeptWithoutFlatLafUserScaling() {
        // 150% and 200%: the sizes a HiDPI Windows (125–150%) or Linux
        // desktop would hand the module once Synthetica has applied its
        // own scale factor. Headless CI cannot produce those from the
        // screen, so the font is put directly — that is the input
        // keepStockFont actually sees.
        for (int pt : new int[] {18, 24}) {
            FontUIResource scaled = new FontUIResource("Dialog", Font.PLAIN, pt);
            UIManager.put("Label.font", scaled);
            // FlatLaf derives every *.font from defaultFont when present;
            // leave it unset under stock so the pin is what installs it.
            UIManager.put("defaultFont", null);

            manager.apply(true);

            Font dark = UIManager.getFont("Label.font");
            assertEquals(pt, dark.getSize(),
                "dark mode must keep the Synthetica-scaled size (" + pt
                    + "pt), not collapse to FlatLaf's unscaled default. "
                    + "That is keepStockFont doing the HiDPI work #76 asked "
                    + "about, without turning user scaling back on.");
            assertEquals(1.0f, UIScale.getUserScaleFactor(), 0.001f,
                "user scale must stay 1.0 after the switch at " + pt + "pt; "
                    + "if it rose, FlatLaf would be scaling insets on top of "
                    + "an already-scaled font");

            // What FlatLaf *would* do if user scaling were re-enabled with
            // this font still in the table: derive a factor from the font
            // size. On Linux that is pt/15; anywhere it is > 1 for these
            // sizes. Recording it here so a future change that flips the
            // property can see why that is not free.
            float wouldBe = UIScale.computeFontScaleFactor(scaled);
            assertTrue(wouldBe > 1.0f,
                "sanity: FlatLaf would treat Dialog " + pt
                    + "pt as a user scale of " + wouldBe
                    + " if user scaling were on — which is exactly why the "
                    + "font pin, not a re-enable, is the HiDPI answer");

            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases(),
                "the cycle at " + pt + "pt must be clean");
        }
    }
}
