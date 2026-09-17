package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.inductiveautomation.factorypmi.application.FPMIWindow;
import com.inductiveautomation.factorypmi.application.components.BasicContainer;
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
 * The corruption sweep: does a dark Designer change what a Vision save
 * carries, for EVERY palette component, in every state the switch can leave
 * the Designer in?
 *
 * <p>The claim under test is the one made on the announcement thread: that
 * FlatLaf cannot be dropped into the Designer without corrupting Vision,
 * corruption meaning look-and-feel values written into saved resources by
 * the Designer rather than by the user. {@code VisionWindowSaveTest} proves
 * each fix on a three-component window; this test takes the same standard to
 * the whole palette and the states a session can be in.
 *
 * <p>The standard is exact: a component saved from a dark Designer, or from
 * a Designer that has been dark, must serialize to the SAME bytes as the same
 * component saved from a Designer that has never been dark — after the one
 * difference the module accepts and documents, the stock colour a
 * palette-fresh component gets written with instead of nothing. Anything
 * else the switch leaves behind shows up as a diff.
 *
 * <p>Only runs with {@code -Pvision.jars}; see {@code ops/vision-jars.sh}.
 */
class VisionCorruptionSweepTest {

    /** Every component the Vision palette offers, by class. */
    private static final String PACKAGE = "com.inductiveautomation.factorypmi.application.components.";
    private static final List<String> PALETTE = List.of(
        "PMI2StateButton", "PMIBarChart", "PMIBarcode", "PMIBoxWhiskerChart", "PMIButton",
        "PMIChart", "PMICheckBox", "PMICircle", "PMIComboBox", "PMICommentsPanel",
        "PMICommentsPanel2", "PMICompass", "PMIControlButton", "PMICylindricalTank",
        "PMIDateRange", "PMIDateTimePopupSelector", "PMIDateTimeSelector", "PMIDayView",
        "PMIDigitalDisplay", "PMIEasyChart", "PMIEditorPane", "PMIFillLevelIndicator",
        "PMIFormattedTextField", "PMIGanttChart", "PMIImage", "PMILabel",
        "PMILightrailSignal", "PMILine", "PMIList", "PMIMeter", "PMIMomentaryButton",
        "PMIMomentaryButton2", "PMIMonthView", "PMIMultiStateIndicator", "PMINStateButton",
        "PMINumericLabel", "PMINumericTextField", "PMIPaintableCanvas", "PMIPasswordField",
        "PMIPieChart", "PMIPipe", "PMIPipeJoint", "PMIPolygon", "PMIProgressBar",
        "PMIRadioButton", "PMIRectangle", "PMIShape", "PMISignalGenerator", "PMISlider",
        "PMISoundComponent", "PMISpinner", "PMITable", "PMITextArea", "PMITextField",
        "PMIThermometer", "PMITimer", "PMIToggleButton", "PMITrackSegment", "PMITreeView",
        "PMIWeekView");

    private ThemeManager manager;
    private CellRendererSanitizer renderers;

    @BeforeAll
    static void visionBeanInfos() throws Exception {
        BeanInfoFactory.addBeanInfoSearchPackage(
            "com.inductiveautomation.factorypmi.designer.beaninfo");
        VisionClientStubs.install();
    }

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        renderers = new CellRendererSanitizer();
        SerializerCleanCopies.refresh();
    }

    @AfterEach
    void leaveTheJvmLight() throws Exception {
        renderers.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
        SerializerCleanCopies.refresh();
    }

    // --- the sweep ------------------------------------------------------------

    @Test
    @DisplayName("every palette component saves the same bytes from a dark Designer as from a stock one")
    void everyPaletteComponentSavedUnderDark() throws Exception {
        onEdt(() -> {
            Map<String, String> reference = new LinkedHashMap<>();
            Map<String, String> unbuildable = new LinkedHashMap<>();
            for (String kind : PALETTE) {
                try {
                    reference.put(kind, comparable(attachedAndSaved(windowWith(kind))));
                } catch (Throwable t) {
                    unbuildable.put(kind, t.toString());
                }
            }
            assertTrue(reference.size() > 50, "the sweep must cover the palette; built only "
                + reference.size() + " of " + PALETTE.size() + ": " + unbuildable);
            System.out.println("Sweep: built " + reference.size() + " of " + PALETTE.size()
                + " palette kinds; unbuildable headless: " + unbuildable);

            goDark();
            List<String> differing = new ArrayList<>();
            for (String kind : reference.keySet()) {
                BasicContainer window = windowWith(kind);
                // What the component watcher does as a component is dropped.
                manager.correctVisionConstructionColors(window);
                String raw = attachedAndSaved(window);
                String dark = comparable(raw);
                if (!dark.equals(reference.get(kind))) {
                    differing.add(kind + ":\n" + firstDiff(reference.get(kind), dark));
                }
                assertNoFlatLafClass(raw, kind);
                assertLoads(raw, kind);
            }
            assertEquals(List.of(), differing,
                "these components serialize differently from a dark Designer (built from the "
                    + "palette under dark, saved under dark) than from a stock one, beyond the "
                    + "documented stock-value write of a token the palette handed them");
        });
    }

    @Test
    @DisplayName("every palette component born under stock, cycled through dark, saves the same bytes")
    void everyPaletteComponentSurvivesACycle() throws Exception {
        onEdt(() -> {
            Map<String, BasicContainer> windows = new LinkedHashMap<>();
            Map<String, String> reference = new LinkedHashMap<>();
            for (String kind : PALETTE) {
                try {
                    BasicContainer window = windowWith(kind);
                    // The reference is a window that has been through what a
                    // Designer does before its first save: one tree update
                    // (a date range's updateUI sets a flag its save then
                    // carries, in any theme) and one save (the first save of
                    // a container creates its fpmi.lc record, the second
                    // writes it).
                    ThemeManager.updateComponentTreeUiResiliently(window, new java.util.LinkedHashSet<>());
                    save(window);
                    reference.put(kind, comparable(save(window)));
                    windows.put(kind, window);
                } catch (Throwable ignored) {
                    // Counted by the other test.
                }
            }
            javax.swing.JPanel desktop = new javax.swing.JPanel();
            windows.values().forEach(desktop::add);

            // The window is open through the whole cycle, and every pass that
            // reaches an open window runs over it — twice.
            // The module's own guarded walk, not Swing's: a Vision date
            // picker's day buttons NPE inside setBackground on a bare
            // updateComponentTreeUI, and the switch isolates exactly that.
            for (int cycle = 0; cycle < 2; cycle++) {
                goDark();
                ThemeManager.updateComponentTreeUiResiliently(desktop, new java.util.LinkedHashSet<>());
                renderers.install();
                renderers.installIn(desktop);
                manager.swapWhiteTokenBackgrounds(desktop);
                goLight();
                ThemeManager.updateComponentTreeUiResiliently(desktop, new java.util.LinkedHashSet<>());
                renderers.uninstall();
                manager.refreshComponentsLeftDark(desktop);
            }

            List<String> differing = new ArrayList<>();
            for (Map.Entry<String, BasicContainer> entry : windows.entrySet()) {
                String after = comparable(save(entry.getValue()));
                if (!after.equals(reference.get(entry.getKey()))) {
                    differing.add(entry.getKey() + ":\n" + firstDiff(reference.get(entry.getKey()), after));
                }
            }
            assertEquals(List.of(), differing,
                "these components serialize differently after two dark/light cycles with the "
                    + "window open than they did before");
        });
    }

    @Test
    @DisplayName("hand-set values on every palette component survive a dark save exactly as they survive a stock one")
    void handSetValuesSurviveUnderDark() throws Exception {
        onEdt(() -> {
            // What a stock Designer keeps of a hand-set value is the reference:
            // many kinds do not expose font or foreground as bean properties
            // at all, and that is Vision's choice, not the module's.
            Map<String, String> stock = new LinkedHashMap<>();
            Map<String, String> stockReload = new LinkedHashMap<>();
            for (String kind : PALETTE) {
                try {
                    String raw = save(handSet(kind));
                    stock.put(kind, comparable(raw));
                    stockReload.put(kind, reloaded(raw));
                } catch (Throwable ignored) {
                    // counted by the first test
                }
            }
            goDark();
            List<String> differing = new ArrayList<>();
            Map<String, String> darkSaves = new LinkedHashMap<>();
            for (String kind : stock.keySet()) {
                BasicContainer window = handSet(kind);
                manager.correctVisionConstructionColors(window);
                String raw = save(window);
                darkSaves.put(kind, raw);
                String dark = comparable(raw);
                if (!dark.equals(stock.get(kind))) {
                    differing.add(kind + ":\n" + firstDiff(stock.get(kind), dark));
                }
            }
            // What a CLIENT gets back: loaded under the stock theme, like a
            // client is, so a component with no colour of its own inherits
            // the same container in both cases. (Some kinds override a plain
            // foreground with their own state colours on load — Vision's
            // choice, in any theme — which is why the reference is a stock
            // save's reload, not the value that was set.)
            goLight();
            for (Map.Entry<String, String> entry : darkSaves.entrySet()) {
                String back = reloaded(entry.getValue());
                if (!back.equals(stockReload.get(entry.getKey()))) {
                    differing.add(entry.getKey() + ": a client shows " + back
                        + " from the dark save, " + stockReload.get(entry.getKey()) + " from the stock one");
                }
            }
            assertEquals(List.of(), differing,
                "a dark save of a component with hand-set values differs from the stock save of the same");
        });
    }

    private static final Color MINE = new Color(0x123456);

    /** What a client would show of the hand-set colours after loading the save. */
    private static String reloaded(String xml) throws Exception {
        Component back = ((BasicContainer) load(xml)).getComponent(0);
        return "fg=" + rgb(back.getForeground()) + " bg=" + rgb(back.getBackground());
    }

    private static String rgb(Color c) {
        return c == null ? "null" : String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

    private static BasicContainer handSet(String kind) throws Exception {
        JComponent component = palette(kind);
        component.setBackground(MINE);
        component.setForeground(MINE);
        component.setToolTipText("mine " + kind);
        component.setFont(new java.awt.Font("Serif", java.awt.Font.ITALIC, 17));
        BasicContainer window = new BasicContainer();
        window.addComponent(component);
        return window;
    }

    @Test
    @DisplayName("nested containers and a template holder save clean under dark and after the restore")
    void nestedContainersAndTemplates() throws Exception {
        onEdt(() -> {
            String stock = comparable(save(nested()));
            goDark();
            BasicContainer darkBorn = nested();
            manager.correctVisionConstructionColors(darkBorn);
            String dark = comparable(save(darkBorn));
            assertEquals(stock, dark, "a nested window saves differently under dark:\n" + firstDiff(stock, dark));
            BasicContainer open = nested();
            javax.swing.JPanel desktop = new javax.swing.JPanel();
            desktop.add(open);
            SwingUtilities.updateComponentTreeUI(desktop);
            manager.swapWhiteTokenBackgrounds(desktop);
            goLight();
            SwingUtilities.updateComponentTreeUI(desktop);
            manager.refreshComponentsLeftDark(desktop);
            String rawAfter = save(open);
            String after = comparable(rawAfter);
            assertEquals(stock, after, "a nested window open across the cycle saves differently:\n" + firstDiff(stock, after));
            assertLoads(rawAfter, "nested");
        });
    }

    @Test
    @DisplayName("a real FPMIWindow, not just its root container, round-trips under dark")
    void fullWindowUnderDark() throws Exception {
        onEdt(() -> {
            FPMIWindow stockWindow = new FPMIWindow("Main Window");
            stockWindow.getRootContainer().addComponent(palette("PMIButton"));
            stockWindow.getRootContainer().addComponent(palette("PMITextField"));
            String stock = comparable(save(stockWindow));
            goDark();
            FPMIWindow darkWindow = new FPMIWindow("Main Window");
            darkWindow.getRootContainer().addComponent(palette("PMIButton"));
            darkWindow.getRootContainer().addComponent(palette("PMITextField"));
            String raw = save(darkWindow);
            String dark = comparable(raw);
            assertEquals(stock, dark, "a whole window saves differently under dark:\n" + firstDiff(stock, dark));
            Object back = load(raw);
            assertTrue(back instanceof FPMIWindow, "the dark save did not load back as a window");
        });
    }

    // --- fixtures -------------------------------------------------------------

    private static BasicContainer windowWith(String kind) throws Exception {
        BasicContainer container = new BasicContainer();
        container.addComponent(palette(kind));
        return container;
    }

    private static BasicContainer nested() throws Exception {
        BasicContainer root = new BasicContainer();
        BasicContainer inner = new BasicContainer();
        inner.addComponent(palette("PMIButton"));
        inner.addComponent(palette("PMITextArea"));
        BasicContainer innermost = new BasicContainer();
        innermost.addComponent(palette("PMIProgressBar"));
        innermost.addComponent(palette("PMICheckBox"));
        inner.addComponent(innermost);
        root.addComponent(inner);
        root.addComponent(palette("PMILabel"));
        VisionTemplate template = new VisionTemplate();
        template.addComponent(palette("PMIMultiStateIndicator"));
        root.addComponent(template);
        return root;
    }

    private static JComponent palette(String kind) throws Exception {
        return JavaBeanPaletteItem.createJavaBean(Class.forName(PACKAGE + kind));
    }

    private void goDark() {
        manager.apply(true);
        assertEquals(List.of(), manager.failedPhases(), "the dark switch had a failed phase");
    }

    private void goLight() {
        manager.apply(false);
        assertEquals(List.of(), manager.failedPhases(), "the light restore had a failed phase");
    }

    /**
     * A save as the Designer makes one: a fresh serializer with the delegates
     * {@code VisionDesignerImpl.configureSerializer} registers (those with
     * no-arg constructors; the shape and paint ones carry property lists this
     * sweep does not reach), then our hook.
     */
    private String save(Object root) throws Exception {
        XMLSerializer serializer = new XMLSerializer().initDefaults();
        serializer.addSupertypeDelegate(JComponent.class, new DefaultComponentDelegate());
        serializer.addSupertypeDelegate(
            com.inductiveautomation.factorypmi.application.binding.InteractionDescriptor.class,
            new com.inductiveautomation.factorypmi.designer.xmlserialization.InteractionDescriptorDelegate());
        serializer.addSerializationDelegate(BasicContainer.class, new BasicContainerDelegate());
        serializer.addSerializationDelegate(
            com.inductiveautomation.factorypmi.application.components.util.ColorState.class,
            new com.inductiveautomation.factorypmi.designer.xmlserialization.ColorStateDelegate());
        serializer.addSerializationDelegate(
            com.inductiveautomation.factorypmi.application.components.util.FPMI_LC.class,
            new com.inductiveautomation.factorypmi.designer.xmlserialization.LayoutConstraintsDelegate());
        serializer.addSerializationDelegate(FPMIWindow.class,
            new com.inductiveautomation.factorypmi.designer.xmlserialization.WindowDelegate());
        serializer.addSerializationDelegate(
            com.inductiveautomation.factorypmi.application.components.PMITable.class,
            new com.inductiveautomation.factorypmi.designer.xmlserialization.TableDelegate());
        TokenColorDelegate.register(serializer, manager.stockTokenRgb());
        LookAndFeelBorders.register(serializer);
        serializer.addObject(root);
        return serializer.serializeXML();
    }

    private static Object load(String xml) throws Exception {
        XMLDeserializer deserializer = new XMLDeserializer();
        ClientContextImpl.configureDeserializer(deserializer);
        deserializer.getClassNameMap().addDefaults();
        return deserializer.deserialize(xml).getRootObjects().get(0);
    }

    /**
     * A window as the Designer has it by the time of its first save: the
     * component watcher's tree update has run over it (which is where a
     * table rewrites its scroll pane's border, and a date range sets the
     * flag its save then carries), and one save has created its
     * {@code fpmi.lc} record. The save returned is the second.
     */
    private String attachedAndSaved(BasicContainer window) throws Exception {
        // Inside a template, as in the Designer: the walk's border alignment
        // applies to Vision content only.
        VisionTemplate holder = new VisionTemplate();
        holder.addComponent(window);
        ThemeManager.updateComponentTreeUiResiliently(window, new java.util.LinkedHashSet<>());
        save(window);
        return save(window);
    }

    /** The client's criterion: a class it does not have makes the window unloadable. */
    private static void assertNoFlatLafClass(String xml, String what) {
        assertFalse(xml.contains("com.formdev"), what + ": the dark save names a FlatLaf class, "
            + "which a Vision client cannot resolve:\n" + xml);
    }

    private static void assertLoads(String xml, String what) throws Exception {
        Object root = load(xml);
        assertTrue(root != null, what + ": the dark save did not load back");
    }

    /**
     * Strip what differs between two saves of the same thing in the SAME
     * theme: object ids, reference numbers, and the timestamps some
     * components stamp. Everything else must be identical.
     */
    private static String normalise(String xml) {
        return xml.replaceAll(" id=\"\\d+\"", "")
            .replaceAll("<ref>\\d+</ref>", "<ref/>")
            .replaceAll("\\d{13}", "T");
    }

    /**
     * Normalised, with the accepted differences removed, so that only a
     * look-and-feel value leaking into the file can still diff:
     *
     * <ul>
     *   <li>A {@code setForeground}/{@code setBackground} carrying a STOCK
     *       token value. A component the palette handed a token OBJECT saves
     *       that colour as the stock value under dark, written by the module,
     *       where a stock Designer writes nothing because the value equals
     *       the default (or the reverse: nothing under dark where stock wrote
     *       the stock value). Same colour on every client. Documented in
     *       ARCHITECTURE under TokenColorDelegate. Only STOCK values are
     *       dropped, so a dark value still diffs. {@code setWeekendForeground}
     *       is the same residue one step removed: a date-time selector's
     *       weekend colour starts as the Base900 token, the tree update's
     *       {@code setForeground} replaces it with the panel foreground, and
     *       under dark that is FlatLaf's, written back as the stock value
     *       (#2E2E2E) where under stock it is RGB-equal to the token and
     *       nothing is written.</li>
     *   <li>The preferred size the palette computes at drop time
     *       ({@code <p2df>}): FlatLaf's fonts and insets measure a label two
     *       pixels smaller. Cosmetic, and the user resizes anyway.</li>
     *   <li>Timestamps and the random sample data charts generate, which
     *       differ between two stock saves too; and every date-valued
     *       setter, since a component's "now" and its clean copy's differ
     *       whenever a second boundary falls between them.</li>
     * </ul>
     */
    private static String comparable(String xml) {
        String out = normalise(xml);
        for (int stock : new int[] {0xFFFAFAFB, 0xFF2E2E2E, 0xFFF1F1F1, 0xFFFAFAFA}) {
            out = out.replaceAll("\\s*<c-c m=\"set(Foreground|Background|ButtonBG|WeekendForeground)\" s=\"1;clr\">"
                + Pattern.quote(clr(stock)) + "</c-c>", "");
        }
        return out.replaceAll("<p2df>[\\d.]+;[\\d.]+</p2df>", "<p2df/>")
            // A FlatLaf-only default (ComboBox.maximumRowCount, 15; Synthetica
            // defines none, Swing's own is 8) that a combo box keeps from the
            // theme it was built or updated under, and saves as a plain int.
            // Fifteen dropdown rows instead of eight; documented, not a colour.
            .replaceAll("\\s*<c-c m=\"setMaximumRowCount\" s=\"1;i\"><int>\\d+</int></c-c>", "")
            // A date-time selector's formatted date is the moment it was
            // built; whether the save writes it depends on whether the clean
            // copy was built in the same second.
            .replaceAll("\\s*<c-c m=\"setFormattedDate\" s=\"1;str\"><str>[^<]*</str></c-c>", "")
            // Likewise every date-valued setter (a spinner's setDateValue, a
            // date range's setStartDate…): "now" at construction, written or
            // not by the clock.
            .replaceAll("\\s*<c-c m=\"set[A-Za-z]*\" s=\"1;date\"><date>\\d+</date></c-c>", "")
            .replaceAll("<date>\\d+</date>", "<date/>")
            .replaceAll("<int>\\d+</int>", "<int/>")
            .replaceAll("<dbl>[\\d.\\-E]+</dbl>", "<dbl/>");
    }

    private static String clr(int argb) {
        return "<clr>" + argb + "</clr>";
    }

    private static String firstDiff(String a, String b) {
        String[] la = a.split("\n"), lb = b.split("\n");
        for (int i = 0; i < Math.max(la.length, lb.length); i++) {
            String x = i < la.length ? la[i] : "<end>", y = i < lb.length ? lb[i] : "<end>";
            if (!x.equals(y)) {
                return "  line " + (i + 1) + "\n    stock: " + x.trim() + "\n    other: " + y.trim();
            }
        }
        return "  (no line differs; lengths " + a.length() + " vs " + b.length() + ")";
    }

    interface Body {
        void run() throws Exception;
    }

    private static void onEdt(Body body) throws Exception {
        Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure[0] = t;
            }
        });
        if (failure[0] instanceof AssertionError) {
            throw (AssertionError) failure[0];
        }
        if (failure[0] != null) {
            throw new RuntimeException(failure[0]);
        }
    }
}
