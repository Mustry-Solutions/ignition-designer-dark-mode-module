package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The serializer's clean-copy cache across a theme switch (#92, part 1).
 *
 * <p>The corruption that once kept dark mode out of Vision has a mechanism that needs no
 * Vision at all: {@link XMLSerializer} caches one clean instance per class,
 * built under whatever look and feel was installed at the first save, and
 * writes every property that differs from it. So the platform serializer and
 * a {@code JButton} with a Vision-style {@code BeanInfo}
 * ({@link SerializerProbeButton}) reproduce it here — a copy built under
 * Synthetica makes a FlatLaf-born button save its FlatLaf border by class
 * name, which is the line a Vision client cannot load.
 *
 * <p>Two things are asserted, and the order matters. First that the
 * instrument sees the corruption at all: a deliberately stale copy must put
 * {@code com.formdev} into the XML, or a passing second assertion would mean
 * nothing. Then that the module's refresh removes it, and that the switch
 * sequence actually runs the refresh, in both directions.
 *
 * <p>What this cannot see: Vision's own {@code DefaultComponentDelegate} and
 * the colour constants its components bake in at construction (#92, part 2).
 * The Vision jars are not a published artifact; the probe that covers them is
 * described in {@code docs/ARCHITECTURE.md} under Vision.
 */
class SerializerCleanCopyTest {

    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        // Other tests' saves must not leak into this one's counts.
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
    @DisplayName("a switch in either direction empties the clean-copy cache")
    void switchEmptiesTheCache() throws Exception {
        serialize(new SerializerProbeButton());
        assertTrue(SerializerCleanCopies.size() > 0,
            "a save must seed the cache, or this test empties nothing");

        manager.apply(true);
        assertEquals(List.of(), manager.failedPhases());
        assertTrue(manager.phaseTrace().contains("serializerCleanCopies"),
            "the refresh is not part of the switch: " + manager.phaseTrace());
        assertEquals("serializerCleanCopies",
            manager.phaseTrace().get(manager.phaseTrace().size() - 1),
            "the refresh must run LAST, after every phase that changes a default; "
                + "a copy built before the last change is stale on arrival: "
                + manager.phaseTrace());
        assertEquals(0, SerializerCleanCopies.size(),
            "the dark switch left copies built under the stock look and feel");

        serialize(new SerializerProbeButton());
        assertTrue(SerializerCleanCopies.size() > 0);

        manager.apply(false);
        assertEquals(List.of(), manager.failedPhases());
        assertEquals(0, SerializerCleanCopies.size(),
            "the light restore left copies built under FlatLaf");
    }

    @Test
    @DisplayName("a stock-built clean copy puts FlatLaf's border into a dark save; the refresh takes it out")
    void staleCopyWritesFlatLafBorderAndRefreshRemovesIt() throws Exception {
        // Born under Synthetica and never attached to a window, so the tree
        // update in the switch does not touch it: it keeps the stock border,
        // font and colours, exactly like a copy cached by a save before the
        // switch.
        SerializerProbeButton stockBorn = new SerializerProbeButton();

        manager.apply(true);
        assertEquals(List.of(), manager.failedPhases());

        // The stale cache the module used to leave behind, reinstated through
        // the platform's own public seam.
        XMLSerializer.setCleanCopy(SerializerProbeButton.class, stockBorn);
        String staleSave = serialize(new SerializerProbeButton());
        assertTrue(staleSave.contains("com.formdev"),
            "the control failed: a FlatLaf-born button diffed against a Synthetica copy "
                + "should write a FlatLaf border by class name, and did not — so the "
                + "assertion below would pass on an instrument that sees nothing:\n" + staleSave);

        SerializerCleanCopies.refresh();
        String freshSave = serialize(new SerializerProbeButton());
        assertFalse(freshSave.contains("com.formdev"),
            "a FlatLaf class name survived the refresh; a Vision client would fail to open "
                + "this window with ClassNotFoundException:\n" + freshSave);
        assertFalse(freshSave.contains("setFont"),
            "a font that is the look and feel's own was written as if set by hand:\n"
                + freshSave);
        assertFalse(freshSave.contains("setBorder"),
            "a border that is the look and feel's own was written as if set by hand:\n"
                + freshSave);
    }

    @Test
    @DisplayName("hand-set values still round-trip after the refresh")
    void handSetValuesSurvive() throws Exception {
        manager.apply(true);
        SerializerCleanCopies.refresh();

        SerializerProbeButton button = new SerializerProbeButton();
        button.setToolTipText("Saves the window");
        String xml = serialize(button);
        assertTrue(xml.contains("Saves the window"),
            "the refresh must only remove what the look and feel put there:\n" + xml);
    }

    /** One save, the way the Designer builds one: a fresh serializer per save. */
    private static String serialize(Object object) throws Exception {
        XMLSerializer serializer = new XMLSerializer().initDefaults();
        serializer.addObject(object);
        return serializer.serializeXML();
    }
}
