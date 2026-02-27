package link.botwmcs.core.api.module;

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
}
