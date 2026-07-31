package com.skilles.chronoclones.client;

import com.skilles.chronoclones.replay.Placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NudgeDirectionTest {

    private static BlockPos step(NudgeDirection.Key key, Direction player, Direction anchor) {
        return NudgeDirection.step(key, player, anchor);
    }

    @Test
    @DisplayName("on a north-facing anchor, forward is away from the player")
    void forwardIsAwayFromThePlayer() {
        assertEquals(new BlockPos(0, 0, -1), step(NudgeDirection.Key.FORWARD, Direction.NORTH, Direction.NORTH));
        assertEquals(new BlockPos(0, 0, 1), step(NudgeDirection.Key.FORWARD, Direction.SOUTH, Direction.NORTH));
        assertEquals(new BlockPos(1, 0, 0), step(NudgeDirection.Key.FORWARD, Direction.EAST, Direction.NORTH));
        assertEquals(new BlockPos(-1, 0, 0), step(NudgeDirection.Key.FORWARD, Direction.WEST, Direction.NORTH));
    }

    @Test
    @DisplayName("back is the opposite of forward, from wherever you stand")
    void backIsTheOppositeOfForward() {
        for (Direction player : Direction.Plane.HORIZONTAL) {
            for (Direction anchor : Direction.Plane.HORIZONTAL) {
                BlockPos forward = step(NudgeDirection.Key.FORWARD, player, anchor);
                BlockPos back = step(NudgeDirection.Key.BACK, player, anchor);
                assertEquals(forward.multiply(-1), back,
                        "forward and back disagreed for player " + player + " anchor " + anchor);
            }
        }
    }

    @Test
    @DisplayName("left and right are opposites, and perpendicular to forward")
    void leftAndRightArePerpendicular() {
        for (Direction player : Direction.Plane.HORIZONTAL) {
            for (Direction anchor : Direction.Plane.HORIZONTAL) {
                BlockPos forward = step(NudgeDirection.Key.FORWARD, player, anchor);
                BlockPos left = step(NudgeDirection.Key.LEFT, player, anchor);
                BlockPos right = step(NudgeDirection.Key.RIGHT, player, anchor);

                assertEquals(left.multiply(-1), right,
                        "left and right disagreed for player " + player + " anchor " + anchor);
                int dot = forward.getX() * left.getX() + forward.getZ() * left.getZ();
                assertEquals(0, dot, "left was not perpendicular to forward");
            }
        }
    }

    @Test
    @DisplayName("a step moves the origin the way the player is pointing, whatever the anchor faces")
    void stepMovesTheOriginTheWayThePlayerPoints() {
        BlockPos anchorPos = new BlockPos(50, 64, 50);

        for (Direction anchor : Direction.Plane.HORIZONTAL) {
            for (Direction player : Direction.Plane.HORIZONTAL) {
                BlockPos step = step(NudgeDirection.Key.FORWARD, player, anchor);
                BlockPos moved = Placement.of(anchorPos, anchor, step).origin();

                assertEquals(anchorPos.relative(player), moved,
                        "forward from a player facing " + player + " at a " + anchor
                                + "-facing anchor landed at " + moved);
            }
        }
    }

    @Test
    @DisplayName("left really is the player's left in the world")
    void leftIsThePlayersLeft() {
        BlockPos anchorPos = new BlockPos(50, 64, 50);

        for (Direction anchor : Direction.Plane.HORIZONTAL) {
            for (Direction player : Direction.Plane.HORIZONTAL) {
                BlockPos step = step(NudgeDirection.Key.LEFT, player, anchor);
                assertEquals(anchorPos.relative(player.getCounterClockWise()),
                        Placement.of(anchorPos, anchor, step).origin(),
                        "left was not to the left for player " + player + " anchor " + anchor);
            }
        }
    }

    @Test
    @DisplayName("vertical is the same everywhere")
    void verticalIsRotationInvariant() {
        for (Direction player : Direction.Plane.HORIZONTAL) {
            for (Direction anchor : Direction.Plane.HORIZONTAL) {
                assertEquals(new BlockPos(0, 1, 0), step(NudgeDirection.Key.UP, player, anchor));
                assertEquals(new BlockPos(0, -1, 0), step(NudgeDirection.Key.DOWN, player, anchor));
            }
        }
    }

    @Test
    @DisplayName("every step moves exactly one block")
    void stepsAreOneBlock() {
        for (NudgeDirection.Key key : NudgeDirection.Key.values()) {
            for (Direction player : Direction.Plane.HORIZONTAL) {
                for (Direction anchor : Direction.Plane.HORIZONTAL) {
                    BlockPos step = step(key, player, anchor);
                    int manhattan = Math.abs(step.getX()) + Math.abs(step.getY()) + Math.abs(step.getZ());
                    assertEquals(1, manhattan, key + " moved " + step + " rather than one block");
                }
            }
        }
    }
}
