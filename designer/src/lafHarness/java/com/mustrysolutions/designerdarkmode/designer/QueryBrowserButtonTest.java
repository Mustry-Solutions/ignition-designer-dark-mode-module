package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Query Browser's result-table buttons (#51).
 *
 * <p>{@code ResultTable$EditButton} paints its own vertical gradient behind the
 * label from eight literal {@code Color} constants — {@code GRADIENT1_COLOR} is
 * plain white — so `Auto Refresh`, `Edit`, `Apply` and `Discard` showed pale
 * boxes on dark chrome. The button component itself is correctly dark
 * ({@code bg=#4E5052|uires}), which is exactly why an inspection of the
 * component chain came back clean while the screen showed the problem.
 *
 * <h2>The assumption guard</h2>
 *
 * <p>This test reaches a package-private class by name, so it is only as
 * durable as that name. It is present on every version checked so far (8.3.0
 * through 8.3.8), and the guard is not there to paper over a known gap — it is
 * there so that a version which drops the class reports a clear skip rather
 * than a {@code ClassNotFoundException} that reads like a broken test. The
 * corresponding entry in {@code IaColorTokens.CLASS_DARK} fails soft the same
 * way.
 */
class QueryBrowserButtonTest {

    private static final String BUTTON =
        "com.inductiveautomation.ignition.designer.querybrowser.ResultTable$EditButton";

    /** The gradient and state fills the fix darkens. */
    private static final String[] FILLS = {
        "GRADIENT1_COLOR", "GRADIENT2_COLOR",
        "GRADIENT1_COLOR_DOWN", "GRADIENT2_COLOR_DOWN",
        "GRADIENT1_COLOR_TOGGLE", "GRADIENT2_COLOR_TOGGLE",
        "DISABLED_COLOR",
    };

    /** Accents the fix leaves alone: they read correctly against dark chrome. */
    private static final String[] ACCENTS = {"BORDER_COLOR_FOCUS", "BORDER_COLOR_OVER"};

    private IaColorTokens tokens;
    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        Assumptions.assumeTrue(present(),
            BUTTON + " is not on this harness classpath, so #51 cannot be exercised "
                + "against this Ignition version.");
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        tokens = new IaColorTokens();
    }

    @AfterEach
    void leaveTheJvmLight() {
        if (tokens != null) {
            tokens.uninstall();
        }
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("the button gradients go dark and come back exactly (#51)")
    void theGradientsAreDarkenedAndRestored() throws Exception {
        Map<String, String> stock = fills();

        assertTrue(stock.get("GRADIENT1_COLOR").equals("#FFFFFF"),
            "GRADIENT1_COLOR is " + stock.get("GRADIENT1_COLOR") + ", not white — the "
                + "button no longer paints the gradient this fix is about");

        tokens.install();

        fills().forEach((name, value) ->
            assertTrue(ThemeManager.luminance(Color.decode(value)) < 110,
                name + " is still light under dark mode: " + value));

        tokens.uninstall();

        assertEquals(stock, fills(),
            "the button fills did not come back to their stock values");
    }

    @Test
    @DisplayName("the focus and hover accents are left alone (#51)")
    void theAccentsAreUntouched() throws Exception {
        Map<String, String> stock = accents();

        tokens.install();

        assertEquals(stock, accents(),
            "the amber focus/hover accents were darkened; they are readable against "
                + "dark chrome already and recolouring them loses the affordance");
    }

    private static boolean present() {
        try {
            Class.forName(BUTTON);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private static Map<String, String> fills() throws Exception {
        return read(FILLS);
    }

    private static Map<String, String> accents() throws Exception {
        return read(ACCENTS);
    }

    private static Map<String, String> read(String[] names) throws Exception {
        Class<?> button = Class.forName(BUTTON);
        Map<String, String> colours = new LinkedHashMap<>();
        for (String name : names) {
            Field field = button.getDeclaredField(name);
            field.setAccessible(true);
            Color colour = (Color) field.get(null);
            colours.put(name, String.format("#%06X", colour.getRGB() & 0xFFFFFF));
        }
        return colours;
    }
}
