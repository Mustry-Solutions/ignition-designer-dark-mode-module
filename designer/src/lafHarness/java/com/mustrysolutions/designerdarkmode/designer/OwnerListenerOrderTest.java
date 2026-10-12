package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jidesoft.grid.TreeTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The owner-listener reorder that fixed #130 moves only what #130 needs.
 *
 * <p>{@link TableRendererCastTest} pins that the Open/Create Project dialog's
 * listener ends up behind the table UI's handler. These pin the other side:
 * {@code moveOwnedMouseListenersLast} moves nothing else.
 *
 * <ul>
 *   <li>A helper listener added after the owner's is not a reason to move
 *       the owner's (#141). The rule before was "an owner listener ahead of
 *       ANY other listener", which reversed an order the application set
 *       up.</li>
 *   <li>A listener the table's own class declares is not an owner listener
 *       (#186). On the Vision property table, a JIDE {@code TreeTable}, that
 *       was {@code TreeTable$ExpandMouseListener}, which stock puts ahead of
 *       the UI's handler. It was moved behind it under dark and stayed there
 *       after the light restore.</li>
 * </ul>
 *
 * <p>The owning panels are top-level classes in this file, not nested in the
 * test: the owner walk counts a component's enclosing classes, so a panel
 * nested here would make the test's own helper an owner listener too.
 */
class OwnerListenerOrderTest {

    private ThemeManager manager;
    private CellRendererSanitizer renderers;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = ManagerCleanup.newManager();
        manager.captureStockLaf();
        renderers = new CellRendererSanitizer();
    }

    @AfterEach
    void leaveTheJvmLight() {
        renderers.unwrap();
        renderers.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("a table already in order keeps a helper added after the owner's listener (#141)")
    void aHelperAfterTheOwnerIsLeftAlone() {
        ListenerOwningPanel panel = new ListenerOwningPanel(new JTable(new DefaultTableModel(1, 1)));
        panel.table.addMouseListener(new Helper());
        List<String> stock = order(panel.table);
        assertTrue(stock.indexOf(handler(panel.table)) < stock.indexOf("owner"),
            "the table was not built in the right order, so this proves nothing: " + stock);

        CellRendererSanitizer.moveOwnedMouseListenersLast(panel.table);

        assertEquals(stock, order(panel.table),
            "the owner's listener was moved behind a helper added after it, which "
                + "reverses the order the application set up");
    }

    @Test
    @DisplayName("a replaced UI's handler gets the owner's listener straight behind it again")
    void theOwnerGoesStraightBehindTheNewHandler() {
        ListenerOwningPanel panel = new ListenerOwningPanel(new JTable(new DefaultTableModel(1, 1)));
        panel.table.addMouseListener(new Helper());

        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(panel);
        // One added after the new UI, as ToolTipManager re-adds itself: the
        // owner's listener belongs ahead of it, not at the end.
        panel.table.addMouseListener(new Helper());
        List<String> replaced = order(panel.table);
        assertTrue(replaced.indexOf("owner") < replaced.indexOf(handler(panel.table)),
            "the dark UI's handler did not land behind the owner's listener, so the "
                + "reorder has nothing to do: " + replaced);
        renderers.installIn(panel);

        List<String> after = order(panel.table);
        assertEquals(after.indexOf(handler(panel.table)) + 1, after.indexOf("owner"),
            "the owner's listener is not directly behind the dark UI's handler: " + after);
        replaced.remove("owner");
        after.remove("owner");
        assertEquals(replaced, after, "listeners other than the owner's were reordered");
    }

    @Test
    @DisplayName("a JIDE TreeTable's own expand listener keeps its stock place through a round trip (#186)")
    void theTreeTablesExpandListenerStays() {
        // The Vision property table's shape: a TreeTable in a scroll pane in
        // the panel that adds its own listener after building it.
        ListenerOwningPanel panel = new ListenerOwningPanel(new TreeTable());
        List<String> stock = order(panel.table);
        assertTrue(stock.indexOf("TreeTable$ExpandMouseListener")
                < stock.indexOf(handler(panel.table)),
            "stock no longer puts JIDE's expand listener ahead of the UI's handler: " + stock);

        manager.apply(true);
        SwingUtilities.updateComponentTreeUI(panel);
        renderers.installIn(panel);
        assertEquals(stock, order(panel.table),
            "under dark the property table's listeners are not in their stock order");

        manager.apply(false);
        SwingUtilities.updateComponentTreeUI(panel);
        renderers.uninstall();
        manager.refreshComponentsLeftDark(panel);
        assertEquals(stock, order(panel.table),
            "after the light restore the property table's listeners are not in their "
                + "stock order");
    }

    /** Listener classes in order, with the test's own two named. */
    private static List<String> order(JTable table) {
        List<String> names = new ArrayList<>();
        for (MouseListener listener : table.getMouseListeners()) {
            if (listener instanceof ListenerOwningPanel.OwnerListener) {
                names.add("owner");
            } else if (listener instanceof Helper) {
                names.add("helper");
            } else {
                String name = listener.getClass().getName();
                names.add(name.substring(name.lastIndexOf('.') + 1));
            }
        }
        return names;
    }

    /** The current UI's handler, as {@link #order} names it. */
    private static String handler(JTable table) {
        for (MouseListener listener : table.getMouseListeners()) {
            if (CellRendererSanitizer.declaredByTheUi(table.getUI(), listener.getClass())) {
                String name = listener.getClass().getName();
                return name.substring(name.lastIndexOf('.') + 1);
            }
        }
        throw new AssertionError("no UI handler on " + table.getClass().getName());
    }

    /** A listener from neither the owner nor the look and feel, added last. */
    private static final class Helper extends MouseAdapter {
    }
}

/** A panel that builds a table and then adds its own listener, as IA's do. */
final class ListenerOwningPanel extends JPanel {
    final JTable table;

    ListenerOwningPanel(JTable table) {
        super(new BorderLayout());
        this.table = table;
        add(new JScrollPane(table), BorderLayout.CENTER);
        table.addMouseListener(new OwnerListener());
        setSize(300, 120);
    }

    final class OwnerListener extends MouseAdapter {
    }
}
