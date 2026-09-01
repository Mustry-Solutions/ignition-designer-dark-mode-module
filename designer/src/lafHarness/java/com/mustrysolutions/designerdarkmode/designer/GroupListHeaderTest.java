package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.BorderLayout;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A JIDE {@code GroupList} paints its group HEADERS through a second renderer
 * slot (#59).
 *
 * <p>{@code setCellRenderer} never touches that slot, so wrapping only the cell
 * renderer themes the rows and leaves the headers above them in stock
 * light-mode colours. Reporting's Data tab showed exactly that: "StartDate" and
 * "EndDate" correct and dark, under light-blue "Parameters" and "Data Sources"
 * bars.
 *
 * <p>The list itself probes clean — the live Designer reported its
 * {@code SyntheticaSafeGroupList} as {@code bg=#46494B fg=#DDDDDD} — because a
 * group header is not a component, exactly like a table's cell renderers. Only
 * reaching into the second slot fixes it.
 *
 * <p>{@code GroupList} is JIDE and not a compile dependency here, so the
 * production code finds the slot reflectively by name. This stands in for it
 * with the same shape: {@code getGroupCellRenderer} / {@code setGroupCellRenderer}
 * returning and accepting a {@code ListCellRenderer}.
 */
class GroupListHeaderTest {

    private CellRendererSanitizer renderers;
    private ThemeManager manager;

    @BeforeEach
    void installStockDesignerLookAndFeel() throws Exception {
        DesignerLookAndFeel.installStock();
        manager = new ThemeManager();
        manager.captureStockLaf();
        renderers = new CellRendererSanitizer();
    }

    @AfterEach
    void leaveTheJvmLight() {
        renderers.uninstall();
        if (UIManager.getLookAndFeel() instanceof FlatDarkLaf) {
            manager.apply(false);
        }
    }

    @Test
    @DisplayName("the group-header renderer is wrapped, and put back on restore")
    void theGroupSlotIsWrapped() {
        GroupList list = new GroupList();
        ListCellRenderer<?> stockGroup = list.getGroupCellRenderer();
        ListCellRenderer<?> stockCell = list.getCellRenderer();
        JPanel panel = panelWith(list);

        manager.apply(true);
        renderers.installIn(panel);

        assertNotSame(stockGroup, list.getGroupCellRenderer(),
            "the group renderer was left alone, so the 'Parameters' and 'Data "
                + "Sources' headers keep painting in light-mode colours over "
                + "correctly themed rows");
        assertNotSame(stockCell, list.getCellRenderer(),
            "the ordinary cell renderer stopped being wrapped, so this fix has "
                + "displaced the existing one rather than added to it");

        renderers.uninstall();

        assertSame(stockGroup, list.getGroupCellRenderer(),
            "the stock group renderer was not restored, so the headers stay dark "
                + "after switching back to light");
    }

    @Test
    @DisplayName("a plain JList without a group slot is unaffected")
    void aPlainListIsUnaffected() {
        JList<String> list = new JList<>(new String[] {"StartDate", "EndDate"});
        ListCellRenderer<?> stockCell = list.getCellRenderer();
        JPanel panel = panelWith(list);

        manager.apply(true);
        // The reflective lookup finds no group slot here; it must decline
        // quietly rather than throw and take the rest of the walk with it.
        renderers.installIn(panel);

        assertNotSame(stockCell, list.getCellRenderer(),
            "a plain list stopped being wrapped, so the group lookup is throwing "
                + "and aborting the walk");
    }

    private static JPanel panelWith(JList<?> list) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(list, BorderLayout.CENTER);
        panel.setSize(300, 200);
        return panel;
    }

    /** The shape JIDE's {@code GroupList} has: a second renderer slot. */
    private static final class GroupList extends JList<String> {

        private ListCellRenderer groupCellRenderer = new DefaultListCellRenderer();

        GroupList() {
            super(new String[] {"StartDate", "EndDate"});
        }

        public ListCellRenderer getGroupCellRenderer() {
            return groupCellRenderer;
        }

        public void setGroupCellRenderer(ListCellRenderer renderer) {
            groupCellRenderer = renderer;
        }
    }
}
