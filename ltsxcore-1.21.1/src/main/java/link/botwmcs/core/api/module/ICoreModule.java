package link.botwmcs.core.api.module;

import link.botwmcs.core.api.command.LtsxCommandRegistrar;

/**
 * Core module contract. External module jars expose implementations via ServiceLoader.
 */
public interface ICoreModule {
    /**
     * Unique module identifier, usually matching the module modId.
     */
    String moduleId();

    /**
     * Load order (lower value means earlier registration).
     */
    int loadOrder();

    /**
     * Registration callback invoked by the core.
     */
    void onRegister(CoreModuleContext ctx);

    /**
     * Registers menus under the shared /ltsx command root.
     */
    default void registerLtsxCommands(LtsxCommandRegistrar registrar) {
    }
}
