package com.skilles.chronoclones.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Live HUD counters for a recorder in RECORDING state.
 *
 * <p>Carried as a data component so ordinary inventory sync delivers it — no custom packet. If
 * that proves too laggy in practice the fallback is a small periodic payload, but this is the
 * cheapest thing that can work and is worth trying first.
 */
public record RecordingProgress(int elapsedTicks, int actionCount, boolean outOfRangeWarning) {

    public static final RecordingProgress EMPTY = new RecordingProgress(0, 0, false);

    public static final Codec<RecordingProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("elapsed").forGetter(RecordingProgress::elapsedTicks),
            Codec.INT.fieldOf("actions").forGetter(RecordingProgress::actionCount),
            Codec.BOOL.optionalFieldOf("out_of_range", false).forGetter(RecordingProgress::outOfRangeWarning)
    ).apply(i, RecordingProgress::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordingProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RecordingProgress::elapsedTicks,
            ByteBufCodecs.VAR_INT, RecordingProgress::actionCount,
            ByteBufCodecs.BOOL, RecordingProgress::outOfRangeWarning,
            RecordingProgress::new);
}
