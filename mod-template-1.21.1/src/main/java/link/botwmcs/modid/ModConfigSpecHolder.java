package link.botwmcs.modid;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfigSpecHolder {
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("General settings").push("general");
        builder.pop();
        SPEC = builder.build();
    }

    private ModConfigSpecHolder() {
    }
}
