package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A FlatLaf border, saved (#92, part 4).
 *
 * <p>The JDK's {@code JTable.configureEnclosingScrollPaneUI} — run from
 * {@code updateUI} and {@code addNotify} — replaces the enclosing scroll
 * pane's border with {@code Table.scrollPaneBorder}. FlatLaf resolves that
 * to a second shared instance, distinct from the {@code ScrollPane.border}
 * the clean copy holds, and FlatLaf's borders do not implement
 * {@code equals}; so a table component that saves clean when freshly built
 * saves {@code <o cls="com.formdev.flatlaf.ui.FlatScrollPaneBorder"/>} once
 * the Designer's tree update has reached it — and a Vision client cannot
 * open the window. {@link LookAndFeelBorders} extends the serializer's own
 * "two SynthBorders are equal" rule to FlatLaf's classes.
 *
 * <p>The control comes first: without the rule the save must carry the
 * border, or the assertions with it would pass against an instrument that
 * sees nothing.
 */
class LookAndFeelBorderOnSaveTest {

    private static final String FLATLAF = "com.formdev";

    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        SerializerCleanCopies.refresh();
    }

    @AfterEach
    void leaveTheJvmLight() throws Exception {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        SerializerCleanCopies.refresh();
    }

    @Test
    @DisplayName("a table tree-updated under dark saves no FlatLaf border once the rule is registered")
    void treeUpdatedTableSavesNoFlatLafBorder() throws Exception {
        manager.apply(true);
        assertEquals(List.of(), manager.failedPhases());

        SerializerProbeTable dropped = new SerializerProbeTable();
        String fresh = save(dropped, false);
        assertFalse(fresh.contains(FLATLAF),
            "freshly built, the component and its clean copy share FlatLaf's one "
                + "ScrollPane.border instance; nothing should be written yet:\n" + fresh);

        // What the component watcher's rescan does to an attached component.
        ThemeManager.updateComponentTreeUiResiliently(dropped, new LinkedHashSet<>());
        assertTrue(UIManager.getBorder("Table.scrollPaneBorder") != null
                && UIManager.getBorder("Table.scrollPaneBorder") != UIManager.getBorder("ScrollPane.border"),
            "FlatLaf no longer keeps Table.scrollPaneBorder as a second instance; "
                + "the mechanism under test is gone");

        String control = save(dropped, false);
        assertTrue(control.contains("com.formdev.flatlaf.ui.FlatScrollPaneBorder"),
            "without the rule the save must carry the FlatLaf border, or nothing here is under test:\n"
                + control);

        String withRule = save(dropped, true);
        assertFalse(withRule.contains(FLATLAF),
            "with the rule a FlatLaf border must never be written:\n" + withRule);
    }

    @Test
    @DisplayName("a border the user set is still written under dark")
    void handSetBorderStillWritten() throws Exception {
        manager.apply(true);
        SerializerProbeTable dropped = new SerializerProbeTable();
        ThemeManager.updateComponentTreeUiResiliently(dropped, new LinkedHashSet<>());
        dropped.setBorder(BorderFactory.createLineBorder(Color.RED, 3));

        String saved = save(dropped, true);
        assertTrue(saved.contains("javax.swing.border.LineBorder"),
            "a hand-set border must round-trip:\n" + saved);
        assertFalse(saved.contains(FLATLAF), saved);
    }

    @Test
    @DisplayName("under stock the rule changes nothing")
    void stockSaveUnchanged() throws Exception {
        SerializerProbeTable dropped = new SerializerProbeTable();
        ThemeManager.updateComponentTreeUiResiliently(dropped, new LinkedHashSet<>());
        assertEquals(save(dropped, false), save(dropped, true));
    }

    @Test
    @DisplayName("every listed FlatLaf border class registers")
    void everyClassRegisters() {
        assertEquals(LookAndFeelBorders.FLATLAF_BORDER_CLASSES.size(),
            LookAndFeelBorders.register(new XMLSerializer().initDefaults()));
    }

    private String save(Object object, boolean withRule) throws Exception {
        XMLSerializer serializer = new XMLSerializer().initDefaults();
        if (withRule) {
            LookAndFeelBorders.register(serializer);
        }
        serializer.addObject(object);
        return serializer.serializeXML();
    }
}
