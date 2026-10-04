package link.botwmcs.ltsxlogica.mastery.rune;

import java.util.List;
import link.botwmcs.ltsxlogica.api.mastery.RuneInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Like EnchantedBookItem: one non-enchantable item with a typed stored payload. */
public final class RuneItem extends Item {
    public RuneItem(Properties properties) { super(properties.stacksTo(1)); }
    @Override public boolean isEnchantable(ItemStack stack) { return false; }
    @Override public boolean isFoil(ItemStack stack) { return stack.has(MasteryItems.RUNE_DATA.get()); }
    @Override public Component getName(ItemStack stack) {
        Component name = stack.get(net.minecraft.core.component.DataComponents.ITEM_NAME);
        if (name != null) return name;
        RuneInstance rune = stack.get(MasteryItems.RUNE_DATA.get());
        return rune == null ? super.getName(stack) : Component.literal(rune.runeId().toString());
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        RuneInstance rune = stack.get(MasteryItems.RUNE_DATA.get());
        if (rune != null) lines.add(Component.translatable("rune.ltsxlogica.binding_hint"));
        super.appendHoverText(stack, context, lines, flag);
    }
}
