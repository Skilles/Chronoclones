package com.skilles.chronoclones;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side tunables. MAX_RADIUS is enforced at record time and again at execute time. */
public final class ChronoclonesConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_RADIUS;
    public static final ModConfigSpec.IntValue MAX_RECORDING_TICKS;
    public static final ModConfigSpec.IntValue MAX_ACTIONS;
    public static final ModConfigSpec.IntValue MAX_ACTIONS_PER_TICK;
    public static final ModConfigSpec.BooleanValue ALLOW_PVP;
    public static final ModConfigSpec.IntValue GOGGLE_RADIUS;
    /**
     * Whether Chrono Goggles show anchors somebody else imprinted.
     */
    public static final ModConfigSpec.BooleanValue GOGGLES_SHOW_OTHERS;

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
        GOGGLE_RADIUS = b.comment("How far Chrono Goggles reveal anchors, in blocks.")
                .defineInRange("goggleRadius", 24, 4, 64);
        GOGGLES_SHOW_OTHERS = b.comment(
                        "Whether Chrono Goggles reveal anchors owned by other players.",
                        "Turning this off keeps a routine private to whoever imprinted it; leaving it",
                        "on lets anyone with goggles inspect what an anchor near them will do.")
                .define("gogglesShowOthers", true);
        b.pop();

        SPEC = b.build();
    }

    private ChronoclonesConfig() {}
}
