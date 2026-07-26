package com.skilles.chronoclones.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.skilles.chronoclones.recording.ChronoActionType;
import com.skilles.chronoclones.recording.Recording;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Human-readable summary of a recording, shared by the recorder and the Chrono Shard.
 *
 * <p>This exists because handing someone an opaque item that turns out to mine a shaft under their
 * base is a bad surprise in single player and an actual attack on a shared server. A recording must
 * be fully inspectable <em>before</em> it is imprinted, so this reports who authored it, how long it
 * runs, exactly what it does broken down by action type, and how far from the anchor it reaches.
 *
 * <p>Cheap to compute and computed client-side from the synced component — no server round trip.
 */
public final class RecordingTooltips {

    private RecordingTooltips() {}

    public static List<Component> describe(Recording recording) {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable("tooltip.chronoclones.recording.author",
                        Component.literal(recording.authorName()).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));

        lines.add(Component.translatable("tooltip.chronoclones.recording.length",
                        recording.lengthSeconds(), recording.actions().size())
                .withStyle(ChatFormatting.GRAY));

        Map<ChronoActionType, Integer> counts = recording.actionCounts();
        if (counts.isEmpty()) {
            lines.add(Component.translatable("tooltip.chronoclones.recording.no_actions")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            // Enumerate every type present, in fidelity order, so nothing hides in an "other" bucket.
            for (ChronoActionType type : ChronoActionType.values()) {
                int count = counts.getOrDefault(type, 0);
                if (count > 0) {
                    lines.add(Component.literal(" ")
                            .append(Component.translatable(
                                    "tooltip.chronoclones.recording.action." + type.getSerializedName(), count))
                            .withStyle(colourFor(type)));
                }
            }
        }

        lines.add(Component.translatable("tooltip.chronoclones.recording.reach",
                        String.format("%.1f", recording.reach()))
                .withStyle(ChatFormatting.GRAY));

        return lines;
    }

    /** Destructive action types read hotter, so a dangerous routine is obvious at a glance. */
    private static ChatFormatting colourFor(ChronoActionType type) {
        return switch (type) {
            case BREAK_BLOCK -> ChatFormatting.GOLD;
            case PLACE_BLOCK -> ChatFormatting.GREEN;
            case ATTACK_ENTITY -> ChatFormatting.RED;
            case USE_CONTAINER -> ChatFormatting.YELLOW;
            case USE_ON_BLOCK, USE_ITEM, INTERACT_ENTITY -> ChatFormatting.AQUA;
        };
    }
}
