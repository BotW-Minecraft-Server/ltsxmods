package link.botwmcs.core.service.fizzy.overlay;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;
import link.botwmcs.core.api.fizzier.overlay.IFizzyOverlayService;
import link.botwmcs.fizzy.api.AnnounceAPI;
import link.botwmcs.fizzy.api.OverlayAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class FizzyOverlayService implements IFizzyOverlayService {
    @Override
    public void sendOverlayTo(ServerPlayer player, String title, String slidingText, String message) {
        Objects.requireNonNull(player, "player");
        OverlayAPI.sendTo(player, title, slidingText, message);
    }

    @Override
    public void sendOverlayTo(Collection<? extends ServerPlayer> players, String title, String slidingText, String message) {
        Objects.requireNonNull(players, "players");
        OverlayAPI.sendTo(players, title, slidingText, message);
    }

    @Override
    public void sendOverlayToIf(ServerLevel level, Predicate<ServerPlayer> predicate, String title, String slidingText, String message) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(predicate, "predicate");
        OverlayAPI.sendToIf(level, predicate, title, slidingText, message);
    }

    @Override
    public void sendOverlayToNear(ServerLevel level, BlockPos origin, double radius, String title, String slidingText, String message) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");
        OverlayAPI.sendToNear(level, origin, radius, title, slidingText, message);
    }

    @Override
    public void broadcastOverlay(ServerLevel level, String title, String slidingText, String message) {
        Objects.requireNonNull(level, "level");
        OverlayAPI.broadcast(level, title, slidingText, message);
    }

    @Override
    public void hideOverlay(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        OverlayAPI.hide(player);
    }

    @Override
    public void announceTo(ServerPlayer player, String message, int durationTicks) {
        Objects.requireNonNull(player, "player");
        AnnounceAPI.sendTo(player, message, durationTicks);
    }

    @Override
    public void announceTo(Collection<? extends ServerPlayer> players, String message, int durationTicks) {
        Objects.requireNonNull(players, "players");
        AnnounceAPI.sendTo(players, message, durationTicks);
    }

    @Override
    public void announceToIf(ServerLevel level, Predicate<ServerPlayer> predicate, String message, int durationTicks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(predicate, "predicate");
        AnnounceAPI.sendToIf(level, predicate, message, durationTicks);
    }

    @Override
    public void announceToNear(ServerLevel level, BlockPos origin, double radius, String message, int durationTicks) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");
        AnnounceAPI.sendToNear(level, origin, radius, message, durationTicks);
    }

    @Override
    public void broadcastAnnouncement(ServerLevel level, String message, int durationTicks) {
        Objects.requireNonNull(level, "level");
        AnnounceAPI.broadcast(level, message, durationTicks);
    }

    @Override
    public void hideAnnouncement(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        AnnounceAPI.hide(player);
    }
}
