package link.botwmcs.ltsxlogica.api.mastery;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Registration metadata only; declaring an enchantment does not execute its gameplay effect. */
public record RuneDefinition(ResourceLocation id, String name, Set<ResourceLocation> series,
                              int requiredLevel, ResourceLocation enchantment, int enchantmentLevel) {
    public RuneDefinition {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(enchantment, "enchantment");
        series = Set.copyOf(series);
        if (series.isEmpty() || name.isBlank() || name.length() > 128 || requiredLevel < 1 || requiredLevel > 100
                || enchantmentLevel < 1 || enchantmentLevel > 255) throw new IllegalArgumentException("Invalid rune definition");
    }
}
