package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.awt.Insets;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.synth.SynthLookAndFeel;

/**
 * Keeps Synthetica's uninstall from undoing the alignment and margin an
 * application gave its buttons (#174).
 *
 * <p>The first time Synthetica styles a button it records the button's
 * horizontal and vertical alignment and its margin, and when it is
 * uninstalled it writes every recorded value back. It records all three
 * whether or not its theme changes them, and it records them during
 * construction, before the application has set anything. Vision builds each
 * component palette item as a {@code JideToggleButton} and only then makes it
 * left-aligned with 20 px more left margin; the switch to dark put back
 * CENTER and the look and feel's margin, and every palette row came out
 * centered, in dark and after the restore. Any button an application aligns
 * after building it goes the same way.
 *
 * <p>So, just before the switch, the entries whose write-back would only undo
 * the application are dropped from Synthetica's store. An alignment entry
 * goes unless Synthetica's theme sets that alignment and the button still has
 * it; Ignition's theme sets neither. A margin entry goes when the button is
 * a JIDE button and the current margin is the application's, not a
 * {@link UIResource}. Plain buttons keep losing theirs, on purpose: Ignition
 * gives the Vision welcome page's buttons a margin of 0 above and below,
 * which Synthetica's border pads but FlatLaf's does not, so kept, they came
 * out cramped in dark. Every other kind of entry is left to Synthetica.
 */
final class SyntheticaPropertyStore {

    static final String STYLE_FACTORY_CLASS = "de.javasoft.plaf.synthetica.StyleFactory";
    /** {@code StyleFactory.componentPropertyStore}, the store. */
    static final String STORE_FIELD = "componentPropertyStore";
    static final String STORE_CLASS = STYLE_FACTORY_CLASS + "$ComponentPropertyStore";
    /** {@code ComponentPropertyStore.componentProperties}, a {@code HashSet} its cleaner thread locks on. */
    static final String ENTRIES_FIELD = "componentProperties";
    static final String ENTRY_CLASS = STYLE_FACTORY_CLASS + "$ComponentProperty";
    /** {@code ComponentProperty.component}, a {@code WeakReference<Component>}. */
    static final String ENTRY_COMPONENT_FIELD = "component";
    static final String ENTRY_NAME_FIELD = "propertyName";

    static final String HALIGN = "SYCP_BUTTON_HALIGN";
    static final String VALIGN = "SYCP_BUTTON_VALIGN";
    static final String MARGIN = "SYCP_BUTTON_MARGIN";

    private SyntheticaPropertyStore() {
    }

    /**
     * Drop the button entries that would only undo the application.
     *
     * @return how many were dropped; 0 when Synthetica is not the installed
     *         style factory
     */
    static int keepApplicationButtonLayout() throws ReflectiveOperationException {
        Object factory = SynthLookAndFeel.getStyleFactory();
        if (factory == null || !STYLE_FACTORY_CLASS.equals(factory.getClass().getName())) {
            DebugLog.detail("SyntheticaPropertyStore: Synthetica is not the style factory; nothing to keep.");
            return 0;
        }
        Object store = field(STYLE_FACTORY_CLASS, STORE_FIELD).get(factory);
        if (store == null) {
            return 0;
        }
        Set<?> entries = (Set<?>) field(STORE_CLASS, ENTRIES_FIELD).get(store);
        Field component = field(ENTRY_CLASS, ENTRY_COMPONENT_FIELD);
        Field name = field(ENTRY_CLASS, ENTRY_NAME_FIELD);
        Method themedInt = Class.forName(ThemeManager.SYNTHETICA_LAF)
            .getMethod("getInt", String.class, Component.class, int.class);
        int dropped = 0;
        synchronized (entries) {
            for (Iterator<?> it = entries.iterator(); it.hasNext();) {
                Object entry = it.next();
                Object target = ((Reference<?>) component.get(entry)).get();
                if (target instanceof AbstractButton
                        && setByApplication((AbstractButton) target, (String) name.get(entry), themedInt)) {
                    it.remove();
                    dropped++;
                }
            }
        }
        DebugLog.log("SyntheticaPropertyStore: kept the application's layout on " + dropped
            + " button entr" + (dropped == 1 ? "y" : "ies") + ".");
        return dropped;
    }

    private static boolean setByApplication(AbstractButton button, String property, Method themedInt)
            throws ReflectiveOperationException {
        switch (property) {
            case HALIGN:
                return !themed(themedInt, button, "Synthetica.button.horizontalAlignment",
                    button.getHorizontalAlignment());
            case VALIGN:
                return !themed(themedInt, button, "Synthetica.button.verticalAlignment",
                    button.getVerticalAlignment());
            case MARGIN:
                Insets margin = button.getMargin();
                return JideButtonMargins.isJideButton(button.getClass())
                    && margin != null && !(margin instanceof UIResource);
            default:
                return false;
        }
    }

    /** Whether Synthetica's theme gives this button an alignment and the button still has it. */
    private static boolean themed(Method themedInt, AbstractButton button, String key, int current)
            throws ReflectiveOperationException {
        int value = (Integer) themedInt.invoke(null, key, button, -1);
        return value >= 0 && value == current;
    }

    private static Field field(String className, String name) throws ReflectiveOperationException {
        Field field = Class.forName(className, true, SyntheticaPropertyStore.class.getClassLoader())
            .getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
