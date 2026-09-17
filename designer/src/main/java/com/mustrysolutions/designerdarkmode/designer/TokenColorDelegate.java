package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.util.function.Function;

import com.inductiveautomation.ignition.common.xmlserialization.SerializationException;
import com.inductiveautomation.ignition.common.xmlserialization.encoding.Encoders;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.Element;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.SerializationDelegate;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializationContext;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializer;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.delegates.EncoderFactoryDelegate;

/**
 * Writes a restyled design token with its stock colour when a window is
 * saved (#92, part 2).
 *
 * <p>Vision components dropped from the palette are handed the static
 * {@code IgnitionLookAndFeel$Colors} objects themselves: {@code
 * PMIButton.initialize()} does {@code setForeground(Colors.ButtonForeground)},
 * and the same goes for the button and indicator colours in a multi-state
 * button's state dataset, a check box's default background, a progress bar's
 * text colour. In a stock Designer those objects equal the look-and-feel
 * defaults and nothing is written. Under dark mode {@link IaColorTokens} has
 * rewritten the very same objects in place, so a save made while dark writes
 * {@code setForeground #DDE0E3} into the window — and a Vision client, which
 * is always light, shows light-grey text on a light button. Only palette-fresh
 * components are affected ({@code initialize()} has a single caller, the
 * palette), and only a save made while dark: the objects are put back in
 * place by the light restore, so a later light save is clean.
 *
 * <p>The fix is at the point of writing. This delegate replaces the
 * serializer's own {@code java.awt.Color} delegate with one that asks, by
 * identity, whether the colour is a token the token pass has restyled or a
 * look-and-feel colour FlatLaf owns ({@link LookAndFeelColors}: a component
 * that inherits its container's {@code Panel.foreground} has that written
 * too, and under dark it is FlatLaf's), and if so hands the platform's
 * encoder a copy holding the stock value instead.
 * A colour the user picked is a different object, even at the same RGB, and
 * is written as picked. Datasets are covered by the same path: the platform
 * serializes their cells one object at a time through the delegate table.
 *
 * <p>The saved window then carries the stock value where a stock Designer
 * would have written nothing — an explicit colour equal to the default, which
 * renders identically on every client. That noise is the residue of Vision
 * copying a token object rather than reading a default, and it is the same in
 * both directions: it cannot be made to disappear without changing what the
 * component holds.
 *
 * <p>Registered on every save through {@code DesignerModuleHook
 * .configureSerializer}, light or dark: the substitution is a pass-through
 * while no token is restyled, so nothing depends on the theme at the moment
 * the serializer is built. The platform's encoder does the actual writing,
 * constructed here the way {@code XMLSerializer.initDefaults} constructs it,
 * so the XML is byte-for-byte what the platform would produce.
 */
final class TokenColorDelegate implements SerializationDelegate<Color> {

    private final SerializationDelegate<Object> platform;
    private final Function<Color, Integer> stockRgb;

    /**
     * @param stockRgb by identity: the stock ARGB of a restyled token, or
     *        {@code null} for any other colour
     */
    TokenColorDelegate(Function<Color, Integer> stockRgb) {
        this.platform = new EncoderFactoryDelegate("clr", Encoders.ColorEncoder.FACTORY);
        this.stockRgb = stockRgb;
    }

    /**
     * Put this delegate on a serializer, in place of the platform's own.
     * Failure-soft: a save must never be the thing this module breaks.
     */
    static void register(XMLSerializer serializer, Function<Color, Integer> stockRgb) {
        try {
            TokenColorDelegate delegate = new TokenColorDelegate(stockRgb);
            serializer.addSerializationDelegate(Color.class, delegate);
            // The platform keys its delegates by exact class and registers
            // ColorUIResource separately; an inherited look-and-feel colour is
            // one of those, and it must come through here too.
            serializer.addSerializationDelegate(javax.swing.plaf.ColorUIResource.class, delegate);
        } catch (Throwable t) {
            DebugLog.log("TokenColorDelegate could not be registered; a save made under "
                + "dark mode may carry dark token colours.", t);
        }
    }

    @Override
    public Element serialize(XMLSerializationContext context, Color color)
            throws SerializationException {
        Integer stock = stockRgb.apply(color);
        return platform.serialize(context, stock == null ? color : new Color(stock, true));
    }

    @Override
    public boolean isSkipReferenceTracking() {
        return platform.isSkipReferenceTracking();
    }
}
