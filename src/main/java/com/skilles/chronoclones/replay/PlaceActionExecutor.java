package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import java.util.Optional;

import com.skilles.chronoclones.recording.ActionPose;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.skilles.chronoclones.inventory.StackInventory;

import net.neoforged.neoforge.common.util.FakePlayer;

public final class PlaceActionExecutor {

    private PlaceActionExecutor() {}

    public static ActionResult execute(ActionContext ctx, ChronoAction.PlaceBlock action) {
        ServerLevel level = ctx.level();
        StackInventory inventory = ctx.items();
        BlockPos worldPos = ctx.placement().toWorld(action.localPos());

        if (!ctx.placement().withinRadius(worldPos)) {
            return ActionResult.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }
        if (!level.isLoaded(worldPos)) {
            return ActionResult.fail(FailureReason.UNLOADED, action.localPos());
        }

        BlockState existing = level.getBlockState(worldPos);
        if (existing.typeHolder().is(ModTags.ANCHOR_UNBREAKABLE) || existing.hasBlockEntity()) {
            return ActionResult.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        int found = ctx.recordedSubject()
                ? findSlotWith(inventory, ItemMatch.of(action.itemTemplate(), ctx.settings().item()),
                        ctx.slot())
                : findAnyBlock(inventory, ctx.slot());
        if (found < 0) {
            return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
        }

        Item item = inventory.getItem(found).getItem();
        if (!(item instanceof BlockItem blockItem)) {
            return ActionResult.fail(FailureReason.NOT_PLACEABLE, action.localPos());
        }

        ItemStack toPlace = inventory.getItem(found).copyWithCount(1);

        Optional<ChronoAction.PlaceContext> recorded = action.context();
        Direction face = ctx.placement().toWorld(action.localFace());
        BlockPos clickedPos = recorded
                .map(c -> ctx.placement().toWorld(c.localClicked()))
                .orElse(worldPos);
        Vec3 hitVec = Vec3.atCenterOf(clickedPos).add(recorded
                .map(c -> LocalSpace.rotateY(c.localHitOffset(),
                        LocalSpace.stepsFromNorth(ctx.placement().facing())))
                .orElse(Vec3.ZERO));
        boolean inside = recorded.map(ChronoAction.PlaceContext::inside).orElse(false);
        InteractionHand hand = recorded.map(ChronoAction.PlaceContext::hand)
                .orElse(InteractionHand.MAIN_HAND);

        ActionPose pose = recorded.map(ChronoAction.PlaceContext::pose)
                .orElse(ActionPose.OVER_THE_ANCHOR);
        FakePlayer owner = ctx.acquire(pose.worldPos(ctx.placement().origin(), ctx.placement().facing()),
                pose.worldYaw(ctx.placement().facing()), pose.pitch(), hand, toPlace);
        try {
            BlockHitResult hit = new BlockHitResult(hitVec, face, clickedPos, inside);
            BlockPlaceContext context = new BlockPlaceContext(
                    level, owner, hand, owner.getItemInHand(hand), hit);

            if (!existing.canBeReplaced(context)) {
                return ActionResult.fail(FailureReason.OBSTRUCTED, action.localPos());
            }

            // Consume first, refund on failure: a placement that succeeds is never free, and one
            // that fails never eats the item.
            ItemStack taken = inventory.extract(found, 1);
            if (taken.isEmpty()) {
                return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
            }

            InteractionResult placed = blockItem.place(context);
            if (!placed.consumesAction()) {
                // The slot it just left always has room for the one item back.
                if (inventory.insert(found, taken, 1) < 1) {
                    inventory.insert(taken, 1);
                }
                return ActionResult.fail(FailureReason.OBSTRUCTED, action.localPos());
            }

            return ActionResult.OK;
        } finally {
            ctx.release(owner);
        }
    }

    private static int findAnyBlock(StackInventory inventory, SlotRule rule) {
        if (holdsABlock(inventory, rule.preferred())) {
            return rule.preferred();
        }
        if (rule.strict()) {
            return -1;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (holdsABlock(inventory, slot)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean holdsABlock(StackInventory inventory, int slot) {
        if (slot < 0 || slot >= inventory.size()) {
            return false;
        }
        ItemStack held = inventory.getItem(slot);
        return !held.isEmpty() && held.getItem() instanceof BlockItem;
    }

    private static int findSlotWith(StackInventory inventory, ItemMatch match,
                                    SlotRule rule) {
        if (holds(inventory, rule.preferred(), match)) {
            return rule.preferred();
        }
        if (rule.strict()) {
            return -1;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (holds(inventory, slot, match)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean holds(StackInventory inventory, int slot, ItemMatch match) {
        if (slot < 0 || slot >= inventory.size()) {
            return false;
        }
        ItemStack held = inventory.getItem(slot);
        return !held.isEmpty() && match.accepts(held);
    }
}
