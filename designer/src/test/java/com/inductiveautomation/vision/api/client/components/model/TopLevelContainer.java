package com.inductiveautomation.vision.api.client.components.model;

/**
 * A stand-in for Vision's {@code TopLevelContainer}, under its real name.
 *
 * <p>{@link com.mustrysolutions.designerdarkmode.designer.VisionWindows} recognises
 * a Vision window or template by this interface's NAME, because the Vision
 * jars are not on any test classpath (they are not a published SDK artifact).
 * The name is the contract, so the test double carries it exactly; nothing
 * here is Vision's code.
 */
public interface TopLevelContainer {
}
