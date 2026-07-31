package com.skilles.chronoclones.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.recording.MotionSample;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MotionTrackTest {

    private static MotionTrack straightLine() {
        List<MotionSample> samples = new ArrayList<>();
        for (int tick = 0; tick <= 60; tick += MotionSample.SAMPLE_INTERVAL_TICKS) {
            samples.add(new MotionSample(tick, new Vec3(tick / 10.0, 0, 0), 0f, 0f));
        }
        return new MotionTrack(samples);
    }

    @Test
    @DisplayName("sampled ticks return their sample exactly")
    void samplesAreExact() {
        MotionTrack track = straightLine();
        for (int tick = 0; tick <= 60; tick += 2) {
            assertEquals(tick / 10.0, track.localPositionAt(tick).x, 1.0e-12, "tick " + tick);
        }
    }

    @Test
    @DisplayName("ticks between samples interpolate rather than snapping")
    void betweenSamplesInterpolates() {
        MotionTrack track = straightLine();
        assertEquals(0.5, track.localPositionAt(5).x, 1.0e-12);
        assertEquals(0.1, track.localPositionAt(1).x, 1.0e-12);
    }

    @Test
    @DisplayName("querying the same tick repeatedly always gives the same answer")
    void queriesArePure() {
        MotionTrack track = straightLine();
        for (int tick = 0; tick <= 60; tick++) {
            Vec3 first = track.localPositionAt(tick);
            for (int repeat = 0; repeat < 5; repeat++) {
                assertEquals(0.0, track.localPositionAt(tick).distanceTo(first), 0.0);
            }
        }
    }

    @Test
    @DisplayName("looping a thousand times returns to exactly the starting position")
    void loopingDoesNotDrift() {
        MotionTrack track = straightLine();
        Vec3 start = track.localPositionAt(0);

        for (int loop = 0; loop < 1000; loop++) {
            assertEquals(0.0, track.localPositionAt(0).distanceTo(start), 0.0, "loop " + loop);
        }
    }

    @Test
    @DisplayName("out-of-range ticks clamp instead of extrapolating past the routine")
    void outOfRangeClamps() {
        MotionTrack track = straightLine();

        assertEquals(track.localPositionAt(0).x, track.localPositionAt(-50).x, 0.0);
        assertEquals(track.localPositionAt(60).x, track.localPositionAt(9999).x, 0.0);
    }

    @Test
    @DisplayName("an empty track is safe to query rather than throwing")
    void emptyTrackIsSafe() {
        MotionTrack empty = new MotionTrack(List.of());
        assertTrue(empty.isEmpty());
        assertEquals(Vec3.ZERO, empty.localPositionAt(17));
        assertEquals(0.0f, empty.localYawAt(17));
    }

    @Test
    @DisplayName("yaw interpolates the short way around, not backwards through 360")
    void yawTakesTheShortPath() {
        MotionTrack track = new MotionTrack(List.of(
                new MotionSample(0, Vec3.ZERO, 170f, 0f),
                new MotionSample(2, Vec3.ZERO, -170f, 0f)));

        float mid = track.localYawAt(1);
        assertEquals(180.0f, Math.abs(mid), 0.001f, "midpoint should be at the wrap, got " + mid);
    }

    @Test
    @DisplayName("world position applies the anchor's facing, so a rotated anchor rotates the clone")
    void worldPositionRespectsAnchorFacing() {
        MotionTrack track = new MotionTrack(List.of(
                new MotionSample(0, new Vec3(0, 0, -3), 0f, 0f)));

        BlockPos anchor = BlockPos.ZERO;

        assertEquals(new Vec3(0, 0, -3), track.worldPositionAt(0, anchor, Direction.NORTH));
        assertEquals(new Vec3(3, 0, 0), track.worldPositionAt(0, anchor, Direction.EAST));
        assertEquals(new Vec3(0, 0, 3), track.worldPositionAt(0, anchor, Direction.SOUTH));
        assertEquals(new Vec3(-3, 0, 0), track.worldPositionAt(0, anchor, Direction.WEST));
    }

    @Test
    @DisplayName("irregular sample spacing still resolves correctly")
    void irregularSpacingWorks() {
        MotionTrack track = new MotionTrack(List.of(
                new MotionSample(0, new Vec3(0, 0, 0), 0f, 0f),
                new MotionSample(10, new Vec3(10, 0, 0), 0f, 0f),
                new MotionSample(12, new Vec3(12, 0, 0), 0f, 0f)));

        assertEquals(5.0, track.localPositionAt(5).x, 1.0e-12);
        assertEquals(11.0, track.localPositionAt(11).x, 1.0e-12);
        assertEquals(12.0, track.localPositionAt(12).x, 1.0e-12);
    }
}
