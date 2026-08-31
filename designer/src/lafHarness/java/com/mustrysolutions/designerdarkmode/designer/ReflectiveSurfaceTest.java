package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Paint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every Ignition, JIDE, JFreeChart and JDK name this module reaches by
 * <em>string</em>, asserted to still resolve (#53).
 *
 * <h2>The failure mode this exists for</h2>
 *
 * <p>This module does its work by reaching into internals: a class called
 * {@code InlineTipLabel}, a field called {@code COLOR}, a method called
 * {@code setLineHighlightColor}. None of that is checked by the compiler,
 * because none of it is written as a type. When IA or JIDE renames one, the
 * pass that depends on it stops working — and it stops working <em>silently</em>,
 * because every one of these passes is deliberately wrapped in a guard so that
 * a failure costs a surface rather than the Designer. [#35] was exactly this,
 * discovered only because someone went looking.
 *
 * <p>So: enumerate the names and resolve them. It cannot tell you a class still
 * <em>behaves</em> the same — that is what a Designer and the QA checklist are
 * for — but it turns "quietly stopped theming after an upgrade" into a red
 * build on the day the upgrade lands.
 *
 * <h2>Where the inventory comes from</h2>
 *
 * <p>Class names, token fields and the class-constant map are read from the
 * production classes themselves, so those halves cannot drift. Method and field
 * names are listed here, grouped by the class that reaches them — when you add
 * a reflective call, add it below.
 *
 * <h2>Run it against the versions people use</h2>
 *
 * <p>Pointless against the compile floor alone, which is the whole of #53: the
 * harness resolves {@code harness_sdk_version} (8.3.8 by default,
 * {@code -Pharness.sdk=8.3.6} to pin). A rename in 8.3.7 is invisible if you
 * only ever test 8.3.0.
 */
class ReflectiveSurfaceTest {

    @Test
    @DisplayName("every class the module names by string still resolves (#53)")
    void everyNamedClassResolves() {
        Map<String, String> named = new LinkedHashMap<>();
        named.put("BlockWorkspaceTheme", BlockWorkspaceTheme.BASIC_BLOCK_UI);
        named.put("CodeEditorTheme", CodeEditorTheme.CODE_EDITOR);
        named.put("ConsoleTextTheme", ConsoleTextTheme.OUTPUT_CONSOLE);
        named.put("DiagnosticsChartTheme", DiagnosticsChartTheme.CHART_PANEL);
        named.put("IaColorTokens", IaColorTokens.COLORS_CLASS);
        named.put("ScriptEditorTheme", ScriptEditorTheme.NAMED_THEME);
        named.put("ThemeManager.stock", ThemeManager.STOCK_LAF_CLASS);
        named.put("ThemeManager.synthetica", ThemeManager.SYNTHETICA_LAF);
        named.put("ThemeManager.jideFactory", ThemeManager.JIDE_LAF_FACTORY);
        named.put("ThemeManager.basicPainter", ThemeManager.BASIC_PAINTER);
        named.put("ThemeManager.themePainter", ThemeManager.THEME_PAINTER_TYPE);

        List<String> missing = new ArrayList<>();
        named.forEach((owner, className) -> {
            if (!resolves(className)) {
                missing.add(owner + " -> " + className);
            }
        });

        assertEquals(List.of(), missing,
            "these classes no longer exist on this Ignition. The pass named on the left "
                + "has silently stopped working — its guard swallows the failure by design");
    }

    @Test
    @DisplayName("every IA colour token the module restyles still exists (#53)")
    void everyColourTokenResolves() throws Exception {
        Class<?> colours = Class.forName(IaColorTokens.COLORS_CLASS);
        List<String> missing = new ArrayList<>();
        for (String token : IaColorTokens.DARK.keySet()) {
            try {
                if (colours.getField(token).getType() != Color.class) {
                    missing.add(token + " (no longer a Color)");
                }
            } catch (NoSuchFieldException gone) {
                missing.add(token);
            }
        }
        assertTrue(IaColorTokens.DARK.size() > 10,
            "only " + IaColorTokens.DARK.size() + " tokens in the map — that is not the "
                + "palette this module restyles, so this test is checking almost nothing");
        assertEquals(List.of(), missing,
            "these design tokens are gone from " + IaColorTokens.COLORS_CLASS
                + "; the chrome they colour is no longer themed");
    }

    @Test
    @DisplayName("every hardcoded class constant the module rewrites still exists (#53)")
    void everyClassConstantResolves() {
        List<String> missing = new ArrayList<>();
        IaColorTokens.CLASS_DARK.forEach((className, fields) -> {
            Class<?> owner;
            try {
                owner = Class.forName(className);
            } catch (ClassNotFoundException absent) {
                missing.add(className + " (whole class)");
                return;
            }
            fields.keySet().forEach(field -> {
                try {
                    if (owner.getDeclaredField(field).getType() != Color.class) {
                        missing.add(className + "." + field + " (no longer a Color)");
                    }
                } catch (NoSuchFieldException gone) {
                    missing.add(className + "." + field);
                }
            });
        });
        assertEquals(List.of(), missing,
            "these literal colours can no longer be found and are no longer darkened — "
                + "each one is a surface that stays light under dark mode");
    }

    @Test
    @DisplayName("every method and field the module reaches by name still resolves (#53)")
    void everyMemberResolves() {
        List<String> missing = new ArrayList<>();

        // --- CodeEditorTheme (#48) -----------------------------------------
        methods(missing, CodeEditorTheme.CODE_EDITOR,
            "getLineHighlightColor", "getCaretColor", "getSelectionColor",
            "getBracketHighlightColor", "getStyles");
        setters(missing, CodeEditorTheme.CODE_EDITOR, Color.class,
            "setLineHighlightColor", "setCaretColor", "setSelectionColor",
            "setBracketHighlightColor");
        methods(missing, "com.jidesoft.editor.SyntaxStyleSchema", "getStyleCount");
        method(missing, "com.jidesoft.editor.SyntaxStyleSchema", "getStyleByIndex", int.class);
        methods(missing, "com.jidesoft.editor.SyntaxStyle", "getForeground", "getBackground");
        setters(missing, "com.jidesoft.editor.SyntaxStyle", Color.class,
            "setForeground", "setBackground");

        // --- DiagnosticsChartTheme (#50) -----------------------------------
        fields(missing, DiagnosticsChartTheme.CHART_PANEL, "domain", "range", "chart");
        for (String paint : DiagnosticsChartTheme.AXIS_PAINTS) {
            methods(missing, "org.jfree.chart.axis.ValueAxis", "get" + paint);
            method(missing, "org.jfree.chart.axis.ValueAxis", "set" + paint, Paint.class);
        }
        methods(missing, "org.jfree.chart.JFreeChart", "getPlot", "getBackgroundPaint");
        method(missing, "org.jfree.chart.JFreeChart", "setBackgroundPaint", Paint.class);
        methods(missing, "org.jfree.chart.plot.Plot", "getBackgroundPaint", "getOutlinePaint");
        setters(missing, "org.jfree.chart.plot.Plot", Paint.class,
            "setBackgroundPaint", "setOutlinePaint");

        // --- ConsoleTextTheme (#52) ----------------------------------------
        methods(missing, ConsoleTextTheme.OUTPUT_CONSOLE, "getInstance");
        fields(missing, ConsoleTextTheme.OUTPUT_CONSOLE, "pane", "_out", "_err");
        fields(missing, "com.inductiveautomation.ignition.common.util.BifurcatingOutputStream",
            "subs");
        fields(missing, ConsoleTextTheme.OUTPUT_CONSOLE + "$ConsoleAppender", "bg");

        // --- ThemeManager --------------------------------------------------
        fields(missing, ThemeManager.SYNTHETICA_LAF, "activeInstance");
        method(missing, ThemeManager.SYNTHETICA_LAF, "setLookAndFeel",
            String.class, boolean.class, boolean.class);
        method(missing, ThemeManager.SYNTHETICA_LAF, "setFont", String.class, int.class);
        methods(missing, ThemeManager.JIDE_LAF_FACTORY, "installJideExtension");
        method(missing, ThemeManager.JIDE_LAF_FACTORY, "installJideExtension", int.class);
        methods(missing, ThemeManager.BASIC_PAINTER, "getInstance");

        // --- ScriptEditorTheme ---------------------------------------------
        methods(missing, ScriptEditorTheme.NAMED_THEME, "getTheme");

        // --- BlockWorkspaceTheme (alarm pipelines, SFC) ---------------------
        for (String property : BlockWorkspaceTheme.FILLS) {
            methods(missing, BlockWorkspaceTheme.BASIC_BLOCK_UI, "get" + property);
            method(missing, BlockWorkspaceTheme.BASIC_BLOCK_UI, "set" + property, Color.class);
        }
        for (String property : BlockWorkspaceTheme.STROKES) {
            methods(missing, BlockWorkspaceTheme.BASIC_BLOCK_UI, "get" + property);
            method(missing, BlockWorkspaceTheme.BASIC_BLOCK_UI, "set" + property, Color.class);
        }

        // --- CellRendererSanitizer + IaColorTokens reach into the JDK -------
        // These break on a JDK upgrade rather than an Ignition one, and the
        // module already fails soft on them — but silently, so they belong here.
        fields(missing, "javax.swing.plaf.basic.BasicTableUI", "rendererPane");
        fields(missing, "java.awt.Color", "value", "frgbvalue", "fvalue");

        assertEquals(List.of(), missing,
            "these members can no longer be reached. Each one is a pass that has stopped "
                + "working without saying so");
    }

    private static boolean resolves(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    private static void methods(List<String> missing, String className, String... names) {
        for (String name : names) {
            method(missing, className, name);
        }
    }

    private static void setters(List<String> missing, String className,
            Class<?> parameter, String... names) {
        for (String name : names) {
            method(missing, className, name, parameter);
        }
    }

    private static void method(List<String> missing, String className,
            String name, Class<?>... parameters) {
        try {
            Class.forName(className).getMethod(name, parameters);
        } catch (ClassNotFoundException absent) {
            missing.add(className + " (class)");
        } catch (NoSuchMethodException gone) {
            missing.add(className + "." + name + signature(parameters));
        }
    }

    private static void fields(List<String> missing, String className, String... names) {
        for (String name : names) {
            try {
                Class.forName(className).getDeclaredField(name);
            } catch (ClassNotFoundException absent) {
                missing.add(className + " (class)");
            } catch (NoSuchFieldException gone) {
                missing.add(className + "." + name);
            }
        }
    }

    private static String signature(Class<?>... parameters) {
        if (parameters.length == 0) {
            return "()";
        }
        StringBuilder text = new StringBuilder("(");
        for (int i = 0; i < parameters.length; i++) {
            text.append(i == 0 ? "" : ", ").append(parameters[i].getSimpleName());
        }
        return text.append(")").toString();
    }
}
