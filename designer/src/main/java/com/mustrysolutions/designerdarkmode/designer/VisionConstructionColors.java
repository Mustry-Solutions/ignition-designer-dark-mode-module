package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.swing.UIManager;

/**
 * Puts the stock colour back on a Vision component that copied a
 * look-and-feel colour into a plain field at construction (#92 follow-up).
 *
 * <p>Almost everything a Vision save carries can be corrected at the point of
 * writing (see {@code TokenColorDelegate}): the colour object is still the
 * look and feel's, or the token's, and can be recognised. The Tree View is
 * the exception. Its constructor reads four {@code Tree.*} colours from
 * {@code UIManager}, copies each into a plain {@code new Color(r, g, b, a)},
 * and builds its ten-row sample dataset with them as {@code color(r,g,b,a)}
 * STRINGS in the background, foreground, selectedBackground and
 * selectedForeground columns. (It stores them as its default node colours
 * too, but adding the component to a Vision container resets those to the
 * look and feel's, which under dark the save delegate already writes as
 * stock; the dataset stays as built.) By the time a save sees the
 * strings they are indistinguishable from values the user typed. A Tree View
 * dropped from the palette under dark mode therefore saves FlatLaf's dark
 * tree colours in its sample rows, and a light client renders them dark. The
 * corruption sweep found it, and only it, across the palette.
 *
 * <p>So it is corrected where it can be: when the component is attached
 * under dark mode, every colour cell of its data that still encodes
 * FlatLaf's value for one of the four keys is rewritten with the stock
 * value of that key, and the dataset put back through {@code setData}. A
 * cell the user has changed no longer encodes FlatLaf's colour and is left
 * alone. The stock values are captured before FlatLaf goes in.
 *
 * <p>Reached by name: the Tree View is Vision's, not SDK surface. A Designer
 * without Vision never sees one. The Vision probe covers the real class.
 */
final class VisionConstructionColors {

    static final String TREE_VIEW = "com.inductiveautomation.factorypmi.application.components.PMITreeView";
    static final String DATASET = "com.inductiveautomation.ignition.common.Dataset";
    static final String BASIC_DATASET = "com.inductiveautomation.ignition.common.BasicDataset";

    /** Bean property -> the UIManager key {@code PMITreeView()} reads for it. */
    private static final Map<String, String> TREE_VIEW_DEFAULTS = Map.of(
        "DefaultBackground", "Tree.textBackground",
        "DefaultForeground", "Tree.textForeground",
        "DefaultSelectedBackground", "Tree.selectionBackground",
        "DefaultSelectedForeground", "Tree.selectionForeground");

    private final Map<String, Integer> stockByKey = new HashMap<>();
    private final Map<String, Integer> darkByKey = new HashMap<>();

    /** Before the dark look and feel goes in. */
    void captureStock() {
        stockByKey.clear();
        for (String key : TREE_VIEW_DEFAULTS.values()) {
            Color c = UIManager.getColor(key);
            if (c != null) {
                stockByKey.put(key, c.getRGB());
            }
        }
    }

    /** After the dark look and feel and every default is in. */
    void captureDark() {
        darkByKey.clear();
        for (String key : TREE_VIEW_DEFAULTS.values()) {
            Color c = UIManager.getColor(key);
            if (c != null) {
                darkByKey.put(key, c.getRGB());
            }
        }
    }

    void clear() {
        darkByKey.clear();
    }

    /**
     * Correct every Tree View under {@code root} whose defaults are still
     * FlatLaf's. Safe to run repeatedly.
     *
     * @return how many properties were put back
     */
    int correct(Component root) {
        if (darkByKey.isEmpty() || stockByKey.isEmpty()) {
            return 0;
        }
        int corrected = 0;
        if (ClassNames.extendsNamed(root.getClass(), TREE_VIEW)) {
            corrected += correctTreeView(root);
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                corrected += correct(child);
            }
        }
        return corrected;
    }

    private int correctTreeView(Component treeView) {
        int corrected = correctData(treeView);
        if (corrected > 0) {
            DebugLog.detail("VisionConstructionColors: put " + corrected
                + " stock colour(s) back on a Tree View built under dark mode.");
        }
        return corrected;
    }

    /** The sample rows: every {@code color(r,g,b,a)} cell that is FlatLaf's, made stock. */
    private int correctData(Component treeView) {
        Map<String, String> darkToStock = new HashMap<>();
        for (String key : TREE_VIEW_DEFAULTS.values()) {
            Integer dark = darkByKey.get(key);
            Integer stock = stockByKey.get(key);
            if (dark != null && stock != null) {
                darkToStock.put(colorString(dark), colorString(stock));
            }
        }
        try {
            Method getData = treeView.getClass().getMethod("getData");
            Object data = getData.invoke(treeView);
            if (data == null) {
                return 0;
            }
            Class<?> datasetType = Class.forName(DATASET, true, treeView.getClass().getClassLoader());
            if (!datasetType.isInstance(data)) {
                return 0;
            }
            @SuppressWarnings("unchecked")
            java.util.List<String> names = (java.util.List<String>) datasetType.getMethod("getColumnNames").invoke(data);
            @SuppressWarnings("unchecked")
            java.util.List<Class<?>> types = (java.util.List<Class<?>>) datasetType.getMethod("getColumnTypes").invoke(data);
            int rows = (Integer) datasetType.getMethod("getRowCount").invoke(data);
            Method valueAt = datasetType.getMethod("getValueAt", int.class, int.class);
            // BasicDataset takes its cells column-major: data[column][row].
            Object[][] cells = new Object[names.size()][rows];
            int corrected = 0;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < names.size(); c++) {
                    Object value = valueAt.invoke(data, r, c);
                    String stock = value instanceof String ? darkToStock.get(value) : null;
                    if (stock != null) {
                        value = stock;
                        corrected++;
                    }
                    cells[c][r] = value;
                }
            }
            if (corrected == 0) {
                return 0;
            }
            Object rebuilt = Class.forName(BASIC_DATASET, true, treeView.getClass().getClassLoader())
                .getConstructor(java.util.List.class, java.util.List.class, Object[][].class)
                .newInstance(names, types, cells);
            treeView.getClass().getMethod("setData", datasetType).invoke(treeView, rebuilt);
            return corrected;
        } catch (ReflectiveOperationException | RuntimeException e) {
            DebugLog.log("VisionConstructionColors: could not correct the sample data of "
                + treeView.getClass().getName(), e);
            return 0;
        }
    }

    /** The Tree View's own encoding of a colour in its data. */
    static String colorString(int argb) {
        Color c = new Color(argb, true);
        return "color(" + c.getRed() + "," + c.getGreen() + "," + c.getBlue() + "," + c.getAlpha() + ")";
    }
}
