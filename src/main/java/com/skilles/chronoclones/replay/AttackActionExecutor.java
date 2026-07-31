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

/**
 * Swinging at a creature, with a weapon the clone actually owns.
 */
public final class AttackActionExecutor {

    private AttackActionExecutor() {}

    /**
     * Swings at whatever the rule admits nearest the recorded point.
     *
     * @param sticky the entity this clone was already working on, or null to pick afresh
     */
    public static AttackOutcome execute(ActionContext ctx, ChronoAction.AttackEntity action,
                                        @Nullable LivingEntity sticky) {

        ServerLevel level = ctx.level();
        Vec3 worldPos = ctx.placement().toWorld(action.localPos());
        BlockPos blockPos = BlockPos.containing(worldPos);
        // Attack positions are continuous; diagnostics report the containing block.
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

        // The weapon the clone owns, not the one the recording was made with. Lent rather than
        // read, because a swing damages what it is holding: this was the last action taking what it
        // needed straight off the recording, which made one enchanted sword into every anchor's.
        HeldItemLoan.Loan loan = weaponFor(ctx, action);
        if (loan == null) {
            return AttackOutcome.missed(FailureReason.NO_ITEM, localBlock);
        }

        FakePlayer owner = ctx.acquire(worldPos, 0.0f, 0.0f,
                loan.stack());
        try {
            // A fake player never ticks, so its attack cooldown never refills on its own and every
            // swing would land at the uncharged 0.2x.
            AnchorFakePlayer.chargeAttack(owner);

            float before = target.getHealth() + target.getAbsorptionAmount();
            int hurtBefore = target.hurtTime;

            // Vanilla's own entry point, so enchantments, knockback, fire aspect, sweeping,
            // durability, the attack event and whatever mods hang off it all apply. Attributed to
            // the owner, so XP, loot tables and looting resolve as if they had swung it themselves.
            owner.attack(target);

            // A swing absorbed by invulnerability frames still found its target, so it is neither a
            // failure nor worth charging for; the action simply waits and swings again. attack()
            // reports nothing, so this is read off the target rather than returned.
            boolean landed = target.getHealth() + target.getAbsorptionAmount() < before
                    || target.hurtTime > hurtBefore
                    || !target.isAlive();

            return new AttackOutcome(ActionResult.OK, target.getId(), target.isAlive(), landed);
        } finally {
            // Whatever is left of it: a weapon that broke mid-swing comes home as nothing.
            HeldItemLoan.giveBack(level, ctx.anchorPos(), ctx.items(), loan,
                    owner.getMainHandItem().copy());
            ctx.release(owner);
        }
    }

    /**
     * The weapon this swing will use, taken out of the clone's own squares.
     *
     * <p>An attack recorded bare-handed asks for nothing and always gets it, which is how a routine
     * that punches sheep still punches sheep.
     *
     * @return the loan, or null if the anchor has nothing this rule accepts
     */
    private static HeldItemLoan.@Nullable Loan weaponFor(ActionContext ctx,
                                                         ChronoAction.AttackEntity action) {
        return switch (ctx.tool()) {
            case EXACT -> HeldItemLoan.take(ctx.items(), action.weaponTemplate().getItem(), ctx.slot());
            case SMART -> takeBestWeapon(ctx.items());
        };
    }

    /**
     * The hardest-hitting thing in the clone's own squares, or bare hands if nothing hits harder.
     *
     * <p>The slot rule is not consulted, for the same reason a smart break ignores it: it answers
     * which square to reach into, and the whole point of this rule is that the anchor decides.
     */
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

        // Nothing in here swings harder than a fist, so swing the fist.
        return best < 0
                ? HeldItemLoan.EMPTY_HANDED
                : HeldItemLoan.take(inventory, inventory.getResource(best).getItem(),
                        SlotRule.prefer(best));
    }

    /**
     * What this stack adds to a bare hand's damage.
     *
     * <p>Flat modifiers only. The multiplying operations scale a total this has no way to know, and
     * an item that only multiplies is not the melee weapon this is looking for.
     */
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

    /**
     * The entity already being worked on if it is still valid, else the recorded type nearest the
     * point, else the nearest thing at all.
     */
    private static @Nullable LivingEntity chooseTarget(ActionContext ctx,
                                                       ChronoAction.AttackEntity action,
                                                       Vec3 worldPos, @Nullable LivingEntity sticky) {

        TargetRule rule = ctx.target();
        boolean allowPvp = ChronoclonesConfig.ALLOW_PVP.get();
        AABB box = Targeting.boxAround(worldPos, rule);

        if (sticky != null && sticky.isAlive() && box.contains(sticky.position())) {
            return sticky;
        }

        // ChronoCloneEntity is a bare Entity, not a LivingEntity, so it cannot appear here.
        // Structural rather than a filter.
        List<LivingEntity> candidates = ctx.level().getEntitiesOfClass(LivingEntity.class, box, entity ->
                entity.isAlive()
                        && !entity.getUUID().equals(ctx.operator().id())
                        && (allowPvp || !(entity instanceof Player))
                        && rule.accepts(entity.getType()));

        return Targeting.choose(candidates, worldPos, action.expectedType().value(),
                ctx.recordedSubject());
    }
}
