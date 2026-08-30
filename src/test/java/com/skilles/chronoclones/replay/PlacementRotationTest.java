package com.skilles.chronoclones.replay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacementRotationTest {

    private static final BlockPos ANCHOR = new BlockPos(50, 64, 50);
    private static final BlockPos IN_FRONT = new BlockPos(0, 0, -1);

    @Test
    @DisplayName("a quarter turn swings the routine's blocks clockwise around the origin")
    void quarterTurnSwingsTheRoutineClockwise() {
        Placement placement = Placement.of(ANCHOR, Direction.NORTH, BlockPos.ZERO, 1);

        assertEquals(ANCHOR.east(), placement.toWorld(IN_FRONT));
        assertEquals(Direction.EAST, placement.toWorld(Direction.NORTH));
    }

    @Test
    @DisplayName("four quarter turns are no turn at all")
    void fourQuarterTurnsAreIdentity() {
        Placement straight = Placement.of(ANCHOR, Direction.NORTH, BlockPos.ZERO, 0);
        Placement fullCircle = Placement.of(ANCHOR, Direction.NORTH, BlockPos.ZERO, 4);

        assertEquals(straight.toWorld(IN_FRONT), fullCircle.toWorld(IN_FRONT));
        assertEquals(straight.facing(), fullCircle.facing());
    }

    @Test
    @DisplayName("rotating leaves a nudged origin where it is; only the routine spins")
    void rotationDoesNotMoveTheOrigin() {
        BlockPos offset = new BlockPos(-2, 1, 3);

        for (Direction anchorFacing : Direction.Plane.HORIZONTAL) {
            BlockPos unrotated = Placement.of(ANCHOR, anchorFacing, offset).origin();
            for (int steps = 0; steps < 4; steps++) {
                assertEquals(unrotated,
                        Placement.of(ANCHOR, anchorFacing, offset, steps).origin(),
                        "the origin moved when rotating " + steps + " steps at a "
                                + anchorFacing + "-facing anchor");
            }
        }
    }

    @Test
    @DisplayName("rotation composes with the anchor's own facing")
    void rotationComposesWithAnchorFacing() {
        // An east-facing anchor already turns the routine one step; one more lands south.
        Placement placement = Placement.of(ANCHOR, Direction.EAST, BlockPos.ZERO, 1);

        assertEquals(ANCHOR.south(), placement.toWorld(IN_FRONT));
    }
}
