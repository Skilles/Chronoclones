package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ChronoAction;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Right-clicking with nothing targeted: a snowball thrown, a bow drawn and loosed, a meal eaten.
 *
 * <p>Two shapes, told apart by what the recording captured. A use the player finished the instant
 * they clicked runs and is done. A use they held -- every bow, crossbow, trident, shield, spyglass
 * and mouthful of food -- is one action spread over as many ticks as they held it, and the clone
 * has to hold it for the same length of time or the thing never happens at all.
 */
public final class UseItemActionExecutor {

    private UseItemActionExecutor() {}

    /**
     * One tick of a use.
     *
     * @param result   what to report, meaningful only once {@code finished}
     * @param finished false while the clone is still holding the item down
     */
    public record Progress(ActionResult result, boolean finished) {

        static final Progress HOLDING = new Progress(ActionResult.OK, false);

        static Progress done(ActionResult result) {
            return new Progress(result, true);
        }
    }

    /**
     * Advances this use by one tick, starting it if it has not started.
     */
    public static Progress tick(ActionContext ctx, ChronoAction.UseItem action, CloneRuntime runtime) {
        return runtime.isUsing() ? keepHolding(ctx, action, runtime) : begin(ctx, action, runtime);
    }

    /**
     * Clicks the item, and either finishes there or settles in to hold it.
     */
    private static Progress begin(ActionContext ctx, ChronoAction.UseItem action, CloneRuntime runtime) {
        ServerLevel level = ctx.level();

        HeldItemLoan.Loan loan = HeldItemLoan.take(ctx.items(), action.item().value(), ctx.slot());
        if (loan == null) {
            return Progress.done(ActionResult.fail(FailureReason.NO_ITEM, BlockPos.ZERO));
        }
        if (loan.stack().isEmpty()) {
            // Right-clicking air with an empty hand does nothing.
            return Progress.done(ActionResult.OK);
        }

        FakePlayer owner = acquire(ctx, loan.stack());

        // A bow with nothing to fire simply refuses to draw, and the ammunition it looks for is in
        // its own inventory rather than the anchor's -- so whatever it shoots is lent too.
        HeldItemLoan.Loan ammo = lendAmmunition(ctx, owner, loan.stack());

        InteractionResult result = owner.gameMode.useItem(owner, level,
                owner.getMainHandItem(), action.hand());

        // Held items report their duration by starting a use rather than by finishing one. Both
        // halves have to agree: an action recorded as instant is finished here however the item
        // behaves now, and one recorded as held only waits if the item actually started.
        if (action.isHeld() && owner.isUsingItem()) {
            runtime.beginUse(loan, ammo);
            return Progress.HOLDING;
        }

        // Nothing is being held, so nothing can be waited for -- including an item that used to
        // have a duration and no longer does.
        owner.stopUsingItem();
        takeAmmunitionBack(ctx, owner, ammo);
        ActionResult done = finish(ctx, owner, loan, result);
        ctx.release(owner);
        return Progress.done(done);
    }

    /**
     * Counts down a use already in progress, and lets go when the recording says the player did.
     */
    private static Progress keepHolding(ActionContext ctx, ChronoAction.UseItem action,
                                        CloneRuntime runtime) {
        HeldItemLoan.Loan loan = runtime.usingLoan();
        FakePlayer owner = ctx.actor().current(ctx.cloneIndex());

        // The player went away underneath us -- an anchor unloaded and reloaded mid-draw. There is
        // nothing left holding anything, so give the item back and call it a failure.
        if (loan == null || owner == null || !owner.isUsingItem()) {
            if (loan != null && owner != null) {
                takeAmmunitionBack(ctx, owner, runtime.ammoLoan());
                HeldItemLoan.giveBack(ctx.level(), ctx.anchorPos(), ctx.items(), loan,
                        owner.getMainHandItem().copy());
            }
            runtime.clearUse();
            return Progress.done(ActionResult.fail(FailureReason.NO_TARGET, BlockPos.ZERO));
        }

        runtime.tickUse();

        // Vanilla's own countdown, which lives in a private method on a player that never ticks.
        // A bow reads its draw straight off what is left here, so skipping this looses at nothing.
        if (owner.useItemRemaining > 0) {
            owner.useItemRemaining--;
        }
        if (owner.useItemRemaining <= 0) {
            // Ran its full duration: food is eaten, a potion drunk, the use completes itself.
            owner.completeUsingItem();
            return Progress.done(release(ctx, runtime, owner, loan));
        }

        if (runtime.usingTicks() < action.holdTicks()) {
            return Progress.HOLDING;
        }

        // As long as the player held it, so let go exactly as they did: this is what fires a bow
        // at the draw it was recorded at rather than at full or at none.
        owner.releaseUsingItem();
        return Progress.done(release(ctx, runtime, owner, loan));
    }

    /** Hands the item and whatever is left of its ammunition back, and lets the clone move on. */
    private static ActionResult release(ActionContext ctx, CloneRuntime runtime, FakePlayer owner,
                                        HeldItemLoan.Loan loan) {
        takeAmmunitionBack(ctx, owner, runtime.ammoLoan());
        runtime.clearUse();
        ActionResult result = finish(ctx, owner, loan, InteractionResult.CONSUME);
        ctx.release(owner);
        return result;
    }

    /**
     * Puts what this weapon fires into the player's own squares, so it has something to fire.
     *
     * <p>Vanilla looks for ammunition in the shooter's inventory, not in whatever happens to be
     * nearby, so a clone drawing a bow out of an anchor stocked with arrows would find none and
     * quietly decline to draw at all.
     *
     * @return the loan to give back afterwards, or null if nothing was lent
     */
    private static HeldItemLoan.@Nullable Loan lendAmmunition(ActionContext ctx, FakePlayer owner,
                                                              ItemStack weapon) {
        if (!(weapon.getItem() instanceof ProjectileWeaponItem projectile)) {
            return null;
        }
        Predicate<ItemStack> accepts = projectile.getAllSupportedProjectiles(weapon);

        for (int slot = 0; slot < ctx.items().size(); slot++) {
            ItemResource resource = ctx.items().getResource(slot);
            int amount = ctx.items().getAmountAsInt(slot);
            if (resource.isEmpty() || amount <= 0 || !accepts.test(resource.toStack(1))) {
                continue;
            }
            HeldItemLoan.Loan loan = HeldItemLoan.take(ctx.items(), resource.getItem(),
                    SlotRule.prefer(slot));
            if (loan != null) {
                owner.getInventory().setItem(AMMUNITION_SLOT, loan.stack());
                return loan;
            }
        }
        return null;
    }

    /** Takes back whatever was not fired. */
    private static void takeAmmunitionBack(ActionContext ctx, FakePlayer owner,
                                           HeldItemLoan.@Nullable Loan ammo) {
        if (ammo == null) {
            return;
        }
        ItemStack left = owner.getInventory().getItem(AMMUNITION_SLOT).copy();
        owner.getInventory().setItem(AMMUNITION_SLOT, ItemStack.EMPTY);
        HeldItemLoan.giveBack(ctx.level(), ctx.anchorPos(), ctx.items(), ammo, left);
    }

    /** A square of the player's own inventory, kept out of the way of the hand. */
    private static final int AMMUNITION_SLOT = 9;

    /** Gives back what came home, and says whether the interaction did anything. */
    private static ActionResult finish(ActionContext ctx, FakePlayer owner, HeldItemLoan.Loan loan,
                                       InteractionResult result) {
        return Interactions.finish(ctx, owner, loan, result, BlockPos.ZERO);
    }

    private static FakePlayer acquire(ActionContext ctx, ItemStack held) {
        return ctx.acquire(Vec3.atCenterOf(ctx.anchorPos()).add(0.0, 1.0, 0.0),
                ctx.placement().facing().toYRot(), 0.0f, held);
    }
}
