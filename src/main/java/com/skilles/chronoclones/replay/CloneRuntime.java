package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.entity.ChronoCloneEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/** One clone playing back a recording: its playhead, cursor, and what it is part way through. */
public final class CloneRuntime {

    private final int index;
    private final int phaseOffset;
    private int playhead;
    private int actionCursor;
    private @Nullable ChronoCloneEntity clone;

    public CloneRuntime(int index, int phaseOffset) {
        this.index = index;
        this.phaseOffset = phaseOffset;
        this.playhead = phaseOffset;
        this.actionCursor = 0;
    }

    public int index() {
        return index;
    }

    public static int phaseOffsetFor(int index, int count, int lengthTicks) {
        if (count <= 0) {
            return 0;
        }
        return (int) ((long) lengthTicks * index / count);
    }

    public int playhead() {
        return playhead;
    }

    public int phaseOffset() {
        return phaseOffset;
    }

    public int actionCursor() {
        return actionCursor;
    }

    public void advance(int ticks) {
        playhead += ticks;
    }

    public void consumeAction() {
        actionCursor++;
        clearMining();
    }

    private float miningProgress;
    private @Nullable BlockPos miningPos;

    public float miningProgress() {
        return miningProgress;
    }

    public @Nullable BlockPos miningPos() {
        return miningPos;
    }

    /** Restarts if the target moved. */
    public float mine(BlockPos pos, float perTick) {
        if (!pos.equals(miningPos)) {
            miningPos = pos;
            miningProgress = 0.0f;
        }
        miningProgress += perTick;
        return miningProgress;
    }

    public void clearMining() {
        miningProgress = 0.0f;
        miningPos = null;
    }

    private HeldItemLoan.@Nullable Loan usingLoan;

    private HeldItemLoan.@Nullable Loan ammoLoan;
    private int usingTicks;

    public HeldItemLoan.@Nullable Loan usingLoan() {
        return usingLoan;
    }

    public HeldItemLoan.@Nullable Loan ammoLoan() {
        return ammoLoan;
    }

    public boolean isUsing() {
        return usingLoan != null;
    }

    public int usingTicks() {
        return usingTicks;
    }

    public void beginUse(HeldItemLoan.Loan loan, HeldItemLoan.@Nullable Loan ammo) {
        usingLoan = loan;
        ammoLoan = ammo;
        usingTicks = 0;
    }

    public void tickUse() {
        usingTicks++;
    }

    public void clearUse() {
        usingLoan = null;
        ammoLoan = null;
        usingTicks = 0;
    }

    private int targetId = NO_TARGET;
    private int targetTicks;

    public static final int NO_TARGET = -1;

    public @Nullable LivingEntity target(ServerLevel level) {
        if (targetId == NO_TARGET) {
            return null;
        }
        return level.getEntity(targetId) instanceof LivingEntity living && living.isAlive()
                ? living
                : null;
    }

    public void setTarget(int entityId) {
        if (entityId != targetId) {
            targetTicks = 0;
        }
        targetId = entityId;
    }

    public int targetTicks() {
        return targetTicks;
    }

    public void awaitTarget() {
        targetTicks++;
    }

    public void releaseTarget() {
        targetTicks = 0;
    }

    public void loop(int lengthTicks) {
        if (lengthTicks <= 0) {
            playhead = 0;
            actionCursor = 0;
            return;
        }
        playhead -= lengthTicks;
        if (playhead < 0 || playhead >= lengthTicks) {
            playhead = Math.floorMod(playhead, lengthTicks);
        }
        actionCursor = 0;
    }

    public @Nullable ChronoCloneEntity cloneEntity() {
        return clone;
    }

    public void setClone(@Nullable ChronoCloneEntity clone) {
        this.clone = clone;
    }

    public void discardClone() {
        if (clone != null) {
            clone.discard();
            clone = null;
        }
    }
}
