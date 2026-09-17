package com.mustrysolutions.designerdarkmode.designer;

import javax.swing.JButton;

/**
 * A button the platform serializer can save without Vision on the classpath.
 *
 * <p>{@code DefaultObjectSerializationDelegate} walks every property the
 * bean's {@code BeanInfo} lists, and a plain {@code JButton} has no
 * {@code BeanInfo}, so the Introspector hands it all of them — including
 * {@code actionMap}, whose parent chain the serializer cannot write. Vision
 * solves this with a {@code BeanInfo} per component that names the properties
 * a window may carry; {@link SerializerProbeButtonBeanInfo} does the same for
 * this class, restricted to the ones a look and feel dresses. The Introspector
 * finds it by name in this package, exactly as it finds Vision's.
 *
 * <p>Public with a public no-arg constructor, because the serializer builds
 * its clean copy with {@code Class.newInstance()}.
 */
public class SerializerProbeButton extends JButton {

    public SerializerProbeButton() {
        super("Save");
    }
}
