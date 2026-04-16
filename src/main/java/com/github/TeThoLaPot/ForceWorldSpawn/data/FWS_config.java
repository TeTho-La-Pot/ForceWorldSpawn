package com.github.TeThoLaPot.ForceWorldSpawn.data;

import net.minecraftforge.common.ForgeConfigSpec;

public class FWS_config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue FORCE_SPAWN;
    public static final ForgeConfigSpec.BooleanValue FORCE_SPAWN_LOGIN;

    static {
        BUILDER.push("Force World Spawn config");


        FORCE_SPAWN = BUILDER.comment("ForceSpawn")
                .define("Enable forced spawn points", true);
        FORCE_SPAWN_LOGIN = BUILDER.comment("ForceSpawnPoint upon Login")
                .define("Set ForceSpawnPoint upon Login",false);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
