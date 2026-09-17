package com.mustrysolutions.designerdarkmode.designer;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/** The properties a save of {@link SerializerProbeTable} may carry. */
public class SerializerProbeTableBeanInfo extends SimpleBeanInfo {

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[] {
                new PropertyDescriptor("border", SerializerProbeTable.class),
                new PropertyDescriptor("font", SerializerProbeTable.class),
                new PropertyDescriptor("foreground", SerializerProbeTable.class),
                new PropertyDescriptor("background", SerializerProbeTable.class),
                new PropertyDescriptor("toolTipText", SerializerProbeTable.class),
            };
        } catch (IntrospectionException e) {
            throw new IllegalStateException("JScrollPane no longer has one of these properties", e);
        }
    }
}
