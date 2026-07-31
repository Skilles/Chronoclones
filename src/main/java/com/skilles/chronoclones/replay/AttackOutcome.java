package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

import net.minecraft.core.BlockPos;

/** One swing, and what it found. */
public record AttackOutcome(ActionResult result, int targetId, boolean targetAlive,
                            boolean hitLanded) {
    public static final int NO_TARGET = -1;

    static AttackOutcome missed(FailureReason reason, BlockPos localPos) {
        return new AttackOutcome(ActionResult.fail(reason, localPos), NO_TARGET, false, false);
    }
}
