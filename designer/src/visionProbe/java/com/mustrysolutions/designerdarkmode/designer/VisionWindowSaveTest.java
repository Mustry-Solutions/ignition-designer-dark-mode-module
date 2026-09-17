package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.factorypmi.application.FPMIWindow;
import com.inductiveautomation.factorypmi.application.components.BasicContainer;
import com.inductiveautomation.factorypmi.application.components.PMIButton;
import com.inductiveautomation.factorypmi.application.components.PMILabel;
import com.inductiveautomation.factorypmi.application.components.PMITextField;
import com.inductiveautomation.factorypmi.application.components.template.VisionTemplate;
import com.inductiveautomation.factorypmi.application.runtime.ClientContextImpl;
import com.inductiveautomation.factorypmi.designer.xmlserialization.BasicContainerDelegate;
import com.inductiveautomation.factorypmi.designer.xmlserialization.DefaultComponentDelegate;
import com.inductiveautomation.ignition.common.beans.BeanInfoFactory;
import com.inductiveautomation.ignition.common.xmlserialization.deserialization.XMLDeserializer;
import com.inductiveautomation.ignition.common.xmlserialization.serialization.XMLSerializer;
import com.inductiveautomation.vision.api.designer.palette.JavaBeanPaletteItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Real Vision windows, saved and loaded across a theme switch (#92).
 *
 * <p>The harness's serializer tests prove the mechanism on a stand-in button;
 * this one runs the same saves through Vision's own component classes,
 * delegates and BeanInfos, the way the Designer does. A window is a
 * {@code BasicContainer} holding a button, a label and a text field, each
 * created the way the palette creates it ({@code JavaBeanPaletteItem
 * .createJavaBean}, which runs {@code initialize()}), and a save is a fresh
 * serializer with Vision's delegates and the module's hook on it.
 *
 * <p>Every save is held to one standard, whichever theme it was made under:
 * nothing from FlatLaf or Synthetica by class name, no font the user did not
 * set, no dark token colour — the things a light Vision client cannot load or
 * would show wrong. And every save is loaded back, which is the client's side
 * of the round trip.
 *
 * <p>Only runs with {@code -Pvision.jars}; see {@code ops/vision-jars.sh}.
 */
class VisionWindowSaveTest {

    private ThemeManager manager;
    private Color base900;
    private Color base100;

    @BeforeAll
    static void visionBeanInfos() {
        // Where Vision keeps the BeanInfo for each of its components; the
        // Designer registers this at startup.
        BeanInfoFactory.addBeanInfoSearchPackage(
            "com.inductiveautomation.factorypmi.designer.beaninfo");
    }

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        SerializerCleanCopies.refresh();
        Class<?> colors = Class.forName(IaColorTokens.COLORS_CLASS);
        base900 = (Color) colors.getField("Base900").get(null);
        base100 = (Color) colors.getField("Base100").get(null);
    }

    @AfterEach
    void leaveTheJvmLight() throws Exception {
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        SerializerCleanCopies.refresh();
    }

    // --- the Vision-side name the module keys on --------------------------

    @Test
    @DisplayName("the interface that marks Vision content still exists, and windows and templates implement it")
    void topLevelContainerResolves() throws Exception {
        Class<?> topLevel = Class.forName(VisionWindows.TOP_LEVEL_CONTAINER);
        assertTrue(topLevel.isAssignableFrom(FPMIWindow.class),
            "FPMIWindow no longer implements " + VisionWindows.TOP_LEVEL_CONTAINER
                + "; the colour passes would no longer spare a window's content");
        assertTrue(topLevel.isAssignableFrom(VisionTemplate.class),
            "VisionTemplate no longer implements " + VisionWindows.TOP_LEVEL_CONTAINER
                + "; the colour passes would no longer spare a template's content");
    }

    // --- part 1: the clean-copy cache -----------------------------------

    @Test
    @DisplayName("a stock-born window saved under dark carries nothing from FlatLaf")
    void stockBornWindowSavedUnderDark() throws Exception {
        onEdt(() -> {
            BasicContainer window = window();
            String stockSave = save(window);
            assertClean(stockSave, "stock save of a stock-born window");

            manager.apply(true);
            SwingUtilities.updateComponentTreeUI(window);
            String darkSave = save(window);
            assertClean(darkSave, "dark save of a stock-born window");
            assertLoads(darkSave);
        });
    }

    @Test
    @DisplayName("a stale stock-built clean copy puts FlatLaf into a real Vision save; the refresh takes it out")
    void staleCleanCopyIsTheMechanism() throws Exception {
        onEdt(() -> {
            PMIButton stockBorn = (PMIButton) palette(PMIButton.class);
            manager.apply(true);
            XMLSerializer.setCleanCopy(PMIButton.class, stockBorn);
            BasicContainer window = window();
            String stale = save(window);
            assertTrue(stale.contains("com.formdev"),
                "the control failed: a stale Synthetica-built copy of PMIButton should put a "
                    + "FlatLaf border class into a dark save, and did not:\n" + stale);

            SerializerCleanCopies.refresh();
            assertClean(save(window), "dark save after the refresh");
        });
    }

    @Test
    @DisplayName("a window saved under stock, reloaded under dark and saved again matches a stock reload")
    void stockSavedWindowReloadedUnderDark() throws Exception {
        onEdt(() -> {
            String stockSave = save(window());
            // A load adds things a fresh window lacks (the container's own
            // fpmi.lc client property), in any theme: the reference is a
            // stock reload, not the first save.
            String stockResave = save((BasicContainer) load(stockSave));

            manager.apply(true);
            BasicContainer reloaded = (BasicContainer) load(stockSave);
            String darkResave = save(reloaded);
            assertClean(darkResave, "dark save of a stock-saved window");
            // Vision's deserialization handler hands every loaded button the
            // ButtonBackground/ButtonForeground token OBJECTS (see
            // ComponentDeserializationHandler.endSubElement), so under dark the
            // button differs from FlatLaf's clean copy and is written — with
            // the stock value (part 2). That explicit-but-stock colour is the
            // only permitted difference.
            assertEquals(withoutStockTokenColours(stockResave), withoutStockTokenColours(darkResave),
                "a window that only passes through a dark Designer must come out unchanged, "
                    + "apart from explicit stock token colours on buttons");
            assertLoads(darkResave);
        });
    }

    /** Drop {@code setForeground}/{@code setBackground} calls carrying a stock token value. */
    private String withoutStockTokenColours(String xml) {
        int stockText = base900.getRGB();
        int stockSurface = base100.getRGB();
        // Whether or not the pass is installed, the token holds one of the two.
        for (int argb : new int[] {stockText, stockSurface, 0xFF2E2E2E, 0xFFFAFAFB}) {
            xml = xml.replaceAll("\\s*<c-c m=\"set(Foreground|Background)\" s=\"1;clr\">"
                + java.util.regex.Pattern.quote(clr(argb)) + "</c-c>", "");
        }
        return xml;
    }

    // --- part 2: the tokens the palette bakes in -----------------------

    @Test
    @DisplayName("a window built from the palette under dark saves with stock colours and loads")
    void darkBornWindowSavedUnderDark() throws Exception {
        onEdt(() -> {
            int stockText = base900.getRGB();
            manager.apply(true);
            assertTrue(base900.getRGB() != stockText, "the token pass is not active");
            BasicContainer window = window();
            String darkSave = save(window);
            assertClean(darkSave, "dark save of a dark-born window");
            assertTrue(darkSave.contains(clr(stockText)),
                "the palette-fresh button's foreground should be written with the stock token "
                    + "value (PMIButton.initialize copies the token object):\n" + darkSave);
            assertLoads(darkSave);
        });
    }

    @Test
    @DisplayName("the dark passes leave a Vision template's content alone, so a dark save carries none of their colours")
    void darkPassesLeaveVisionContentAlone() throws Exception {
        onEdt(() -> {
            VisionTemplate template = new VisionTemplate();
            template.addComponent(palette(PMITextField.class));
            template.addComponent(palette(PMIButton.class));
            javax.swing.JPanel workspace = new javax.swing.JPanel();
            workspace.add(template);
            manager.apply(true);
            SwingUtilities.updateComponentTreeUI(workspace);
            // What the switch and the component watcher run over every window.
            manager.swapWhiteTokenBackgrounds(workspace);
            String darkSave = save(template);
            assertClean(darkSave, "dark save of a template the dark passes ran over");
            java.awt.Color background = template.getComponent(1).getBackground();
            assertTrue(background == java.awt.Color.WHITE,
                "the text field's white token background was swapped by the module: "
                    + String.format("#%06X", background.getRGB() & 0xFFFFFF));
        });
    }

    // --- part 3: fonts across the light restore ------------------------
    //
    // Both scenarios below failed before ThemeManager.primeSyntheticaStyles
    // (#92, part 3): after the light restore, the text field came back from
    // the tree update holding Synthetica's raw theme font, Tahoma 11 in a
    // ScalableFont, and the save then failed outright — a ScalableFont that
    // differs from the clean copy cannot be serialized. Synthetica serves a
    // stale first formatted-text-field style after a reinstall; the primer
    // takes that request. RestoredTextFieldFontTest shows it without Vision.

    @Test
    @DisplayName("a window open across dark and back saves cleanly after one tree update")
    void windowOpenAcrossTheSwitchSavesCleanlyAfterRestore() throws Exception {
        onEdt(() -> {
            BasicContainer window = window();
            // The first save of a container creates its fpmi.lc layout
            // record and the second writes it, in any theme: the reference
            // is a second save.
            save(window);
            String before = save(window);
            manager.apply(true);
            SwingUtilities.updateComponentTreeUI(window);
            manager.apply(false);
            // The Designer's own restore runs exactly one tree update over
            // each open window; a detached container gets it here.
            SwingUtilities.updateComponentTreeUI(window);
            String after = save(window);
            assertClean(after, "light save after a dark/light cycle; fonts: " + fonts(window));
            assertEquals(before, after,
                "a window that survived a cycle must save as it did before; fonts: " + fonts(window));
        });
    }

    @Test
    @DisplayName("a window built under dark saves cleanly after the light restore")
    void darkBornWindowSavedAfterRestore() throws Exception {
        onEdt(() -> {
            manager.apply(true);
            BasicContainer window = window();
            manager.apply(false);
            SwingUtilities.updateComponentTreeUI(window);
            String lightSave = save(window);
            assertClean(lightSave, "light save of a dark-born window; fonts: " + fonts(window));
            assertLoads(lightSave);
        });
    }

    // --- helpers ------------------------------------------------------------

    /** A window as the palette builds one: three components, each initialized. */
    private static BasicContainer window() throws Exception {
        BasicContainer container = new BasicContainer();
        container.addComponent(palette(PMIButton.class));
        container.addComponent(palette(PMILabel.class));
        container.addComponent(palette(PMITextField.class));
        container.addComponent(palette(com.inductiveautomation.factorypmi.application.components.PMIProgressBar.class));
        container.addComponent(palette(com.inductiveautomation.factorypmi.application.components.PMICheckBox.class));
        return container;
    }

    private static JComponent palette(Class<?> componentClass) throws Exception {
        return JavaBeanPaletteItem.createJavaBean(componentClass);
    }

    /** A save as the Designer makes one: fresh serializer, Vision's delegates, our hook. */
    private String save(Object root) throws Exception {
        XMLSerializer serializer = new XMLSerializer().initDefaults();
        serializer.addSupertypeDelegate(JComponent.class, new DefaultComponentDelegate());
        serializer.addSerializationDelegate(BasicContainer.class, new BasicContainerDelegate());
        TokenColorDelegate.register(serializer, manager.stockTokenRgb());
        serializer.addObject(root);
        return serializer.serializeXML();
    }

    /** A load as a client makes one. */
    private static Object load(String xml) throws Exception {
        XMLDeserializer deserializer = new XMLDeserializer();
        ClientContextImpl.configureDeserializer(deserializer);
        deserializer.getClassNameMap().addDefaults();
        return deserializer.deserialize(xml).getRootObjects().get(0);
    }

    private static void assertLoads(String xml) throws Exception {
        Object root = load(xml);
        assertTrue(root instanceof BasicContainer, "the save did not load back as a window: " + root);
        assertEquals(5, ((BasicContainer) root).getComponentCount(),
            "the loaded window has the wrong number of components");
    }

    private void assertClean(String xml, String what) {
        List<String> faults = new ArrayList<>();
        if (xml.contains("com.formdev")) {
            faults.add("a FlatLaf class name (a Vision client cannot load this)");
        }
        if (xml.contains("de.javasoft")) {
            faults.add("a Synthetica class name");
        }
        if (xml.contains("setFont")) {
            faults.add("a font the user did not set");
        }
        if (xml.contains(clr(darkOf(base900))) || xml.contains(clr(darkOf(base100)))) {
            faults.add("a dark token colour (light-grey text or dark surface on a light client)");
        }
        for (int moduleColour : new int[] {0xFF3A3D3F, 0xFF55595B, 0xFF3C3F41}) {
            if (xml.contains(clr(moduleColour))) {
                faults.add("one of the module's own dark colours, set by hand on a Vision component");
            }
        }
        assertEquals(List.of(), faults, what + " carries: " + faults + "\n" + xml);
    }

    /** The dark value of a token whether or not the pass is installed right now. */
    private static int darkOf(Color token) {
        Integer dark = IaColorTokens.DARK.entrySet().stream()
            .filter(e -> e.getKey().equals(token == null ? "" : tokenName(token)))
            .map(e -> 0xFF000000 | e.getValue()).findFirst().orElse(0);
        return dark;
    }

    private static String tokenName(Color token) {
        try {
            Class<?> colors = Class.forName(IaColorTokens.COLORS_CLASS);
            for (java.lang.reflect.Field field : colors.getFields()) {
                if (field.getType() == Color.class && field.get(null) == token) {
                    return field.getName();
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "";
    }

    private static String clr(int argb) {
        return "<clr>" + argb + "</clr>";
    }

    private static String fonts(BasicContainer window) {
        StringBuilder out = new StringBuilder();
        for (Component child : window.getComponents()) {
            Font font = child.getFont();
            out.append(child.getClass().getSimpleName()).append('=')
                .append(font == null ? "null" : font.getClass().getSimpleName() + "(" + font.getFamily()
                    + " " + font.getSize() + ")")
                .append("; ");
        }
        out.append("UIManager TextField.font=").append(UIManager.getFont("TextField.font"));
        return out.toString();
    }

    interface Body {
        void run() throws Exception;
    }

    /** Vision components expect the event dispatch thread. */
    private static void onEdt(Body body) throws Exception {
        Exception[] failure = new Exception[1];
        AssertionError[] assertion = new AssertionError[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (AssertionError e) {
                assertion[0] = e;
            } catch (Exception e) {
                failure[0] = e;
            }
        });
        if (assertion[0] != null) {
            throw assertion[0];
        }
        if (failure[0] != null) {
            throw failure[0];
        }
    }
}
