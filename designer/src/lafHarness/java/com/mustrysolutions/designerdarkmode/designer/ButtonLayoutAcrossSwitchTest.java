package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.GraphicsEnvironment;
import java.awt.Insets;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jidesoft.swing.JideToggleButton;
import net.miginfocom.swing.MigLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A button keeps the alignment and margin its application gave it through a
 * switch (#174).
 *
 * <p>Vision builds each component palette item as a {@code JideToggleButton}
 * and only then makes it left-aligned with 20 px more left margin. Two things
 * undid that. Synthetica records every button's alignment and margin when it
 * first styles it, during construction, and writes them back when it is
 * uninstalled: the switch to dark put back CENTER, and every row came out
 * centered. And under Synthetica, JIDE's button UI takes the margin from the
 * Synth style on every {@code updateUI}: the light restore's tree update took
 * the 20 px indent away. The palette item sits in a real (never shown) frame,
 * so the restore's tree update reaches it as it reaches the Designer's palette.
 */
@ExtendWith(RunOnEdt.class)
class ButtonLayoutAcrossSwitchTest {

    private ThemeManager manager;
    private JFrame frame;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = ManagerCleanup.newManager();
        manager.captureStockLaf();
    }

    @AfterEach
    void leaveTheJvmLight() {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        if (frame != null) {
            frame.dispose();
        }
    }

    @Test
    @DisplayName("a palette item keeps its left alignment and margin in dark and after the restore (#174)")
    void paletteItem() {
        boolean headless = GraphicsEnvironment.isHeadless();
        if (Boolean.getBoolean(WindowedCycleTest.WINDOWED_PROPERTY)) {
            assertFalse(headless, "-Pharness.windowed=true was given, but this JVM is headless.");
        }
        Assumptions.assumeFalse(headless, "the restore's tree update only reaches a button in a window");
        JideToggleButton item = new JideToggleButton("Numeric Text Field", null, false);
        JPanel view = new JPanel(new MigLayout("ins 0, flowy, fillx, gap 1px, hidemode 3", "[fill]"));
        view.add(item);
        frame = new JFrame("ButtonLayoutAcrossSwitchTest");
        frame.setContentPane(view);
        frame.pack();
        assertLayoutSurvives(item);
    }

    @Test
    @DisplayName("a plain button keeps the alignment its application set")
    void plainButtonAlignment() {
        JButton button = new JButton("Browse...");
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setVerticalAlignment(SwingConstants.TOP);

        manager.apply(true);
        assertEquals(SwingConstants.LEFT, button.getHorizontalAlignment(), "horizontal alignment under dark");
        assertEquals(SwingConstants.TOP, button.getVerticalAlignment(), "vertical alignment under dark");
        manager.apply(false);
        assertEquals(SwingConstants.LEFT, button.getHorizontalAlignment(), "horizontal alignment after the restore");
        assertEquals(SwingConstants.TOP, button.getVerticalAlignment(), "vertical alignment after the restore");
    }

    /**
     * Only a JIDE button keeps its margin. Ignition gives the Vision welcome
     * page's buttons a margin of 0 above and below; Synthetica's border pads
     * them, FlatLaf's does not, and kept into dark they came out cramped.
     */
    @Test
    @DisplayName("a plain button's margin still gives way to the dark look and feel's")
    void plainButtonMargin() {
        JButton button = new JButton("Create");
        Insets welcomePage = new Insets(0, 10, 0, 10);
        button.setMargin(welcomePage);

        manager.apply(true);
        assertNotEquals(welcomePage, button.getMargin(), "the welcome page's margin was kept into dark");
    }

    /** What Vision's CollapsiblePanePalette$GroupView$View does to each item it builds. */
    private void assertLayoutSurvives(AbstractButton button) {
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setVerticalAlignment(SwingConstants.TOP);
        Insets stock = button.getMargin();
        Insets set = new Insets(stock.top, stock.left + 20, stock.bottom, stock.right);
        button.setMargin(set);

        manager.apply(true);
        assertEquals(SwingConstants.LEFT, button.getHorizontalAlignment(), "horizontal alignment under dark");
        assertEquals(SwingConstants.TOP, button.getVerticalAlignment(), "vertical alignment under dark");
        assertEquals(set, button.getMargin(), "margin under dark");

        manager.apply(false);
        assertEquals(SwingConstants.LEFT, button.getHorizontalAlignment(), "horizontal alignment after the restore");
        assertEquals(SwingConstants.TOP, button.getVerticalAlignment(), "vertical alignment after the restore");
        assertEquals(set, button.getMargin(), "margin after the restore");
    }
}
