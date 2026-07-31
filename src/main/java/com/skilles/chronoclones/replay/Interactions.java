package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * What every right-click shares: giving the borrowed item back, and reading the answer.
 */
final class Interactions {

    private Interactions() {}

    /**
     * Returns the borrowed item and reports whether the interaction did anything.
     */
    static ActionResult finish(ActionContext ctx, FakePlayer owner, HeldItemLoan.Loan loan,
                               InteractionResult result, BlockPos localPos) {
        HeldItemLoan.giveBack(ctx.level(), ctx.anchorPos(), ctx.items(), loan,
                owner.getMainHandItem().copy());

        // PASS means nothing was interactable, the same shape of failure as swinging at empty air,
        // and reported so a routine that has drifted out of alignment says so.
        return result.consumesAction() ? ActionResult.OK : ActionResult.fail(FailureReason.NO_TARGET, localPos);
    }
}
