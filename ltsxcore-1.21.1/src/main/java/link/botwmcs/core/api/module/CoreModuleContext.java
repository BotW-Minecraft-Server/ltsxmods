package link.botwmcs.core.api.module;

import java.util.Objects;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;

/**
 * Shared bootstrap context for modules. Keeps module integration decoupled from core internals.
 */
public record CoreModuleContext(
        String coreModId,
        Logger logger,
        IEventBus neoForgeBus,
        IEventBus modBus
) {
    public CoreModuleContext {
        Objects.requireNonNull(coreModId, "coreModId");
        Objects.requireNonNull(logger, "logger");
        Objects.requireNonNull(neoForgeBus, "neoForgeBus");
        Objects.requireNonNull(modBus, "modBus");
    }
}
