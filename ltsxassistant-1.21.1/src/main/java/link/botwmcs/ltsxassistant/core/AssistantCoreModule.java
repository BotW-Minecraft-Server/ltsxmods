package link.botwmcs.ltsxassistant.core;

import link.botwmcs.core.api.command.LtsxCommandRegistrar;
import link.botwmcs.core.api.module.CoreModuleContext;
import link.botwmcs.core.api.module.ICoreModule;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import net.minecraft.network.chat.Component;

/**
 * ltsxcore module adapter for ltsxassistant.
 */
public final class AssistantCoreModule implements ICoreModule {
    private static final int LOAD_ORDER = 300;
    private static final String LOG_PREFIX = "[ltsxassistant] ";

    @Override
    public String moduleId() {
        return LTSXAssistant.MODID;
    }

    @Override
    public int loadOrder() {
        return LOAD_ORDER;
    }

    @Override
    public void onRegister(CoreModuleContext ctx) {
        ctx.logger().info("{}Registered assistant module bridge.", LOG_PREFIX);
    }

    @Override
    public void registerLtsxCommands(LtsxCommandRegistrar registrar) {
        registrar.menu(
                "assistant",
                Component.literal("Assistant module commands"),
                Component.literal("LTSX Assistant"),
                assistant -> assistant.action(
                        "status",
                        Component.literal("Show assistant module status"),
                        context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal("LTSX Assistant is loaded through ltsxcore."),
                                    false
                            );
                            return 1;
                        }
                )
        );
    }
}
