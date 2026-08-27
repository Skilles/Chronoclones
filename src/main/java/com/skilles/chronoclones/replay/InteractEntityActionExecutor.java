package com.skilles.chronoclones.replay;

import java.util.List;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.ChronoAction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.skilles.chronoclones.platform.ClonePlayer;

public final class InteractEntityActionExecutor {

    private InteractEntityActionExecutor() {}

    public static ActionResult execute(ActionContext ctx, ChronoAction.InteractEntity action) {
        ServerLevel level = ctx.level();
        Vec3 worldPos = ctx.placement().toWorld(action.localPos());
        BlockPos localBlock = BlockPos.containing(action.localPos());

        if (!ctx.placement().withinRadius(worldPos)) {
            return ActionResult.fail(FailureReason.OUT_OF_RANGE, localBlock);
        }
        if (!level.isLoaded(BlockPos.containing(worldPos))) {
            return ActionResult.fail(FailureReason.UNLOADED, localBlock);
        }

        boolean allowPvp = ChronoclonesConfig.allowPvp();
        TargetRule rule = ctx.target();
        AABB box = Targeting.boxAround(worldPos, rule);
        List<Entity> candidates = level.getEntitiesOfClass(Entity.class, box, entity ->
                entity.isAlive()
                        && !(entity instanceof ChronoCloneEntity)
                        && !entity.getUUID().equals(ctx.operator().id())
                        && (allowPvp || !(entity instanceof Player))
                        && rule.accepts(entity.getType()));

        Entity target = Targeting.choose(candidates, worldPos, action.expectedType().value(),
                ctx.recordedSubject());
        if (target == null) {
            return ActionResult.fail(FailureReason.NO_TARGET, localBlock);
        }

        HeldItemLoan.Loan loan = HeldItemLoan.take(ctx.items(),
                ItemMatch.of(action.itemTemplate(), ctx.settings().item()), ctx.slot());
        if (loan == null) {
            return ActionResult.fail(FailureReason.NO_ITEM, localBlock);
        }

        ClonePlayer owner = ctx.acquire(worldPos,
                ctx.placement().facing().toYRot(), 0.0f, action.hand(), loan.stack());
        try {
            //? if >=26 {
            InteractionResult result = owner.interactOn(target, action.hand(),
                    worldPos.subtract(target.position()));
            //?} else {
            /*InteractionResult result = owner.interactOn(target, action.hand());
            *///?}
            return Interactions.finish(ctx, owner, action.hand(), loan, result, localBlock);
        } finally {
            ctx.release(owner);
        }
    }
}
