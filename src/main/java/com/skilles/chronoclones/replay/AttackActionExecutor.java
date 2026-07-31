package com.skilles.chronoclones.replay;

import java.util.List;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.ActionSettings.ToolRule;
import com.skilles.chronoclones.recording.ChronoAction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

public final class AttackActionExecutor {

    private AttackActionExecutor() {}

    public static AttackOutcome execute(ActionContext ctx, ChronoAction.AttackEntity action,
                                        @Nullable LivingEntity sticky) {
        ServerLevel level = ctx.level();
        Vec3 worldPos = ctx.placement().toWorld(action.localPos());
        BlockPos blockPos = BlockPos.containing(worldPos);
        BlockPos localBlock = BlockPos.containing(action.localPos());

        if (!ctx.placement().withinRadius(blockPos)) {
            return AttackOutcome.missed(FailureReason.OUT_OF_RANGE, localBlock);
        }
        if (!level.isLoaded(blockPos)) {
            return AttackOutcome.missed(FailureReason.UNLOADED, localBlock);
        }

        LivingEntity target = chooseTarget(ctx, action, worldPos, sticky);
        if (target == null) {
            return AttackOutcome.missed(FailureReason.NO_TARGET, localBlock);
        }

        HeldItemLoan.Loan loan = weaponFor(ctx, action);
        if (loan == null) {
            return AttackOutcome.missed(FailureReason.NO_ITEM, localBlock);
        }

        FakePlayer owner = ctx.acquire(worldPos, 0.0f, 0.0f,
                loan.stack());
        try {
            AnchorFakePlayer.chargeAttack(owner);

            float before = target.getHealth() + target.getAbsorptionAmount();
            int hurtBefore = target.hurtTime;

            owner.attack(target);

            // attack() reports nothing, so whether the hit landed is read off the target. A swing
            // absorbed by invulnerability frames is not a failure and is not charged for.
            boolean landed = target.getHealth() + target.getAbsorptionAmount() < before
                    || target.hurtTime > hurtBefore
                    || !target.isAlive();

            return new AttackOutcome(ActionResult.OK, target.getId(), target.isAlive(), landed);
        } finally {
            HeldItemLoan.giveBack(level, ctx.anchorPos(), ctx.items(), loan,
                    owner.getMainHandItem().copy());
            ctx.release(owner);
        }
    }

    private static HeldItemLoan.@Nullable Loan weaponFor(ActionContext ctx,
                                                         ChronoAction.AttackEntity action) {
        return switch (ctx.tool()) {
            case EXACT -> HeldItemLoan.take(ctx.items(), action.weaponTemplate().getItem(), ctx.slot());
            case SMART -> takeBestWeapon(ctx.items());
        };
    }

    /** The slot rule is ignored here for the same reason it is for a smart tool. */
    private static HeldItemLoan.@Nullable Loan takeBestWeapon(ResourceHandler<ItemResource> inventory) {
        int best = -1;
        double bestDamage = 0.0;

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemResource resource = inventory.getResource(slot);
            if (resource.isEmpty() || inventory.getAmountAsInt(slot) <= 0) {
                continue;
            }
            double damage = meleeDamageOf(resource.toStack(1));
            if (damage > bestDamage) {
                best = slot;
                bestDamage = damage;
            }
        }

        return best < 0
                ? HeldItemLoan.EMPTY_HANDED
                : HeldItemLoan.take(inventory, inventory.getResource(best).getItem(),
                        SlotRule.prefer(best));
    }

    /** Flat modifiers only: the multiplying ones scale a total this cannot know. */
    private static double meleeDamageOf(ItemStack stack) {
        double[] total = {0.0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ATTACK_DAMAGE.value()
                    && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                total[0] += modifier.amount();
            }
        });
        return total[0];
    }

    private static @Nullable LivingEntity chooseTarget(ActionContext ctx,
                                                       ChronoAction.AttackEntity action,
                                                       Vec3 worldPos, @Nullable LivingEntity sticky) {
        TargetRule rule = ctx.target();
        boolean allowPvp = ChronoclonesConfig.ALLOW_PVP.get();
        AABB box = Targeting.boxAround(worldPos, rule);

        if (sticky != null && sticky.isAlive() && box.contains(sticky.position())) {
            return sticky;
        }

        List<LivingEntity> candidates = ctx.level().getEntitiesOfClass(LivingEntity.class, box, entity ->
                entity.isAlive()
                        && !entity.getUUID().equals(ctx.operator().id())
                        && (allowPvp || !(entity instanceof Player))
                        && rule.accepts(entity.getType()));

        return Targeting.choose(candidates, worldPos, action.expectedType().value(),
                ctx.recordedSubject());
    }
}
