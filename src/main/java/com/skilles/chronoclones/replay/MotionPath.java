package com.skilles.chronoclones.replay;

import java.util.List;

import net.minecraft.world.phys.Vec3;

/** Interpolation between two motion samples. */
public final class MotionPath {

    private final List<Vec3> waypoints;
    private final int ticksPerLeg;
    private final int lengthTicks;

    public MotionPath(List<Vec3> waypoints, int ticksPerLeg) {
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("need at least 2 waypoints, got " + waypoints.size());
        }
        if (ticksPerLeg < 1) {
            throw new IllegalArgumentException("ticksPerLeg must be >= 1, got " + ticksPerLeg);
        }
        this.waypoints = List.copyOf(waypoints);
        this.ticksPerLeg = ticksPerLeg;
        this.lengthTicks = this.waypoints.size() * ticksPerLeg;
    }

    public int lengthTicks() {
        return lengthTicks;
    }

    public Vec3 positionAt(int tick) {
        int wrapped = Math.floorMod(tick, lengthTicks);
        int leg = wrapped / ticksPerLeg;
        double t = (wrapped % ticksPerLeg) / (double) ticksPerLeg;

        Vec3 from = waypoints.get(leg);
        Vec3 to = waypoints.get((leg + 1) % waypoints.size());

        return new Vec3(
                from.x + (to.x - from.x) * t,
                from.y + (to.y - from.y) * t,
                from.z + (to.z - from.z) * t);
    }

    public float yawAt(int tick) {
        int wrapped = Math.floorMod(tick, lengthTicks);
        int leg = wrapped / ticksPerLeg;

        Vec3 from = waypoints.get(leg);
        Vec3 to = waypoints.get((leg + 1) % waypoints.size());
        double dx = to.x - from.x;
        double dz = to.z - from.z;

        if (dx * dx + dz * dz < 1.0e-9) {
            return 0.0f;
        }
        return (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
    }
}
