package link.botwmcs.ltsxlogica.api.mastery;

import net.minecraft.server.level.ServerPlayer;

public interface IPlayerStatisticsService {
    long query(ServerPlayer player, StatisticSelector selector);
    void registerProvider(IPlayerStatisticProvider provider);
    void markDirty(ServerPlayer player);
}
