package com.mustrysolutions.designerdarkmode.designer;

import javax.swing.JScrollPane;
import javax.swing.JTable;

/**
 * A table in a scroll pane the platform serializer can save without Vision on
 * the classpath — the shape of a Vision Table, Comments Panel or Alarm Status
 * Table: the component <em>is</em> the scroll pane, and its border is what
 * {@code JTable.configureEnclosingScrollPaneUI} rewrites.
 *
 * <p>{@link SerializerProbeTableBeanInfo} lists the properties a save may
 * carry, as {@link SerializerProbeButton}'s does. Public with a public no-arg
 * constructor, because the serializer builds its clean copy with
 * {@code Class.newInstance()}.
 */
public class SerializerProbeTable extends JScrollPane {

    public SerializerProbeTable() {
        super(new JTable(2, 2));
    }

    /** The table inside, for a tree walk that reaches it as the Designer's does. */
    public JTable table() {
        return (JTable) getViewport().getView();
    }
}
