package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.common.gui.border.FieldBorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Ignition's {@code FieldBorder} paints under dark (#194).
 *
 * <p>Every {@code FieldBorder} paints through one {@code private static final}
 * border, taken from a throwaway {@code JTextField} when the class is first
 * used: under the stock look and feel, a {@code SynthBorder} whose Synthetica
 * painter throws a {@code NullPointerException} once FlatLaf is installed.
 * The throw aborted the whole paint of Vision's Border Chooser, and left
 * every component given a Field Border without one, logging the exception
 * on every repaint.
 */
@ExtendWith(RunOnEdt.class)
class FieldBorderTest {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 28;

    private ThemeManager manager;

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
    }

    /** The precondition that makes the test mean anything: the class was set up under stock. */
    private static void assertTakenUnderStock() throws Exception {
        assertEquals("javax.swing.plaf.synth.SynthBorder", sharedBorder().getClass().getName(),
            "FieldBorder was first used under another look and feel, so this JVM cannot show #194");
    }

    private static Border sharedBorder() throws Exception {
        Field sub = FieldBorder.class.getDeclaredField("subBorder");
        sub.setAccessible(true);
        return (Border) sub.get(null);
    }

    /** What the shared border paints with: Synthetica's UI, or one of this module's classes. */
    private static Class<?> sharedBorderUi() throws Exception {
        Border shared = sharedBorder();
        Field ui = shared.getClass().getDeclaredField("ui");
        ui.setAccessible(true);
        return ui.get(shared).getClass();
    }

    private static boolean paintsSomething(int[] pixels) {
        for (int pixel : pixels) {
            if ((pixel >>> 24) != 0) {
                return true;
            }
        }
        return false;
    }

    private static int[] paint(Border border, JComponent on) {
        on.setSize(WIDTH, HEIGHT);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            border.paintBorder(on, g, 0, 0, WIDTH, HEIGHT);
        } finally {
            g.dispose();
        }
        return image.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
    }

    @Test
    @DisplayName("a FieldBorder paints under dark, on a panel and on a text field")
    void paintsUnderDark() throws Exception {
        FieldBorder border = new FieldBorder();
        assertTakenUnderStock();

        manager.apply(true);

        int[] onPanel = assertDoesNotThrow(() -> paint(border, new JPanel()),
            "a Vision component with a Field Border");
        int[] onField = assertDoesNotThrow(() -> paint(border, new JTextField()),
            "a text field, which FieldBorder paints through its own stand-in panel");
        assertTrue(paintsSomething(onPanel), "no exception, but no border either, on a panel");
        assertTrue(paintsSomething(onField), "no exception, but no border either, on a text field");
    }

    /**
     * The shared border is a static of Ignition's: left holding one of this
     * module's classes, it would pin the module's classloader after an
     * unload, and paint from code that should be gone.
     */
    @Test
    @DisplayName("module shutdown gives FieldBorder its own Synthetica UI back")
    void shutdownPutsTheOriginalBack() throws Exception {
        new FieldBorder();
        assertTakenUnderStock();

        manager.apply(true);
        assertEquals(FieldBorders.Substitute.class, sharedBorderUi(), "the switch installed nothing");
        manager.shutdown();

        assertFalse(sharedBorderUi().getName().startsWith("com.mustrysolutions."),
            "shutdown left this module's class in FieldBorder's static border");
    }

    @Test
    @DisplayName("after the light restore a FieldBorder paints as it did before the switch")
    void paintsAsStockAfterTheRestore() throws Exception {
        boolean headless = GraphicsEnvironment.isHeadless();
        if (Boolean.getBoolean(WindowedCycleTest.WINDOWED_PROPERTY)) {
            assertFalse(headless, "-Pharness.windowed=true was given, but this JVM is headless.");
        }
        Assumptions.assumeFalse(headless, "no display: Synthetica cannot paint headlessly.");
        FieldBorder border = new FieldBorder();
        assertTakenUnderStock();
        int[] before = paint(border, new JPanel());

        manager.apply(true);
        paint(border, new JPanel());
        manager.apply(false);

        assertArrayEquals(before, paint(border, new JPanel()),
            "the restored Designer paints a Field Border differently from the stock one");
    }
}
