package com.skilles.chronoclones.recording;

import net.minecraft.world.phys.Vec3;

public record MotionSample(int tick, Vec3 localPos, float localYaw, float pitch) {

    public static final int SAMPLE_INTERVAL_TICKS = 2;
}
