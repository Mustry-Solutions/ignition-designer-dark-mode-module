package com.mustrysolutions.designerdarkmode.designer;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * Puts back the one border a Vision component borrows from the look and feel
 * at construction and loses to a tree update under FlatLaf (#92, part 4).
 *
 * <p>{@code PMIDateTimePopupSelector} is a panel that wants to look like a
 * text field: its constructor does {@code setBorder(UIManager.getBorder(
 * "TextField.border"))}. That border is a {@code UIResource}, so every
 * {@code updateUI} is free to replace it with the panel's own default —
 * which is what {@code LookAndFeel.installBorder} does. Synthetica gives
 * every region a {@code SynthBorder}, so under stock the selector comes out
 * of a tree update with a panel border and the serializer's own rule (any
 * two {@code SynthBorder}s are equal) keeps that out of a save. FlatLaf
 * defines no {@code Panel.border}, so under dark the same update leaves the
 * selector with <em>no</em> border: it paints without one, and a save then
 * writes {@code setBorder} {@code <null/>} — loadable, but a client shows
 * the selector borderless where the stock Designer's save would not have
 * touched it.
 *
 * <p>So after each {@code updateUI} in the module's tree walk, a selector
 * left without a border gets the current look and feel's
 * {@code TextField.border} again — the constructor's own choice, re-read.
 * Under dark that is FlatLaf's shared instance, the same object the
 * serializer's clean copy holds, so nothing is written; under stock the
 * update has already installed a border and this is a no-op. It is the only
 * Vision component that borrows a border this way (a scan of every class in
 * {@code factorypmi.application.components} for {@code UIManager.getBorder}
 * finds no other), which is why this is a name and not a rule.
 */
final class VisionConstructionBorders {

    static final String DATE_TIME_POPUP_SELECTOR =
        "com.inductiveautomation.factorypmi.application.components.PMIDateTimePopupSelector";

    /** The key the selector's constructor reads. */
    static final String BORROWED_KEY = "TextField.border";

    private VisionConstructionBorders() {
    }

    /**
     * Restore the borrowed border on a component whose {@code updateUI} has
     * just run, if it is the selector and the update left it bare.
     *
     * @return true when a border was put back
     */
    static boolean restore(JComponent updated) {
        if (updated.getBorder() != null
                || !DATE_TIME_POPUP_SELECTOR.equals(updated.getClass().getName())) {
            return false;
        }
        Border borrowed = UIManager.getBorder(BORROWED_KEY);
        if (borrowed == null) {
            return false;
        }
        updated.setBorder(borrowed);
        return true;
    }
}
