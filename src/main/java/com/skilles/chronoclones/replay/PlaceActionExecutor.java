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
                ? findSlotWith(inventory, ItemMatch.of(action.itemTemplate(), ctx.settings().item()),
                        ctx.slot())
                : findAnyBlock(inventory, ctx.slot());
        if (found < 0) {
            return ActionResult.fail(FailureReason.NO_ITEM, action.localPos());
        }

        Item item = inventory.getResource(found).getItem();
        if (!(item instanceof BlockItem blockItem)) {
            return ActionResult.fail(FailureReason.NOT_PLACEABLE, action.localPos());
        }

        // The item as the clone actually holds it, components and all, rather than a fresh
        // default one: a shulker box with something in it is not the same block as an empty one.
        ItemStack toPlace = inventory.getResource(found).toStack(1);

        // Where it was clicked, if the recording remembers. A routine recorded before placements
        // kept their click falls back to the middle of the block's own square, which is what it
        // has always done.
        Optional<ChronoAction.PlaceContext> recorded = action.context();
        // The face that was clicked when the recording knows it, and the upward face it always
        // assumed when it does not: capture writes the real one into localFace either way.
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

        // Standing and looking where the player was. Vanilla reads both off the placing player to
        // decide which way stairs face, which half a slab fills, and where a door hangs.
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
    private static int findSlotWith(ResourceHandler<ItemResource> inventory, ItemMatch match,
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

    private static boolean holds(ResourceHandler<ItemResource> inventory, int slot, ItemMatch match) {
        if (slot < 0 || slot >= inventory.size()) {
            return false;
        }
        ItemResource resource = inventory.getResource(slot);
        return !resource.isEmpty() && match.accepts(resource) && inventory.getAmountAsInt(slot) > 0;
    }
}
