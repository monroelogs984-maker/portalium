package com.glennfo.portalium;

import net.minecraftforge.common.ForgeConfigSpec;

public class PortaliumConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue UNLOAD_DELAY_SECONDS;
    public static final ForgeConfigSpec.IntValue OVERWORLD_DELAY_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Portalium - dimension unload settings").push("general");

        UNLOAD_DELAY_SECONDS = builder
            .comment("Seconds after all players leave a dimension before it unloads (default 8)")
            .defineInRange("unloadDelaySeconds", 8, 1, 300);

        OVERWORLD_DELAY_MULTIPLIER = builder
            .comment("Multiplier on the unload delay for the overworld. Higher = more cautious (default 3 = 24s)")
            .defineInRange("overworldDelayMultiplier", 3, 1, 20);

        builder.pop();
        SPEC = builder.build();
    }
}
