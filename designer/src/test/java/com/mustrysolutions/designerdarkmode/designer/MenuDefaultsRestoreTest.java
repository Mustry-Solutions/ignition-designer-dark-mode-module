package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the ordering that {@link ThemeManager#applyMenuDefaults(boolean)}
 * depends on (#23).
 *
 * <p>Under dark mode the module copies every FlatLaf default into the
 * DEVELOPER defaults, because {@code installJideExtension()} otherwise
 * overwrites standard Swing keys with a light mapping. Unwinding that copy is
 * {@code UIManager.put(key, null)}, which does not restore the previous value —
 * it deletes the entry outright, and cannot tell our copy from whatever
 * occupied the key before.
 *
 * <p>That only matters because the stock Designer keeps those colours in the
 * developer defaults rather than in the look and feel's own table: Synthetica
 * is Synth-based and defines no {@code TextField.background},
 * {@code Table.background} or {@code List.background} of its own — they are
 * written into the developer defaults by Synthetica's compatibility defaults
 * and by JIDE. Clearing after the stock look and feel is back therefore
 * deletes the values the restore just put there.
 *
 * <p>The stub look and feels below are shaped like the real pair: the stock one
 * carries its colours in the developer defaults, the dark one in its own table.
 */
class MenuDefaultsRestoreTest {

    private static final String KEY = "TextField.background";
    private static final Color STOCK = new ColorUIResource(0xFFFFFF);
    private static final Color DARK = new ColorUIResource(0x3C3F41);

    private LookAndFeel original;
    private ThemeManager manager;

    @BeforeEach
    void setUp() {
        original = UIManager.getLookAndFeel();
        manager = new ThemeManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        UIManager.put(KEY, null);
        if (original != null) {
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    @DisplayName("clearing the overrides before the stock LaF returns keeps its colours")
    void clearingBeforeTheRestoreKeepsStockDefaults() throws Exception {
        UIManager.setLookAndFeel(new StockLikeLaf());
        assertEquals(STOCK, UIManager.getColor(KEY), "sanity: the stock value is in place");

        UIManager.setLookAndFeel(new DarkLikeLaf());
        manager.snapshotMenuDefaults();
        manager.applyMenuDefaults(true);
        assertEquals(DARK, UIManager.getColor(KEY), "sanity: dark mode overrides the key");

        // The shipped order: clear while the dark look and feel is still
        // installed, then let the stock one repopulate.
        manager.applyMenuDefaults(false);
        UIManager.setLookAndFeel(new StockLikeLaf());

        assertEquals(STOCK, UIManager.getColor(KEY),
            "a text field with no TextField.background inherits its parent's — "
                + "the amber property editor of #23");
    }

    @Test
    @DisplayName("clearing them after the stock LaF returns deletes its colours")
    void clearingAfterTheRestoreDeletesStockDefaults() throws Exception {
        UIManager.setLookAndFeel(new StockLikeLaf());
        UIManager.setLookAndFeel(new DarkLikeLaf());
        manager.snapshotMenuDefaults();
        manager.applyMenuDefaults(true);

        // The order that caused #23, kept as a characterisation of the trap:
        // the clear runs last and takes the stock value with it.
        UIManager.setLookAndFeel(new StockLikeLaf());
        manager.applyMenuDefaults(false);

        assertNull(UIManager.getColor(KEY),
            "UIManager.put(key, null) deletes rather than reverts");
    }

    /**
     * Synthetica-shaped: no colours in its own defaults table, written into the
     * developer defaults from {@code initialize()} and withdrawn again from
     * {@code uninitialize()} — which is why dark mode sees FlatLaf's value at
     * snapshot time and why the stock value is back before the phase-0 clear.
     */
    private static final class StockLikeLaf extends StubLaf {
        @Override
        public void initialize() {
            UIManager.put(KEY, STOCK);
        }

        @Override
        public void uninitialize() {
            UIManager.put(KEY, null);
        }
    }

    /** FlatLaf-shaped: the colour lives in the look and feel's own table. */
    private static final class DarkLikeLaf extends StubLaf {
        @Override
        public UIDefaults getDefaults() {
            UIDefaults defaults = new UIDefaults();
            defaults.put(KEY, DARK);
            return defaults;
        }
    }

    private abstract static class StubLaf extends LookAndFeel {
        @Override
        public UIDefaults getDefaults() {
            return new UIDefaults();
        }

        @Override
        public String getName() {
            return getClass().getSimpleName();
        }

        @Override
        public String getID() {
            return getClass().getSimpleName();
        }

        @Override
        public String getDescription() {
            return getClass().getSimpleName();
        }

        @Override
        public boolean isNativeLookAndFeel() {
            return false;
        }

        @Override
        public boolean isSupportedLookAndFeel() {
            return true;
        }
    }
}
