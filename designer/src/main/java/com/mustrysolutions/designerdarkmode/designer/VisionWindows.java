package com.mustrysolutions.designerdarkmode.designer;

import java.awt.Component;

/**
 * Recognises Vision windows and templates, by name.
 *
 * <p>The colour passes leave the content of a Vision window alone — the
 * canvas is the operator's screen, not Designer chrome — while the chrome
 * around it (the palette, the property editor, their filters) is themed
 * like the rest. Telling the two apart needs the interface both
 * {@code FPMIWindow} and {@code VisionTemplate} implement, and the Vision
 * jars are not SDK surface, so it is reached by name; a Designer without
 * Vision simply never finds one. The Vision probe pins the name against the
 * real jars.
 *
 * <p>What is left of {@code VisionGate}, which until 0.4.0 refused dark mode
 * while Vision was open and dropped a dark Designer to light on the way in,
 * because a window saved under FlatLaf could not be opened by a Vision
 * client. The three things that made that necessary are fixed at the
 * serializer (see {@code SerializerCleanCopies}, {@code TokenColorDelegate}
 * and the restore's style primer in {@code ThemeManager}); the gate went
 * with them.
 */
final class VisionWindows {

    /** Implemented by Vision windows AND templates — the two things a save serializes. */
    static final String TOP_LEVEL_CONTAINER =
        "com.inductiveautomation.vision.api.client.components.model.TopLevelContainer";

    private VisionWindows() {
    }

    /** True for a Vision window or template, or a subclass of one. */
    static boolean isVisionTopLevel(Component component) {
        return component != null
            && ClassNames.implementsNamed(component.getClass(), TOP_LEVEL_CONTAINER);
    }
}
