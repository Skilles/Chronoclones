package com.skilles.chronoclones.replay;

import java.util.List;

import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.recording.MotionSample;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** A recording's motion samples, interpolated to any tick. */
public final class MotionTrack {

    private final List<MotionSample> samples;

    public MotionTrack(List<MotionSample> samples) {
        this.samples = List.copyOf(samples);
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    public int size() {
        return samples.size();
    }

    public Vec3 localPositionAt(int tick) {
        if (samples.isEmpty()) {
            return Vec3.ZERO;
        }
        int i = indexAtOrBefore(tick);
        MotionSample from = samples.get(i);
        if (i == samples.size() - 1) {
            return from.localPos();
        }
        MotionSample to = samples.get(i + 1);
        double t = progressBetween(from, to, tick);

        return new Vec3(
                from.localPos().x + (to.localPos().x - from.localPos().x) * t,
                from.localPos().y + (to.localPos().y - from.localPos().y) * t,
                from.localPos().z + (to.localPos().z - from.localPos().z) * t);
    }

    public float localYawAt(int tick) {
        if (samples.isEmpty()) {
            return 0.0f;
        }
        int i = indexAtOrBefore(tick);
        MotionSample from = samples.get(i);
        if (i == samples.size() - 1) {
            return from.localYaw();
        }
        MotionSample to = samples.get(i + 1);
        double t = progressBetween(from, to, tick);

        float delta = LocalSpace.wrapDegrees(to.localYaw() - from.localYaw());
        return LocalSpace.wrapDegrees(from.localYaw() + (float) (delta * t));
    }

    public float pitchAt(int tick) {
        if (samples.isEmpty()) {
            return 0.0f;
        }
        int i = indexAtOrBefore(tick);
        MotionSample from = samples.get(i);
        if (i == samples.size() - 1) {
            return from.pitch();
        }
        MotionSample to = samples.get(i + 1);
        double t = progressBetween(from, to, tick);
        return from.pitch() + (float) ((to.pitch() - from.pitch()) * t);
    }

    public Vec3 worldPositionAt(int tick, BlockPos anchorPos, Direction anchorFacing) {
        return LocalSpace.toWorld(localPositionAt(tick), anchorPos, anchorFacing);
    }

    public float worldYawAt(int tick, Direction anchorFacing) {
        return LocalSpace.toWorldYaw(localYawAt(tick), anchorFacing);
    }

    private static double progressBetween(MotionSample from, MotionSample to, int tick) {
        int span = to.tick() - from.tick();
        if (span <= 0) {
            return 0.0;
        }
        double t = (tick - from.tick()) / (double) span;
        return Math.clamp(t, 0.0, 1.0);
    }

    private int indexAtOrBefore(int tick) {
        int lo = 0;
        int hi = samples.size() - 1;
        if (tick <= samples.get(0).tick()) {
            return 0;
        }
        if (tick >= samples.get(hi).tick()) {
            return hi;
        }
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (samples.get(mid).tick() <= tick) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }
}
