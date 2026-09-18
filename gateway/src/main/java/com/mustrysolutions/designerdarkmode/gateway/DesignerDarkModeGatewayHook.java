package com.mustrysolutions.designerdarkmode.gateway;

import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Gateway-scope entry point, present for one reason: to tell the gateway the
 * module is free (#114).
 *
 * <p>Everything the module does happens in the Designer, and up to 0.4.1 it
 * shipped as designer scope only. Its {@code module.xml} has said
 * {@code <freeModule>true</freeModule>} since the first release, but the 8.3
 * gateway never reads that element: {@code ModuleInfoParser} declares the
 * field and neither assigns nor forwards it. The only path that marks a module
 * free is {@code ModuleInstance.loadHook()}, which loads the <em>gateway</em>
 * hook and asks it {@link #isFreeModule()}. A module with no gateway hook is
 * never asked, so {@code LicenseManagerImpl} falls through to the platform
 * trial state and the Modules page lists it as "Trial" — while the module
 * itself has no gate and works the same either way. This hook exists so that
 * question gets asked.
 *
 * <p>It registers nothing: no routes, no scripting functions, no resources.
 */
public class DesignerDarkModeGatewayHook extends AbstractGatewayModuleHook {

    @Override
    public void setup(GatewayContext context) {
        // Nothing to prepare: the module has no gateway-side state.
    }

    @Override
    public void startup(LicenseState activationState) {
        // Nothing to start.
    }

    @Override
    public void shutdown() {
        // Nothing to stop.
    }

    /**
     * The module is free. This is what actually reaches the gateway's license
     * evaluation; the {@code <freeModule>} element in module.xml does not.
     */
    @Override
    public boolean isFreeModule() {
        return true;
    }
}
