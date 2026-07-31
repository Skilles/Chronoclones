package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.util.FakePlayer;

/** The tail every right-click shares: give the borrowed item back, read the result. */
final class Interactions {

    private Interactions() {}

    static ActionResult finish(ActionContext ctx, FakePlayer owner, InteractionHand hand,
                               HeldItemLoan.Loan loan, InteractionResult result, BlockPos localPos) {
        HeldItemLoan.giveBack(ctx.level(), ctx.anchorPos(), ctx.items(), loan,
                owner.getItemInHand(hand).copy());

        return result.consumesAction()
                ? ActionResult.OK
                : ActionResult.fail(FailureReason.REFUSED, localPos);
    }
}
