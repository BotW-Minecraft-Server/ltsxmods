package link.botwmcs.ltsxlogica.api.mastery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** One provider/stat type and exactly one selector: all, IDs, or a registry tag. */
public record StatisticSelector(ResourceLocation provider, ResourceLocation statType,
                                boolean all, List<ResourceLocation> ids, Optional<ResourceLocation> tag) {
    public static final ResourceLocation VANILLA = ResourceLocation.fromNamespaceAndPath("ltsxlogica", "vanilla");
    public StatisticSelector {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(statType);
        ids = List.copyOf(ids);
        Objects.requireNonNull(tag);
        if ((all ? 1 : 0) + (ids.isEmpty() ? 0 : 1) + (tag.isPresent() ? 1 : 0) != 1) {
            throw new IllegalArgumentException("Specify exactly one of all, ids, or tag");
        }
    }
}
