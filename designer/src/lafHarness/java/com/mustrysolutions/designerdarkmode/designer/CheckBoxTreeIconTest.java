package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import javax.swing.CellRendererPane;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jidesoft.swing.CheckBoxTree;
import com.jidesoft.swing.CheckBoxTreeCellRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The project Export and Import dialogs' resource icons are recoloured (#41).
 *
 * <p>Both dialogs list the project in a JIDE {@code CheckBoxTree}. Its
 * {@code CheckBoxTreeCellRenderer} renders back through the renderer the tree
 * was given, so wrapping it recurses, and {@link TreeIconRecolorer} skipped
 * those trees outright: the resource icons, {@code NavTreeIcon}s at a
 * luminance of 70 to 86, painted on the #3C3F41 tree in their stock dark grey,
 * a view's icon all but invisible. The same icons in the Project Browser come
 * out near 150. The tree is now recoloured at paint time, the route the Tag
 * Editor's event tree takes (#136), which never touches its renderer.
 */
class CheckBoxTreeIconTest {

    /** The dark grey of a stock resource icon. */
    private static final Color STOCK_ICON = new Color(0x505357);

    private ThemeManager manager;
    private TreeIconRecolorer icons;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = ManagerCleanup.newManager();
        manager.captureStockLaf();
        icons = new TreeIconRecolorer();
    }

    @AfterEach
    void leaveTheJvmLight() {
        icons.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("a CheckBoxTree's icons are recoloured at paint time, its renderer left alone")
    void checkBoxTreeIconsAreRecoloured() throws Exception {
        manager.apply(true);
        Icon stockIcon = solidIcon(STOCK_ICON);
        CheckBoxTree tree = checkBoxTree(stockIcon);
        TreeCellRenderer renderer = tree.getCellRenderer();
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tree, BorderLayout.CENTER);
        panel.setSize(300, 200);
        panel.doLayout();
        tree.setSize(300, 200);

        icons.installIn(panel);

        assertSame(renderer, tree.getCellRenderer(),
            "the CheckBoxTree's renderer was wrapped, which recurses through its "
                + "actual renderer");
        assertNotSame(CellRendererPane.class, rendererPane(tree).getClass(),
            "the CheckBoxTree's renderer pane was not intercepted, so nothing "
                + "recolours its icons");

        paint(tree);

        JLabel cell = (JLabel) ((CheckBoxTreeCellRenderer) renderer).getActualTreeRenderer();
        Icon painted = cell.getIcon();
        assertNotSame(stockIcon, painted,
            "the last row painted with its stock icon, so the pane recoloured nothing");
        assertTrue(luminance(painted) > luminance(stockIcon) + 40,
            "the recoloured icon is no lighter than the stock one ("
                + luminance(painted) + " vs " + luminance(stockIcon)
                + "), so it still disappears on the dark tree");
    }

    @Test
    @DisplayName("the checkbox column keeps working: the renderer still draws a checkbox per row")
    void theCheckBoxesAreStillDrawn() throws Exception {
        manager.apply(true);
        CheckBoxTree tree = checkBoxTree(solidIcon(STOCK_ICON));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tree, BorderLayout.CENTER);
        panel.setSize(300, 200);
        panel.doLayout();
        tree.setSize(300, 200);
        icons.installIn(panel);

        Component row = tree.getCellRenderer().getTreeCellRendererComponent(
            tree, tree.getPathForRow(1).getLastPathComponent(), false, false, true, 1, false);

        assertTrue(row instanceof CheckBoxTreeCellRenderer,
            "the row is no longer drawn by JIDE's checkbox renderer");
        assertNotEquals(0, ((CheckBoxTreeCellRenderer) row).getComponentCount(),
            "JIDE's renderer drew an empty row");
    }

    private static CheckBoxTree checkBoxTree(Icon icon) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Views");
        root.add(new DefaultMutableTreeNode("QA"));
        root.add(new DefaultMutableTreeNode("Main"));
        CheckBoxTree tree = new CheckBoxTree(new DefaultTreeModel(root));
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree t, Object value, boolean selected,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(t, value, selected, expanded, leaf, row, hasFocus);
                setIcon(icon);
                return this;
            }
        });
        return tree;
    }

    private static Icon solidIcon(Color color) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(2, 2, 12, 12);
        } finally {
            g.dispose();
        }
        return new ImageIcon(image);
    }

    private static void paint(JTree tree) {
        BufferedImage image = new BufferedImage(tree.getWidth(), tree.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            tree.paint(g);
        } finally {
            g.dispose();
        }
    }

    /** Mean luminance of an icon's opaque pixels, 0-255. */
    private static double luminance(Icon icon) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            icon.paintIcon(new JLabel(), g, 0, 0);
        } finally {
            g.dispose();
        }
        double sum = 0;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) > 128) {
                    Color c = new Color(argb);
                    sum += 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
                    count++;
                }
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private static CellRendererPane rendererPane(JTree tree) throws Exception {
        Field pane = BasicTreeUI.class.getDeclaredField("rendererPane");
        pane.setAccessible(true);
        return (CellRendererPane) pane.get(tree.getUI());
    }
}
