package link.botwmcs.core.api.fizzier.text;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public interface IFizzyTextService {
    FormattedText formatFormattedText(FormattedText text);

    Component formatComponent(Component component);

    FormattedCharSequence formatVisualOrder(Component component, FormattedCharSequence visualOrderText);

    FormattedCharSequence formatVisualOrder(String rawText);

    LtsxTextRendererBuilder renderer(Component text);

    void registerStaticEmoji(String token, ResourceLocation texture, float width, float height);

    void registerStaticInteractiveEmoji(
            String token,
            ResourceLocation texture,
            float width,
            float height,
            LtsxEmojiClickHandler clickHandler
    );

    void registerAnimatedEmoji(
            String token,
            List<ResourceLocation> textures,
            long frameMillis,
            float width,
            float height
    );

    void registerAnimatedInteractiveEmoji(
            String token,
            List<ResourceLocation> textures,
            long frameMillis,
            float width,
            float height,
            LtsxEmojiClickHandler clickHandler
    );

    void registerEmojiPack(String packId, Consumer<LtsxEmojiRegistrar> registrarConsumer);

    void unregisterEmoji(String token);

    void unregisterEmojiPack(String packId);

    Collection<String> emojiTokens();

    int registerInlineImage(String key, LtsxInlineImageSource source);

    void registerPlaceholder(LtsxPlaceholderResolver resolver);

    void unregisterPlaceholder(String id);

    Collection<LtsxPlaceholderResolver> placeholders();

    void ensureDefaultPlaceholders();

    interface LtsxTextRendererBuilder {
        LtsxTextRendererBuilder text(Component text);

        LtsxTextRendererBuilder lines(List<Component> lines);

        LtsxTextRendererBuilder singleLine();

        LtsxTextRendererBuilder multiLine();

        LtsxTextRendererBuilder wrap(boolean wrap);

        LtsxTextRendererBuilder align(LtsxTextAlign align);

        LtsxTextRendererBuilder textScale(float textScale);

        LtsxTextRendererBuilder lineSpacing(float lineSpacing);

        LtsxTextRendererBuilder letterSpacing(float letterSpacing);

        LtsxTextRendererBuilder color(int colorArgb);

        LtsxTextRendererBuilder shadow(boolean shadow);

        LtsxTextRendererBuilder clipToPad(boolean clipToPad);

        LtsxTextRendererBuilder allowOverflow(boolean allowOverflow);

        LtsxTextRendererBuilder bold(boolean bold);

        LtsxTextRendererBuilder underline(boolean underline);

        LtsxTextRendererBuilder strikethrough(boolean strikethrough);

        LtsxTextRendererBuilder gradient(int... colors);

        LtsxTextRendererBuilder rainbow();

        LtsxTextRendererBuilder rainbow(float speed);

        LtsxTextRendererBuilder floating(boolean enabled, float amplitude, float speed);

        LtsxTextRendererBuilder floatingPixelated(boolean pixelated);

        LtsxTextRenderer build();
    }

    interface LtsxTextRenderer {
        LtsxTextMetrics measure(Font font, int maxWidthPx);

        void render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick);
    }

    record LtsxTextMetrics(List<FormattedCharSequence> lines, int maxWidth, float totalHeight) {
    }

    enum LtsxTextAlign {
        LEFT,
        CENTER,
        RIGHT
    }

    interface LtsxInlineImageSource {
        ResourceLocation texture(long nowMillis);

        float width();

        float height();
    }

    interface LtsxEmojiClickHandler {
        void onClick(LtsxEmojiClickContext context);
    }

    record LtsxEmojiClickContext(Minecraft minecraft, ChatScreen chatScreen, String token, Style style) {
    }

    interface LtsxEmojiRegistrar {
        LtsxEmojiRegistrar registerStatic(String token, ResourceLocation texture, float width, float height);

        LtsxEmojiRegistrar registerStaticInteractive(
                String token,
                ResourceLocation texture,
                float width,
                float height,
                LtsxEmojiClickHandler clickHandler
        );

        LtsxEmojiRegistrar registerAnimated(
                String token,
                List<ResourceLocation> textures,
                long frameMillis,
                float width,
                float height
        );

        LtsxEmojiRegistrar registerAnimatedInteractive(
                String token,
                List<ResourceLocation> textures,
                long frameMillis,
                float width,
                float height,
                LtsxEmojiClickHandler clickHandler
        );
    }

    record LtsxPlaceholderContext(Style baseStyle, String id, String payload, String rawToken) {
    }

    sealed interface LtsxPlaceholderToken permits LtsxTextPlaceholderToken, LtsxImagePlaceholderToken {
    }

    record LtsxTextPlaceholderToken(Component component) implements LtsxPlaceholderToken {
    }

    record LtsxImagePlaceholderToken(String key, LtsxInlineImageSource source) implements LtsxPlaceholderToken {
    }

    interface LtsxPlaceholderResolver {
        String id();

        Optional<LtsxPlaceholderToken> resolve(String payload, LtsxPlaceholderContext context);
    }
}
