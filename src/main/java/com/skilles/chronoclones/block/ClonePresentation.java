package com.skilles.chronoclones.block;

import java.util.List;

import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.replay.CloneRuntime;
import com.skilles.chronoclones.replay.MotionTrack;
import com.skilles.chronoclones.replay.Placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** What a running anchor looks like. None of it decides anything. */
final class ClonePresentation {

    private ClonePresentation() {}

    private static final int IDLE_PARTICLE_INTERVAL_TICKS = 30;

    static void sync(ServerLevel level, CloneRuntime runtime, MotionTrack track, Placement placement,
                     Direction facing, @Nullable Recording recording) {
        if (track.isEmpty()) {
            return;
        }
        ChronoCloneEntity clone = runtime.cloneEntity();
        if (clone == null || clone.isRemoved()) {
            clone = ChronoCloneEntity.create(level);
            if (recording != null) {
                clone.setAuthor(recording.authorId(), recording.authorName());
            }
            runtime.setClone(clone);
            level.addFreshEntity(clone);
        }

        Vec3 pos = track.worldPositionAt(runtime.playhead(), placement.origin(), facing);
        float yaw = track.worldYawAt(runtime.playhead(), facing);
        clone.driveTo(pos, yaw, track.pitchAt(runtime.playhead()));
        ChronoAction upcoming = upcomingAction(runtime, recording);
        boolean offhand = upcoming != null && upcoming.heldHand() == InteractionHand.OFF_HAND;
        ItemStack shown = upcoming == null ? ItemStack.EMPTY : upcoming.heldTemplate();
        clone.setHeldItem(offhand ? ItemStack.EMPTY : shown);
        clone.setOffhandItem(offhand ? shown : ItemStack.EMPTY);
    }

    private static @Nullable ChronoAction upcomingAction(CloneRuntime runtime,
                                                         @Nullable Recording recording) {
        if (recording == null) {
            return null;
        }
        List<TimedAction> actions = recording.actions();
        int cursor = runtime.actionCursor();
        return cursor >= actions.size() ? null : actions.get(cursor).action();
    }

    static void showCracks(ServerLevel level, CloneRuntime runtime, BlockPos anchorPos,
                           BlockPos worldPos, float progress) {
        int stage = Math.min((int) (progress * 10.0f), 9);
        level.destroyBlockProgress(breakerIdOf(runtime, anchorPos), worldPos, stage);
    }

    static void stopMining(ServerLevel level, CloneRuntime runtime, BlockPos anchorPos) {
        BlockPos was = runtime.miningPos();
        if (was != null) {
            level.destroyBlockProgress(breakerIdOf(runtime, anchorPos), was, -1);
        }
        runtime.clearMining();
    }

    private static int breakerIdOf(CloneRuntime runtime, BlockPos anchorPos) {
        ChronoCloneEntity clone = runtime.cloneEntity();
        return clone != null ? clone.getId() : anchorPos.hashCode();
    }

    static void idleParticles(ServerLevel level, BlockPos anchorPos) {
        if (level.getGameTime() % IDLE_PARTICLE_INTERVAL_TICKS != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                anchorPos.getX() + 0.5, anchorPos.getY() + 1.05, anchorPos.getZ() + 0.5,
                1, 0.15, 0.0, 0.15, 0.0);
    }

    static void failureParticles(ServerLevel level, BlockPos worldPos) {
        level.sendParticles(ParticleTypes.SMOKE,
                worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5,
                6, 0.2, 0.2, 0.2, 0.01);
    }
}
