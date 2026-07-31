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
        if (level.getBlockState(worldPos).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return ActionResult.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        if (ctx.recordedSubject() && action.expectedBlock().isPresent()
                && level.getBlockState(worldPos).getBlock() != action.expectedBlock().get().value()) {
            return ActionResult.fail(FailureReason.WRONG_BLOCK, action.localPos());
        }

        HeldItemLoan.Loan loan = HeldItemLoan.take(ctx.items(),
                ItemMatch.of(action.itemTemplate(), ctx.settings().item()), ctx.slot());
        if (loan == null) {
            return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
        }

        Direction face = ctx.placement().toWorld(action.localFace());
        Vec3 hit = Vec3.atCenterOf(worldPos)
                .add(LocalSpace.rotateY(action.localHitOffset(),
                        LocalSpace.stepsFromNorth(ctx.placement().facing())));

        FakePlayer owner = ctx.acquire(Vec3.atCenterOf(worldPos),
                face.getOpposite().toYRot(), 0.0f, action.hand(), loan.stack());
        try {
            InteractionResult result = owner.gameMode.useItemOn(owner, level,
                    owner.getItemInHand(action.hand()), action.hand(),
                    new BlockHitResult(hit, face, worldPos, action.inside()));

            return Interactions.finish(ctx, owner, action.hand(), loan, result, action.localPos());
        } finally {
            ctx.release(owner);
        }
    }
}
