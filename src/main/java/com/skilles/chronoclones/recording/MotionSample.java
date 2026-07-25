package com.skilles.chronoclones.recording;

import net.minecraft.world.phys.Vec3;

/**
 * One sampled frame of the author's movement, in anchor-local space.
 *
 * <p>Sampled every {@value #SAMPLE_INTERVAL_TICKS} ticks rather than every tick: at replay the
 * ghost interpolates between samples, which is visually identical and halves the data.
 *
 * <p>Pitch is stored absolute because rotation about Y leaves it unchanged. Pose is deliberately
 * not captured — it is purely cosmetic and sits last on the spec's cut list.
 *
 * <p>Serialization lives in {@link RecordingCodecs}.
 */
public record MotionSample(int tick, Vec3 localPos, float localYaw, float pitch) {
    public static final int SAMPLE_INTERVAL_TICKS = 2;
}
