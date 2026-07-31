package com.skilles.chronoclones.recording;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where the player was standing and which way they were looking when they did something.
 *
 * <p>Stored in anchor-local space, like everything else a recording keeps, so a rotated anchor
 * rotates the stance along with the routine.
 *
 * <p>This is not the same fact as where the action happened, and conflating the two is what put
 * every thrown snowball, ender pearl and arrow at the anchor rather than where the clone was
 * standing, travelling flat regardless of how far up or down the player had been aiming.
 */
public record ActionPose(Vec3 localPos, float localYaw, float pitch) {

    /** Where a clone stands when a recording has nothing better to say: above its anchor. */
    public static final ActionPose OVER_THE_ANCHOR = new ActionPose(new Vec3(0.0, 1.0, 0.0), 0.0f, 0.0f);

    /** The world position this stance sits at, for an anchor at {@code origin} facing that way. */
    public Vec3 worldPos(BlockPos origin, Direction anchorFacing) {
        return LocalSpace.toWorld(localPos, origin, anchorFacing);
    }

    public float worldYaw(Direction anchorFacing) {
        return LocalSpace.toWorldYaw(localYaw, anchorFacing);
    }
}
