package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.util.List;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.util.SystemInfo;
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
 * {@code keepStockFont} reads. The one thing with content here is that the
 * stock (already scaled) font size is what dark mode paints — colour only,
 * which is the whole point of the pin in #93 — at 18 and 24 pt rather than
 * the Dialog 12 the cycle test uses.
 *
 * <p>The rest is bookkeeping, and is labelled as such: {@link
 * UIScale#isSystemScalingEnabled} is a Java-version check (true on 9+, it
 * never reads the property), and with user scaling off {@code UIScale}
 * returns before it computes anything, so a factor of 1.0 follows from the
 * property rather than measuring it. Nothing headless can see the JDK
 * transform; a live 125–150 % {@code env:} block still can (#96).
 *
 * <p>Re-enabling user scaling is not a fix for the Linux concern in #76.
 * With an already-scaled stock font, FlatLaf on Linux or macOS would derive
 * a user factor {@code > 1} from that font and scale insets on top of it —
 * and on every OS the permanent listener would come back. (On Windows
 * FlatLaf itself declines to double-scale; see the test body.) The font pin
 * is the portable answer; the comment in {@code startup} now says so for all
 * three platforms.
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
    @DisplayName("the harness runs under the shipped property, and FlatLaf's user-scale path is inert under it (#76)")
    void systemScalingStaysOnWhileUserScalingIsOff() {
        assertEquals("false", System.getProperty("flatlaf.uiScale.enabled"),
            "the harness must ship the same property ThemeManager.startup sets, "
                + "or the font test below runs a different configuration");
        // Bookkeeping, not measurement (see the class comment): the flag is a
        // Java-version check and the factor is what UIScale returns before it
        // computes anything. Kept so a FlatLaf upgrade that changes either
        // contract shows up here rather than in a user's log.
        assertTrue(UIScale.isSystemScalingEnabled(),
            "FlatLaf's system-scaling flag is true on Java 9+ and independent "
                + "of flatlaf.uiScale.enabled");
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
            // size — pt/15 on GNOME, pt/13 on KDE and macOS, so > 1 for these
            // sizes. Recording it here so a future change that flips the
            // property can see why that is not free off Windows.
            //
            // Not on Windows, where FlatLaf has its own guard against exactly
            // this double scaling: for a UIResource font whose size matches
            // the desktop's win.messagebox.font (or when that property is
            // null) it returns 1 as long as system scaling is on. On a 150 %
            // Windows desktop the message font IS 18 px, so the factor there
            // is 1, and the inset argument does not apply — the permanent
            // listener is the reason the property stays off on that OS.
            float wouldBe = UIScale.computeFontScaleFactor(scaled);
            if (!SystemInfo.isWindows) {
                assertTrue(wouldBe > 1.0f,
                    "sanity: FlatLaf would treat Dialog " + pt
                        + "pt as a user scale of " + wouldBe
                        + " if user scaling were on — which is why the font "
                        + "pin, not a re-enable, is the HiDPI answer here");
            }

            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases(),
                "the cycle at " + pt + "pt must be clean");
        }
    }
}
