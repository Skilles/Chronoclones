package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

import net.minecraft.core.BlockPos;

/** Outcome of one attempted action. NONE means it happened. */
public record ActionResult(FailureReason reason, BlockPos localPos) {

    public static final ActionResult OK = new ActionResult(FailureReason.NONE, BlockPos.ZERO);

    public static ActionResult fail(FailureReason reason, BlockPos localPos) {
        return new ActionResult(reason, localPos);
    }

    public boolean succeeded() {
        return reason == FailureReason.NONE;
    }
}
