package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Right-clicking a block, run through the server's own entry point.
 */
public final class UseBlockActionExecutor {

    private UseBlockActionExecutor() {}

    public static ActionResult execute(ActionContext ctx, ChronoAction.UseOnBlock action) {
        ServerLevel level = ctx.level();
        BlockPos worldPos = ctx.placement().toWorld(action.localPos());

        if (!ctx.placement().withinRadius(worldPos)) {
            return ActionResult.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }
        if (!level.isLoaded(worldPos)) {
            return ActionResult.fail(FailureReason.UNLOADED, action.localPos());
        }
        // An anchor must never operate another anchor: that is how a routine
        // reconfigures its neighbours.
        if (level.getBlockState(worldPos).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return ActionResult.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        // The block it was used on, when one was recorded and nobody has widened it: a hoe told to
        // till dirt should say so rather than striking whatever is standing there now.
        if (ctx.recordedSubject() && action.expectedBlock().isPresent()
                && level.getBlockState(worldPos).getBlock() != action.expectedBlock().get().value()) {
            return ActionResult.fail(FailureReason.WRONG_BLOCK, action.localPos());
        }

        HeldItemLoan.Loan loan = HeldItemLoan.take(ctx.items(), action.item().value(), ctx.slot());
        if (loan == null) {
            return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
        }

        // The sub-block hit point rotates with the anchor, as the block position does, so a
        // rotated routine still clicks the same corner of the same face.
        Direction face = ctx.placement().toWorld(action.localFace());
        Vec3 hit = Vec3.atCenterOf(worldPos)
                .add(LocalSpace.rotateY(action.localHitOffset(),
                        LocalSpace.stepsFromNorth(ctx.placement().facing())));

        FakePlayer owner = ctx.acquire(Vec3.atCenterOf(worldPos),
                face.getOpposite().toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.gameMode.useItemOn(owner, level,
                    owner.getMainHandItem(), action.hand(),
                    new BlockHitResult(hit, face, worldPos, action.inside()));

            return Interactions.finish(ctx, owner, loan, result, action.localPos());
        } finally {
            ctx.release(owner);
        }
    }
}
