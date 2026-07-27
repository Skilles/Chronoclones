package com.skilles.chronoclones.recording;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Conversion between world space and anchor-local space, so one recording rotates onto anchors
 * facing any direction.
 *
 * <p>Cardinal rotations only, because arbitrary yaw does not map the block grid onto itself.
 * Local space is "as if facing north", and a quarter-turn clockwise is
 * {@code (x,z) -> (-z,x)}, which is also +90 degrees of yaw.
 */
public final class LocalSpace {

    private LocalSpace() {}

    /** Mandatory at record start; see the class docs. */
    public static Direction snapToCardinal(float yaw) {
        return Direction.fromYRot(yaw);
    }

    /** Quarter-turns clockwise from NORTH. NORTH=0, EAST=1, SOUTH=2, WEST=3. */
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

    // ------------------------------------------------------------------ rotation primitives

    /** Rotates by {@code steps} quarter-turns clockwise about Y. Pure integer math. */
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

    /** Rotates by {@code steps} quarter-turns clockwise about Y. Exact for the same four cases. */
    public static Vec3 rotateY(Vec3 vec, int steps) {
        return switch (normalizeSteps(steps)) {
            case 0 -> new Vec3(vec.x, vec.y, vec.z);
            case 1 -> new Vec3(-vec.z, vec.y, vec.x);
            case 2 -> new Vec3(-vec.x, vec.y, -vec.z);
            default -> new Vec3(vec.z, vec.y, -vec.x);
        };
    }

    /** Rotates a direction by {@code steps} quarter-turns clockwise. Vertical directions are fixed. */
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

    // ------------------------------------------------------------------ world -> local

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

    /** Pitch is rotation-invariant about Y and is deliberately not converted. */
    public static float toLocalYaw(float worldYaw, Direction originFacing) {
        return wrapDegrees(worldYaw - originFacing.toYRot());
    }

    // ------------------------------------------------------------------ local -> world

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

    /** Normalises to (-180, 180], matching how Minecraft stores entity yaw. */
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
