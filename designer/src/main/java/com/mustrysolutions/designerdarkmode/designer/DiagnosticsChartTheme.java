package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Paint;
import java.awt.Window;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The axes of the Diagnostics performance charts (#50).
 *
 * <h2>Why only the axes</h2>
 *
 * <p>{@code DynamicTimeSeriesChart} builds a JFreeChart {@code XYPlot} and
 * colours part of it from IA's own design tokens — {@code Base100} for the plot
 * background, {@code Base500} for both sets of gridlines, {@code IconPositive}
 * and {@code IconNegative} for the series. {@link IaColorTokens} already
 * restyles those, which is why the plot area comes out correctly dark and this
 * looked half-themed rather than untouched.
 *
 * <p>What IA never sets is the axis paints, so they keep JFreeChart's own
 * defaults: {@code Axis.DEFAULT_AXIS_LABEL_PAINT} and
 * {@code DEFAULT_TICK_LABEL_PAINT} are {@code Color.black}, and so are the axis
 * line and tick marks. Black on {@code #3C3F41} — the axis numbers and titles
 * are all but invisible, which is what a real Designer showed.
 *
 * <h2>Why this is targeted rather than general</h2>
 *
 * <p>It would be easy to walk for any {@code ChartPanel} and theme whatever
 * chart it holds. That would be wrong: Vision windows render <em>user</em>
 * charts, and repainting those would misrepresent what an operator will
 * actually see — the same reasoning that keeps every pass in this module out of
 * the Vision design canvas. {@code DynamicTimeSeriesChart} is Designer
 * diagnostics chrome by definition, so it is named explicitly.
 */
final class DiagnosticsChartTheme {

    // Package-private so ReflectiveSurfaceTest can assert this name still
    // resolves against the Ignition the harness runs.
    static final String CHART_PANEL =
        "com.inductiveautomation.ignition.client.util.gui.diagnostics.DynamicTimeSeriesChart";

    /** Axis titles and tick labels: the same value as the dark Base900 text. */
    private static final Color AXIS_TEXT = new Color(0xC4C9CD);
    /** Axis lines and tick marks, a shade below the text so they recede. */
    private static final Color AXIS_LINE = new Color(0x5F6467);
    /**
     * The chart's own background — the margin the axis labels sit in.
     *
     * <p>JFreeChart defaults this to white and IA never sets it, so it is the
     * one surface that stays bright after the axes are fixed. Matched to the
     * dialog behind it so the chart does not read as a pale card.
     */
    private static final Color CHART_BACKGROUND = new Color(0x3C3F41);
    /** The plot well, a shade below the chart so the data area reads as inset. */
    private static final Color PLOT_BACKGROUND = new Color(0x35383A);

    /** The four paints this pass sets on each axis, in order. */
    // Package-private so ReflectiveSurfaceTest can assert this name still
    // resolves against the Ignition the harness runs.
    static final String[] AXIS_PAINTS = {
        "LabelPaint", "TickLabelPaint", "AxisLinePaint", "TickMarkPaint",
    };

    /** axis -> its stock paints, in {@link #AXIS_PAINTS} order. */
    private final Map<Object, Paint[]> axisPaints = new IdentityHashMap<>();
    /** chart -> {chart background, plot background, plot outline}, as found. */
    private final Map<Object, Paint[]> chartPaints = new IdentityHashMap<>();

    private boolean unavailable;

    /** Theme the axes of every diagnostics chart in the UI. Safe to re-run. */
    void install() {
        for (Window window : Window.getWindows()) {
            installIn(window);
        }
    }

    /** Package-private so tests can drive the walk without a real Window. */
    void installIn(Container container) {
        if (unavailable) {
            return;
        }
        for (Component child : container.getComponents()) {
            if (isDiagnosticsChart(child)) {
                theme(child);
            }
            if (child instanceof Container) {
                installIn((Container) child);
            }
        }
    }

    /** Put every recorded paint back. */
    void uninstall() {
        chartPaints.forEach((chart, stock) -> {
            try {
                set(chart, "setBackgroundPaint", stock[0]);
                Object plot = chart.getClass().getMethod("getPlot").invoke(chart);
                set(plot, "setBackgroundPaint", stock[1]);
                set(plot, "setOutlinePaint", stock[2]);
            } catch (Throwable t) {
                DebugLog.log("Could not restore a chart's background paints.", t);
            }
        });
        chartPaints.clear();
        axisPaints.forEach((axis, stock) -> {
            for (int i = 0; i < AXIS_PAINTS.length; i++) {
                try {
                    axis.getClass()
                        .getMethod("set" + AXIS_PAINTS[i], Paint.class)
                        .invoke(axis, stock[i]);
                } catch (Throwable t) {
                    DebugLog.log("Could not restore a chart axis's "
                        + AXIS_PAINTS[i] + ".", t);
                }
            }
        });
        axisPaints.clear();
    }

    private static boolean isDiagnosticsChart(Component component) {
        for (Class<?> type = component.getClass(); type != null; type = type.getSuperclass()) {
            if (CHART_PANEL.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private void theme(Object chart) {
        try {
            for (Object axis : axesOf(chart)) {
                if (axis == null || axisPaints.containsKey(axis)) {
                    continue;
                }
                Paint[] stock = new Paint[AXIS_PAINTS.length];
                for (int i = 0; i < AXIS_PAINTS.length; i++) {
                    stock[i] = (Paint) axis.getClass()
                        .getMethod("get" + AXIS_PAINTS[i]).invoke(axis);
                }
                axisPaints.put(axis, stock);
                set(axis, "setLabelPaint", AXIS_TEXT);
                set(axis, "setTickLabelPaint", AXIS_TEXT);
                set(axis, "setAxisLinePaint", AXIS_LINE);
                set(axis, "setTickMarkPaint", AXIS_LINE);
            }
            themeChartBackground(chart);
            ((Component) chart).repaint();
        } catch (Throwable t) {
            // A JFreeChart or an IA class that has moved fails identically on
            // every chart, so stop rather than log once per panel.
            unavailable = true;
            DebugLog.log("Diagnostics chart axes could not be themed.", t);
        }
    }

    /**
     * The chart's own background, the plot well and the plot outline.
     *
     * <p>The axes alone are not enough: JFreeChart's default chart background
     * is white and IA never overrides it, so fixing only the axis paints left
     * the label margin bright — which is what a Designer showed after the first
     * cut of this pass.
     */
    private void themeChartBackground(Object panel) throws Exception {
        Field field = panel.getClass().getDeclaredField("chart");
        field.setAccessible(true);
        Object chart = field.get(panel);
        if (chart == null || chartPaints.containsKey(chart)) {
            return;
        }
        Object plot = chart.getClass().getMethod("getPlot").invoke(chart);
        chartPaints.put(chart, new Paint[] {
            (Paint) chart.getClass().getMethod("getBackgroundPaint").invoke(chart),
            (Paint) plot.getClass().getMethod("getBackgroundPaint").invoke(plot),
            (Paint) plot.getClass().getMethod("getOutlinePaint").invoke(plot),
        });
        set(chart, "setBackgroundPaint", CHART_BACKGROUND);
        set(plot, "setBackgroundPaint", PLOT_BACKGROUND);
        set(plot, "setOutlinePaint", AXIS_LINE);
    }

    /**
     * The chart's two axes, read from the private {@code domain} and
     * {@code range} fields.
     *
     * <p>Read from the fields rather than from the plot because that is where
     * IA keeps them and it needs no assumptions about the plot's axis
     * indexing — but either way this is private state of a class that is not
     * SDK surface, which is why the whole pass fails soft.
     */
    private static List<Object> axesOf(Object chart) throws Exception {
        List<Object> axes = new ArrayList<>(2);
        for (String name : new String[] {"domain", "range"}) {
            Field field = chart.getClass().getDeclaredField(name);
            field.setAccessible(true);
            axes.add(field.get(chart));
        }
        return axes;
    }

    private static void set(Object target, String setter, Paint value) throws Exception {
        target.getClass().getMethod(setter, Paint.class).invoke(target, value);
    }
}
