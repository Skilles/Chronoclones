package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
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
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Putting a block down, out of the clone's own squares.
 */
public final class PlaceActionExecutor {

    private PlaceActionExecutor() {}

    public static ActionResult execute(ActionContext ctx, ChronoAction.PlaceBlock action) {
        ServerLevel level = ctx.level();
        ResourceHandler<ItemResource> inventory = ctx.items();
        BlockPos worldPos = ctx.placement().toWorld(action.localPos());

        if (!ctx.placement().withinRadius(worldPos)) {
            return ActionResult.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }
        if (!level.isLoaded(worldPos)) {
            return ActionResult.fail(FailureReason.UNLOADED, action.localPos());
        }

        // Never build over another anchor, or over anything on the protected tag.
        BlockState existing = level.getBlockState(worldPos);
        if (existing.typeHolder().is(ModTags.ANCHOR_UNBREAKABLE) || existing.hasBlockEntity()) {
            return ActionResult.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        // Widened to anything, the square decides what goes there rather than the recording: the
        // routine becomes "build with whatever you are given" instead of "build in cobblestone".
        int found = ctx.recordedSubject()
                ? findSlotWith(inventory, action.item().value(), ctx.slot())
                : findAnyBlock(inventory, ctx.slot());
        if (found < 0) {
            return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
        }

        Item item = inventory.getResource(found).getItem();
        if (!(item instanceof BlockItem blockItem)) {
            return ActionResult.fail(FailureReason.NOT_PLACEABLE, action.localPos());
        }

        ItemStack toPlace = new ItemStack(item);
        Direction face = ctx.placement().toWorld(action.localFace());

        FakePlayer owner = ctx.acquire(Vec3.atCenterOf(worldPos),
                0.0f, 0.0f, toPlace);
        try {
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(worldPos), face, worldPos, false);
            BlockPlaceContext context = new BlockPlaceContext(
                    level, owner, InteractionHand.MAIN_HAND, owner.getMainHandItem(), hit);

            if (!existing.canBeReplaced(context)) {
                return ActionResult.fail(FailureReason.OBSTRUCTED, action.localPos());
            }

            // Consume first, inside a transaction, so a placement that succeeds can never be free
            // and one that fails can never eat the item.
            try (Transaction tx = Transaction.openRoot()) {
                int taken = inventory.extract(found, ItemResource.of(toPlace), 1, tx);
                if (taken != 1) {
                    return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
                }

                // Placing through the BlockItem path gives correct orientation, waterlogging,
                // block-place events and sounds for free.
                InteractionResult placed = blockItem.place(context);
                if (!placed.consumesAction()) {
                    return ActionResult.fail(FailureReason.OBSTRUCTED, action.localPos());
                }
                tx.commit();
            }

            return ActionResult.OK;
        } finally {
            ctx.release(owner);
        }
    }

    /** The same search, for a placement that no longer cares which block it is putting down. */
    private static int findAnyBlock(ResourceHandler<ItemResource> inventory, SlotRule rule) {
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

    private static boolean holdsABlock(ResourceHandler<ItemResource> inventory, int slot) {
        if (slot < 0 || slot >= inventory.size()) {
            return false;
        }
        ItemResource resource = inventory.getResource(slot);
        return !resource.isEmpty() && resource.getItem() instanceof BlockItem
                && inventory.getAmountAsInt(slot) > 0;
    }

    /** The recorded slot if it still holds the item, otherwise as much of a search as allowed. */
    private static int findSlotWith(ResourceHandler<ItemResource> inventory, Item item, SlotRule rule) {
        if (holds(inventory, rule.preferred(), item)) {
            return rule.preferred();
        }
        if (rule.strict()) {
            return -1;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (holds(inventory, slot, item)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean holds(ResourceHandler<ItemResource> inventory, int slot, Item item) {
        if (slot < 0 || slot >= inventory.size()) {
            return false;
        }
        ItemResource resource = inventory.getResource(slot);
        return !resource.isEmpty() && resource.getItem() == item && inventory.getAmountAsInt(slot) > 0;
    }
}
