package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.recording.ActionPose;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ChronoAction;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import com.skilles.chronoclones.platform.ClonePlayer;
import org.jspecify.annotations.Nullable;

public final class UseItemActionExecutor {

    private UseItemActionExecutor() {}

    /** One tick of a use. The result means nothing until {@code finished}. */
    public record Progress(ActionResult result, boolean finished) {

        static final Progress HOLDING = new Progress(ActionResult.OK, false);

        static Progress done(ActionResult result) {
            return new Progress(result, true);
        }
    }

    public static Progress tick(ActionContext ctx, ChronoAction.UseItem action, CloneRuntime runtime) {
        return runtime.isUsing() ? keepHolding(ctx, action, runtime) : begin(ctx, action, runtime);
    }

    private static Progress begin(ActionContext ctx, ChronoAction.UseItem action, CloneRuntime runtime) {
        ServerLevel level = ctx.level();

        HeldItemLoan.Loan loan = HeldItemLoan.take(ctx.items(),
                ItemMatch.of(action.itemTemplate(), ctx.settings().item()), ctx.slot());
        if (loan == null) {
            return Progress.done(ActionResult.fail(FailureReason.NO_ITEM, BlockPos.ZERO));
        }
        if (loan.stack().isEmpty()) {
            return Progress.done(ActionResult.OK);
        }

        ClonePlayer owner = acquire(ctx, action, loan.stack());

        if (owner.getCooldowns().isOnCooldown(loan.stack())) {
            return giveUp(ctx, action, owner, loan, FailureReason.ON_COOLDOWN);
        }

        HeldItemLoan.Loan ammo = lendAmmunition(ctx, owner, loan.stack());
        if (ammo == null && needsAmmunition(loan.stack())) {
            return giveUp(ctx, action, owner, loan, FailureReason.NO_AMMO);
        }

        InteractionResult result = owner.gameMode.useItem(owner, level,
                owner.getItemInHand(action.hand()), action.hand());

        // Both halves have to agree: an action recorded as instant finishes here however the item
        // behaves now, and one recorded as held only waits if the item actually started.
        if (action.isHeld() && owner.isUsingItem()) {
            runtime.beginUse(loan, ammo);
            return Progress.HOLDING;
        }

        // Recorded as held, nothing started, and nothing happened instead: the item has no duration
        // here and never will, so waiting cannot help.
        if (action.isHeld() && !result.consumesAction()) {
            owner.stopUsingItem();
            takeAmmunitionBack(ctx, owner, ammo);
            ActionResult unsupported = ActionResult.fail(FailureReason.UNSUPPORTED, BlockPos.ZERO);
            HeldItemLoan.giveBack(ctx.level(), ctx.anchorPos(), ctx.items(), loan,
                    owner.getItemInHand(action.hand()).copy());
            ctx.release(owner);
            return Progress.done(unsupported);
        }

        owner.stopUsingItem();
        takeAmmunitionBack(ctx, owner, ammo);
        ActionResult done = finish(ctx, action, owner, loan, result);
        ctx.release(owner);
        return Progress.done(done);
    }

    private static Progress keepHolding(ActionContext ctx, ChronoAction.UseItem action,
                                        CloneRuntime runtime) {
        HeldItemLoan.Loan loan = runtime.usingLoan();
        ClonePlayer owner = ctx.actor().current(ctx.cloneIndex());

        if (loan == null || owner == null || !owner.isUsingItem()) {
            if (loan != null && owner != null) {
                takeAmmunitionBack(ctx, owner, runtime.ammoLoan());
                HeldItemLoan.giveBack(ctx.level(), ctx.anchorPos(), ctx.items(), loan,
                        owner.getMainHandItem().copy());
            }
            runtime.clearUse();
            return Progress.done(ActionResult.fail(FailureReason.UNFINISHED, BlockPos.ZERO));
        }

        runtime.tickUse();

        // Vanilla counts this down in the private updatingUsingItem, and a bow reads its draw
        // straight off what is left.
        if (owner.useItemRemaining > 0) {
            owner.useItemRemaining--;
        }
        if (owner.useItemRemaining <= 0) {
            owner.completeUsingItem();
            return Progress.done(release(ctx, action, runtime, owner, loan));
        }

        if (runtime.usingTicks() < action.holdTicks()) {
            return Progress.HOLDING;
        }

        owner.releaseUsingItem();
        return Progress.done(release(ctx, action, runtime, owner, loan));
    }

    private static ActionResult release(ActionContext ctx, ChronoAction.UseItem action,
                                        CloneRuntime runtime, ClonePlayer owner,
                                        HeldItemLoan.Loan loan) {
        takeAmmunitionBack(ctx, owner, runtime.ammoLoan());
        runtime.clearUse();
        ActionResult result = finish(ctx, action, owner, loan, InteractionResult.CONSUME);
        ctx.release(owner);
        return result;
    }

    /** Vanilla looks for ammunition in the shooter's own inventory, not in the anchor. */
    private static HeldItemLoan.@Nullable Loan lendAmmunition(ActionContext ctx, ClonePlayer owner,
                                                              ItemStack weapon) {
        if (!(weapon.getItem() instanceof ProjectileWeaponItem projectile)) {
            return null;
        }
        Predicate<ItemStack> accepts = projectile.getAllSupportedProjectiles(weapon);

        for (int slot = 0; slot < ctx.items().size(); slot++) {
            ItemStack held = ctx.items().getItem(slot);
            if (held.isEmpty() || !accepts.test(held.copyWithCount(1))) {
                continue;
            }
            HeldItemLoan.Loan loan = HeldItemLoan.take(ctx.items(), held.getItem(),
                    SlotRule.prefer(slot));
            if (loan != null) {
                owner.getInventory().setItem(AMMUNITION_SLOT, loan.stack());
                return loan;
            }
        }
        return null;
    }

    /** Whatever was not fired. */
    private static void takeAmmunitionBack(ActionContext ctx, ClonePlayer owner,
                                           HeldItemLoan.@Nullable Loan ammo) {
        if (ammo == null) {
            return;
        }
        ItemStack left = owner.getInventory().getItem(AMMUNITION_SLOT).copy();
        owner.getInventory().setItem(AMMUNITION_SLOT, ItemStack.EMPTY);
        HeldItemLoan.giveBack(ctx.level(), ctx.anchorPos(), ctx.items(), ammo, left);
    }

    private static final int AMMUNITION_SLOT = 9;

    private static Progress giveUp(ActionContext ctx, ChronoAction.UseItem action, ClonePlayer owner,
                                   HeldItemLoan.Loan loan, FailureReason reason) {
        HeldItemLoan.giveBack(ctx.level(), ctx.anchorPos(), ctx.items(), loan,
                owner.getItemInHand(action.hand()).copy());
        ctx.release(owner);
        return Progress.done(ActionResult.fail(reason, BlockPos.ZERO));
    }

    private static boolean needsAmmunition(ItemStack stack) {
        return stack.getItem() instanceof ProjectileWeaponItem;
    }

    private static ActionResult finish(ActionContext ctx, ChronoAction.UseItem action,
                                       ClonePlayer owner, HeldItemLoan.Loan loan,
                                       InteractionResult result) {
        return Interactions.finish(ctx, owner, action.hand(), loan, result, BlockPos.ZERO);
    }

    private static ClonePlayer acquire(ActionContext ctx, ChronoAction.UseItem action, ItemStack held) {
        ActionPose pose = action.pose().orElse(ActionPose.OVER_THE_ANCHOR);
        return ctx.acquire(pose.worldPos(ctx.placement().origin(), ctx.placement().facing()),
                pose.worldYaw(ctx.placement().facing()), pose.pitch(), action.hand(), held);
    }
}
