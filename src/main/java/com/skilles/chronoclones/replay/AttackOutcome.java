package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

import net.minecraft.core.BlockPos;

/**
 * One swing, and what it found.
 *
 * @param targetId    the entity swung at, so a sticky action can stay on it
 * @param targetAlive whether it is still standing, which is what an until-dead action waits on
 * @param hitLanded   false while a target is inside its invulnerability window, which is not a
 *                    failure and must not be charged for
 */
public record AttackOutcome(ActionResult result, int targetId, boolean targetAlive,
                            boolean hitLanded) {

    public static final int NO_TARGET = -1;

    static AttackOutcome missed(FailureReason reason, BlockPos localPos) {
        return new AttackOutcome(ActionResult.fail(reason, localPos), NO_TARGET, false, false);
    }
}
