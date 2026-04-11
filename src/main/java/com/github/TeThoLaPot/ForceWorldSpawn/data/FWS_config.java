package com.github.TeThoLaPot.ForceWorldSpawn.data;

import net.minecraftforge.common.ForgeConfigSpec;

public class FWS_config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue FORCE_SPAWN_LOGIN;

    static {
        BUILDER.push("Force World Spawn config");

        FORCE_SPAWN_LOGIN = BUILDER.comment("ForceSpawnPoint IN LOGIN")
                .define("Set ForceSpawnPoint",false);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
