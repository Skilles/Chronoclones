package com.skilles.chronoclones.replay;

import java.util.List;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Breaking a block: whether it may begin, how fast it goes, and what it leaves behind.
 */
public final class BreakActionExecutor {

    private BreakActionExecutor() {}

    /**
     * Whether a break may begin, without beginning it.
     *
     * @return the failure, or {@code null} if mining may proceed
     */
    public static @Nullable ActionResult canBreak(ActionContext ctx, ChronoAction.BreakBlock action) {
        ServerLevel level = ctx.level();
        BlockPos worldPos = ctx.placement().toWorld(action.localPos());

        // 1. Radius, re-checked here: a recording is untrusted once it can be traded. Measured
        //    from the anchor block, never the routine's origin.
        if (!ctx.placement().withinRadius(worldPos)) {
            return ActionResult.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }

        // 2. Loaded chunks only: no force-loading, no catch-up.
        if (!level.isLoaded(worldPos)) {
            return ActionResult.fail(FailureReason.UNLOADED, action.localPos());
        }

        BlockState state = level.getBlockState(worldPos);
        if (state.isAir()) {
            return ActionResult.fail(FailureReason.NO_BLOCK, action.localPos());
        }

        // 3. Blacklist, plus a blanket refusal to touch block entities. Before the subject check,
        //    so a routine aimed at a chest still reports the refusal that matters.
        if (state.typeHolder().is(ModTags.ANCHOR_UNBREAKABLE) || state.hasBlockEntity()) {
            return ActionResult.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        // 4. Indestructible blocks refuse regardless of tags.
        if (state.getDestroySpeed(level, worldPos) < 0.0f) {
            return ActionResult.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        // 5. What the block is, unless the owner has widened this to anything. A routine that
        //    mines one square of a quarry should not quietly start eating whatever grew back
        //    there, and one that harvests a crop should say so rather than chewing the farmland.
        if (ctx.recordedSubject() && state.getBlock() != action.expectedBlock().value()) {
            return ActionResult.fail(FailureReason.WRONG_BLOCK, action.localPos());
        }

        // 6. The tool, which the clone has to actually own. Breaking was the one action that took
        //    what it needed from the recording rather than from the inventory, so a routine
        //    recorded with a netherite pickaxe mined with one whether or not the anchor had it.
        if (toolFor(action, ctx.items(), ctx.slot(), ctx.tool(), state) == null) {
            return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
        }
        return null;
    }

    /**
     * The tool the clone would swing, or null if it has none it may use.
     *
     * <p>Read rather than lent: a swing neither consumes the tool nor damages it, and a loan taken
     * and returned on every tick of a ten-second dig would be churn for its own sake.
     */
    private static @Nullable ItemStack toolFor(
            ChronoAction.BreakBlock action, ResourceHandler<ItemResource> inventory,
            ActionSettings.SlotRule slot, ActionSettings.ToolRule rule, BlockState state) {

        return switch (rule) {
            case EXACT -> HeldItemLoan.peek(inventory, ItemMatch.sameItem(
                    action.toolTemplate().getItem()), slot);
            case SMART -> bestToolFor(inventory, state);
        };
    }

    /**
     * The fastest tool in the clone's own squares, or bare hands where those would still drop
     * something.
     *
     * <p>The slot rule is not consulted: it answers which square to reach into, and the whole point
     * of this one is that the anchor decides. A block that wants a tool and has none refuses rather
     * than pulverising itself for nothing.
     */
    private static @Nullable ItemStack bestToolFor(
            ResourceHandler<ItemResource> inventory, BlockState state) {

        // Bare hands are a candidate like any other, and the one every anchor always has.
        ItemStack best = ItemStack.EMPTY;
        boolean bestDrops = earnsDrops(ItemStack.EMPTY, state);
        float bestSpeed = ItemStack.EMPTY.getDestroySpeed(state);

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemResource resource = inventory.getResource(slot);
            if (resource.isEmpty() || inventory.getAmountAsInt(slot) <= 0) {
                continue;
            }
            ItemStack candidate = resource.toStack(1);
            boolean drops = earnsDrops(candidate, state);
            float speed = candidate.getDestroySpeed(state);

            // Anything that earns the drops beats anything that does not, however fast it is.
            if ((drops && !bestDrops) || (drops == bestDrops && speed > bestSpeed)) {
                best = candidate;
                bestDrops = drops;
                bestSpeed = speed;
            }
        }

        // Nothing here, hands included, would leave anything behind. Breaking it would be
        // destroying it for nothing, which is the one thing an anchor never does.
        return bestDrops ? best : null;
    }

    /**
     * Whether breaking {@code state} while holding this would drop anything.
     *
     * <p>Not {@link ItemStack#isCorrectToolForDrops}, which asks whether a thing is the right tool
     * and so answers no for bare hands -- including on the dirt that bare hands harvest perfectly
     * well. The question vanilla asks before rolling a loot table is this one.
     */
    private static boolean earnsDrops(ItemStack candidate, BlockState state) {
        return !state.requiresCorrectToolForDrops() || candidate.isCorrectToolForDrops(state);
    }

    /**
     * How much of a block one tick of mining removes, as a fraction of the whole.
     */
    public static float progressPerTick(ActionContext ctx, ChronoAction.BreakBlock action) {
        ServerLevel level = ctx.level();
        BlockPos worldPos = ctx.placement().toWorld(action.localPos());
        // The clone's own tool, not the recording's: an Efficiency V pickaxe in the recording does
        // not make a plain one in the anchor dig any faster.
        ItemStack tool = toolFor(action, ctx.items(), ctx.slot(), ctx.tool(),
                level.getBlockState(worldPos));
        FakePlayer owner = ctx.acquire(Vec3.atCenterOf(worldPos),
                0.0f, 0.0f, tool == null ? ItemStack.EMPTY : tool);
        try {
            return level.getBlockState(worldPos).getDestroyProgress(owner, level, worldPos);
        } finally {
            ctx.release(owner);
        }
    }

    /**
     * Finishes a break whose mining is complete: permission, drops, removal.
     */
    public static ActionResult finish(ActionContext ctx, ChronoAction.BreakBlock action) {
        ServerLevel level = ctx.level();
        ResourceHandler<ItemResource> inventory = ctx.items();
        BlockPos worldPos = ctx.placement().toWorld(action.localPos());
        BlockState state = level.getBlockState(worldPos);

        ItemStack tool = toolFor(action, inventory, ctx.slot(), ctx.tool(), state);
        if (tool == null) {
            return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
        }

        FakePlayer owner = ctx.acquire(Vec3.atCenterOf(worldPos),
                0.0f, 0.0f, tool);
        try {
            // 6. Protection mods and land claims get their say, as the owner.
            var breakEvent = CommonHooks.fireBlockBreak(level, GameType.SURVIVAL, owner, worldPos, state);
            if (breakEvent.isCanceled()) {
                return ActionResult.fail(FailureReason.PROTECTED, action.localPos());
            }

            // 7. The normal loot path, so fortune and silk touch on the clone's own tool apply.
            List<ItemStack> drops = Block.getDrops(state, level, worldPos, null, owner, tool);

            // 8. Insert everything or nothing, so a full anchor never destroys what it cannot
            //    store and the action is simply re-runnable once emptied.
            try (Transaction tx = Transaction.openRoot()) {
                for (ItemStack drop : drops) {
                    if (drop.isEmpty()) {
                        continue;
                    }
                    int inserted = inventory.insert(ItemResource.of(drop), drop.getCount(), tx);
                    if (inserted < drop.getCount()) {
                        return ActionResult.fail(FailureReason.INVENTORY_FULL, action.localPos());
                    }
                }
                tx.commit();
            }

            // 9. The experience the block owes, which destroyBlock will not pay: it never runs
            //    playerDestroy, so an ore mined by a clone dropped none at all.
            int experience = state.getExpDrop(level, worldPos, level.getBlockEntity(worldPos),
                    owner, action.toolTemplate());
            if (experience > 0) {
                owner.giveExperiencePoints(experience);
            }

            // 10. Remove the block only once its drops are stored.
            level.destroyBlock(worldPos, false, owner);
            return ActionResult.OK;
        } finally {
            ctx.release(owner);
        }
    }
}
