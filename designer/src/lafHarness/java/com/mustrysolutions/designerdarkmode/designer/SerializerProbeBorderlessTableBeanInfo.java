package com.mustrysolutions.designerdarkmode.designer;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/** The properties a save of {@link SerializerProbeBorderlessTable} may carry. */
public class SerializerProbeBorderlessTableBeanInfo extends SimpleBeanInfo {

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[] {
                new PropertyDescriptor("border", SerializerProbeBorderlessTable.class),
                new PropertyDescriptor("toolTipText", SerializerProbeBorderlessTable.class),
            };
        } catch (IntrospectionException e) {
            throw new IllegalStateException("JScrollPane no longer has one of these properties", e);
        }
    }
}
