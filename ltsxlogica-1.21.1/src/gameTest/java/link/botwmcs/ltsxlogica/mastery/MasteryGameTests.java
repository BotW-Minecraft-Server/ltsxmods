package link.botwmcs.ltsxlogica.mastery;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import link.botwmcs.core.data.CoreData;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.ltsxlogica.api.mastery.*;
import link.botwmcs.ltsxlogica.data.persistence.mastery.PlayerMasteryData;
import link.botwmcs.ltsxlogica.mastery.rune.MasteryItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ltsxlogica")
@PrefixGameTestTemplate(false)
public final class MasteryGameTests {
    private static final ResourceLocation MINERS = ResourceLocation.parse("ltsxlogica:miners");
    private static final ResourceLocation KNIGHT = ResourceLocation.parse("ltsxlogica:knight");
    private static final ResourceLocation EFFICIENCY = ResourceLocation.parse("ltsxlogica:efficiency_i");
    private static ServerPlayer player(GameTestHelper helper) {
        return new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "mastery-test"));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    @GameTest(template = "mastery_empty", timeoutTicks = 100)
    public static void statisticsAndLifecycle(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IMasteryService service = CoreServices.get(IMasteryService.class);
        check(CoreServices.get(IMasteryRegistry.class).rune(EFFICIENCY).isPresent(), "Default rune registered");
        check(service.refresh(player).masteries().size() == 4, "All default masteries unlocked");
        player.getStats().setValue(player, Stats.BLOCK_MINED.get(Blocks.STONE), 225);
        check(service.refresh(player).masteries().get(MINERS).level() == 4, "Committed statistics drive catch-up");
        check(service.snapshot(player).masteries().get(MINERS).unlockedSlots() == 3, "One slot per upgrade");
        player.getStats().setValue(player, Stats.BLOCK_MINED.get(Blocks.STONE), 0);
        check(service.refresh(player).masteries().get(MINERS).level() == 4, "Reset does not revoke earned levels");
        ServerPlayer clone = player(helper);
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(clone, player, true));
        check(service.snapshot(clone).masteries().get(MINERS).level() == 4, "Death clone preserves mastery");
        CoreData.putPlayerTag(clone, PlayerMasteryData.KEY, new net.minecraft.nbt.CompoundTag());
        check(service.snapshot(player).masteries().get(MINERS).level() == 4, "Clone is independent");
        helper.succeed();
    }

    @GameTest(template = "mastery_empty", timeoutTicks = 100)
    public static void bindingTransactions(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        IMasteryService service = CoreServices.get(IMasteryService.class);
        IRuneService runes = CoreServices.get(IRuneService.class);
        player.getStats().setValue(player, Stats.BLOCK_MINED.get(Blocks.STONE), 100);
        player.getStats().setValue(player, Stats.ENTITY_KILLED.get(net.minecraft.world.entity.EntityType.ZOMBIE), 10);
        var state = service.refresh(player);
        ItemStack item = runes.createRune(EFFICIENCY);
        RuneInstance instance = item.get(MasteryItems.RUNE_DATA.get());
        player.getInventory().setItem(0, item);
        check(runes.bindFromInventory(player, MINERS, 0, 0, state.stateRevision()) == MasteryResult.SLOT_NOT_ACTIVATED, "Must activate first");
        check(service.activateSlot(player, KNIGHT, 0, state.stateRevision()) == MasteryResult.SUCCESS, "Knight has a slot");
        state = service.snapshot(player);
        check(runes.bindFromInventory(player, KNIGHT, 0, 0, state.stateRevision()) == MasteryResult.SERIES_MISMATCH, "Mining rune cannot bind to knight");
        check(player.getInventory().getItem(0).getCount() == 1, "Failure does not consume rune");
        check(service.activateSlot(player, MINERS, 0, state.stateRevision()) == MasteryResult.SUCCESS, "Activate miners slot");
        state = service.snapshot(player);
        check(runes.bindFromInventory(player, MINERS, 0, 0, state.stateRevision()) == MasteryResult.SUCCESS, "Bind succeeds");
        check(player.getInventory().getItem(0).isEmpty(), "Exactly one item consumed");
        check(runes.appliedRunes(player).equals(java.util.List.of(instance)), "API exposes applied instance");
        check(runes.bindFromInventory(player, MINERS, 0, 0, state.stateRevision()) == MasteryResult.STALE_REVISION, "Repeated revision cannot reapply");
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        state = service.snapshot(player);
        check(runes.unbind(player, MINERS, 0, state.stateRevision()) == MasteryResult.INVENTORY_FULL, "Full inventory keeps binding");
        check(runes.appliedRunes(player).size() == 1, "Failed unbind leaves binding intact");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        check(runes.unbind(player, MINERS, 0, state.stateRevision()) == MasteryResult.SUCCESS, "Unbind succeeds");
        check(player.getInventory().getItem(0).get(MasteryItems.RUNE_DATA.get()).equals(instance), "Original rune instance returned");
        check(runes.appliedRunes(player).isEmpty(), "Binding removed");
        helper.succeed();
    }
}
