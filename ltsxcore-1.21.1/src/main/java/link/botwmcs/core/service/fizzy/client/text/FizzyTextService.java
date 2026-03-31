package link.botwmcs.core.service.fizzy.client.text;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import link.botwmcs.core.api.fizzier.text.IFizzyTextService;
import link.botwmcs.fizzy.client.formatting.FizzyComponentService;
import link.botwmcs.fizzy.client.formatting.emoji.EmojiClickHandler;
import link.botwmcs.fizzy.client.formatting.emoji.EmojiPack;
import link.botwmcs.fizzy.client.formatting.emoji.EmojiRegistry;
import link.botwmcs.fizzy.client.formatting.inline.AnimatedInlineImageSource;
import link.botwmcs.fizzy.client.formatting.inline.FizzyInlineImageRegistry;
import link.botwmcs.fizzy.client.formatting.inline.InlineImageSource;
import link.botwmcs.fizzy.client.formatting.inline.StaticInlineImageSource;
import link.botwmcs.fizzy.client.formatting.placeholder.PlaceholderContext;
import link.botwmcs.fizzy.client.formatting.placeholder.PlaceholderImageToken;
import link.botwmcs.fizzy.client.formatting.placeholder.PlaceholderRegistry;
import link.botwmcs.fizzy.client.formatting.placeholder.PlaceholderResolver;
import link.botwmcs.fizzy.client.formatting.placeholder.PlaceholderTextToken;
import link.botwmcs.fizzy.client.formatting.placeholder.PlaceholderToken;
import link.botwmcs.fizzy.client.util.TextRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public final class FizzyTextService implements IFizzyTextService {
    @Override
    public FormattedText formatFormattedText(FormattedText text) {
        return FizzyComponentService.formatFormattedText(Objects.requireNonNull(text, "text"));
    }

    @Override
    public Component formatComponent(Component component) {
        return FizzyComponentService.formatComponent(Objects.requireNonNull(component, "component"));
    }

    @Override
    public FormattedCharSequence formatVisualOrder(Component component, FormattedCharSequence visualOrderText) {
        return FizzyComponentService.formatVisualOrder(
                Objects.requireNonNull(component, "component"),
                Objects.requireNonNull(visualOrderText, "visualOrderText")
        );
    }

    @Override
    public FormattedCharSequence formatVisualOrder(String rawText) {
        return FizzyComponentService.formatVisualOrder(Objects.requireNonNull(rawText, "rawText"));
    }

    @Override
    public LtsxTextRendererBuilder renderer(Component text) {
        return new TextRendererBuilderBridge(TextRenderer.builder(Objects.requireNonNull(text, "text")));
    }

    @Override
    public void registerStaticEmoji(String token, ResourceLocation texture, float width, float height) {
        EmojiRegistry.registerStatic(token, texture, width, height);
    }

    @Override
    public void registerStaticInteractiveEmoji(
            String token,
            ResourceLocation texture,
            float width,
            float height,
            LtsxEmojiClickHandler clickHandler
    ) {
        EmojiRegistry.registerStaticInteractive(token, texture, width, height, adaptClickHandler(clickHandler));
    }

    @Override
    public void registerAnimatedEmoji(
            String token,
            List<ResourceLocation> textures,
            long frameMillis,
            float width,
            float height
    ) {
        EmojiRegistry.registerAnimated(token, textures, frameMillis, width, height);
    }

    @Override
    public void registerAnimatedInteractiveEmoji(
            String token,
            List<ResourceLocation> textures,
            long frameMillis,
            float width,
            float height,
            LtsxEmojiClickHandler clickHandler
    ) {
        EmojiRegistry.registerAnimatedInteractive(token, textures, frameMillis, width, height, adaptClickHandler(clickHandler));
    }

    @Override
    public void registerEmojiPack(String packId, Consumer<LtsxEmojiRegistrar> registrarConsumer) {
        Objects.requireNonNull(packId, "packId");
        Objects.requireNonNull(registrarConsumer, "registrarConsumer");
        EmojiRegistry.registerPack(packId, registrar -> registrarConsumer.accept(new EmojiRegistrarBridge(registrar)));
    }

    @Override
    public void unregisterEmoji(String token) {
        EmojiRegistry.unregister(Objects.requireNonNull(token, "token"));
    }

    @Override
    public void unregisterEmojiPack(String packId) {
        EmojiRegistry.unregisterPack(Objects.requireNonNull(packId, "packId"));
    }

    @Override
    public Collection<String> emojiTokens() {
        return List.copyOf(EmojiRegistry.tokens());
    }

    @Override
    public int registerInlineImage(String key, LtsxInlineImageSource source) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        return FizzyInlineImageRegistry.intern(key, adaptInlineSource(source));
    }

    @Override
    public void registerPlaceholder(LtsxPlaceholderResolver resolver) {
        PlaceholderRegistry.register(adaptPlaceholderResolver(Objects.requireNonNull(resolver, "resolver")));
    }

    @Override
    public void unregisterPlaceholder(String id) {
        PlaceholderRegistry.unregister(Objects.requireNonNull(id, "id"));
    }

    @Override
    public Collection<LtsxPlaceholderResolver> placeholders() {
        return PlaceholderRegistry.allResolvers().stream()
                .map(PlaceholderResolverBridge::new)
                .map(LtsxPlaceholderResolver.class::cast)
                .toList();
    }

    @Override
    public void ensureDefaultPlaceholders() {
        PlaceholderRegistry.ensureDefaults();
    }

    private static EmojiClickHandler adaptClickHandler(LtsxEmojiClickHandler clickHandler) {
        Objects.requireNonNull(clickHandler, "clickHandler");
        return context -> clickHandler.onClick(new LtsxEmojiClickContext(
                context.minecraft(),
                context.chatScreen(),
                context.token(),
                context.style()
        ));
    }

    private static InlineImageSource adaptInlineSource(LtsxInlineImageSource source) {
        return new InlineImageSource() {
            @Override
            public ResourceLocation texture(long nowMillis) {
                return source.texture(nowMillis);
            }

            @Override
            public float width() {
                return source.width();
            }

            @Override
            public float height() {
                return source.height();
            }
        };
    }

    private static PlaceholderResolver adaptPlaceholderResolver(LtsxPlaceholderResolver resolver) {
        return new PlaceholderResolver() {
            @Override
            public String id() {
                return resolver.id();
            }

            @Override
            public Optional<PlaceholderToken> resolve(String payload, PlaceholderContext context) {
                return resolver.resolve(payload, new LtsxPlaceholderContext(
                                context.baseStyle(),
                                context.id(),
                                context.payload(),
                                context.rawToken()))
                        .map(FizzyTextService::adaptPlaceholderToken);
            }
        };
    }

    private static PlaceholderToken adaptPlaceholderToken(LtsxPlaceholderToken token) {
        if (token instanceof LtsxTextPlaceholderToken textToken) {
            return new PlaceholderTextToken(textToken.component());
        }
        if (token instanceof LtsxImagePlaceholderToken imageToken) {
            return new PlaceholderImageToken(imageToken.key(), adaptInlineSource(imageToken.source()));
        }
        throw new IllegalArgumentException("Unsupported placeholder token: " + token.getClass().getName());
    }

    private static LtsxTextAlign mapAlign(link.botwmcs.fizzy.client.util.TextRenderer.Align align) {
        return switch (align) {
            case LEFT -> LtsxTextAlign.LEFT;
            case CENTER -> LtsxTextAlign.CENTER;
            case RIGHT -> LtsxTextAlign.RIGHT;
        };
    }

    private static link.botwmcs.fizzy.client.util.TextRenderer.Align mapAlign(LtsxTextAlign align) {
        return switch (align) {
            case LEFT -> link.botwmcs.fizzy.client.util.TextRenderer.Align.LEFT;
            case CENTER -> link.botwmcs.fizzy.client.util.TextRenderer.Align.CENTER;
            case RIGHT -> link.botwmcs.fizzy.client.util.TextRenderer.Align.RIGHT;
        };
    }

    private static final class TextRendererBuilderBridge implements LtsxTextRendererBuilder {
        private final TextRenderer.Builder<?> delegate;

        private TextRendererBuilderBridge(TextRenderer.Builder<?> delegate) {
            this.delegate = delegate;
        }

        @Override
        public LtsxTextRendererBuilder text(Component text) {
            delegate.text(text);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder lines(List<Component> lines) {
            delegate.lines(lines);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder singleLine() {
            delegate.singleLine();
            return this;
        }

        @Override
        public LtsxTextRendererBuilder multiLine() {
            delegate.multiLine();
            return this;
        }

        @Override
        public LtsxTextRendererBuilder wrap(boolean wrap) {
            delegate.wrap(wrap);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder align(LtsxTextAlign align) {
            delegate.align(mapAlign(align));
            return this;
        }

        @Override
        public LtsxTextRendererBuilder textScale(float textScale) {
            delegate.textScale(textScale);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder lineSpacing(float lineSpacing) {
            delegate.lineSpacing(lineSpacing);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder letterSpacing(float letterSpacing) {
            delegate.letterSpacing(letterSpacing);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder color(int colorArgb) {
            delegate.color(colorArgb);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder shadow(boolean shadow) {
            delegate.shadow(shadow);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder clipToPad(boolean clipToPad) {
            delegate.clipToPad(clipToPad);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder allowOverflow(boolean allowOverflow) {
            delegate.allowOverflow(allowOverflow);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder bold(boolean bold) {
            delegate.bold(bold);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder underline(boolean underline) {
            delegate.underline(underline);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder strikethrough(boolean strikethrough) {
            delegate.strikethrough(strikethrough);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder gradient(int... colors) {
            delegate.gradient(colors);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder rainbow() {
            delegate.rainbow();
            return this;
        }

        @Override
        public LtsxTextRendererBuilder rainbow(float speed) {
            delegate.rainbow(speed);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder floating(boolean enabled, float amplitude, float speed) {
            delegate.floating(enabled, amplitude, speed);
            return this;
        }

        @Override
        public LtsxTextRendererBuilder floatingPixelated(boolean pixelated) {
            delegate.floatingPixelated(pixelated);
            return this;
        }

        @Override
        public LtsxTextRenderer build() {
            return new TextRendererBridge(delegate.buildRenderer());
        }
    }

    private record TextRendererBridge(TextRenderer delegate) implements LtsxTextRenderer {
        @Override
        public LtsxTextMetrics measure(Font font, int maxWidthPx) {
            TextRenderer.TextMetrics metrics = delegate.measure(font, maxWidthPx);
            return new LtsxTextMetrics(metrics.lines(), metrics.maxWidth(), metrics.totalHeight());
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick) {
            delegate.render(graphics, x, y, width, height, partialTick);
        }
    }

    private record EmojiRegistrarBridge(EmojiPack.Registrar delegate) implements LtsxEmojiRegistrar {
        @Override
        public LtsxEmojiRegistrar registerStatic(String token, ResourceLocation texture, float width, float height) {
            delegate.token(token, new StaticInlineImageSource(texture, width, height));
            return this;
        }

        @Override
        public LtsxEmojiRegistrar registerStaticInteractive(
                String token,
                ResourceLocation texture,
                float width,
                float height,
                LtsxEmojiClickHandler clickHandler
        ) {
            delegate.tokenInteractive(token, new StaticInlineImageSource(texture, width, height), adaptClickHandler(clickHandler));
            return this;
        }

        @Override
        public LtsxEmojiRegistrar registerAnimated(
                String token,
                List<ResourceLocation> textures,
                long frameMillis,
                float width,
                float height
        ) {
            delegate.token(token, new AnimatedInlineImageSource(textures, frameMillis, width, height));
            return this;
        }

        @Override
        public LtsxEmojiRegistrar registerAnimatedInteractive(
                String token,
                List<ResourceLocation> textures,
                long frameMillis,
                float width,
                float height,
                LtsxEmojiClickHandler clickHandler
        ) {
            delegate.tokenInteractive(
                    token,
                    new AnimatedInlineImageSource(textures, frameMillis, width, height),
                    adaptClickHandler(clickHandler)
            );
            return this;
        }
    }

    private record PlaceholderResolverBridge(PlaceholderResolver delegate) implements LtsxPlaceholderResolver {
        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public Optional<LtsxPlaceholderToken> resolve(String payload, LtsxPlaceholderContext context) {
            return delegate.resolve(payload, new PlaceholderContext(
                            context.baseStyle(),
                            context.id(),
                            context.payload(),
                            context.rawToken()))
                    .map(token -> {
                        if (token instanceof PlaceholderTextToken textToken) {
                            return new LtsxTextPlaceholderToken(textToken.component());
                        }
                        if (token instanceof PlaceholderImageToken imageToken) {
                            return new LtsxImagePlaceholderToken(imageToken.key(), new LtsxInlineImageSource() {
                                @Override
                                public ResourceLocation texture(long nowMillis) {
                                    return imageToken.source().texture(nowMillis);
                                }

                                @Override
                                public float width() {
                                    return imageToken.source().width();
                                }

                                @Override
                                public float height() {
                                    return imageToken.source().height();
                                }
                            });
                        }
                        throw new IllegalArgumentException("Unsupported placeholder token: " + token.getClass().getName());
                    });
        }
    }
}
