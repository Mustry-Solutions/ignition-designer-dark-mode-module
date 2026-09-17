package com.mustrysolutions.designerdarkmode.designer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.swing.border.Border;

import com.formdev.flatlaf.FlatLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link LookAndFeelBorders#FLATLAF_BORDER_CLASSES} to the FlatLaf jar
 * in the build: the equality rule is keyed by exact class, so a border class
 * FlatLaf adds in an upgrade would be written into windows until the list
 * names it.
 */
class FlatLafBorderClassesTest {

    @Test
    @DisplayName("the list names every concrete Border in the FlatLaf jar, and nothing else")
    void listMatchesTheJar() throws Exception {
        File jar = new File(FlatLaf.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        TreeSet<String> inJar = new TreeSet<>();
        try (ZipFile zip = new ZipFile(jar)) {
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                String name = entries.nextElement().getName();
                if (!name.endsWith(".class") || !name.startsWith("com/formdev/flatlaf/")
                        || name.endsWith("module-info.class")) {
                    continue;
                }
                String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                Class<?> type;
                try {
                    type = Class.forName(className, false, FlatLaf.class.getClassLoader());
                } catch (Throwable notLoadable) {
                    continue;
                }
                if (Border.class.isAssignableFrom(type) && !type.isInterface()
                        && !Modifier.isAbstract(type.getModifiers())) {
                    inJar.add(className);
                }
            }
        }
        assertEquals(inJar, new TreeSet<>(LookAndFeelBorders.FLATLAF_BORDER_CLASSES),
            "LookAndFeelBorders.FLATLAF_BORDER_CLASSES is out of step with " + jar.getName());
    }
}
