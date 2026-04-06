package link.botwmcs.ltsxassistant.service.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import link.botwmcs.core.api.fizzier.contrib.IFizzyProxyRuleContributor;
import link.botwmcs.core.api.fizzier.proxy.IFizzyProxyService;
import link.botwmcs.fizzy.client.elements.VanillaLikeAbstractButton;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.proxy.api.KernelAttachSpec;
import link.botwmcs.fizzy.proxy.api.KernelUiSpec;
import link.botwmcs.fizzy.proxy.api.TooltipPolicy;
import link.botwmcs.fizzy.proxy.rule.ProxyBuildContext;
import link.botwmcs.fizzy.proxy.rule.ProxyRule;
import link.botwmcs.fizzy.proxy.runtime.ScreenProxyRuntime;
import link.botwmcs.fizzy.ui.element.button.VanillaLikeButtonElement;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.fizzy.ui.element.icon.FizzyIcon;
import link.botwmcs.fizzy.ui.pad.PixelPadSpec;
import link.botwmcs.ltsxassistant.LTSXAssistant;
import link.botwmcs.ltsxassistant.service.client.elements.PlayerEntityElements;
import link.botwmcs.ltsxassistant.service.client.screen.SkinWorkbenchScreen;
import link.botwmcs.ltsxassistant.service.client.utils.PlayerHeadRenderUtils;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

public final class AssistantTitleScreenProxyContributor implements IFizzyProxyRuleContributor {
    private static final ResourceLocation RULE_ID =
            ResourceLocation.fromNamespaceAndPath(LTSXAssistant.MODID, "proxy/title_right_icon_buttons");
    private static final int COMPACT_BUTTON_SIZE_PX = 20;
    private static final int WIDE_BUTTON_WIDTH_PX = 100;
    private static final int WIDE_BUTTON_HEIGHT_PX = 20;
    private static final int WIDE_LAYOUT_MIN_GAP_PX = 140;
    private static final int BUTTON_ROW_STEP_PX = 24;
    private static final int RIGHT_MARGIN_PX = 8;
    private static final int TOP_BASE_OFFSET_PX = 48;
    private static final int PLAYER_LEFT_MARGIN_PX = 8;
    private static final int PLAYER_GAP_FROM_MENU_PX = 16;
    private static final int PLAYER_MIN_SIZE_PX = 64;
    private static final int PLAYER_MAX_SIZE_PX = 140;
    private static final int PLAYER_BOTTOM_MARGIN_PX = 8;
    private static final int PLAYER_PROFILE_BUTTON_WIDTH_PX = 80;
    private static final int PLAYER_PROFILE_BUTTON_HEIGHT_PX = 20;
    private static final int PLAYER_PROFILE_BUTTON_GAP_PX = 6;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private static final List<IconButtonSpec> ICON_BUTTONS = List.of(
            new IconButtonSpec("cosmetics", "Cosmetics", FizzyIcon.COSMETICS),
            new IconButtonSpec("friends", "Friends", FizzyIcon.FRIENDS),
            new IconButtonSpec("mc_folder", "Folder", FizzyIcon.MC_FOLDER),
            new IconButtonSpec("fullscreen", "Fullscreen", FizzyIcon.FULLSCREEN),
            new IconButtonSpec("pictures", "Pictures", FizzyIcon.PICTURES)
    );

    @Override
    public void contribute(IFizzyProxyService proxyService) {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        var registry = ScreenProxyRuntime.instance().ruleRegistry();
        registry.unregister(RULE_ID);
        registry.register(new TitleScreenRightIconButtonsRule());
    }

    private static final class TitleScreenRightIconButtonsRule implements ProxyRule {
        @Override
        public ResourceLocation id() {
            return RULE_ID;
        }

        @Override
        public int priority() {
            return 300;
        }

        @Override
        public boolean matches(ProxyBuildContext context) {
            return context.screen() instanceof TitleScreen;
        }

        @Override
        public KernelAttachSpec build(ProxyBuildContext context) {
            TitleScreen titleScreen = (TitleScreen) context.screen();
            Anchor anchor = resolveSingleplayerAnchor(titleScreen);

            int rowStepPx = anchor.rowStepPx();
            int rootWidth = context.geometry().rootWidth();

            int compactButtonX = Math.max(RIGHT_MARGIN_PX, rootWidth - RIGHT_MARGIN_PX - COMPACT_BUTTON_SIZE_PX);
            int wideButtonX = Math.max(RIGHT_MARGIN_PX, rootWidth - RIGHT_MARGIN_PX - WIDE_BUTTON_WIDTH_PX);
            boolean useWideLayout = wideButtonX - anchor.singleplayerRightX() >= WIDE_LAYOUT_MIN_GAP_PX;

            ButtonLayout layout = useWideLayout ? ButtonLayout.WIDE : ButtonLayout.COMPACT;
            int buttonX = useWideLayout ? wideButtonX : compactButtonX;
            int baseY = anchor.baseY();
            int rootHeight = context.geometry().rootHeight();

            KernelUiSpec.Builder uiBuilder = KernelUiSpec.builder();
            PlayerPadLayout playerPadLayout = resolvePlayerPadLayout(anchor, baseY, rowStepPx, layout, rootHeight);
            addPlayerEntityPad(uiBuilder, playerPadLayout);
            addPlayerProfileButtonPad(uiBuilder, playerPadLayout);
            for (int i = 0; i < ICON_BUTTONS.size(); i++) {
                IconButtonSpec spec = ICON_BUTTONS.get(i);
                int buttonY = baseY + i * rowStepPx;
                uiBuilder.addPad(new PixelPadSpec(
                        buttonX,
                        buttonY,
                        layout.widthPx(),
                        layout.heightPx(),
                        List.of(createButton(spec, layout))
                ));
            }

            return new KernelAttachSpec(uiBuilder.build(), null, TooltipPolicy.BOTH, null);
        }

        private static PlayerPadLayout resolvePlayerPadLayout(
                Anchor anchor,
                int baseY,
                int rowStepPx,
                ButtonLayout layout,
                int rootHeight
        ) {
            int desiredSize = Math.max(
                    PLAYER_MIN_SIZE_PX,
                    Math.min(
                            PLAYER_MAX_SIZE_PX,
                            rowStepPx * (ICON_BUTTONS.size() - 1) + layout.heightPx()
                    )
            );
            int availableLeftWidth = anchor.singleplayerLeftX() - PLAYER_LEFT_MARGIN_PX - PLAYER_GAP_FROM_MENU_PX;
            int playerSizePx = Math.max(40, Math.min(desiredSize, availableLeftWidth));
            int playerX = Math.max(PLAYER_LEFT_MARGIN_PX, anchor.singleplayerLeftX() - PLAYER_GAP_FROM_MENU_PX - playerSizePx);
            int maxTopY = Math.max(0, rootHeight - PLAYER_BOTTOM_MARGIN_PX - playerSizePx);
            int playerY = Math.max(0, Math.min(baseY, maxTopY));
            return new PlayerPadLayout(playerX, playerY, playerSizePx);
        }

        private static void addPlayerEntityPad(KernelUiSpec.Builder uiBuilder, PlayerPadLayout playerPadLayout) {
            uiBuilder.addPad(new PixelPadSpec(
                    playerPadLayout.x(),
                    playerPadLayout.y(),
                    playerPadLayout.sizePx(),
                    playerPadLayout.sizePx(),
                    List.of(new PlayerEntityElements())
            ));
        }

        private static void addPlayerProfileButtonPad(KernelUiSpec.Builder uiBuilder, PlayerPadLayout playerPadLayout) {
            int buttonX = Math.max(
                    PLAYER_LEFT_MARGIN_PX,
                    playerPadLayout.x() + (playerPadLayout.sizePx() - PLAYER_PROFILE_BUTTON_WIDTH_PX) / 2
            );
            int buttonY = Math.max(
                    0,
                    playerPadLayout.y() - PLAYER_PROFILE_BUTTON_HEIGHT_PX - PLAYER_PROFILE_BUTTON_GAP_PX
            );
            uiBuilder.addPad(new PixelPadSpec(
                    buttonX,
                    buttonY,
                    PLAYER_PROFILE_BUTTON_WIDTH_PX,
                    PLAYER_PROFILE_BUTTON_HEIGHT_PX,
                    List.of(createPlayerProfileButton())
            ));
        }

        private static VanillaLikeButtonElement createPlayerProfileButton() {
            String username = PlayerHeadRenderUtils.currentUsername();
            ResourceLocation headTexture = PlayerHeadRenderUtils.currentPlayerHeadTexture(8);
            VanillaLikeButtonElement.Builder builder = VanillaLikeButtonElement.builder(
                            button -> onTodoPressed("player_profile")
                    )
                    .colorTheme(VanillaLikeAbstractButton.ColorTheme.GREEN)
                    .layout(VanillaLikeButtonElement.ContentLayout.ICON_LEFT_TEXT_RIGHT)
                    .text(createProfileButtonText(username))
                    .iconSizePx(8)
                    .contentGapPx(4)
                    .contentPaddingPx(6, 6)
                    .tooltip(Component.literal("TODO: " + username))
                    .narration(Component.literal(username));
            if (headTexture != null) {
                builder.icon(headTexture, true, true);
            }
            return builder.build();
        }

        private static FizzyComponentElement createProfileButtonText(String username) {
            return new FizzyComponentElement.Builder()
                    .addText(Component.literal(username))
                    .wrap(false)
                    .align(TextRenderer.Align.LEFT)
                    .shadow(true)
                    .autoEllipsis(true)
                    .build();
        }

        private static Anchor resolveSingleplayerAnchor(TitleScreen screen) {
            Button singleplayerButton = null;
            Button multiplayerButton = null;
            for (var child : screen.children()) {
                if (!(child instanceof Button button)) {
                    continue;
                }
                String key = translatableKey(button);
                if ("menu.singleplayer".equals(key)) {
                    singleplayerButton = button;
                } else if ("menu.multiplayer".equals(key)) {
                    multiplayerButton = button;
                }
            }

            if (singleplayerButton != null) {
                int rowStepPx = resolveRowStep(singleplayerButton, multiplayerButton);
                return new Anchor(singleplayerButton.getY(), singleplayerButton.getX(), singleplayerButton.getRight(), rowStepPx);
            }
            return new Anchor(
                    screen.height / 4 + TOP_BASE_OFFSET_PX,
                    screen.width / 2 - 100,
                    screen.width / 2 + 100,
                    BUTTON_ROW_STEP_PX
            );
        }

        private static int resolveRowStep(Button singleplayerButton, Button multiplayerButton) {
            if (multiplayerButton != null) {
                int delta = multiplayerButton.getY() - singleplayerButton.getY();
                if (delta > 0) {
                    return delta;
                }
            }
            return BUTTON_ROW_STEP_PX;
        }

        private static String translatableKey(Button button) {
            if (button.getMessage().getContents() instanceof TranslatableContents translatable) {
                return translatable.getKey();
            }
            return "";
        }

        private static VanillaLikeButtonElement createButton(IconButtonSpec spec, ButtonLayout layout) {
            if (layout == ButtonLayout.WIDE) {
                return createWideButton(spec);
            }
            return createCompactButton(spec);
        }

        private static VanillaLikeButtonElement createCompactButton(IconButtonSpec spec) {
            return VanillaLikeButtonElement.builder(button -> onTodoPressed(spec.actionName()))
                    .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                    .icon(spec.icon(), false, false)
                    .iconSizePx(14)
                    .contentPaddingPx(0, 0)
                    .tooltip(Component.literal("TODO: " + spec.actionName()))
                    .narration(Component.literal(spec.actionName()))
                    .build();
        }

        private static VanillaLikeButtonElement createWideButton(IconButtonSpec spec) {
            return VanillaLikeButtonElement.builder(button -> onTodoPressed(spec.actionName()))
                    .colorTheme(VanillaLikeAbstractButton.ColorTheme.GRAY)
                    .text(createButtonText(spec))
                    .icon(spec.icon(), false, false)
                    .layout(VanillaLikeButtonElement.ContentLayout.TEXT_LEFT_ICON_RIGHT)
                    .iconSizePx(14)
                    .contentGapPx(4)
                    .contentPaddingPx(6, 6)
                    .tooltip(Component.literal("TODO: " + spec.actionName()))
                    .narration(Component.literal(spec.label()))
                    .build();
        }

        private static FizzyComponentElement createButtonText(IconButtonSpec spec) {
            return new FizzyComponentElement.Builder()
                    .addText(Component.literal(spec.label()))
                    .shadow(true)
                    .wrap(false)
                    .autoEllipsis(true)
                    .build();
        }
    }

    private static void onTodoPressed(String actionName) {
        if ("cosmetics".equals(actionName)) {
            Minecraft.getInstance().setScreen(new SkinWorkbenchScreen());
            return;
        }
        LTSXAssistant.LOGGER.info("[ltsxassistant] TODO action button pressed: {}", actionName);
    }

    private record IconButtonSpec(String actionName, String label, FizzyIcon icon) {
    }

    private record Anchor(int baseY, int singleplayerLeftX, int singleplayerRightX, int rowStepPx) {
    }

    private record PlayerPadLayout(int x, int y, int sizePx) {
    }

    private enum ButtonLayout {
        COMPACT(COMPACT_BUTTON_SIZE_PX, COMPACT_BUTTON_SIZE_PX),
        WIDE(WIDE_BUTTON_WIDTH_PX, WIDE_BUTTON_HEIGHT_PX);

        private final int widthPx;
        private final int heightPx;

        ButtonLayout(int widthPx, int heightPx) {
            this.widthPx = widthPx;
            this.heightPx = heightPx;
        }

        public int widthPx() {
            return widthPx;
        }

        public int heightPx() {
            return heightPx;
        }
    }
}
