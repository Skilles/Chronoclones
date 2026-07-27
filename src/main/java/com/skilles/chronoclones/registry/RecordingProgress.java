package com.skilles.chronoclones.registry;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Live HUD counters for a recorder in RECORDING state.
 */
public record RecordingProgress(UUID sessionId, int elapsedTicks, int actionCount,
                                boolean outOfRangeWarning) {

    /** Placeholder for display defaults only; never written to an item. */
    public static final RecordingProgress EMPTY =
            new RecordingProgress(new UUID(0L, 0L), 0, 0, false);

    public static final Codec<RecordingProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("session").forGetter(RecordingProgress::sessionId),
            Codec.INT.fieldOf("elapsed").forGetter(RecordingProgress::elapsedTicks),
            Codec.INT.fieldOf("actions").forGetter(RecordingProgress::actionCount),
            Codec.BOOL.optionalFieldOf("out_of_range", false).forGetter(RecordingProgress::outOfRangeWarning)
    ).apply(i, RecordingProgress::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordingProgress> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.cast(), RecordingProgress::sessionId,
            ByteBufCodecs.VAR_INT, RecordingProgress::elapsedTicks,
            ByteBufCodecs.VAR_INT, RecordingProgress::actionCount,
            ByteBufCodecs.BOOL, RecordingProgress::outOfRangeWarning,
            RecordingProgress::new);
}
