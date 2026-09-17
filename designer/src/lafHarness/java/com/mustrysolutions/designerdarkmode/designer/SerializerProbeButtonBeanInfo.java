package com.mustrysolutions.designerdarkmode.designer;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.SimpleBeanInfo;

/**
 * The properties a save of {@link SerializerProbeButton} may carry: the four
 * a look and feel dresses, and one that only a hand can set.
 *
 * <p>Explicit descriptors suppress the Introspector's own walk of the
 * superclass, which is what keeps {@code actionMap} and its kin out.
 */
public class SerializerProbeButtonBeanInfo extends SimpleBeanInfo {

    @Override
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return new PropertyDescriptor[] {
                new PropertyDescriptor("border", SerializerProbeButton.class),
                new PropertyDescriptor("font", SerializerProbeButton.class),
                new PropertyDescriptor("foreground", SerializerProbeButton.class),
                new PropertyDescriptor("background", SerializerProbeButton.class),
                new PropertyDescriptor("toolTipText", SerializerProbeButton.class),
            };
        } catch (IntrospectionException e) {
            throw new IllegalStateException("JButton no longer has one of these properties", e);
        }
    }
}
