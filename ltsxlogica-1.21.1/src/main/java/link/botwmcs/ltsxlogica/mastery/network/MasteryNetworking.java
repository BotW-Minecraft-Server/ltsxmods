package link.botwmcs.ltsxlogica.mastery.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import link.botwmcs.core.net.CoreNetwork;
import link.botwmcs.ltsxlogica.LTSXLogicA;
import link.botwmcs.ltsxlogica.api.mastery.*;
import link.botwmcs.ltsxlogica.mastery.MasteryFeature;
import link.botwmcs.ltsxlogica.mastery.rune.MasteryItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class MasteryNetworking {
    private record Request(long id, String result) {}
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final AtomicLong TRANSFERS = new AtomicLong();
    private MasteryNetworking() {}
    public static void register() {
        CoreNetwork.registerPlayToServer(MasteryActionPayload.TYPE, MasteryActionPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Request previous = REQUESTS.get(player.getUUID());
            if (payload.requestId() <= 0 || (previous != null && payload.requestId() <= previous.id())) {
                sync(player, false, previous != null && previous.id() == payload.requestId() ? previous.result() : MasteryResult.INVALID_REQUEST.name());
                return;
            }
            var manager = MasteryFeature.manager();
            MasteryResult result;
            try {
                if (payload.definitionRevision() != manager.registry().revision()) result = MasteryResult.STALE_REVISION;
                else result = switch (payload.action()) {
                    case "refresh" -> { manager.refresh(player); yield MasteryResult.SUCCESS; }
                    case "activate" -> manager.activateSlot(player, payload.mastery(), payload.slot(), payload.stateRevision());
                    case "bind" -> manager.bindFromInventory(player, payload.mastery(), payload.slot(), payload.inventorySlot(), payload.stateRevision());
                    case "unbind" -> manager.unbind(player, payload.mastery(), payload.slot(), payload.stateRevision());
                    default -> MasteryResult.INVALID_REQUEST;
                };
            } catch (RuntimeException ex) {
                LTSXLogicA.LOGGER.error("Mastery request failed for {}", player.getUUID(), ex);
                result = MasteryResult.INVALID_REQUEST;
            }
            REQUESTS.put(player.getUUID(), new Request(payload.requestId(), result.name()));
            sync(player, false, result.name());
        }));
        CoreNetwork.registerPlayToClient(MasterySyncPayload.TYPE, MasterySyncPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            try {
                Class.forName("link.botwmcs.ltsxlogica.mastery.client.MasteryClientAccess")
                        .getMethod("accept", MasterySyncPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException ex) { LTSXLogicA.LOGGER.error("Cannot receive mastery client state", ex); }
        }));
    }
    public static void forget(UUID id) { REQUESTS.remove(id); }
    public static void clearRequests() { REQUESTS.clear(); }

    public static void sync(ServerPlayer player, boolean open, String result) {
        if (player.connection == null || player.isFakePlayer()) return;
        var manager = MasteryFeature.manager();
        PlayerMasterySnapshot snapshot = manager.snapshot(player);
        JsonObject root = new JsonObject();
        root.addProperty("revision", snapshot.stateRevision());
        root.addProperty("definition_revision", snapshot.definitionRevision());
        root.addProperty("result", result);
        JsonArray masteries = new JsonArray();
        var ids = new java.util.TreeSet<net.minecraft.resources.ResourceLocation>(java.util.Comparator.comparing(Object::toString));
        ids.addAll(manager.registry().masteries().keySet()); ids.addAll(snapshot.masteries().keySet());
        for (var id : ids) {
            MasteryDefinition definition = manager.registry().masteries().get(id);
            var progress = snapshot.masteries().get(id);
            JsonObject view = new JsonObject();
            view.addProperty("id", id.toString());
            view.addProperty("name", definition == null ? id + " (unavailable)" : definition.name());
            view.addProperty("available", definition != null);
            view.addProperty("level", progress == null ? 0 : progress.level());
            JsonArray activated = new JsonArray();
            JsonArray bindings = new JsonArray();
            if (progress != null) {
                progress.activatedSlots().stream().sorted().forEach(activated::add);
                progress.bindings().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(binding -> {
                    JsonObject value = new JsonObject(); value.addProperty("slot", binding.getKey());
                    value.addProperty("rune", binding.getValue().runeId().toString()); bindings.add(value);
                });
            }
            view.add("activated", activated); view.add("bindings", bindings);
            MasteryStage stage = definition == null ? null : progress == null ? definition.unlock() : definition.stages().get(progress.level() + 1);
            JsonArray conditions = new JsonArray();
            if (stage != null) for (StatisticCondition condition : stage.conditions()) {
                conditions.add(condition.statistic().statType() + ": " + manager.query(player, condition.statistic()) + " "
                        + condition.operator().symbol() + " " + condition.value().atLevel(stage.level()).toPlainString());
            }
            view.add("conditions", conditions); masteries.add(view);
        }
        root.add("masteries", masteries);
        JsonArray inventory = new JsonArray();
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            RuneInstance instance = item.get(MasteryItems.RUNE_DATA.get());
            if (!item.is(MasteryItems.RUNE.get()) || instance == null) continue;
            RuneDefinition rune = manager.registry().runes().get(instance.runeId());
            JsonObject view = new JsonObject();
            view.addProperty("slot", slot); view.addProperty("id", instance.runeId().toString());
            view.addProperty("name", rune == null ? instance.runeId().toString() : rune.name());
            view.addProperty("required_level", rune == null ? 100 : rune.requiredLevel());
            inventory.add(view);
        }
        root.add("inventory", inventory);
        var chunks = MasterySyncPayload.split(root.toString());
        long transfer = TRANSFERS.incrementAndGet();
        for (int index = 0; index < chunks.size(); index++) {
            CoreNetwork.sendToPlayer(player, new MasterySyncPayload(transfer, index, chunks.size(), open, chunks.get(index)));
        }
    }
}
