package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.entity.ChronoCloneEntity;
import org.jspecify.annotations.Nullable;

/**
 * One clone playing back a recording.
 *
 * <p>Multiple runtimes share a single recording, separated by {@link #phaseOffset} so they string
 * out along the routine — the bucket-brigade visual the spec calls the showpiece.
 *
 * <p>{@link #playhead} is an integer tick index and the sole piece of motion state; position is
 * always recomputed from it rather than accumulated. {@link #actionCursor} advances monotonically
 * through the action list so due-action lookup stays O(1) per tick instead of rescanning.
 */
public final class CloneRuntime {

    private final int phaseOffset;
    private int playhead;
    private int actionCursor;
    private @Nullable ChronoCloneEntity ghost;

    public CloneRuntime(int phaseOffset) {
        this.phaseOffset = phaseOffset;
        this.playhead = phaseOffset;
        this.actionCursor = 0;
    }

    /** Phase offset for clone {@code i} of {@code n} — evenly distributed along the timeline. */
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

    public @Nullable ChronoCloneEntity ghost() {
        return ghost;
    }

    public void setGhost(@Nullable ChronoCloneEntity ghost) {
        this.ghost = ghost;
    }

    public void discardGhost() {
        if (ghost != null) {
            ghost.discard();
            ghost = null;
        }
    }
}
