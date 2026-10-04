package link.botwmcs.ltsxlogica.mastery;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.data.CoreData;
import link.botwmcs.ltsxlogica.LTSXLogicA;
import link.botwmcs.ltsxlogica.api.mastery.*;
import link.botwmcs.ltsxlogica.data.mastery.MasteryDefinitions;
import link.botwmcs.ltsxlogica.data.mastery.MasteryRegistry;
import link.botwmcs.ltsxlogica.data.persistence.mastery.PlayerMasteryData;
import link.botwmcs.ltsxlogica.mastery.network.MasteryNetworking;
import link.botwmcs.ltsxlogica.mastery.rune.MasteryItems;
import link.botwmcs.ltsxlogica.mastery.stats.VanillaStatisticProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class MasteryManager implements IMasteryService, IRuneService, IPlayerStatisticsService {
    private final MasteryRegistry registry = new MasteryRegistry();
    private final Map<ResourceLocation, IPlayerStatisticProvider> providers = new ConcurrentHashMap<>();
    private final VanillaStatisticProvider vanilla = new VanillaStatisticProvider();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final java.util.Queue<UUID> pending = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Map<UUID, Long> syncedDefinitions = new HashMap<>();
    private final AtomicBoolean reloadPending = new AtomicBoolean();

    public MasteryManager() { providers.put(vanilla.id(), vanilla); }
    public MasteryRegistry registry() { return registry; }
    public void definitionsChanged() { reloadPending.set(true); }
    public void registerProvider(IPlayerStatisticProvider provider) {
        if (providers.putIfAbsent(provider.id(), provider) != null) throw new IllegalArgumentException("Duplicate statistic provider: " + provider.id());
        definitionsChanged();
    }
    public void validateDefinitions(MasteryDefinitions definitions) {
        for (MasteryDefinition definition : definitions.masteries().values()) {
            validate(definition.unlock());
            definition.stages().values().forEach(this::validate);
        }
    }
    private void validate(MasteryStage stage) {
        for (StatisticCondition condition : stage.conditions()) provider(condition.statistic()).validate(condition.statistic());
    }
    private IPlayerStatisticProvider provider(StatisticSelector selector) {
        IPlayerStatisticProvider provider = providers.get(selector.provider());
        if (provider == null) throw new IllegalArgumentException("Unknown statistic provider: " + selector.provider());
        return provider;
    }
    public long query(ServerPlayer player, StatisticSelector selector) {
        checkThread(player);
        long value = provider(selector).query(player, selector);
        if (value < 0) throw new IllegalArgumentException("Statistic provider returned a negative count");
        return value;
    }
    public void markDirty(ServerPlayer player) {
        if (dirty.add(player.getUUID())) pending.add(player.getUUID());
    }
    public void tick(MinecraftServer server) {
        boolean reload = reloadPending.getAndSet(false);
        if (reload) vanilla.clearCache();
        if (reload || server.getTickCount() % 200 == 0) server.getPlayerList().getPlayers().forEach(this::markDirty);
        // FIFO prevents frequently changing players from starving the rest of the queue.
        for (int processed = 0; processed < 8; processed++) {
            UUID id = pending.poll();
            if (id == null) break;
            if (!dirty.remove(id)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            try {
                refresh(player);
                if (!java.util.Objects.equals(syncedDefinitions.get(id), registry.revision())) {
                    MasteryNetworking.sync(player, false, "");
                    syncedDefinitions.put(id, registry.revision());
                }
            } catch (RuntimeException ex) {
                LTSXLogicA.LOGGER.error("Cannot refresh mastery for {}", player.getUUID(), ex);
            }
        }
    }
    public void remove(ServerPlayer player) { dirty.remove(player.getUUID()); syncedDefinitions.remove(player.getUUID()); MasteryNetworking.forget(player.getUUID()); }
    public void stop() { dirty.clear(); pending.clear(); syncedDefinitions.clear(); vanilla.clearCache(); reloadPending.set(false); MasteryNetworking.clearRequests(); }

    private static void checkThread(ServerPlayer player) {
        if (player.getServer() == null || !player.getServer().isSameThread()) throw new IllegalStateException("Mastery API requires the server thread");
    }
    private static PlayerMasteryData load(ServerPlayer player) {
        return new PlayerMasteryData(CoreData.getPlayerTag(player, PlayerMasteryData.KEY));
    }
    private static void commit(ServerPlayer player, PlayerMasteryData data) {
        data.revision = Math.incrementExact(data.revision);
        CoreData.putPlayerTag(player, PlayerMasteryData.KEY, data.save());
    }
    public PlayerMasterySnapshot snapshot(ServerPlayer player) {
        checkThread(player);
        PlayerMasteryData data = load(player);
        Map<ResourceLocation, PlayerMasterySnapshot.Progress> result = new HashMap<>();
        data.masteries.forEach((id, progress) -> result.put(id, progress.snapshot()));
        return new PlayerMasterySnapshot(player.getUUID(), data.revision, registry.revision(), result);
    }
    public PlayerMasterySnapshot refresh(ServerPlayer player) {
        checkThread(player);
        PlayerMasteryData data = load(player);
        Map<StatisticSelector, Long> values = new HashMap<>();
        java.util.function.ToLongFunction<StatisticSelector> reader = selector -> values.computeIfAbsent(selector, s -> query(player, s));
        boolean changed = false;
        for (MasteryDefinition definition : registry.masteries().values()) {
            PlayerMasteryData.Progress progress = data.masteries.get(definition.id());
            if (progress == null && definition.unlock().matches(reader)) {
                progress = new PlayerMasteryData.Progress();
                data.masteries.put(definition.id(), progress);
                changed = true;
            }
            if (progress != null) {
                int level = definition.advance(progress.level, reader);
                if (level != progress.level) { progress.level = level; changed = true; }
            }
        }
        if (changed) { commit(player, data); MasteryNetworking.sync(player, false, ""); }
        return snapshot(player);
    }
    public void openApplicationScreen(ServerPlayer player) { refresh(player); MasteryNetworking.sync(player, true, ""); }

    private MasteryResult validateSlot(PlayerMasteryData data, ResourceLocation mastery, int slot, long expectedRevision) {
        if (data.revision != expectedRevision) return MasteryResult.STALE_REVISION;
        if (!registry.masteries().containsKey(mastery)) return MasteryResult.UNKNOWN_DEFINITION;
        PlayerMasteryData.Progress progress = data.masteries.get(mastery);
        if (progress == null) return MasteryResult.NOT_UNLOCKED;
        if (slot < 0 || slot >= progress.level - 1) return MasteryResult.SLOT_LOCKED;
        return MasteryResult.SUCCESS;
    }
    public MasteryResult activateSlot(ServerPlayer player, ResourceLocation mastery, int slot, long expectedRevision) {
        refresh(player);
        PlayerMasteryData data = load(player);
        MasteryResult result = validateSlot(data, mastery, slot, expectedRevision);
        if (result != MasteryResult.SUCCESS) return result;
        if (data.masteries.get(mastery).activated.add(slot)) { commit(player, data); MasteryNetworking.sync(player, false, ""); }
        return MasteryResult.SUCCESS;
    }
    public ItemStack createRune(ResourceLocation runeId) {
        if (!registry.runes().containsKey(runeId)) throw new IllegalArgumentException("Unknown rune: " + runeId);
        return stack(new RuneInstance(runeId, UUID.randomUUID()));
    }
    private ItemStack stack(RuneInstance instance) {
        ItemStack stack = new ItemStack(MasteryItems.RUNE.get());
        stack.set(MasteryItems.RUNE_DATA.get(), instance);
        RuneDefinition definition = registry.runes().get(instance.runeId());
        if (definition != null) stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                net.minecraft.network.chat.Component.literal(definition.name()));
        return stack;
    }
    public MasteryResult bindFromInventory(ServerPlayer player, ResourceLocation mastery, int slot, int inventorySlot, long expectedRevision) {
        refresh(player);
        PlayerMasteryData data = load(player);
        MasteryResult result = validateSlot(data, mastery, slot, expectedRevision);
        if (result != MasteryResult.SUCCESS) return result;
        PlayerMasteryData.Progress progress = data.masteries.get(mastery);
        if (!progress.activated.contains(slot)) return MasteryResult.SLOT_NOT_ACTIVATED;
        if (progress.bindings.containsKey(slot)) return MasteryResult.SLOT_OCCUPIED;
        if (inventorySlot < 0 || inventorySlot >= player.getInventory().items.size()) return MasteryResult.INVALID_ITEM;
        ItemStack item = player.getInventory().getItem(inventorySlot);
        RuneInstance instance = item.get(MasteryItems.RUNE_DATA.get());
        if (!item.is(MasteryItems.RUNE.get()) || item.isEmpty() || item.getCount() != 1 || instance == null) return MasteryResult.INVALID_ITEM;
        RuneDefinition rune = registry.runes().get(instance.runeId());
        if (rune == null) return MasteryResult.UNKNOWN_DEFINITION;
        if (progress.level < rune.requiredLevel()) return MasteryResult.LEVEL_TOO_LOW;
        if (registry.masteries().get(mastery).allowedSeries().stream().noneMatch(rune.series()::contains)) return MasteryResult.SERIES_MISMATCH;
        for (var record : data.masteries.values()) for (RuneInstance binding : record.bindings.values()) {
            RuneDefinition other = registry.runes().get(binding.runeId());
            if (binding.instanceId().equals(instance.instanceId()) || (other != null && other.enchantment().equals(rune.enchantment()))) return MasteryResult.CONFLICT;
        }
        progress.bindings.put(slot, instance);
        // Serialization is validated before touching inventory; both live states commit on this thread.
        CompoundCommit commit = prepare(data);
        item.shrink(1);
        CoreData.putPlayerTag(player, PlayerMasteryData.KEY, commit.tag());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        MasteryNetworking.sync(player, false, "");
        return MasteryResult.SUCCESS;
    }
    private record CompoundCommit(net.minecraft.nbt.CompoundTag tag) {}
    private static CompoundCommit prepare(PlayerMasteryData data) {
        data.revision = Math.incrementExact(data.revision);
        return new CompoundCommit(data.save());
    }
    public MasteryResult unbind(ServerPlayer player, ResourceLocation mastery, int slot, long expectedRevision) {
        checkThread(player);
        PlayerMasteryData data = load(player);
        // Unbinding remains available for removed definitions so players can recover suspended runes.
        if (data.revision != expectedRevision) return MasteryResult.STALE_REVISION;
        PlayerMasteryData.Progress progress = data.masteries.get(mastery);
        if (progress == null) return MasteryResult.NOT_UNLOCKED;
        RuneInstance instance = progress.bindings.get(slot);
        if (instance == null) return MasteryResult.INVALID_ITEM;
        int empty = player.getInventory().getFreeSlot();
        if (empty < 0) return MasteryResult.INVENTORY_FULL;
        ItemStack returned = stack(instance);
        progress.bindings.remove(slot);
        CompoundCommit commit = prepare(data);
        player.getInventory().setItem(empty, returned);
        CoreData.putPlayerTag(player, PlayerMasteryData.KEY, commit.tag());
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        MasteryNetworking.sync(player, false, "");
        return MasteryResult.SUCCESS;
    }
    public List<RuneInstance> appliedRunes(ServerPlayer player) {
        PlayerMasterySnapshot snapshot = snapshot(player);
        return snapshot.masteries().entrySet().stream().filter(e -> registry.masteries().containsKey(e.getKey())).flatMap(entry -> {
            MasteryDefinition mastery = registry.masteries().get(entry.getKey());
            var progress = entry.getValue();
            return progress.bindings().entrySet().stream().filter(binding -> {
                RuneDefinition rune = registry.runes().get(binding.getValue().runeId());
                return rune != null && progress.activatedSlots().contains(binding.getKey()) && progress.level() >= rune.requiredLevel()
                        && mastery.allowedSeries().stream().anyMatch(rune.series()::contains);
            }).map(Map.Entry::getValue);
        }).toList();
    }
}
