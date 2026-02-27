package link.botwmcs.ltsxlogica.heat;

import link.botwmcs.ltsxlogica.LTSXLogicA;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags used by the heat system.
 */
public final class HeatBlockTags {
    public static final TagKey<Block> BURNABLE = tag("burnable");
    public static final TagKey<Block> IGNITABLE = tag("ignitable");
    public static final TagKey<Block> PCM = tag("pcm");
    public static final TagKey<Block> THERMAL_WOOD = tag("thermal_wood");
    public static final TagKey<Block> THERMAL_LEAVES = tag("thermal_leaves");
    public static final TagKey<Block> THERMAL_ICE = tag("thermal_ice");
    public static final TagKey<Block> THERMAL_STONE = tag("thermal_stone");

    private HeatBlockTags() {
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(LTSXLogicA.MODID, path));
    }
}
