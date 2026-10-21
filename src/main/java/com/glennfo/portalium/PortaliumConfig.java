package com.glennfo.portalium;

import net.neoforged.neoforge.common.ModConfigSpec;

public class PortaliumConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue UNLOAD_DELAY_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Portalium - dimension unload settings").push("general");

        UNLOAD_DELAY_SECONDS = builder
            .comment("Seconds after all players leave a dimension before it goes dormant (default 8). The overworld never goes dormant.")
            .defineInRange("unloadDelaySeconds", 8, 1, 300);

        builder.pop();
        SPEC = builder.build();
    }
}
