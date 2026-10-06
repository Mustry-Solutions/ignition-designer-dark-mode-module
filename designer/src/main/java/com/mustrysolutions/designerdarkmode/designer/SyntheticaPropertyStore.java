package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.synth.SynthLookAndFeel;

/**
 * Keeps Synthetica's uninstall from undoing what an application set on its
 * components (#174 and its follow-up audit).
 *
 * <p>Synthetica records some of a component's properties each time it styles
 * it, and when it is uninstalled, the first step of every switch to dark, it
 * writes every recorded value back. A value recorded during construction,
 * before the application has set anything, is the one written back, so the
 * switch undid whatever the application set since. The record of a
 * {@code JComponent} is the client property named after the kind; the
 * entries in the store only say which components and kinds to write back.
 *
 * <p>So, just before the switch, the entries whose write-back would only undo
 * the application, or would write something Synthetica never set, are
 * dropped from Synthetica's store. Every other entry is left to Synthetica.
 *
 * <ul>
 *   <li><b>Button alignment</b> (#174). Vision builds each component palette
 *       item as a {@code JideToggleButton} and only then makes it
 *       left-aligned with 20 px more left margin; the switch put back CENTER
 *       and every palette row came out centered. An alignment entry goes
 *       unless Synthetica's theme sets that alignment and the button still
 *       has it; Ignition's theme sets neither.</li>
 *   <li><b>Button margin</b> (#174). The entry goes when the button is a JIDE
 *       button and the current margin is the application's, not a
 *       {@link UIResource}. Plain buttons keep losing theirs, on purpose:
 *       Ignition gives the Vision welcome page's buttons a margin of 0 above
 *       and below, which Synthetica's border pads but FlatLaf's does not, so
 *       kept, they came out cramped in dark.</li>
 *   <li><b>Toolbar separator size.</b> Synthetica's own size is a
 *       {@code UIResource} from its style. A separator built with a size, as
 *       {@code JToolBar.addSeparator(Dimension)} builds one, is styled before
 *       the size is set, so the record is {@code null}, for which Synthetica
 *       writes a plain 10&times;10 that no look and feel replaces. The entry
 *       stays only while the separator holds a look-and-feel size and there
 *       is a recorded size to put back.</li>
 *   <li><b>Table default editors and renderers.</b> Synthetica replaces
 *       {@code JTable}'s Object and Number editors and Object and Boolean
 *       renderers with its own and records the ones it replaced. The entry
 *       stays only while the table still holds Synthetica's; the Tag
 *       Browser's table sets Ignition's {@code NumberCellEditor} after
 *       construction and lost it for the session.</li>
 *   <li><b>Combo box layout.</b> Synthetica records this kind without reading
 *       a value, so the record is {@code null} and the write-back removes the
 *       layout. A combo the switch does not reach kept no layout into light,
 *       and once shown its arrow button was 0&times;0. A {@code null} record
 *       always goes; a combo the switch reaches gets its layout from the new
 *       UI either way.</li>
 * </ul>
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
    static final String SEPARATOR_SIZE = "SYCP_TOOLBAR_SEPARATOR_SIZE";
    static final String TABLE_OBJECT_RENDERER = "SYCP_TABLE_OBJECT_DEFAULT_RENDERER";
    static final String TABLE_BOOLEAN_RENDERER = "SYCP_TABLE_BOOLEAN_DEFAULT_RENDERER";
    static final String TABLE_OBJECT_EDITOR = "SYCP_TABLE_OBJECT_DEFAULT_EDITOR";
    static final String TABLE_NUMBER_EDITOR = "SYCP_TABLE_NUMBER_DEFAULT_EDITOR";
    static final String COMBO_LAYOUT = "SYCP_COMBOBOX_DEFAULT_LAYOUT";

    /** The package of everything Synthetica installs on a component. */
    private static final String SYNTHETICA_PACKAGE = "de.javasoft.plaf.synthetica.";

    private SyntheticaPropertyStore() {
    }

    /**
     * Drop the entries that would only undo the application.
     *
     * @return how many were dropped; 0 when Synthetica is not the installed
     *         style factory
     */
    static int keepApplicationProperties() throws ReflectiveOperationException {
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
                if (target instanceof Component
                        && onlyUndoesApplication((Component) target, (String) name.get(entry), themedInt)) {
                    it.remove();
                    dropped++;
                }
            }
        }
        DebugLog.log("SyntheticaPropertyStore: dropped " + dropped + " entr" + (dropped == 1 ? "y" : "ies")
            + " that would have undone the application.");
        return dropped;
    }

    private static boolean onlyUndoesApplication(Component target, String property, Method themedInt)
            throws ReflectiveOperationException {
        if (target instanceof AbstractButton) {
            return setByApplication((AbstractButton) target, property, themedInt);
        }
        if (target instanceof JToolBar.Separator && SEPARATOR_SIZE.equals(property)) {
            Dimension size = ((JToolBar.Separator) target).getSeparatorSize();
            return !(size instanceof UIResource) || recorded(target, property) == null;
        }
        if (target instanceof JTable) {
            JTable table = (JTable) target;
            switch (property) {
                case TABLE_OBJECT_RENDERER:
                    return !installedBySynthetica(table.getDefaultRenderer(Object.class));
                case TABLE_BOOLEAN_RENDERER:
                    return !installedBySynthetica(table.getDefaultRenderer(Boolean.class));
                case TABLE_OBJECT_EDITOR:
                    return !installedBySynthetica(table.getDefaultEditor(Object.class));
                case TABLE_NUMBER_EDITOR:
                    return !installedBySynthetica(table.getDefaultEditor(Number.class));
                default:
                    return false;
            }
        }
        return target instanceof JComboBox && COMBO_LAYOUT.equals(property) && recorded(target, property) == null;
    }

    /** What Synthetica will write back: the client property named after the kind. */
    private static Object recorded(Component target, String property) {
        return target instanceof JComponent ? ((JComponent) target).getClientProperty(property) : null;
    }

    private static boolean installedBySynthetica(Object value) {
        return value != null && value.getClass().getName().startsWith(SYNTHETICA_PACKAGE);
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
