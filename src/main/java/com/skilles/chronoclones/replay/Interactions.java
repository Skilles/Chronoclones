package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
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
    static ActionResult finish(ActionContext ctx, FakePlayer owner, InteractionHand hand,
                               HeldItemLoan.Loan loan, InteractionResult result, BlockPos localPos) {
        // From the hand it was lent to. A bucket filled in the off hand comes home from the off
        // hand, and reading the main one would put back what was never borrowed.
        HeldItemLoan.giveBack(ctx.level(), ctx.anchorPos(), ctx.items(), loan,
                owner.getItemInHand(hand).copy());

        // What the interaction actually said. "Nothing to act on" used to be the answer to every
        // one of these, which told a player whose shears were on cooldown and whose crossbow was
        // empty the same untrue thing.
        return result.consumesAction()
                ? ActionResult.OK
                : ActionResult.fail(FailureReason.REFUSED, localPos);
    }
}
