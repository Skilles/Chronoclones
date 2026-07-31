package com.skilles.chronoclones.client;

import com.skilles.chronoclones.recording.LocalSpace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class NudgeDirection {

    private NudgeDirection() {}

    public enum Key {

        FORWARD,
        BACK,
        LEFT,
        RIGHT,
        UP,
        DOWN
    }

    public static BlockPos step(Key key, Direction playerFacing, Direction anchorFacing) {
        return switch (key) {
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
