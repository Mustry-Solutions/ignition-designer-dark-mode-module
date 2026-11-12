package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Graphics;
import java.lang.reflect.Field;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthUI;

import com.formdev.flatlaf.FlatLaf;

/**
 * Lets Ignition's {@code FieldBorder} paint under dark (#194).
 *
 * <p>Every {@code FieldBorder} paints through one {@code private static final}
 * border, taken from a throwaway {@code JTextField} when the class is first
 * used: under the stock look and feel, a {@code SynthBorder} that asks the
 * field's Synthetica UI to paint. Once FlatLaf is installed, Synthetica's
 * painter throws a {@code NullPointerException} on every paint. That aborted
 * the whole paint of Vision's Border Chooser, and left every component with a
 * Field Border without one.
 *
 * <p>The static field cannot be replaced (it is final, and the JIT may have
 * folded it), and a Vision component's {@code FieldBorder} is user content
 * that serializes. So the {@code SynthBorder} behind it keeps its identity and
 * gets a different UI to paint with: {@link Substitute} paints FlatLaf's text
 * field border while FlatLaf is installed, and hands everything else, the
 * insets included, to the original. The layout of a Field Border component
 * does not change with the theme.
 *
 * <p>Installed under the stock look and feel, before the first switch to dark,
 * which also makes sure {@code FieldBorder} is first used under stock, as it
 * is in a Designer that never switches. Taken out at module shutdown: the
 * static outlives the module, and would pin its classloader.
 */
final class FieldBorders {

    static final String FIELD_BORDER_CLASS = "com.inductiveautomation.ignition.common.gui.border.FieldBorder";
    static final String SUB_BORDER_FIELD = "subBorder";
    static final String SYNTH_BORDER_CLASS = "javax.swing.plaf.synth.SynthBorder";
    static final String SYNTH_BORDER_UI_FIELD = "ui";

    private static Border substituted;

    private FieldBorders() {
    }

    /**
     * Give {@code FieldBorder}'s shared border the substitute UI. Idempotent.
     * Call under the stock look and feel.
     */
    static void install() throws ReflectiveOperationException {
        if (substituted != null) {
            return;
        }
        Class<?> fieldBorder = Class.forName(FIELD_BORDER_CLASS, true, FieldBorders.class.getClassLoader());
        Field subBorder = fieldBorder.getDeclaredField(SUB_BORDER_FIELD);
        subBorder.setAccessible(true);
        Border shared = (Border) subBorder.get(null);
        if (shared == null || !SYNTH_BORDER_CLASS.equals(shared.getClass().getName())) {
            // First used under another look and feel: not Synthetica's
            // painter, so not the bug.
            DebugLog.log("FieldBorders: FieldBorder paints with "
                + (shared == null ? "null" : shared.getClass().getName()) + "; left alone.");
            return;
        }
        Field ui = uiField(shared);
        SynthUI original = (SynthUI) ui.get(shared);
        if (original instanceof Substitute) {
            substituted = shared;
            return;
        }
        ui.set(shared, new Substitute(original));
        substituted = shared;
        DebugLog.detail("FieldBorders: FieldBorder paints FlatLaf's text field border under dark.");
    }

    /** Put the original UI back. Never throws. */
    static void uninstall() {
        Border shared = substituted;
        if (shared == null) {
            return;
        }
        substituted = null;
        try {
            Field ui = uiField(shared);
            Object current = ui.get(shared);
            if (current instanceof Substitute) {
                ui.set(shared, ((Substitute) current).original);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            DebugLog.log("FieldBorders: could not put FieldBorder's own UI back.", e);
        }
    }

    private static Field uiField(Border synthBorder) throws NoSuchFieldException {
        Field ui = synthBorder.getClass().getDeclaredField(SYNTH_BORDER_UI_FIELD);
        ui.setAccessible(true);
        return ui;
    }

    /** Synthetica's UI under stock, FlatLaf's text field border under dark. */
    static final class Substitute implements SynthUI {

        final SynthUI original;

        Substitute(SynthUI original) {
            this.original = original;
        }

        @Override
        public SynthContext getContext(JComponent c) {
            return original.getContext(c);
        }

        @Override
        public void paintBorder(SynthContext context, Graphics g, int x, int y, int w, int h) {
            if (UIManager.getLookAndFeel() instanceof FlatLaf) {
                Border flat = UIManager.getBorder("TextField.border");
                if (flat != null) {
                    flat.paintBorder(context.getComponent(), g, x, y, w, h);
                }
                return;
            }
            original.paintBorder(context, g, x, y, w, h);
        }
    }
}
