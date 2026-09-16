package com.mustrysolutions.designerdarkmode.designer;

/**
 * Type tests by class NAME, for the Designer and Vision classes this module
 * must not import.
 *
 * <p>Most of what the module themes is not SDK surface — JIDE docking frames,
 * the block-diagram workspace, the code editor, Vision's window and template
 * containers. Importing them would make the module fail to load on a Designer
 * without that piece; naming them keeps the failure local to the one pass
 * that needs them. The names themselves are pinned by
 * {@code ReflectiveSurfaceTest} in the harness.
 */
final class ClassNames {

    private ClassNames() {
    }

    /** True when {@code type} is, or extends, the class called {@code name}. */
    static boolean extendsNamed(Class<?> type, String name) {
        for (Class<?> t = type; t != null; t = t.getSuperclass()) {
            if (name.equals(t.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when {@code type} is, extends, or implements (directly, through a
     * superclass, or through a super-interface) the type called {@code name}.
     */
    static boolean implementsNamed(Class<?> type, String name) {
        for (Class<?> t = type; t != null; t = t.getSuperclass()) {
            if (name.equals(t.getName())) {
                return true;
            }
            for (Class<?> iface : t.getInterfaces()) {
                if (implementsNamed(iface, name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
