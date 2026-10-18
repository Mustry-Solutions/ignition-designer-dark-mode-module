package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.sfc.client.SfcDisplayPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The SFC chart stays as a client draws it.
 *
 * <p>On 8.3 the chart editor draws the chart with the SFC client's own
 * {@code SfcDisplayPanel}, on a {@code Base000} white background, and its
 * steps and links are the JDK's {@code Color.WHITE} and {@code Color.BLACK},
 * which nothing may restyle. The white swap darkened the panel, which left
 * black links on #3A3D3F: found by the first QA sweep to open an SFC chart
 * (#41). The chart is left white, like a Vision window's canvas.
 */
class SfcChartWhiteSwapTest {

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

    @Test
    @DisplayName("the SFC chart and what it holds keep their white under dark")
    void theChartKeepsItsWhite() {
        SfcDisplayPanel chart = new SfcDisplayPanel();
        chart.setBackground(Color.WHITE);
        JPanel legend = new JPanel();
        legend.setBackground(Color.WHITE);
        chart.add(legend);
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.add(chart, BorderLayout.CENTER);

        manager.apply(true);
        manager.swapWhiteTokenBackgrounds(workspace);

        assertSame(Color.WHITE, chart.getBackground(),
            "the chart's white was swapped, so its black links sit on a dark panel");
        assertSame(Color.WHITE, legend.getBackground(),
            "a panel inside the chart was swapped, so the guard stops at the chart's edge");
    }

    @Test
    @DisplayName("a white panel beside the chart is still swapped (the guard is not a blanket skip)")
    void aWhitePanelOutsideTheChartIsStillSwapped() {
        JPanel chrome = new JPanel();
        chrome.setBackground(Color.WHITE);
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.add(new SfcDisplayPanel(), BorderLayout.CENTER);
        workspace.add(chrome, BorderLayout.SOUTH);

        manager.apply(true);
        manager.swapWhiteTokenBackgrounds(workspace);

        assertNotSame(Color.WHITE, chrome.getBackground(),
            "the white panel next to the chart was left alone, so the guard has disabled "
                + "the swap rather than narrowed it");
    }
}
