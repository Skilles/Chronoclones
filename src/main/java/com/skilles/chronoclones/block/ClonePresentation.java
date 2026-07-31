package com.skilles.chronoclones.block;

import java.util.List;

import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.replay.CloneRuntime;
import com.skilles.chronoclones.replay.MotionTrack;
import com.skilles.chronoclones.replay.Placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a running anchor looks like: the clone entities, where they stand, what they appear to be
 * holding, the cracks on what they are digging, and the drift above the block itself.
 *
 * <p>None of it decides anything. Every method here can be skipped without changing what a routine
 * does, which is exactly why it is worth keeping apart from the code that does decide.
 */
final class ClonePresentation {

    private ClonePresentation() {}

    private static final int IDLE_PARTICLE_INTERVAL_TICKS = 30;

    /**
     * Puts this clone where its playhead says it should be, spawning it if it is not there.
     */
    static void sync(ServerLevel level, CloneRuntime runtime, MotionTrack track, Placement placement,
                     Direction facing, @Nullable Recording recording) {
        if (track.isEmpty()) {
            return;
        }
        ChronoCloneEntity clone = runtime.cloneEntity();
        if (clone == null || clone.isRemoved()) {
            clone = ChronoCloneEntity.create(level);
            // The author, not the owner: authorship only decides the skin.
            if (recording != null) {
                clone.setAuthor(recording.authorId(), recording.authorName());
            }
            runtime.setClone(clone);
            level.addFreshEntity(clone);
        }

        Vec3 pos = track.worldPositionAt(runtime.playhead(), placement.origin(), facing);
        float yaw = track.worldYawAt(runtime.playhead(), facing);
        clone.driveTo(pos, yaw, track.pitchAt(runtime.playhead()));
        clone.setHeldItem(upcomingHeldItem(runtime, recording));
    }

    /**
     * The item for the action the clone is walking towards.
     */
    private static ItemStack upcomingHeldItem(CloneRuntime runtime, @Nullable Recording recording) {
        if (recording == null) {
            return ItemStack.EMPTY;
        }
        List<TimedAction> actions = recording.actions();
        int cursor = runtime.actionCursor();
        if (cursor >= actions.size()) {
            return ItemStack.EMPTY;
        }
        return actions.get(cursor).action().heldTemplate();
    }

    /**
     * The cracking overlay, keyed to the clone doing the digging.
     */
    static void showCracks(ServerLevel level, CloneRuntime runtime, BlockPos anchorPos,
                           BlockPos worldPos, float progress) {
        int stage = Math.min((int) (progress * 10.0f), 9);
        level.destroyBlockProgress(breakerIdOf(runtime, anchorPos), worldPos, stage);
    }

    /** Takes the cracks back off, whether the block was finished or abandoned. */
    static void stopMining(ServerLevel level, CloneRuntime runtime, BlockPos anchorPos) {
        BlockPos was = runtime.miningPos();
        if (was != null) {
            // -1 is vanilla's "no longer breaking this"; without it the cracks persist.
            level.destroyBlockProgress(breakerIdOf(runtime, anchorPos), was, -1);
        }
        runtime.clearMining();
    }

    private static int breakerIdOf(CloneRuntime runtime, BlockPos anchorPos) {
        ChronoCloneEntity clone = runtime.cloneEntity();
        return clone != null ? clone.getId() : anchorPos.hashCode();
    }

    /**
     * Idle particles above a running anchor.
     */
    static void idleParticles(ServerLevel level, BlockPos anchorPos) {
        if (level.getGameTime() % IDLE_PARTICLE_INTERVAL_TICKS != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                anchorPos.getX() + 0.5, anchorPos.getY() + 1.05, anchorPos.getZ() + 0.5,
                1, 0.15, 0.0, 0.15, 0.0);
    }

    /** Smoke over whatever an action refused to do, so a stuck routine is visible in-world. */
    static void failureParticles(ServerLevel level, BlockPos worldPos) {
        level.sendParticles(ParticleTypes.SMOKE,
                worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5,
                6, 0.2, 0.2, 0.2, 0.01);
    }
}
