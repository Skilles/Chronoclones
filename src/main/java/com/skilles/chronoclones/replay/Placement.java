package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.recording.LocalSpace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Where a routine lands in the world, and what its radius is measured from. */
public record Placement(BlockPos anchorPos, BlockPos origin, Direction facing) {

    public static Placement of(BlockPos anchorPos, Direction facing) {
        return new Placement(anchorPos, anchorPos, facing);
    }

    public static Placement of(BlockPos anchorPos, Direction facing, BlockPos localOffset) {
        return of(anchorPos, facing, localOffset, 0);
    }

    /**
     * The offset stays in the anchor block's own space, so rotating spins the routine in place
     * around its current origin instead of swinging it around the anchor.
     */
    public static Placement of(BlockPos anchorPos, Direction facing, BlockPos localOffset,
                               int rotationSteps) {
        return new Placement(anchorPos, LocalSpace.toWorld(localOffset, anchorPos, facing),
                LocalSpace.rotateY(facing, rotationSteps));
    }

    public BlockPos toWorld(BlockPos local) {
        return LocalSpace.toWorld(local, origin, facing);
    }

    public Vec3 toWorld(Vec3 local) {
        return LocalSpace.toWorld(local, origin, facing);
    }

    public Direction toWorld(Direction local) {
        return LocalSpace.toWorld(local, facing);
    }

    public boolean withinRadius(BlockPos worldPos) {
        return worldPos.closerThan(anchorPos, ChronoclonesConfig.maxRadius());
    }

    public boolean withinRadius(Vec3 worldPos) {
        return withinRadius(BlockPos.containing(worldPos));
    }
}
