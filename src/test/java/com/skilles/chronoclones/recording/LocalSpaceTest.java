package com.skilles.chronoclones.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coordinate rebasing is the part most likely to be implemented wrong, so these tests
 * try to break it rather than merely demonstrate it: all 4×4 facing combinations, off-axis and
 * negative positions, and the consistency between rotating a position and rotating a yaw.
 */
class LocalSpaceTest {

    private static final List<Direction> CARDINALS =
            List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    /** Deliberately asymmetric and off-origin so a wrong rotation cannot accidentally pass. */
    private static final List<BlockPos> PROBES = List.of(
            new BlockPos(0, 0, 0),
            new BlockPos(1, 0, 0),
            new BlockPos(0, 0, 1),
            new BlockPos(3, 2, 7),
            new BlockPos(-5, -3, 2),
            new BlockPos(-1, 4, -9),
            new BlockPos(16, 0, -16));

    private static final BlockPos ORIGIN = new BlockPos(100, 64, -250);
    private static final BlockPos ANCHOR = new BlockPos(-40, 12, 900);

    // ------------------------------------------------------------------ round trips

    @Test
    @DisplayName("round trip is the identity when anchor facing matches origin facing")
    void roundTripIsIdentityWhenFacingsMatch() {
        for (Direction facing : CARDINALS) {
            for (BlockPos probe : PROBES) {
                BlockPos world = ORIGIN.offset(probe);
                BlockPos local = LocalSpace.toLocal(world, ORIGIN, facing);
                BlockPos back = LocalSpace.toWorld(local, ORIGIN, facing);
                assertEquals(world, back, "facing " + facing + " probe " + probe);
            }
        }
    }

    @Test
    @DisplayName("round trip preserves offset-from-anchor for every facing pair")
    void roundTripAcrossAllFacingPairs() {
        for (Direction originFacing : CARDINALS) {
            for (Direction anchorFacing : CARDINALS) {
                for (BlockPos probe : PROBES) {
                    BlockPos world = ORIGIN.offset(probe);
                    BlockPos local = LocalSpace.toLocal(world, ORIGIN, originFacing);
                    BlockPos replayed = LocalSpace.toWorld(local, ANCHOR, anchorFacing);

                    // Rebasing back through the same anchor facing must recover the local vector.
                    BlockPos recovered = LocalSpace.toLocal(replayed, ANCHOR, anchorFacing);
                    assertEquals(local, recovered,
                            "origin " + originFacing + " anchor " + anchorFacing + " probe " + probe);
                }
            }
        }
    }

    @Test
    @DisplayName("Vec3 round trips exactly for every facing pair")
    void vec3RoundTrips() {
        Vec3 probe = new Vec3(3.25, 1.5, -7.75);
        for (Direction originFacing : CARDINALS) {
            Vec3 world = new Vec3(ORIGIN.getX() + probe.x, ORIGIN.getY() + probe.y, ORIGIN.getZ() + probe.z);
            Vec3 local = LocalSpace.toLocal(world, ORIGIN, originFacing);
            Vec3 back = LocalSpace.toWorld(local, ORIGIN, originFacing);
            assertEquals(world.x, back.x, 0.0);
            assertEquals(world.y, back.y, 0.0);
            assertEquals(world.z, back.z, 0.0);
        }
    }

    // ------------------------------------------------------------------ rotation algebra

    @Test
    @DisplayName("four quarter-turns compose to the identity")
    void fourRotationsAreIdentity() {
        for (BlockPos probe : PROBES) {
            BlockPos r = probe;
            for (int i = 0; i < 4; i++) {
                r = LocalSpace.rotateY(r, 1);
            }
            assertEquals(probe, r, "probe " + probe);
        }
    }

    @Test
    @DisplayName("rotating by n equals rotating by 1, n times")
    void bulkRotationMatchesRepeatedRotation() {
        for (BlockPos probe : PROBES) {
            for (int steps = -8; steps <= 8; steps++) {
                BlockPos bulk = LocalSpace.rotateY(probe, steps);
                BlockPos repeated = probe;
                for (int i = 0; i < Math.floorMod(steps, 4); i++) {
                    repeated = LocalSpace.rotateY(repeated, 1);
                }
                assertEquals(repeated, bulk, "probe " + probe + " steps " + steps);
            }
        }
    }

    @Test
    @DisplayName("one clockwise quarter-turn maps east to south, matching Minecraft's axes")
    void clockwiseMatchesMinecraftAxes() {
        assertEquals(new BlockPos(0, 0, 1), LocalSpace.rotateY(new BlockPos(1, 0, 0), 1));
        assertEquals(new BlockPos(-1, 0, 0), LocalSpace.rotateY(new BlockPos(0, 0, 1), 1));
        assertEquals(new BlockPos(0, 0, -1), LocalSpace.rotateY(new BlockPos(-1, 0, 0), 1));
        assertEquals(new BlockPos(1, 0, 0), LocalSpace.rotateY(new BlockPos(0, 0, -1), 1));
    }

    @Test
    @DisplayName("rotation never touches Y")
    void rotationPreservesY() {
        for (BlockPos probe : PROBES) {
            for (int steps = 0; steps < 4; steps++) {
                assertEquals(probe.getY(), LocalSpace.rotateY(probe, steps).getY());
            }
        }
    }

    @Test
    @DisplayName("position rotation and Direction rotation agree")
    void directionRotationMatchesPositionRotation() {
        for (Direction dir : CARDINALS) {
            for (int steps = 0; steps < 4; steps++) {
                Direction rotatedDir = LocalSpace.rotateY(dir, steps);
                BlockPos rotatedVec = LocalSpace.rotateY(
                        new BlockPos(dir.getStepX(), 0, dir.getStepZ()), steps);
                assertEquals(
                        new BlockPos(rotatedDir.getStepX(), 0, rotatedDir.getStepZ()),
                        rotatedVec,
                        "dir " + dir + " steps " + steps);
            }
        }
    }

    @Test
    @DisplayName("vertical directions survive rotation untouched")
    void verticalDirectionsAreUnchanged() {
        for (int steps = 0; steps < 4; steps++) {
            assertEquals(Direction.UP, LocalSpace.rotateY(Direction.UP, steps));
            assertEquals(Direction.DOWN, LocalSpace.rotateY(Direction.DOWN, steps));
        }
    }

    // ------------------------------------------------------------------ yaw

    @Test
    @DisplayName("yaw round trips for every facing")
    void yawRoundTrips() {
        for (Direction facing : CARDINALS) {
            for (float yaw : new float[] {0f, 45f, 90f, 179f, -179f, -90f, 137.5f}) {
                float local = LocalSpace.toLocalYaw(yaw, facing);
                float back = LocalSpace.toWorldYaw(local, facing);
                assertEquals(LocalSpace.wrapDegrees(yaw), back, 1.0e-4f,
                        "facing " + facing + " yaw " + yaw);
            }
        }
    }

    @Test
    @DisplayName("a quarter-turn of position corresponds to +90 degrees of yaw")
    void yawStepMatchesPositionStep() {
        // Facing east (1 step from north) must shift local yaw by exactly one quarter-turn.
        float worldYaw = 0.0f;
        assertEquals(
                LocalSpace.wrapDegrees(worldYaw - Direction.EAST.toYRot()),
                LocalSpace.toLocalYaw(worldYaw, Direction.EAST),
                1.0e-4f);
    }

    @Test
    @DisplayName("wrapDegrees normalises into (-180, 180]")
    void wrapDegreesNormalises() {
        assertEquals(0.0f, LocalSpace.wrapDegrees(360.0f), 1.0e-4f);
        assertEquals(180.0f, LocalSpace.wrapDegrees(180.0f), 1.0e-4f);
        assertEquals(180.0f, LocalSpace.wrapDegrees(-180.0f), 1.0e-4f);
        assertEquals(-90.0f, LocalSpace.wrapDegrees(270.0f), 1.0e-4f);
        assertEquals(1.0f, LocalSpace.wrapDegrees(721.0f), 1.0e-4f);
    }

    // ------------------------------------------------------------------ snapping and guards

    @Test
    @DisplayName("yaw snaps to the nearest cardinal")
    void snapToCardinal() {
        assertEquals(Direction.SOUTH, LocalSpace.snapToCardinal(0.0f));
        assertEquals(Direction.WEST, LocalSpace.snapToCardinal(90.0f));
        assertEquals(Direction.NORTH, LocalSpace.snapToCardinal(180.0f));
        assertEquals(Direction.EAST, LocalSpace.snapToCardinal(-90.0f));
        // Near-misses must still snap, not drift to a neighbour.
        assertEquals(Direction.SOUTH, LocalSpace.snapToCardinal(10.0f));
        assertEquals(Direction.SOUTH, LocalSpace.snapToCardinal(-10.0f));
    }

    @Test
    @DisplayName("a vertical facing is rejected rather than silently treated as north")
    void verticalFacingRejected() {
        assertThrows(IllegalArgumentException.class, () -> LocalSpace.stepsFromNorth(Direction.UP));
        assertThrows(IllegalArgumentException.class, () -> LocalSpace.stepsFromNorth(Direction.DOWN));
    }

    // ------------------------------------------------------------------ the payoff

    @Test
    @DisplayName("rotating the anchor rotates the routine: same recording, four rotated copies")
    void rotatingAnchorRotatesRoutine() {
        // A routine recorded facing north that digs three blocks "forward" (north is -Z).
        List<BlockPos> routine = List.of(
                new BlockPos(0, 0, -1),
                new BlockPos(0, 0, -2),
                new BlockPos(0, 0, -3));

        BlockPos anchor = BlockPos.ZERO;

        assertEquals(
                List.of(new BlockPos(0, 0, -1), new BlockPos(0, 0, -2), new BlockPos(0, 0, -3)),
                routine.stream().map(p -> LocalSpace.toWorld(p, anchor, Direction.NORTH)).toList());

        assertEquals(
                List.of(new BlockPos(1, 0, 0), new BlockPos(2, 0, 0), new BlockPos(3, 0, 0)),
                routine.stream().map(p -> LocalSpace.toWorld(p, anchor, Direction.EAST)).toList());

        assertEquals(
                List.of(new BlockPos(0, 0, 1), new BlockPos(0, 0, 2), new BlockPos(0, 0, 3)),
                routine.stream().map(p -> LocalSpace.toWorld(p, anchor, Direction.SOUTH)).toList());

        assertEquals(
                List.of(new BlockPos(-1, 0, 0), new BlockPos(-2, 0, 0), new BlockPos(-3, 0, 0)),
                routine.stream().map(p -> LocalSpace.toWorld(p, anchor, Direction.WEST)).toList());
    }

    @Test
    @DisplayName("radius is rotation-invariant, so record-time and execute-time caps agree")
    void radiusIsRotationInvariant() {
        for (BlockPos probe : PROBES) {
            double expected = Math.sqrt(probe.distSqr(BlockPos.ZERO));
            for (int steps = 0; steps < 4; steps++) {
                BlockPos rotated = LocalSpace.rotateY(probe, steps);
                assertEquals(expected, Math.sqrt(rotated.distSqr(BlockPos.ZERO)), 1.0e-9,
                        "probe " + probe + " steps " + steps);
            }
        }
    }
}
