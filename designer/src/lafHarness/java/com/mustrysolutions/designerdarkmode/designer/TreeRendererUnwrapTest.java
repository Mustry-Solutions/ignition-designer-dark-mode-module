package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellRenderer;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What {@code TreeIconRecolorer.unwrap()} hands each tree back (#42).
 *
 * <p>A tree whose renderer was the look and feel's own gets {@code null}, so
 * the light look and feel installs its default again — that is what puts
 * Ignition's {@code TreeCellRenderer} and its icons back. A tree whose
 * renderer somebody set gets that instance. The choice is recorded at wrap
 * time, from {@code BasicTreeUI.createdRenderer}, and the wrap runs again on
 * every pass of the component watcher for the whole dark session.
 *
 * <p>So the record has to be re-ASSIGNED on each wrap, not accumulated: a
 * view that builds its renderer lazily (a tree added to a window before its
 * model, then given a renderer when the model arrives) is first wrapped with
 * the look and feel's renderer and re-wrapped with its own. With the mark
 * left over from the first wrap, the restore hands that tree {@code null} and
 * its renderer is gone for the rest of the light session — every icon,
 * colour and row height it drew with it.
 *
 * <p>Both halves are pinned here, because a fix that simply stopped marking
 * would pass the first test and put the no-icons bug the null hand-off exists
 * for straight back.
 */
class TreeRendererUnwrapTest {

    private TreeIconRecolorer icons;
    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
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
    @DisplayName("a renderer set between two wraps survives the unwrap")
    void aRendererSetBetweenWrapsIsHandedBack() {
        JTree tree = new JTree();
        JPanel panel = panelWith(tree);
        manager.apply(true);

        // First pass of the watcher: the tree still has the look and feel's
        // own renderer, so this wrap marks it.
        icons.installIn(panel);

        // The view builds its renderer and sets it, the way a Designer tree
        // whose model arrives late does. This replaces our wrapper.
        DefaultTreeCellRenderer own = new DefaultTreeCellRenderer();
        tree.setCellRenderer(own);

        // A later pass of the watcher wraps the tree again — now over a
        // renderer the look and feel did not create.
        icons.installIn(panel);
        assertNotSame(own, tree.getCellRenderer(),
            "the second pass did not re-wrap the tree, so this test proves nothing");

        icons.unwrap();

        assertSame(own, tree.getCellRenderer(),
            "the restore threw away a renderer the view had set. The tree was marked as "
                + "holding the look and feel's renderer on its FIRST wrap, and the mark "
                + "outlived the renderer it described.");
    }

    @Test
    @DisplayName("a tree that never got its own renderer is still handed null")
    void aLookAndFeelRendererIsNotHandedBack() {
        JTree tree = new JTree();
        TreeCellRenderer lookAndFeels = tree.getCellRenderer();
        JPanel panel = panelWith(tree);
        manager.apply(true);

        icons.installIn(panel);
        icons.unwrap();

        TreeCellRenderer restored = tree.getCellRenderer();
        assertNotNull(restored, "the tree was left with no renderer at all");
        assertNotSame(lookAndFeels, restored,
            "the tree got the look and feel's old renderer back as if a view had set it. "
                + "Its UI then treats it as user-set and never installs its own, which is "
                + "how a default-renderered tree came back from a dark cycle with no icons.");
    }

    private static JPanel panelWith(JTree tree) {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(tree, java.awt.BorderLayout.CENTER);
        panel.setSize(300, 200);
        return panel;
    }
}
