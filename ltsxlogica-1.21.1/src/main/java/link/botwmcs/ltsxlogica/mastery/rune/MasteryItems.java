package link.botwmcs.ltsxlogica.mastery.rune;

import link.botwmcs.ltsxlogica.LTSXLogicA;
import link.botwmcs.ltsxlogica.api.mastery.RuneInstance;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MasteryItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LTSXLogicA.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LTSXLogicA.MODID);
    public static final DeferredItem<RuneItem> RUNE = ITEMS.registerItem("rune", RuneItem::new);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RuneInstance>> RUNE_DATA =
            COMPONENTS.registerComponentType("rune", builder -> builder.persistent(RuneInstance.CODEC).networkSynchronized(RuneInstance.STREAM_CODEC));
    private MasteryItems() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); COMPONENTS.register(bus); }
}
