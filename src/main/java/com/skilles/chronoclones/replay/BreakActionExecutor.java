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
import com.skilles.chronoclones.inventory.StackInventory;

//? if neoforge {
import net.neoforged.neoforge.common.CommonHooks;
//?}
import com.skilles.chronoclones.platform.ClonePlayer;
import org.jspecify.annotations.Nullable;

public final class BreakActionExecutor {

    private BreakActionExecutor() {}

    public static @Nullable ActionResult canBreak(ActionContext ctx, ChronoAction.BreakBlock action) {
        ServerLevel level = ctx.level();
        BlockPos worldPos = ctx.placement().toWorld(action.localPos());

        if (!ctx.placement().withinRadius(worldPos)) {
            return ActionResult.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }

        if (!level.isLoaded(worldPos)) {
            return ActionResult.fail(FailureReason.UNLOADED, action.localPos());
        }

        BlockState state = level.getBlockState(worldPos);
        if (state.isAir()) {
            return ActionResult.fail(FailureReason.NO_BLOCK, action.localPos());
        }

        //? if >=26 {
        if (state.typeHolder().is(ModTags.ANCHOR_UNBREAKABLE) || state.hasBlockEntity()) {
        //?} else {
        /*if (state.is(ModTags.ANCHOR_UNBREAKABLE) || state.hasBlockEntity()) {
        *///?}
            return ActionResult.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        if (state.getDestroySpeed(level, worldPos) < 0.0f) {
            return ActionResult.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        if (ctx.recordedSubject() && state.getBlock() != action.expectedBlock().value()) {
            return ActionResult.fail(FailureReason.WRONG_BLOCK, action.localPos());
        }

        if (toolFor(action, ctx.items(), ctx.slot(), ctx.tool(), state) == null) {
            return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
        }
        return null;
    }

    /** Read, not lent: see {@link HeldItemLoan#peek}. */
    private static @Nullable ItemStack toolFor(
            ChronoAction.BreakBlock action, StackInventory inventory,
            ActionSettings.SlotRule slot, ActionSettings.ToolRule rule, BlockState state) {
        return switch (rule) {
            case EXACT -> HeldItemLoan.peek(inventory, ItemMatch.sameItem(
                    action.toolTemplate().getItem()), slot);
            case SMART -> bestToolFor(inventory, state);
        };
    }

    /**
     * The fastest tool in the clone's own slots, or bare hands where those still drop something.
     *
     * <p>The slot rule is ignored on purpose: it says which slot to reach into, and the point of
     * this rule is that the anchor chooses. Returns null rather than pulverising a block for nothing.
     */
    private static @Nullable ItemStack bestToolFor(
            StackInventory inventory, BlockState state) {
        ItemStack best = ItemStack.EMPTY;
        boolean bestDrops = earnsDrops(ItemStack.EMPTY, state);
        float bestSpeed = ItemStack.EMPTY.getDestroySpeed(state);

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack held = inventory.getItem(slot);
            if (held.isEmpty()) {
                continue;
            }
            ItemStack candidate = held.copyWithCount(1);
            boolean drops = earnsDrops(candidate, state);
            float speed = candidate.getDestroySpeed(state);

            if ((drops && !bestDrops) || (drops == bestDrops && speed > bestSpeed)) {
                best = candidate;
                bestDrops = drops;
                bestSpeed = speed;
            }
        }

        return bestDrops ? best : null;
    }

    /** Not isCorrectToolForDrops, which answers no for the bare hands that harvest dirt. */
    private static boolean earnsDrops(ItemStack candidate, BlockState state) {
        return !state.requiresCorrectToolForDrops() || candidate.isCorrectToolForDrops(state);
    }

    public static float progressPerTick(ActionContext ctx, ChronoAction.BreakBlock action) {
        ServerLevel level = ctx.level();
        BlockPos worldPos = ctx.placement().toWorld(action.localPos());
        ItemStack tool = toolFor(action, ctx.items(), ctx.slot(), ctx.tool(),
                level.getBlockState(worldPos));
        ClonePlayer owner = ctx.acquire(Vec3.atCenterOf(worldPos),
                0.0f, 0.0f, tool == null ? ItemStack.EMPTY : tool);
        try {
            return level.getBlockState(worldPos).getDestroyProgress(owner, level, worldPos);
        } finally {
            ctx.release(owner);
        }
    }

    /** Drops are stored before the block is removed, so a full anchor destroys nothing. */
    public static ActionResult finish(ActionContext ctx, ChronoAction.BreakBlock action) {
        ServerLevel level = ctx.level();
        StackInventory inventory = ctx.items();
        BlockPos worldPos = ctx.placement().toWorld(action.localPos());
        BlockState state = level.getBlockState(worldPos);

        ItemStack tool = toolFor(action, inventory, ctx.slot(), ctx.tool(), state);
        if (tool == null) {
            return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
        }

        ClonePlayer owner = ctx.acquire(Vec3.atCenterOf(worldPos),
                0.0f, 0.0f, tool);
        try {
            // The loader's pre-break check, so protection mods can veto a clone's dig.
            //? if neoforge {
            var breakEvent = CommonHooks.fireBlockBreak(level, GameType.SURVIVAL, owner, worldPos, state);
            if (breakEvent.isCanceled()) {
                return ActionResult.fail(FailureReason.PROTECTED, action.localPos());
            }
            //?} else {
            //? if fabric {
            /*boolean allowed = net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE
                    .invoker().beforeBlockBreak(level, owner, worldPos, state,
                            level.getBlockEntity(worldPos));
            if (!allowed) {
                net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.CANCELED
                        .invoker().onBlockBreakCanceled(level, owner, worldPos, state,
                                level.getBlockEntity(worldPos));
                return ActionResult.fail(FailureReason.PROTECTED, action.localPos());
            }
            *///?} else {
            /*if (net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(
                    level, GameType.SURVIVAL, owner, worldPos) == -1) {
                return ActionResult.fail(FailureReason.PROTECTED, action.localPos());
            }
            *///?}
            //?}

            List<ItemStack> drops = Block.getDrops(state, level, worldPos, null, owner, tool);

            if (!inventory.insertAllOrNothing(drops)) {
                return ActionResult.fail(FailureReason.INVENTORY_FULL, action.localPos());
            }

            // destroyBlock never runs playerDestroy, so nothing else pays this.
            //? if neoforge {
            int experience = state.getExpDrop(level, worldPos, level.getBlockEntity(worldPos),
                    owner, action.toolTemplate());
            if (experience > 0) {
                owner.giveExperiencePoints(experience);
            }
            //?} else {
            /*// No NeoForge getExpDrop here: the vanilla after-break hook drops the orbs at the
            // block, and releasing the actor sweeps them into the operator's bank.
            state.spawnAfterBreak(level, worldPos, action.toolTemplate(), true);
            *///?}

            level.destroyBlock(worldPos, false, owner);
            return ActionResult.OK;
        } finally {
            ctx.release(owner);
        }
    }
}
