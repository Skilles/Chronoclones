package com.skilles.chronoclones.client;

import com.skilles.chronoclones.recording.LocalSpace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Turns "the arrow key I pressed" into a step in an anchor's local space.
 */
public final class NudgeDirection {

    private NudgeDirection() {}

    /** Which way a key points, before anyone's facing is taken into account. */
    public enum Key {
        /** Away from the player. */
        FORWARD,
        /** Toward the player. */
        BACK,
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    /**
     * One step, in the anchor's local space.
     *
     * @param playerFacing where the player is looking, snapped to a cardinal
     */
    public static BlockPos step(Key key, Direction playerFacing, Direction anchorFacing) {
        return switch (key) {
            // Vertical is rotation-invariant, so it needs neither conversion.
            case UP -> new BlockPos(0, 1, 0);
            case DOWN -> new BlockPos(0, -1, 0);
            case FORWARD -> horizontal(playerFacing, anchorFacing);
            case BACK -> horizontal(playerFacing.getOpposite(), anchorFacing);
            case LEFT -> horizontal(playerFacing.getCounterClockWise(), anchorFacing);
            case RIGHT -> horizontal(playerFacing.getClockWise(), anchorFacing);
        };
    }

    private static BlockPos horizontal(Direction worldDirection, Direction anchorFacing) {
        Direction local = LocalSpace.toLocal(worldDirection, anchorFacing);
        return new BlockPos(local.getStepX(), 0, local.getStepZ());
    }
}
