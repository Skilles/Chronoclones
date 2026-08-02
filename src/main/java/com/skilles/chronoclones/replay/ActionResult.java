package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

import net.minecraft.core.BlockPos;

/** Outcome of one attempted action. NONE means it happened. */
public record ActionResult(FailureReason reason, BlockPos localPos, int step) {

    /** A failure outside any container has no step to name. */
    public static final int NO_STEP = -1;

    public static final ActionResult OK = new ActionResult(FailureReason.NONE, BlockPos.ZERO, NO_STEP);

    public static ActionResult fail(FailureReason reason, BlockPos localPos) {
        return fail(reason, localPos, NO_STEP);
    }

    public static ActionResult fail(FailureReason reason, BlockPos localPos, int step) {
        return new ActionResult(reason, localPos, step);
    }

    public boolean succeeded() {
        return reason == FailureReason.NONE;
    }
}
