package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.recording.LocalSpace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where an anchor's routine lands in the world.
 *
 * <p>Two positions, never one: geometry comes from {@code origin}, which the nudge keys move,
 * but the radius is measured from {@code anchorPos}.
 */
public record Placement(BlockPos anchorPos, BlockPos origin, Direction facing) {

    /** An anchor with no offset: origin and anchor are the same block. */
    public static Placement of(BlockPos anchorPos, Direction facing) {
        return new Placement(anchorPos, anchorPos, facing);
    }

    /**
     * An anchor whose routine has been nudged.
     */
    public static Placement of(BlockPos anchorPos, Direction facing, BlockPos localOffset) {
        return new Placement(anchorPos, LocalSpace.toWorld(localOffset, anchorPos, facing), facing);
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

    /** Always from the anchor block. See the class docs for why this is not a detail. */
    public boolean withinRadius(BlockPos worldPos) {
        return worldPos.closerThan(anchorPos, ChronoclonesConfig.MAX_RADIUS.getAsInt());
    }

    public boolean withinRadius(Vec3 worldPos) {
        return withinRadius(BlockPos.containing(worldPos));
    }
}
