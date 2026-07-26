package com.skilles.chronoclones;

import java.util.List;

import net.minecraft.resources.Identifier;
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
    public static final ModConfigSpec.ConfigValue<List<? extends String>> COHERENCE_GROUPS;

    /**
     * Block tags that count as "the same kind of thing" to an anchor fitted with an Chrono Lens.
     *
     * <p>Deliberately a short named list rather than every tag two blocks happen to share. Every
     * stone-like block is in {@code #minecraft:mineable/pickaxe}, so accepting any shared tag would
     * turn a routine recorded to clear stone into one that accepts the walls of your base. See
     * {@code Coherence} for the rest of that argument, including why this is config and not a tag.
     */
    public static final List<String> DEFAULT_COHERENCE_GROUPS = List.of(
            "minecraft:base_stone_overworld",
            "minecraft:logs",
            "minecraft:dirt",
            "minecraft:sand",
            "minecraft:terracotta",
            "minecraft:leaves",
            "minecraft:planks",
            "minecraft:wool");

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
        COHERENCE_GROUPS = b.comment(
                        "Block tags an Chrono Lens treats as interchangeable. A break whose recorded",
                        "block and actual block share one of these tags goes ahead. Keep these",
                        "narrow: a broad tag like minecraft:mineable/pickaxe would let a routine",
                        "recorded to clear stone accept almost any block.")
                .defineList("coherenceGroups", DEFAULT_COHERENCE_GROUPS,
                        () -> "", entry -> entry instanceof String id && Identifier.tryParse(id) != null);
        b.pop();

        SPEC = b.build();
    }

    private ChronoclonesConfig() {}
}
