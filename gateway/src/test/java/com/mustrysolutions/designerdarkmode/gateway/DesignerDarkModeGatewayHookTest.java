package com.mustrysolutions.designerdarkmode.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The two answers this hook exists to give. Each is a constant, but losing
 * either override changes how a gateway treats the module: listed as Trial
 * instead of Free (#114), or refused at startup on Maker Edition.
 */
class DesignerDarkModeGatewayHookTest {

    private final DesignerDarkModeGatewayHook hook = new DesignerDarkModeGatewayHook();

    @Test
    void declaresTheModuleFree() {
        assertTrue(hook.isFreeModule());
    }

    @Test
    void declaresMakerEditionCompatibility() {
        assertTrue(hook.isMakerEditionCompatible());
    }
}
