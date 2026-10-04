package link.botwmcs.ltsxlogica.api.mastery;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server-thread-only API. Unregistered and not-yet-unlocked masteries are distinct.
 * All returned player views are immutable; do not mutate CoreData directly. */
public interface IMasteryService {
    /** Reads persisted state; it does not force a statistic/progression refresh. */
    PlayerMasterySnapshot snapshot(ServerPlayer player);
    /** Uses committed statistics, unlocks eligible definitions and advances sequential stages. */
    PlayerMasterySnapshot refresh(ServerPlayer player);
    /** Zero-based slot index; use the latest snapshot's stateRevision for optimistic concurrency. */
    MasteryResult activateSlot(ServerPlayer player, ResourceLocation mastery, int slot, long expectedRevision);
    /** Refreshes and opens the simple Fizzy application screen on this player's client. */
    void openApplicationScreen(ServerPlayer player);
}
