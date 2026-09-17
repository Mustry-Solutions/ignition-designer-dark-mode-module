package com.mustrysolutions.designerdarkmode.designer;

import java.util.List;

import com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializer;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.equalitydelegates.EqualityDelegate;

/**
 * Keeps FlatLaf's borders out of a saved window (#92, part 4).
 *
 * <p>A save writes a component's {@code border} when it differs from the
 * clean copy's, and "differs" is the serializer's own
 * {@code AbstractEqualityDelegateSupport.safeEquals}: identity, then a
 * registered equality delegate for the class, then one hard-coded rule —
 * <em>two borders whose class is named {@code SynthBorder} are equal</em> —
 * and only then {@code equals}. That rule is why a stock Designer never
 * writes a Synthetica border: every Synth delegate hands its component a
 * fresh {@code SynthBorder}, the clean copy has another, and the serializer
 * is told they are the same. FlatLaf's borders get no such rule, and they do
 * not implement {@code equals}, so any two instances differ unless they are
 * the same object.
 *
 * <p>Mostly they are: FlatLaf resolves {@code ScrollPane.border} once and
 * every scroll pane shares that instance, the clean copy included. But
 * {@code JTable.configureEnclosingScrollPaneUI} — run by the JDK from
 * {@code updateUI} and {@code addNotify} — replaces the enclosing scroll
 * pane's border with {@code Table.scrollPaneBorder}, a key Synthetica does
 * not define and FlatLaf resolves to a <em>second</em> shared instance. So a
 * Vision component built around a table saves clean from a dark Designer
 * until it is attached to a window or the component watcher's tree update
 * reaches it; from then on its save carries
 * {@code <o cls="com.formdev.flatlaf.ui.FlatScrollPaneBorder"/>}, which a
 * Vision client cannot resolve, and the window will not open. Found live on
 * 0.4.0 with a Comments Panel; the headless sweep had missed it because it
 * never tree-updated a dark-born component before saving it.
 *
 * <p>The cure is the platform's own, extended to FlatLaf: an equality
 * delegate registered for every concrete FlatLaf border class that declares
 * two borders of that class equal. A FlatLaf border on a Vision component is
 * always the look and feel's — the border editor offers Swing's and Vision's
 * kinds, never these — so "not written" is the only right answer, and the
 * client installs Synthetica's in its place on load, exactly as it does for
 * the stock Designer's unwritten {@code SynthBorder}. A border of any other
 * class still compares as before, so a border the user picked is written as
 * picked.
 *
 * <p>The delegate is keyed by exact class, hence the list: every concrete
 * {@code Border} in the FlatLaf jar this module ships, public or nested.
 * {@code FlatLafBorderClassesTest} scans the jar and fails the build when the
 * list falls out of step with the FlatLaf version in the build.
 *
 * <p>The rule cannot reach a {@code null}: a Vision Comments Panel is a
 * scroll pane whose constructor sets its border to {@code null}, so under
 * FlatLaf its clean copy has none, and the tree update's
 * {@code installBorder} then gives the live one {@code ScrollPane.border}.
 * No equality delegate is consulted for a {@code null}, so that case is
 * settled before the save, by {@link VisionConstructionBorders}: the live
 * border is put back to what a fresh instance has.
 */
final class LookAndFeelBorders {

    /** Every concrete {@code javax.swing.border.Border} in FlatLaf 3.7.2. */
    static final List<String> FLATLAF_BORDER_CLASSES = List.of(
        "com.formdev.flatlaf.ui.FlatBorder",
        "com.formdev.flatlaf.ui.FlatButtonBorder",
        "com.formdev.flatlaf.ui.FlatComboBoxUI$CellPaddingBorder",
        "com.formdev.flatlaf.ui.FlatDropShadowBorder",
        "com.formdev.flatlaf.ui.FlatEmptyBorder",
        "com.formdev.flatlaf.ui.FlatInternalFrameUI$FlatInternalFrameBorder",
        "com.formdev.flatlaf.ui.FlatLineBorder",
        "com.formdev.flatlaf.ui.FlatListCellBorder",
        "com.formdev.flatlaf.ui.FlatListCellBorder$Default",
        "com.formdev.flatlaf.ui.FlatListCellBorder$Focused",
        "com.formdev.flatlaf.ui.FlatListCellBorder$Selected",
        "com.formdev.flatlaf.ui.FlatMarginBorder",
        "com.formdev.flatlaf.ui.FlatMenuBarBorder",
        "com.formdev.flatlaf.ui.FlatMenuItemBorder",
        "com.formdev.flatlaf.ui.FlatNativeWindowBorder$WindowTopBorder",
        "com.formdev.flatlaf.ui.FlatPopupMenuBorder",
        "com.formdev.flatlaf.ui.FlatRootPaneUI$FlatWindowBorder",
        "com.formdev.flatlaf.ui.FlatRootPaneUI$FlatWindowTitleBorder",
        "com.formdev.flatlaf.ui.FlatRoundBorder",
        "com.formdev.flatlaf.ui.FlatScrollPaneBorder",
        "com.formdev.flatlaf.ui.FlatTableCellBorder",
        "com.formdev.flatlaf.ui.FlatTableCellBorder$Default",
        "com.formdev.flatlaf.ui.FlatTableCellBorder$Focused",
        "com.formdev.flatlaf.ui.FlatTableCellBorder$Selected",
        "com.formdev.flatlaf.ui.FlatTableHeaderBorder",
        "com.formdev.flatlaf.ui.FlatTextBorder",
        "com.formdev.flatlaf.ui.FlatTitlePane$FlatTitlePaneBorder",
        "com.formdev.flatlaf.ui.FlatToolBarBorder",
        "com.formdev.flatlaf.ui.FlatUIUtils$NonUIResourceBorder",
        "com.formdev.flatlaf.util.ScaledEmptyBorder");

    /** Two borders of the same FlatLaf class are the look and feel's, and equal. */
    static final EqualityDelegate<Object> SAME_CLASS = new EqualityDelegate<Object>() {
        @Override
        public boolean eq(Object a, Object b) {
            return a != null && b != null && a.getClass() == b.getClass();
        }

        @Override
        public int hash(Object border) {
            return border == null ? 0 : border.getClass().hashCode();
        }
    };

    /**
     * Register the rule on a serializer. Failure-soft, and per class: a save
     * must never be the thing this module breaks, and one class that has gone
     * missing must not take the other twenty-nine with it.
     *
     * @return how many classes were registered
     */
    static int register(XMLSerializer serializer) {
        int registered = 0;
        for (String name : FLATLAF_BORDER_CLASSES) {
            try {
                Class<?> border = Class.forName(name, false, LookAndFeelBorders.class.getClassLoader());
                serializer.registerEqualityDelegate(border, SAME_CLASS);
                registered++;
            } catch (Throwable t) {
                DebugLog.log("LookAndFeelBorders: could not register " + name
                    + "; a save made under dark mode may carry that border.", t);
            }
        }
        return registered;
    }
}
