package link.botwmcs.core.api.fizzier.overlay;

import java.util.Collection;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface IFizzyOverlayService {
    void sendOverlayTo(ServerPlayer player, String title, String slidingText, String message);

    void sendOverlayTo(Collection<? extends ServerPlayer> players, String title, String slidingText, String message);

    void sendOverlayToIf(ServerLevel level, Predicate<ServerPlayer> predicate, String title, String slidingText, String message);

    void sendOverlayToNear(ServerLevel level, BlockPos origin, double radius, String title, String slidingText, String message);

    void broadcastOverlay(ServerLevel level, String title, String slidingText, String message);

    void hideOverlay(ServerPlayer player);

    void announceTo(ServerPlayer player, String message, int durationTicks);

    void announceTo(Collection<? extends ServerPlayer> players, String message, int durationTicks);

    void announceToIf(ServerLevel level, Predicate<ServerPlayer> predicate, String message, int durationTicks);

    void announceToNear(ServerLevel level, BlockPos origin, double radius, String message, int durationTicks);

    void broadcastAnnouncement(ServerLevel level, String message, int durationTicks);

    void hideAnnouncement(ServerPlayer player);
}
