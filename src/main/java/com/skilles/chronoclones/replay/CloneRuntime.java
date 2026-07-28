package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.entity.ChronoCloneEntity;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * One clone playing back a recording.
 */
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

    /** Which of the anchor's inventories this clone draws from and stores into. */
    public int index() {
        return index;
    }

    /** Phase offset for clone {@code i} of {@code n}: evenly distributed along the timeline. */
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
        // Whatever was being mined belonged to the action being left behind.
        clearMining();
    }

    // ------------------------------------------------------------------ mining

    /**
     * How far through the block it is currently breaking, 0 to 1.
     */
    private float miningProgress;
    private @Nullable BlockPos miningPos;

    public float miningProgress() {
        return miningProgress;
    }

    public @Nullable BlockPos miningPos() {
        return miningPos;
    }

    /**
     * Adds one tick of progress, restarting if the target moved.
     *
     * @return the new total
     */
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

    /** Wraps back to the start of the routine, resetting the action cursor with it. */
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
