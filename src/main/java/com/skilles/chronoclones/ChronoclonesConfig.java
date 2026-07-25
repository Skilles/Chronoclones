package com.skilles.chronoclones;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side tunables. Anything that affects world mutation lives here so a server owner can
 * clamp it ("enforce MAX_RADIUS at both record time and execute time").
 */
public final class ChronoclonesConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_RADIUS;
    public static final ModConfigSpec.IntValue MAX_RECORDING_TICKS;
    public static final ModConfigSpec.IntValue MAX_ACTIONS;
    public static final ModConfigSpec.IntValue MAX_ACTIONS_PER_TICK;
    public static final ModConfigSpec.BooleanValue ALLOW_PVP;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("limits");
        MAX_RADIUS = b.comment("Maximum distance in blocks a clone may act from its anchor.")
                .defineInRange("maxRadius", 16, 1, 64);
        MAX_RECORDING_TICKS = b.comment("Base recording length cap in ticks (20 ticks = 1 second).")
                .defineInRange("maxRecordingTicks", 600, 20, 12000);
        MAX_ACTIONS = b.comment("Base cap on recorded actions per recording.")
                .defineInRange("maxActions", 128, 1, 4096);
        MAX_ACTIONS_PER_TICK = b.comment("Global per-level budget of clone actions executed per tick.")
                .defineInRange("maxActionsPerTick", 64, 1, 1024);
        b.pop();

        b.push("gameplay");
        ALLOW_PVP = b.comment("If false, clones never target players.")
                .define("allowPvp", false);
        b.pop();

        SPEC = b.build();
    }

    private ChronoclonesConfig() {}
}
