package link.botwmcs.ltsxlogica.mastery.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import link.botwmcs.fizzy.client.util.TextRenderer;
import link.botwmcs.fizzy.ui.background.SoildColorBg;
import link.botwmcs.fizzy.ui.behind.VanillaBehind;
import link.botwmcs.fizzy.ui.core.FizzyGui;
import link.botwmcs.fizzy.ui.core.FizzyGuiBuilder;
import link.botwmcs.fizzy.ui.core.HostType;
import link.botwmcs.fizzy.ui.element.button.VanillaLikeButtonElement;
import link.botwmcs.fizzy.ui.element.component.FizzyComponentElement;
import link.botwmcs.fizzy.ui.frame.FizzyFrame;
import link.botwmcs.fizzy.ui.host.FizzyScreenHost;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Minimal Fizzy application screen: cycle mastery/slot/rune, activate, apply or remove. */
public final class MasteryScreen extends FizzyScreenHost {
    public record Selection(int mastery, int slot, int rune) {}
    private final JsonObject state;
    private final Selection selection;
    public MasteryScreen(JsonObject state, Selection selected) {
        super(build(state, normalize(state, selected)));
        this.state = state;
        this.selection = normalize(state, selected);
    }
    public Selection selection() { return selection; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void resize(Minecraft minecraft, int width, int height) { minecraft.setScreen(new MasteryScreen(state, selection)); }
    @Override public Component getNarrationMessage() { return Component.translatable("mastery.ltsxlogica.title"); }

    private static Selection normalize(JsonObject state, Selection selection) {
        if (selection == null) selection = new Selection(0, 0, 0);
        JsonArray masteries = state.getAsJsonArray("masteries");
        int mastery = Math.floorMod(selection.mastery(), Math.max(1, masteries.size()));
        int slots = masteries.isEmpty() ? 0 : masteries.get(mastery).getAsJsonObject().get("level").getAsInt() - 1;
        return new Selection(mastery, Math.floorMod(selection.slot(), Math.max(1, slots)),
                Math.floorMod(selection.rune(), Math.max(1, state.getAsJsonArray("inventory").size())));
    }
    private static void show(JsonObject state, Selection selection) { Minecraft.getInstance().setScreen(new MasteryScreen(state, selection)); }
    private static void text(FizzyGuiBuilder gui, int y, Component text) {
        gui.padByPx(12, y, 260, 18).element(new FizzyComponentElement.Builder().addText(text)
                .align(TextRenderer.Align.CENTER).autoEllipsis(true).wrap(false).shadow(true).build()).done();
    }
    private static void button(FizzyGuiBuilder gui, int x, int y, int width, Component text, Runnable action) {
        gui.padByPx(x, y, width, 20).element(VanillaLikeButtonElement.builder(ignored -> action.run()).text(text).build()).done();
    }
    private static FizzyGui build(JsonObject state, Selection selection) {
        FizzyGuiBuilder gui = FizzyGuiBuilder.start().sizeSlots(1, 1).host(HostType.SCREEN)
                .overrideSizePx(286, 218).frame(new FizzyFrame(Component.translatable("mastery.ltsxlogica.title")))
                .background(new SoildColorBg(0xEE202530)).behind(new VanillaBehind());
        JsonArray masteries = state.getAsJsonArray("masteries");
        JsonArray inventory = state.getAsJsonArray("inventory");
        if (masteries.isEmpty()) text(gui, 30, Component.translatable("mastery.ltsxlogica.empty"));
        else {
            JsonObject mastery = masteries.get(selection.mastery()).getAsJsonObject();
            ResourceLocation id = ResourceLocation.parse(mastery.get("id").getAsString());
            int level = mastery.get("level").getAsInt();
            text(gui, 30, Component.literal(mastery.get("name").getAsString() + "  Lv" + level));
            button(gui, 12, 50, 30, Component.literal("<"), () -> show(state, new Selection(selection.mastery() - 1, 0, selection.rune())));
            button(gui, 242, 50, 30, Component.literal(">"), () -> show(state, new Selection(selection.mastery() + 1, 0, selection.rune())));
            JsonArray conditions = mastery.getAsJsonArray("conditions");
            text(gui, 74, conditions.isEmpty() ? Component.translatable("mastery.ltsxlogica.no_next_stage") : Component.literal(conditions.get(0).getAsString()));
            boolean active = false;
            for (var slot : mastery.getAsJsonArray("activated")) if (slot.getAsInt() == selection.slot()) active = true;
            String binding = null;
            for (var value : mastery.getAsJsonArray("bindings")) if (value.getAsJsonObject().get("slot").getAsInt() == selection.slot()) binding = value.getAsJsonObject().get("rune").getAsString();
            String label = level < 2 ? "0 / 0" : (selection.slot() + 1) + " / " + (level - 1);
            text(gui, 51, Component.translatable("mastery.ltsxlogica.slot", label, active ? "✓" : "○"));
            button(gui, 12, 94, 62, Component.translatable("mastery.ltsxlogica.next_slot"), () -> show(state, new Selection(selection.mastery(), selection.slot() + 1, selection.rune())));
            button(gui, 78, 94, 94, Component.translatable("mastery.ltsxlogica.activate"), () -> MasteryClientAccess.request("activate", id, selection.slot(), -1));
            button(gui, 176, 94, 96, Component.translatable("mastery.ltsxlogica.remove"), () -> MasteryClientAccess.request("unbind", id, selection.slot(), -1));
            text(gui, 120, Component.literal(binding == null ? "—" : binding));
            if (!inventory.isEmpty()) {
                JsonObject rune = inventory.get(selection.rune()).getAsJsonObject();
                text(gui, 143, Component.literal(rune.get("name").getAsString() + "  Lv≥" + rune.get("required_level").getAsInt()));
                button(gui, 12, 164, 128, Component.translatable("mastery.ltsxlogica.next_rune"), () -> show(state, new Selection(selection.mastery(), selection.slot(), selection.rune() + 1)));
                button(gui, 144, 164, 128, Component.translatable("mastery.ltsxlogica.apply"), () -> MasteryClientAccess.request("bind", id, selection.slot(), rune.get("slot").getAsInt()));
            } else text(gui, 146, Component.translatable("mastery.ltsxlogica.no_runes"));
        }
        String result = state.get("result").getAsString();
        if (!result.isEmpty()) text(gui, 189, Component.translatable("mastery.ltsxlogica.result." + result));
        button(gui, 202, 4, 36, Component.translatable("mastery.ltsxlogica.refresh"),
                () -> MasteryClientAccess.request("refresh", ResourceLocation.parse("ltsxlogica:default"), -1, -1));
        button(gui, 242, 4, 30, Component.literal("×"), () -> Minecraft.getInstance().setScreen(null));
        return gui.build();
    }
}
