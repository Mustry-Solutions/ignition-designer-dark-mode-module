package com.mustrysolutions.designerdarkmode.designer;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/** The properties a save of {@link SerializerProbeBorrowedBorderPanel} may carry. */
public class SerializerProbeBorrowedBorderPanelBeanInfo extends SimpleBeanInfo {

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[] {
                new PropertyDescriptor("border", SerializerProbeBorrowedBorderPanel.class),
                new PropertyDescriptor("toolTipText", SerializerProbeBorrowedBorderPanel.class),
            };
        } catch (IntrospectionException e) {
            throw new IllegalStateException("JPanel no longer has one of these properties", e);
        }
    }
}
