package com.skilles.chronoclones.recording;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Conversions between world space and a routine's own anchor-relative space. */
public final class LocalSpace {

    private LocalSpace() {}

    /** Rotations are quarter turns, so an origin facing has to be one of the four. */
    public static Direction snapToCardinal(float yaw) {
        return Direction.fromYRot(yaw);
    }

    public static int stepsFromNorth(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("not a horizontal direction: " + facing);
        };
    }

    private static int normalizeSteps(int steps) {
        return Math.floorMod(steps, 4);
    }

    public static BlockPos rotateY(BlockPos pos, int steps) {
        int x = pos.getX();
        int z = pos.getZ();
        return switch (normalizeSteps(steps)) {
            case 0 -> new BlockPos(x, pos.getY(), z);
            case 1 -> new BlockPos(-z, pos.getY(), x);
            case 2 -> new BlockPos(-x, pos.getY(), -z);
            default -> new BlockPos(z, pos.getY(), -x);
        };
    }

    public static Vec3 rotateY(Vec3 vec, int steps) {
        return switch (normalizeSteps(steps)) {
            case 0 -> new Vec3(vec.x, vec.y, vec.z);
            case 1 -> new Vec3(-vec.z, vec.y, vec.x);
            case 2 -> new Vec3(-vec.x, vec.y, -vec.z);
            default -> new Vec3(vec.z, vec.y, -vec.x);
        };
    }

    public static Direction rotateY(Direction direction, int steps) {
        if (direction.getAxis().isVertical()) {
            return direction;
        }
        Direction result = direction;
        for (int i = 0; i < normalizeSteps(steps); i++) {
            result = result.getClockWise();
        }
        return result;
    }

    public static BlockPos toLocal(BlockPos world, BlockPos origin, Direction originFacing) {
        return rotateY(world.subtract(origin), -stepsFromNorth(originFacing));
    }

    public static Vec3 toLocal(Vec3 world, BlockPos origin, Direction originFacing) {
        Vec3 relative = world.subtract(origin.getX(), origin.getY(), origin.getZ());
        return rotateY(relative, -stepsFromNorth(originFacing));
    }

    public static Direction toLocal(Direction world, Direction originFacing) {
        return rotateY(world, -stepsFromNorth(originFacing));
    }

    public static float toLocalYaw(float worldYaw, Direction originFacing) {
        return wrapDegrees(worldYaw - originFacing.toYRot());
    }

    public static BlockPos toWorld(BlockPos local, BlockPos anchor, Direction anchorFacing) {
        return anchor.offset(rotateY(local, stepsFromNorth(anchorFacing)));
    }

    public static Vec3 toWorld(Vec3 local, BlockPos anchor, Direction anchorFacing) {
        Vec3 rotated = rotateY(local, stepsFromNorth(anchorFacing));
        return rotated.add(anchor.getX(), anchor.getY(), anchor.getZ());
    }

    public static Direction toWorld(Direction local, Direction anchorFacing) {
        return rotateY(local, stepsFromNorth(anchorFacing));
    }

    public static float toWorldYaw(float localYaw, Direction anchorFacing) {
        return wrapDegrees(localYaw + anchorFacing.toYRot());
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped <= -180.0f) {
            wrapped += 360.0f;
        } else if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        }
        return wrapped;
    }
}
