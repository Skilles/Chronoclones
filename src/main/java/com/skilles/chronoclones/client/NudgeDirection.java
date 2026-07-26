package com.skilles.chronoclones.client;

import com.skilles.chronoclones.recording.LocalSpace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Turns "the arrow key I pressed" into a step in an anchor's local space.
 *
 * <p>Two rotations, and both are necessary. The key is meaningful relative to <em>the player</em> —
 * up means away from you, left means to your left — because that is what the preview looks like from
 * where you are standing; a routine that jumped north when you pressed up while facing south would be
 * unusable. The stored offset is meaningful relative to <em>the anchor</em>, because that is how
 * every other position in a recording is stored, and an offset that was not would mean something
 * different the moment the routine was replayed on an anchor facing another way.
 *
 * <p>So: key → world (by the player's facing) → local (by the anchor's facing). Pure integer
 * quarter-turns throughout, for the reasons {@link LocalSpace} sets out.
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
     * @param anchorFacing the anchor's own facing, which local space is defined against
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
