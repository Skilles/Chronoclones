package com.skilles.chronoclones.replay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DAY 1 SPIKE (1b), executed as a test rather than by eye.
 *
 * <p>The spec's stated go/no-go was "run it for ten full loops and watch for positional drift".
 * Watching is weaker than asserting, so this asserts it — and over far more than ten loops.
 */
class MotionPathTest {

    private static final List<Vec3> ROUTE = List.of(
            new Vec3(0.5, 0.0, 1.5),
            new Vec3(3.5, 0.0, 1.5),
            new Vec3(3.5, 0.0, 4.5),
            new Vec3(0.5, 1.0, 4.5),
            new Vec3(-2.5, 1.0, 4.5),
            new Vec3(-2.5, 0.0, 1.5));

    private static final int TICKS_PER_LEG = 20;

    private MotionPath path() {
        return new MotionPath(ROUTE, TICKS_PER_LEG);
    }

    @Test
    @DisplayName("ten loops produce exactly zero drift, not merely small drift")
    void noDriftOverTenLoops() {
        MotionPath p = path();
        Vec3 origin = p.positionAt(0);

        for (int loop = 1; loop <= 10; loop++) {
            Vec3 atLoopBoundary = p.positionAt(loop * p.lengthTicks());
            assertEquals(origin.x, atLoopBoundary.x, 0.0, "x drifted at loop " + loop);
            assertEquals(origin.y, atLoopBoundary.y, 0.0, "y drifted at loop " + loop);
            assertEquals(origin.z, atLoopBoundary.z, 0.0, "z drifted at loop " + loop);
        }
    }

    @Test
    @DisplayName("drift stays exactly zero even after 100k loops")
    void noDriftOverManyLoops() {
        MotionPath p = path();
        Vec3 origin = p.positionAt(0);
        Vec3 far = p.positionAt(100_000 * p.lengthTicks());
        assertEquals(0.0, far.distanceTo(origin), 0.0);
    }

    @Test
    @DisplayName("every tick in a loop equals the same tick one loop later")
    void loopIsExactlyPeriodic() {
        MotionPath p = path();
        for (int tick = 0; tick < p.lengthTicks(); tick++) {
            Vec3 a = p.positionAt(tick);
            Vec3 b = p.positionAt(tick + p.lengthTicks());
            assertEquals(0.0, a.distanceTo(b), 0.0, "tick " + tick + " not periodic");
        }
    }

    @Test
    @DisplayName("negative ticks wrap correctly rather than throwing or mirroring")
    void negativeTicksWrap() {
        MotionPath p = path();
        assertEquals(0.0, p.positionAt(-p.lengthTicks()).distanceTo(p.positionAt(0)), 0.0);
        assertEquals(0.0, p.positionAt(-1).distanceTo(p.positionAt(p.lengthTicks() - 1)), 0.0);
    }

    @Test
    @DisplayName("waypoints are hit exactly at leg boundaries")
    void waypointsAreExact() {
        MotionPath p = path();
        for (int i = 0; i < ROUTE.size(); i++) {
            Vec3 expected = ROUTE.get(i);
            Vec3 actual = p.positionAt(i * TICKS_PER_LEG);
            assertEquals(0.0, actual.distanceTo(expected), 0.0, "waypoint " + i + " not hit exactly");
        }
    }

    @Test
    @DisplayName("motion is continuous: no per-tick jump exceeds the longest leg's per-tick step")
    void motionIsContinuous() {
        MotionPath p = path();
        double longestLeg = 0.0;
        for (int i = 0; i < ROUTE.size(); i++) {
            longestLeg = Math.max(longestLeg, ROUTE.get(i).distanceTo(ROUTE.get((i + 1) % ROUTE.size())));
        }
        double maxStep = longestLeg / TICKS_PER_LEG + 1.0e-9;

        for (int tick = 0; tick < p.lengthTicks() * 3; tick++) {
            double step = p.positionAt(tick).distanceTo(p.positionAt(tick + 1));
            assertTrue(step <= maxStep, "tick " + tick + " jumped " + step + " > " + maxStep);
        }
    }

    @Test
    @DisplayName("length is waypoints * ticksPerLeg")
    void lengthIsCorrect() {
        assertEquals(ROUTE.size() * TICKS_PER_LEG, path().lengthTicks());
    }
}
