package link.botwmcs.ltsxlogica.api.mastery;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Player methods require the server thread. Binding consumes one item, removal returns its instance. */
public interface IRuneService {
    /** Creates a registered rune item with a fresh identity. Shops/loot integrations should use this factory. */
    ItemStack createRune(ResourceLocation runeId);
    /** inventorySlot is 0..35; no inventory change occurs on a validation failure. */
    MasteryResult bindFromInventory(ServerPlayer player, ResourceLocation mastery, int slot,
                                   int inventorySlot, long expectedRevision);
    MasteryResult unbind(ServerPlayer player, ResourceLocation mastery, int slot, long expectedRevision);
    /** Valid active bindings only. This registration milestone does not execute enchantment effects. */
    List<RuneInstance> appliedRunes(ServerPlayer player);
}
