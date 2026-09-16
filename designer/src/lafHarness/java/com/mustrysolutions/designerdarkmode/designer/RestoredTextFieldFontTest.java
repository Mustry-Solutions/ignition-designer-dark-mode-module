package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.util.List;

import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.client.IgnitionLookAndFeel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The font a Vision text field comes back with after the light restore
 * (#92, part 3).
 *
 * <p>Ignition tells Synthetica to keep its own font off every Vision
 * component by name: {@code BaseFormattedTextField.setName} calls
 * {@code IgnitionLookAndFeel.disableFontScaling(name)}, which puts
 * {@code Synthetica.font.enabled.<name>=false}, and Synthetica then hands
 * the component its raw theme font wrapped in a {@code ScalableFont} —
 * Dialog 12 in a Designer, because {@code SyntheticaLookAndFeel.setFont}
 * has replaced the theme's Tahoma 11. After a REINSTALL of Synthetica, the
 * first formatted-text-field style Synthetica serves is stale and still
 * carries Tahoma 11; every later one is right, and a component that got
 * the stale one is corrected by its next tree update. So, after the light
 * restore, the first formatted text field the restore's own tree walk
 * reaches ends up on Tahoma 11: visibly the wrong font, and a Vision save
 * of that window fails outright, since a {@code ScalableFont} that differs
 * from the clean copy cannot be serialized.
 *
 * <p>No Vision jar is needed to show it: the name registration is
 * client-api, and a plain {@code JFormattedTextField} carrying a registered
 * name takes the same path. One more ingredient: the switch to FlatLaf drops
 * the registration (Synthetica's uninstall takes its keys with it), so it
 * has to be made again under dark, which is what any Vision component
 * created or named under dark does through {@code setName}. The Vision
 * probe's {@code VisionWindowSaveTest} shows the same on the real component.
 */
class RestoredTextFieldFontTest {

    /** Any name will do; this is the one Vision's text field carries. */
    private static final String VISION_NAME = "Text Field";

    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        // What Vision does when it names a component, at stock.
        IgnitionLookAndFeel.disableFontScaling(VISION_NAME);
    }

    /** Dark mode, plus the registration a Vision component made under it. */
    private void goDark() {
        manager.apply(true);
        IgnitionLookAndFeel.disableFontScaling(VISION_NAME);
    }

    @AfterEach
    void leaveTheJvmLight() {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("a Vision-named text field open across the switch comes back with the stock font")
    void fieldOpenAcrossTheSwitchKeepsItsFont() throws Exception {
        onEdt(() -> {
            JPanel window = new JPanel();
            JFormattedTextField field = visionNamed();
            window.add(field);
            Font stock = field.getFont();
            assertTrue(stock instanceof javax.swing.plaf.UIResource,
                "the field must start with the look and feel's font, or the update never replaces it");

            goDark();
            SwingUtilities.updateComponentTreeUI(window);
            manager.apply(false);
            assertEquals(List.of(), manager.failedPhases());
            // The Designer's own restore runs ONE tree update over each open
            // window; a detached panel gets it here. This is the first
            // formatted-text-field style request after the reinstall.
            SwingUtilities.updateComponentTreeUI(window);

            assertSameFont(stock, field.getFont(), "the field open across the switch");
        });
    }

    @Test
    @DisplayName("a Vision-named text field born under dark comes back with the stock font")
    void fieldBornUnderDarkGetsTheStockFont() throws Exception {
        onEdt(() -> {
            JPanel reference = new JPanel();
            Font stock = visionNamedIn(reference).getFont();

            goDark();
            JPanel window = new JPanel();
            JFormattedTextField field = visionNamedIn(window);
            manager.apply(false);
            SwingUtilities.updateComponentTreeUI(window);

            assertSameFont(stock, field.getFont(), "the field born under dark");
        });
    }

    @Test
    @DisplayName("the first formatted text field styled after the restore is not special")
    void firstStyledFieldAfterRestoreIsRight() throws Exception {
        onEdt(() -> {
            Font stock = visionNamedIn(new JPanel()).getFont();
            goDark();
            manager.apply(false);
            // Nothing has asked Synthetica for a formatted-text-field style
            // since the reinstall except what the switch itself did.
            JFormattedTextField first = visionNamedIn(new JPanel());
            assertSameFont(stock, first.getFont(), "the first field styled after the restore");
        });
    }

    private static JFormattedTextField visionNamed() {
        JFormattedTextField field = new JFormattedTextField("x");
        field.setName(VISION_NAME);
        return field;
    }

    private static JFormattedTextField visionNamedIn(JPanel panel) {
        JFormattedTextField field = visionNamed();
        panel.add(field);
        SwingUtilities.updateComponentTreeUI(panel);
        return field;
    }

    private static void assertSameFont(Font expected, Font actual, String what) {
        assertEquals(describe(expected), describe(actual),
            what + " came back with a different font. Synthetica served its raw theme font "
                + "for the first formatted text field after the reinstall");
    }

    private static String describe(Font font) {
        return font == null ? "null"
            : font.getFamily() + " " + font.getSize2D() + (font.isBold() ? " bold" : "")
                + (font.isItalic() ? " italic" : "");
    }

    interface Body {
        void run() throws Exception;
    }

    private static void onEdt(Body body) throws Exception {
        Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        if (failure[0] instanceof AssertionError) {
            throw (AssertionError) failure[0];
        }
        if (failure[0] instanceof Exception) {
            throw (Exception) failure[0];
        }
        if (failure[0] != null) {
            throw new RuntimeException(failure[0]);
        }
    }
}
