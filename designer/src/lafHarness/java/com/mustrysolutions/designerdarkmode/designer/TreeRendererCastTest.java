package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Component;

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
 * A tree that casts its own renderer must not be wrapped.
 *
 * <p>{@code TagBrowserTree.getTagRenderer()} is literally
 * {@code (TagRenderer) getCellRenderer()}, and the tree's own {@code paint}
 * calls it. Wrapping that tree's renderer therefore turned every repaint of the
 * Tag Browser into a {@code ClassCastException} on the EDT:
 *
 * <pre>
 * java.lang.ClassCastException: TreeIconRecolorer$RecoloringRenderer
 *     cannot be cast to ...tags.tree.TagRenderer
 *   at ...TagBrowserTree.getTagRenderer(TagBrowserTree.java:137)
 *   at ...TagBrowserTree$TagTreeUi.paint(TagBrowserTree.java:246)
 * </pre>
 *
 * <p>Found in a Designer's Output Console during the QA sweep — not by looking
 * at the screen, because the exception is swallowed by the paint loop and the
 * tree simply fails to draw that pass.
 *
 * <p>The guard is by SHAPE, not by class name: any zero-argument method below
 * {@code JTree} returning a {@code TreeCellRenderer} subtype is a cast waiting
 * to happen. Both halves are pinned here — such a tree is skipped, and an
 * ordinary tree is still wrapped, because a guard that skipped everything would
 * pass the first assertion and quietly disable the pass.
 */
class TreeRendererCastTest {

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
    @DisplayName("a tree that casts its own renderer is left alone")
    void aCastingTreeIsNotWrapped() {
        CastingTree tree = new CastingTree();
        TreeCellRenderer stock = tree.getCellRenderer();
        JPanel panel = panelWith(tree);

        manager.apply(true);
        icons.installIn(panel);

        assertSame(stock, tree.getCellRenderer(),
            "the renderer was wrapped on a tree that casts it — its own paint will "
                + "now throw ClassCastException");
        // The cast the real tree performs, proving the hazard is real rather
        // than hypothetical.
        tree.getTypedRenderer();
    }

    @Test
    @DisplayName("an ordinary tree is still wrapped (the guard is not a blanket skip)")
    void anOrdinaryTreeIsStillWrapped() {
        JTree tree = new JTree();
        TreeCellRenderer stock = tree.getCellRenderer();
        JPanel panel = panelWith(tree);

        manager.apply(true);
        icons.installIn(panel);

        assertNotSame(stock, tree.getCellRenderer(),
            "an ordinary tree was skipped too, so the guard has disabled the pass "
                + "rather than narrowed it");
    }

    private static JPanel panelWith(JTree tree) {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(tree, java.awt.BorderLayout.CENTER);
        panel.setSize(300, 200);
        return panel;
    }

    /** The shape {@code TagBrowserTree} has: a typed accessor that casts. */
    private static final class CastingTree extends JTree {

        /** Mirrors {@code TagBrowserTree.getTagRenderer()}. */
        TypedRenderer getTypedRenderer() {
            return (TypedRenderer) getCellRenderer();
        }

        CastingTree() {
            setCellRenderer(new TypedRenderer());
        }
    }

    /** A renderer type the tree above insists on getting back. */
    private static final class TypedRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean focus) {
            return super.getTreeCellRendererComponent(
                tree, value, selected, expanded, leaf, row, focus);
        }
    }
}
