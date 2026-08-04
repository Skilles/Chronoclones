package com.skilles.chronoclones;

//? if neoforge {
import net.neoforged.neoforge.common.ModConfigSpec;
//?}

/**
 * The mod's limits and toggles, read through the static accessors below. The backing store is
 * per-loader: NeoForge keeps a SERVER-type ModConfigSpec (per-world file, synced, config screen);
 * a Fabric build reads a plain JSON file with the same keys and ranges.
 */
public final class ChronoclonesConfig {

    //? if neoforge {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_RADIUS;
    public static final ModConfigSpec.IntValue MAX_RECORDING_TICKS;
    public static final ModConfigSpec.IntValue MAX_ACTIONS;
    public static final ModConfigSpec.IntValue MAX_CONTAINER_STEPS;
    public static final ModConfigSpec.IntValue MAX_RECORDING_BYTES;
    public static final ModConfigSpec.IntValue MAX_ACTIONS_PER_TICK;
    public static final ModConfigSpec.IntValue MAX_ACTION_TICKS;
    public static final ModConfigSpec.BooleanValue ALLOW_PVP;
    public static final ModConfigSpec.IntValue GOGGLE_RADIUS;
    public static final ModConfigSpec.BooleanValue GOGGLES_SHOW_OTHERS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("limits");
        MAX_RADIUS = b.comment("Maximum distance in blocks a clone may act from its anchor.")
                .defineInRange("maxRadius", 24, 1, 64);
        MAX_RECORDING_TICKS = b.comment("Base recording length cap in ticks (20 ticks = 1 second).")
                .defineInRange("maxRecordingTicks", 1200, 20, 12000);
        MAX_ACTIONS = b.comment("Base cap on recorded actions per recording.")
                .defineInRange("maxActions", 256, 1, 4096);
        MAX_CONTAINER_STEPS = b.comment(
                        "Cap on clicks recorded inside one container session.",
                        "maxActions counts a whole session as one action, so without this a player",
                        "clicking in one open chest could grow a single action without limit.")
                .defineInRange("maxContainerSteps", 256, 8, 4096);
        MAX_RECORDING_BYTES = b.comment(
                        "Cap on the encoded size of one recording, in bytes.")
                .defineInRange("maxRecordingBytes", 262_144, 4_096, 8_388_608);
        MAX_ACTIONS_PER_TICK = b.comment("Global per-level budget of clone actions executed per tick.")
                .defineInRange("maxActionsPerTick", 128, 1, 1024);
        MAX_ACTION_TICKS = b.comment(
                        "How long one action may hold a clone before it gives up, in ticks.",
                        "An attack told to finish a kill waits here; a target that cannot die "
                                + "would otherwise stall the routine forever.")
                .defineInRange("maxActionTicks", 160, 20, 1200);
        b.pop();

        b.push("gameplay");
        ALLOW_PVP = b.comment("If false, clones never target players.")
                .define("allowPvp", false);
        GOGGLE_RADIUS = b.comment("How far Chrono Goggles reveal anchors, in blocks.")
                .defineInRange("goggleRadius", 24, 4, 64);
        GOGGLES_SHOW_OTHERS = b.comment(
                        "Whether Chrono Goggles reveal anchors owned by other players.")
                .define("gogglesShowOthers", true);
        b.pop();

        SPEC = b.build();
    }

    public static int maxContainerSteps() {
        return orDefault(MAX_CONTAINER_STEPS, 256);
    }

    public static int maxRecordingTicks() {
        return orDefault(MAX_RECORDING_TICKS, 1200);
    }

    public static int maxActions() {
        return orDefault(MAX_ACTIONS, 256);
    }

    public static int maxRadius() {
        return orDefault(MAX_RADIUS, 24);
    }

    public static int maxRecordingBytes() {
        return orDefault(MAX_RECORDING_BYTES, 262_144);
    }

    public static int maxActionsPerTick() {
        return orDefault(MAX_ACTIONS_PER_TICK, 128);
    }

    public static int maxActionTicks() {
        return orDefault(MAX_ACTION_TICKS, 160);
    }

    public static boolean allowPvp() {
        return SPEC.isLoaded() ? ALLOW_PVP.get() : false;
    }

    public static int goggleRadius() {
        return orDefault(GOGGLE_RADIUS, 24);
    }

    public static boolean gogglesShowOthers() {
        return SPEC.isLoaded() ? GOGGLES_SHOW_OTHERS.get() : true;
    }

    private static int orDefault(ModConfigSpec.IntValue value, int fallback) {
        return SPEC.isLoaded() ? value.getAsInt() : fallback;
    }
    //?}

    private ChronoclonesConfig() {}
}
