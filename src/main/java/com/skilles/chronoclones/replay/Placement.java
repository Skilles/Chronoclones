package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.recording.LocalSpace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where an anchor's routine lands in the world.
 *
 * <p>Two positions, and keeping them apart is a safety property rather than tidiness.
 *
 * <p>{@link #origin} is where the routine is drawn from — the anchor, plus whatever offset its owner
 * has nudged it by. {@link #anchorPos} is the block itself, and it is the <em>only</em> thing
 * {@code MAX_RADIUS} is ever measured from. Measuring the radius from the offset origin instead would
 * turn nudging into a way to make an anchor act arbitrarily far away, walking straight past the
 * protection checks — those fire at the final position, so by then the reach has already been granted.
 *
 * <p>They are bundled into one value precisely so the two can never be swapped at a call site. A pair
 * of loose {@code BlockPos} parameters would compile just as happily in the wrong order.
 */
public record Placement(BlockPos anchorPos, BlockPos origin, Direction facing) {

    /** An anchor with no offset: origin and anchor are the same block. */
    public static Placement of(BlockPos anchorPos, Direction facing) {
        return new Placement(anchorPos, anchorPos, facing);
    }

    /**
     * An anchor whose routine has been nudged.
     *
     * <p>The offset is in local space, so it rotates with the anchor exactly as the routine does —
     * everything else in {@link LocalSpace} works that way, and an offset that did not would make a
     * nudged routine mean something different on an east-facing anchor.
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
