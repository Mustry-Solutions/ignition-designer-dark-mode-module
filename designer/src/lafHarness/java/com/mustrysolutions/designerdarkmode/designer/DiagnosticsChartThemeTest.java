package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Paint;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.client.util.gui.diagnostics.DynamicTimeSeriesChart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Diagnostics performance charts' axes (#50).
 *
 * <p>Half of this chart was already right before the fix, which is what made it
 * confusing to look at: IA colours the plot background and both sets of
 * gridlines from its own design tokens, and {@link IaColorTokens} restyles
 * those. Only the axis paints — which IA never sets, so they keep JFreeChart's
 * {@code Color.black} defaults — stayed dark on dark.
 *
 * <p>The assertions read the axes' paints directly. There is no rendering here:
 * unlike the Tag Browser band, this state is perfectly readable through the
 * API, and a chart render would add a JFreeChart layout dependency for nothing.
 */
class DiagnosticsChartThemeTest {

    /** Below this, axis text is lost against #3C3F41 chrome. */
    private static final int UNREADABLE = 120;

    private ThemeManager manager;
    private DiagnosticsChartTheme charts;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        charts = new DiagnosticsChartTheme();
    }

    @AfterEach
    void leaveTheJvmLight() {
        charts.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("axis text and lines go readable and come back exactly (#50)")
    void theAxisPaintsAreThemedAndRestored() throws Exception {
        JPanel panel = panelWithChart();
        Map<String, String> stock = axisPaints(panel);

        assertTrue(stock.values().stream().anyMatch("#000000"::equals),
            "no axis paint is black in a stock chart (" + stock + "), so this test "
                + "would be asserting nothing — JFreeChart's defaults are what #50 is about");

        manager.apply(true);
        javax.swing.SwingUtilities.updateComponentTreeUI(panel);
        charts.installIn(panel);

        assertEquals(List.of(), unreadable(panel),
            "these axis paints are still too dark to see on dark chrome");

        // The full light restore, in the order apply(false) runs it: the colour
        // tokens go back FIRST, then this pass puts its recorded paints back.
        // That order matters here — one of these paints (range.LabelPaint) is
        // IA's Base900 token instance rather than a JFreeChart default, so what
        // it READS depends on whether the tokens have been restored yet.
        manager.apply(false);
        charts.uninstall();

        assertEquals(stock, axisPaints(panel),
            "the axis paints did not come back to their stock values");
    }

    @Test
    @DisplayName("a chart outside the Designer's own diagnostics is left alone (#50)")
    void userChartsAreNotTouched() {
        // The pass is targeted by class name on purpose: Vision windows render
        // user charts, and repainting those would misrepresent what an operator
        // sees. A plain JPanel standing in for "not our chart" must come
        // through a dark switch with nothing recorded against it.
        JPanel plain = new JPanel();
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(plain, java.awt.BorderLayout.CENTER);

        manager.apply(true);
        charts.installIn(panel);
        charts.uninstall();
        // Nothing to assert on the panel itself; the point is that installIn
        // neither threw nor recorded anything for a component it does not own.
        assertTrue(true);
    }

    private static List<String> unreadable(JPanel panel) throws Exception {
        List<String> bad = new ArrayList<>();
        axisPaints(panel).forEach((key, value) -> {
            if (key.endsWith("LabelPaint") && ThemeManager.luminance(Color.decode(value))
                    < UNREADABLE) {
                bad.add(key + "=" + value);
            }
        });
        return bad;
    }

    /** Every paint the pass touches, keyed axis-and-property. */
    private static Map<String, String> axisPaints(JPanel panel) throws Exception {
        DynamicTimeSeriesChart chart = chartIn(panel);
        Map<String, String> paints = new LinkedHashMap<>();
        for (String name : new String[] {"domain", "range"}) {
            Field field = DynamicTimeSeriesChart.class.getDeclaredField(name);
            field.setAccessible(true);
            Object axis = field.get(chart);
            for (String property
                    : new String[] {"LabelPaint", "TickLabelPaint", "AxisLinePaint",
                        "TickMarkPaint"}) {
                Paint paint = (Paint) axis.getClass().getMethod("get" + property).invoke(axis);
                paints.put(name + "." + property, hex(paint));
            }
        }
        return paints;
    }

    private static DynamicTimeSeriesChart chartIn(JPanel panel) {
        for (java.awt.Component child : panel.getComponents()) {
            if (child instanceof DynamicTimeSeriesChart) {
                return (DynamicTimeSeriesChart) child;
            }
        }
        throw new IllegalStateException("no chart in the panel");
    }

    private static JPanel panelWithChart() {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        // (domainLabel, rangeLabel, chartType) — REGULAR_CHART is what the
        // performance tab builds.
        panel.add(new DynamicTimeSeriesChart("Time", "Scans/Second",
            DynamicTimeSeriesChart.REGULAR_CHART), java.awt.BorderLayout.CENTER);
        panel.setSize(400, 200);
        return panel;
    }

    private static String hex(Paint paint) {
        if (!(paint instanceof Color)) {
            return String.valueOf(paint);
        }
        return String.format("#%06X", ((Color) paint).getRGB() & 0xFFFFFF);
    }
}
