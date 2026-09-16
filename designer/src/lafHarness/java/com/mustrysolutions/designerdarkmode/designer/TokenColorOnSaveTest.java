package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.common.BasicDataset;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A restyled design token, saved (#92, part 2).
 *
 * <p>What {@code PMIButton.initialize()} does to a button dropped from the
 * palette is reproduced literally: the static {@code Colors.ButtonForeground}
 * and {@code Colors.ButtonBackground} objects are set on a component. Under
 * dark mode those objects hold the dark palette, so a save made then writes
 * the dark values — the light-grey text on a light client. The platform
 * serializer and {@link SerializerProbeButton} show it, and show
 * {@link TokenColorDelegate} writing the stock values instead.
 *
 * <p>Same rule as the clean-copy test: the control comes first. A save without
 * the delegate must carry the dark values, or the assertions on the save with
 * it would pass against an instrument that sees nothing.
 */
class TokenColorOnSaveTest {

    private ThemeManager manager;
    private Color buttonForeground;
    private Color buttonBackground;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        SerializerCleanCopies.refresh();
        Class<?> colors = Class.forName(IaColorTokens.COLORS_CLASS);
        buttonForeground = (Color) colors.getField("ButtonForeground").get(null);
        buttonBackground = (Color) colors.getField("ButtonBackground").get(null);
    }

    @AfterEach
    void leaveTheJvmLight() throws Exception {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        SerializerCleanCopies.refresh();
    }

    @Test
    @DisplayName("a palette-fresh button saved under dark carries its stock colours, not the dark ones")
    void paletteFreshButtonSavesStockColours() throws Exception {
        int stockForeground = buttonForeground.getRGB();
        int stockBackground = buttonBackground.getRGB();

        manager.apply(true);
        assertEquals(List.of(), manager.failedPhases());
        int darkForeground = buttonForeground.getRGB();
        int darkBackground = buttonBackground.getRGB();
        assertTrue(darkForeground != stockForeground && darkBackground != stockBackground,
            "the token pass did not restyle the button tokens; nothing here is under test");

        // PMIButton.initialize(), verbatim: the token OBJECTS, not copies.
        SerializerProbeButton dropped = new SerializerProbeButton();
        dropped.setForeground(buttonForeground);
        dropped.setBackground(buttonBackground);

        String control = save(dropped, false);
        assertTrue(control.contains(clr(darkForeground)) && control.contains(clr(darkBackground)),
            "the control failed: without the delegate a dark save should carry the dark "
                + "token values, and did not:\n" + control);

        String saved = save(dropped, true);
        assertTrue(saved.contains(clr(stockForeground)),
            "the button's foreground was not written with the stock token value:\n" + saved);
        assertTrue(saved.contains(clr(stockBackground)),
            "the button's background was not written with the stock token value:\n" + saved);
        assertFalse(saved.contains(clr(darkForeground)) || saved.contains(clr(darkBackground)),
            "a dark token value reached the save; a light Vision client would show it:\n" + saved);
    }

    @Test
    @DisplayName("a colour the user picked is written as picked, even at a token's dark RGB")
    void userPickedColourIsKept() throws Exception {
        manager.apply(true);
        Color picked = new Color(buttonForeground.getRGB(), true);
        SerializerProbeButton button = new SerializerProbeButton();
        button.setForeground(picked);

        String saved = save(button, true);
        assertTrue(saved.contains(clr(picked.getRGB())),
            "a user's own colour must survive a save untouched; only the token OBJECTS "
                + "are substituted:\n" + saved);
    }

    @Test
    @DisplayName("a token inside a dataset cell is written with its stock value too")
    void datasetCellIsCoveredByTheSamePath() throws Exception {
        // PMINStateButton and PMIMultiStateIndicator keep their state colours
        // in a Dataset, built from the token objects in initialize().
        int stock = buttonForeground.getRGB();
        manager.apply(true);
        int dark = buttonForeground.getRGB();
        BasicDataset states = new BasicDataset(
            List.of("foreground"), List.of(Color.class), new Object[][] {{buttonForeground}});

        assertTrue(save(states, false).contains(clr(dark)), "control: the cell should be dark");
        String saved = save(states, true);
        assertTrue(saved.contains(clr(stock)) && !saved.contains(clr(dark)),
            "a dataset cell holding a token was not written with the stock value:\n" + saved);
    }

    @Test
    @DisplayName("under the stock theme the delegate is a pass-through and the XML is the platform's own")
    void lightSaveIsUntouched() throws Exception {
        assertNull(manager.stockTokenRgb().apply(buttonForeground),
            "no token is restyled while light, so nothing may be substituted");
        SerializerProbeButton button = new SerializerProbeButton();
        button.setForeground(buttonForeground);
        assertEquals(save(button, false), save(button, true),
            "with nothing restyled the delegate must produce exactly the platform's XML");
    }

    /** One save, as the Designer builds one — with or without the module's hook. */
    private String save(Object object, boolean withDelegate) throws Exception {
        XMLSerializer serializer = new XMLSerializer().initDefaults();
        if (withDelegate) {
            TokenColorDelegate.register(serializer, manager.stockTokenRgb());
        }
        serializer.addObject(object);
        return serializer.serializeXML();
    }

    /** The platform's own encoding of a colour: its signed ARGB int. */
    private static String clr(int argb) {
        return "<clr>" + argb + "</clr>";
    }
}
