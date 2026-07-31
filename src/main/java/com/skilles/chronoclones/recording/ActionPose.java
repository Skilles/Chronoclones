package com.skilles.chronoclones.recording;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Where the player stood and which way they looked, in anchor-local space. */
public record ActionPose(Vec3 localPos, float localYaw, float pitch) {

    public static final ActionPose OVER_THE_ANCHOR = new ActionPose(new Vec3(0.0, 1.0, 0.0), 0.0f, 0.0f);

    public Vec3 worldPos(BlockPos origin, Direction anchorFacing) {
        return LocalSpace.toWorld(localPos, origin, anchorFacing);
    }

    public float worldYaw(Direction anchorFacing) {
        return LocalSpace.toWorldYaw(localYaw, anchorFacing);
    }
}
