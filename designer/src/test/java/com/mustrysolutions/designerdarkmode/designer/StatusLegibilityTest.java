package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The status bar has to stay readable to be worth writing to.
 *
 * <p>{@code StatusBar.setMessage} sets the message label's foreground to
 * {@code Color.black} on every call, so under dark mode both the Designer's
 * messages and the module's own would be black on a dark bar. Correcting it
 * once is not enough — the next message re-asserts black — which is why the
 * lift is backed by a listener.
 */
class StatusLegibilityTest {

    private LookAndFeel original;
    private DesignerStatus status;

    @BeforeEach
    void setUp() throws Exception {
        original = UIManager.getLookAndFeel();
        UIManager.setLookAndFeel(new FlatDarkLaf());
        status = new DesignerStatus();
    }

    @AfterEach
    void tearDown() throws Exception {
        status.uninstall();
        if (original != null) {
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    @DisplayName("a black status label is lifted to a readable foreground")
    void liftsBlackLabel() {
        JLabel label = new JLabel("Project saved");
        label.setForeground(Color.BLACK);

        status.keepLegible(panelAround(label));

        assertNotEquals(Color.BLACK, label.getForeground());
        assertTrue(ThemeManager.luminance(label.getForeground()) > 90);
    }

    @Test
    @DisplayName("a label that is already readable is left alone")
    void leavesReadableLabelAlone() {
        Color readable = new Color(0xCCCCCC);
        JLabel label = new JLabel("Already light");
        label.setForeground(readable);

        status.keepLegible(panelAround(label));

        assertEquals(readable, label.getForeground());
    }

    @Test
    @DisplayName("black re-asserted later is lifted again")
    void reLiftsWhenTheDesignerResetsBlack() {
        JLabel label = new JLabel("Project saved");
        label.setForeground(Color.BLACK);
        status.keepLegible(panelAround(label));

        // What StatusBar.setMessage does on every single message.
        label.setForeground(Color.BLACK);

        assertNotEquals(Color.BLACK, label.getForeground());
    }

    @Test
    @DisplayName("uninstall hands the label back exactly as it was")
    void restoresTheOriginalForeground() {
        JLabel label = new JLabel("Project saved");
        label.setForeground(Color.BLACK);
        status.keepLegible(panelAround(label));

        status.uninstall();

        assertEquals(Color.BLACK, label.getForeground());
    }

    /**
     * The restore puts the original instance back, not an equal copy: a
     * look-and-feel-owned colour that came back as a plain Color would stop
     * following the theme on the next {@code updateComponentTreeUI}.
     */
    @Test
    @DisplayName("uninstall restores the original colour instance, UIResource and all")
    void restoresTheExactForegroundInstance() {
        Color owned = new ColorUIResource(0x101010);
        JLabel label = new JLabel("Project saved");
        label.setForeground(owned);
        status.keepLegible(panelAround(label));
        assertNotEquals(owned, label.getForeground());

        status.uninstall();

        assertSame(owned, label.getForeground());
    }

    /** Nothing is touched under the stock theme: black on a light bar is correct. */
    @Test
    @DisplayName("the light theme is left alone")
    void doesNothingUnderTheStockTheme() throws Exception {
        UIManager.setLookAndFeel(original);
        JLabel label = new JLabel("Project saved");
        label.setForeground(Color.BLACK);

        status.keepLegible(panelAround(label));

        assertEquals(Color.BLACK, label.getForeground());
    }

    private static JPanel panelAround(JLabel label) {
        JPanel panel = new JPanel();
        panel.add(label);
        return panel;
    }
}
