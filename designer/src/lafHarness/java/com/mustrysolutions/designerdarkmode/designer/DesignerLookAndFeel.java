package com.mustrysolutions.designerdarkmode.designer;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.UIManager;

/**
 * Puts this JVM into the look and feel a real Designer starts in.
 *
 * <p>The whole value of the harness rests on this being the genuine article
 * rather than a stand-in. The module's bugs are not in its own logic; they are
 * in what three look and feels do to each other — Synthetica (through
 * Ignition's {@code IgnitionLookAndFeel$LaF}), JIDE's extension layered on top
 * of it, and FlatLaf arriving over both. A stub look and feel reproduces none
 * of that, which is why the {@code test} source set has never caught one of
 * these.
 *
 * <p>The sequence below is the Designer's own startup sequence, and the calls
 * are made directly rather than reflectively: unlike the module, the harness
 * compiles against these jars and should fail loudly at compile time if they
 * move.
 */
final class DesignerLookAndFeel {

    /** The Designer's stock look and feel — Synthetica, dressed by Ignition. */
    static final String STOCK_LAF_CLASS =
        "com.inductiveautomation.ignition.client.IgnitionLookAndFeel$LaF";

    private DesignerLookAndFeel() {
    }

    /**
     * Install the stock look and feel and the JIDE extension over it, the way
     * the Designer does at launch.
     *
     * <p>{@code installJideExtension()} is not optional dressing: it is what
     * writes several hundred standard Swing defaults into the DEVELOPER
     * defaults table, and that table is where #23 happened. A harness that
     * skipped it would start from a state no Designer is ever in.
     */
    static void installStock() throws Exception {
        de.javasoft.plaf.synthetica.SyntheticaLookAndFeel
            .setLookAndFeel(STOCK_LAF_CLASS, true, true);
        de.javasoft.plaf.synthetica.SyntheticaLookAndFeel.setFont("Dialog", 12);
        com.jidesoft.plaf.LookAndFeelFactory.installJideExtension();
    }

    /** True when the stock look and feel is the one currently installed. */
    static boolean stockIsInstalled() {
        return STOCK_LAF_CLASS.equals(UIManager.getLookAndFeel().getClass().getName());
    }

    /**
     * The JIDE {@code Theme.painter} mapping, as classloader &rarr; painter
     * class name.
     *
     * <p>JIDE resolves the painter behind dock title bars, grippers and split
     * dividers through this per-classloader map rather than through the colour
     * keys. #14 and #19 were both this map being left pointing at the wrong
     * painter, so it is worth asserting on directly — and by class name, since
     * {@code BasicPainter.getInstance()} is a singleton but the Synthetica
     * entries are not guaranteed to be.
     */
    static Map<String, String> themePainters() {
        Object value = UIManager.get("Theme.painter");
        Map<String, String> painters = new LinkedHashMap<>();
        if (!(value instanceof Map)) {
            return painters;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            painters.put(String.valueOf(entry.getKey()),
                entry.getValue() == null ? "null" : entry.getValue().getClass().getName());
        }
        return painters;
    }
}
