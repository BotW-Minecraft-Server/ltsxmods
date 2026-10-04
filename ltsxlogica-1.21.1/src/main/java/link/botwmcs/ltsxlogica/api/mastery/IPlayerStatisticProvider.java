package link.botwmcs.ltsxlogica.api.mastery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Register during common initialization before datapack loading; query runs on the server thread.
 * Return committed non-negative cumulative counts. Validation must not require a player or loaded tags. */
public interface IPlayerStatisticProvider {
    ResourceLocation id();
    long query(ServerPlayer player, StatisticSelector selector);
    /** Validate selectors without creating arbitrary missing vanilla registry entries. */
    void validate(StatisticSelector selector);
}
