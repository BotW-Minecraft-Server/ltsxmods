package link.botwmcs.ltsxassistant.core;

import link.botwmcs.core.api.command.LtsxCommandRegistrar;
import link.botwmcs.core.api.fizzier.contrib.IFizzyProxyRuleContributor;
import link.botwmcs.core.api.fizzier.proxy.IFizzyProxyService;
import link.botwmcs.core.api.module.CoreModuleContext;
import link.botwmcs.core.api.module.ICoreModule;
import link.botwmcs.core.service.CoreServices;
import link.botwmcs.ltsxassistant.api.account.LittleSkinAccountServiceApi;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.api.chat.AdvancedChatWindowService;
import link.botwmcs.ltsxassistant.api.soundengine.MusicCoverApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicPlaybackApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicSceneApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicServerControlApi;
import link.botwmcs.ltsxassistant.api.soundengine.MusicAlbumApi;
import link.botwmcs.ltsxassistant.net.soundengine.AssistantMusicNetworkBootstrap;
import link.botwmcs.ltsxassistant.client.AssistantMusicScreenProxyContributor;
import link.botwmcs.ltsxassistant.client.AssistantSoundOptionsMusicPlayerProxyContributor;
import link.botwmcs.ltsxassistant.client.AssistantTitleScreenProxyContributor;
import link.botwmcs.ltsxassistant.service.account.LittleSkinAccountService;
import link.botwmcs.ltsxassistant.service.chat.AssistantAdvancedChatWindowService;
import link.botwmcs.ltsxassistant.service.soundengine.AssistantMusicEngineService;
import link.botwmcs.ltsxassistant.service.soundengine.AssistantMusicServerControlService;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLEnvironment;

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
        MusicPlaybackApi playbackApi = CoreServices.registerIfAbsent(MusicPlaybackApi.class, new AssistantMusicEngineService());
        if (playbackApi instanceof MusicSceneApi sceneApi) {
            CoreServices.registerIfAbsent(MusicSceneApi.class, sceneApi);
        } else {
            CoreServices.registerIfAbsent(MusicSceneApi.class, new AssistantMusicEngineService());
        }
        if (playbackApi instanceof MusicCoverApi coverApi) {
            CoreServices.registerIfAbsent(MusicCoverApi.class, coverApi);
        }
        if (playbackApi instanceof MusicAlbumApi albumApi) {
            CoreServices.registerIfAbsent(MusicAlbumApi.class, albumApi);
        }
        CoreServices.registerIfAbsent(MusicServerControlApi.class, new AssistantMusicServerControlService());
        AssistantMusicNetworkBootstrap.bootstrap(ctx.logger());

        if (FMLEnvironment.dist.isClient()) {
            CoreServices.registerIfAbsent(LittleSkinAccountServiceApi.class, new LittleSkinAccountService());
            CoreServices.registerIfAbsent(AdvancedChatWindowService.class, new AssistantAdvancedChatWindowService());

            AssistantTitleScreenProxyContributor titleContributor = new AssistantTitleScreenProxyContributor();
            AssistantMusicScreenProxyContributor musicContributor = new AssistantMusicScreenProxyContributor();
            AssistantSoundOptionsMusicPlayerProxyContributor soundOptionsContributor = new AssistantSoundOptionsMusicPlayerProxyContributor();
            CoreServices.registerMulti(IFizzyProxyRuleContributor.class, titleContributor);
            CoreServices.registerMulti(IFizzyProxyRuleContributor.class, musicContributor);
            CoreServices.registerMulti(IFizzyProxyRuleContributor.class, soundOptionsContributor);
            CoreServices.getOptional(IFizzyProxyService.class).ifPresent(proxyService -> {
                titleContributor.contribute(proxyService);
                musicContributor.contribute(proxyService);
                soundOptionsContributor.contribute(proxyService);
                ctx.logger().info("{}Applied assistant proxy rules immediately. count={}", LOG_PREFIX, proxyService.ruleCount());
            });
            ctx.logger().info("{}Registered LittleSkin account service skeleton (LS0).", LOG_PREFIX);
            ctx.logger().info("{}Registered assistant TitleScreen proxy contributor.", LOG_PREFIX);
            ctx.logger().info("{}Registered assistant music screen proxy contributor.", LOG_PREFIX);
            ctx.logger().info("{}Registered assistant sound options music entry proxy contributor.", LOG_PREFIX);
        }
        ctx.logger().info("{}Registered assistant music engine API skeleton (M1).", LOG_PREFIX);
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
