package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.UIResource;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jidesoft.grid.QuickFilterField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A dock panel that was DETACHED while the light restore ran, and attached
 * afterwards.
 *
 * <p>This is the gap behind the Vision component palette's and property
 * editor's filter fields still being dark in an otherwise light Designer. The
 * restore itself is fine — it walks what is attached and fixes it, and the log
 * confirms it did ("re-ran updateUI on 1 component(s)"). What it cannot do is
 * fix a subtree that is not in the hierarchy at the time.
 *
 * <p>The sequence that breaks, reproduced below:
 *
 * <ol>
 *   <li>dark mode is applied while the panel is attached, so its filter field
 *       carries FlatLaf's dark {@code #46494B};</li>
 *   <li>the panel is detached — switching workspaces does this;</li>
 *   <li>the light restore runs and never sees it;</li>
 *   <li>the panel is attached again. {@code updateComponentTreeUI} runs over it
 *       parent first, JIDE's {@code LabeledTextField} copies its still-dark
 *       child's background onto itself, and the child goes light a step later.
 *       The wrapper stays dark for the rest of the session.</li>
 * </ol>
 */
class LateAttachedSubtreeTest {

    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
    }

    @AfterEach
    void leaveTheJvmLight() {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("a subtree attached after the restore is cleaned up too")
    void aLateAttachedSubtreeIsCleanedUp() {
        JPanel dock = new JPanel(new java.awt.BorderLayout());
        JPanel filter = filterPanel();
        dock.add(filter, java.awt.BorderLayout.NORTH);
        dock.setSize(400, 80);

        // 1) dark, while attached.
        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(dock);
        manager.swapWhiteTokenBackgrounds(dock);

        // 2) detached before the restore — a workspace switch.
        dock.remove(filter);

        // 3) the light restore never sees it.
        manager.apply(false);
        SwingUtilities.updateComponentTreeUI(dock);
        manager.refreshComponentsLeftDark(dock);

        // 4) attached again, and updated the way Swing updates it.
        dock.add(filter, java.awt.BorderLayout.NORTH);
        SwingUtilities.updateComponentTreeUI(dock);

        assertTrue(!darkLeftovers(dock).isEmpty(),
            "the late attach did not reproduce the problem, so the assertion below "
                + "would pass without the watcher doing anything");

        // What the light-mode watcher does when it fires.
        manager.refreshComponentsLeftDark(dock);

        assertEquals(List.of(), darkLeftovers(dock),
            "these components kept a dark look-and-feel colour after being attached "
                + "back into a light Designer");
    }

    /** Components still wearing a dark look-and-feel background. */
    private static List<String> darkLeftovers(Container root) {
        List<String> dark = new ArrayList<>();
        collect(root, dark);
        return dark;
    }

    private static void collect(Component component, List<String> dark) {
        if (component.isBackgroundSet()
                && component.getBackground() instanceof UIResource
                && ThemeManager.luminance(component.getBackground()) < 100) {
            dark.add(component.getClass().getName()
                + String.format(" bg=#%06X", component.getBackground().getRGB() & 0xFFFFFF));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, dark);
            }
        }
    }

    /** A JIDE filter field, the shape both affected panels put in their toolbar. */
    private static JPanel filterPanel() {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(new QuickFilterField() {
            @Override
            public void applyFilter(String text) {
            }
        }, java.awt.BorderLayout.CENTER);
        return panel;
    }
}
