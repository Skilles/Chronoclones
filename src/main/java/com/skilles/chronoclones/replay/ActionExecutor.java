package com.skilles.chronoclones.replay;

import java.util.Comparator;
import java.util.List;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Executes a single {@link ChronoAction} against the world, or explains why it could not.
 *
 * <p>This is the only class that mutates the world, and every mutation goes through the owner's
 * fake player so events, protection mods, and land claims all see a real, correctly attributed
 * actor.
 */
public final class ActionExecutor {

    /** Outcome of one attempted action. {@code reason == NONE} means it happened. */
    public record Result(FailureReason reason, BlockPos localPos) {
        public static final Result OK = new Result(FailureReason.NONE, BlockPos.ZERO);

        public static Result fail(FailureReason reason, BlockPos localPos) {
            return new Result(reason, localPos);
        }

        public boolean succeeded() {
            return reason == FailureReason.NONE;
        }
    }

    private ActionExecutor() {}

    public static Result executeBreak(ServerLevel level, ChronoAction.BreakBlock action,
                                      BlockPos anchorPos, Direction anchorFacing,
                                      java.util.UUID ownerId, String ownerName,
                                      ResourceHandler<ItemResource> inventory) {

        BlockPos worldPos = LocalSpace.toWorld(action.localPos(), anchorPos, anchorFacing);

        // 1. Radius, re-checked here and not merely at record time. The recording is untrusted
        //    input the moment it can be transferred between players.
        int maxRadius = ChronoclonesConfig.MAX_RADIUS.getAsInt();
        if (!worldPos.closerThan(anchorPos, maxRadius)) {
            return Result.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }

        // 2. Loaded chunks only. No force-loading and no catch-up, by design.
        if (!level.isLoaded(worldPos)) {
            return Result.fail(FailureReason.UNLOADED, action.localPos());
        }

        BlockState state = level.getBlockState(worldPos);
        if (state.isAir()) {
            return Result.fail(FailureReason.NO_BLOCK, action.localPos());
        }

        // 3. Coherence. STRICT for now; LOOSE and ADAPTIVE are the upgrade axis.
        if (!state.getBlock().equals(action.expectedBlock().value())) {
            return Result.fail(FailureReason.WRONG_BLOCK, action.localPos());
        }

        // 4. Blacklist, plus a blanket refusal to touch block entities in v1 — other mods' machines
        //    are a bug farm and the interaction is not worth the surface area.
        if (state.typeHolder().is(ModTags.ANCHOR_UNBREAKABLE) || state.hasBlockEntity()) {
            return Result.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        // 5. Indestructible blocks (bedrock-like) refuse regardless of tags.
        if (state.getDestroySpeed(level, worldPos) < 0.0f) {
            return Result.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                Vec3.atCenterOf(worldPos), 0.0f, 0.0f, action.toolTemplate());
        try {
            // 6. Give protection mods and land claims their say, as the owner.
            var breakEvent = CommonHooks.fireBlockBreak(level, GameType.SURVIVAL, owner, worldPos, state);
            if (breakEvent.isCanceled()) {
                return Result.fail(FailureReason.PROTECTED, action.localPos());
            }

            // 7. Drops computed through the normal loot path, so fortune and silk touch on the
            //    recorded tool behave exactly as they did for the player.
            List<ItemStack> drops = Block.getDrops(state, level, worldPos, null, owner, action.toolTemplate());

            // 8. Insert everything or nothing. The spec breaks the block first and halts on
            //    overflow; doing it in this order means a full anchor never destroys something it
            //    could not store, so it is simply re-runnable once emptied.
            try (Transaction tx = Transaction.openRoot()) {
                for (ItemStack drop : drops) {
                    if (drop.isEmpty()) {
                        continue;
                    }
                    int inserted = inventory.insert(ItemResource.of(drop), drop.getCount(), tx);
                    if (inserted < drop.getCount()) {
                        return Result.fail(FailureReason.INVENTORY_FULL, action.localPos());
                    }
                }
                tx.commit();
            }

            // 9. Remove the block only after its drops are safely stored.
            level.destroyBlock(worldPos, false, owner);
            return Result.OK;
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    // ------------------------------------------------------------------ place

    public static Result executePlace(ServerLevel level, ChronoAction.PlaceBlock action,
                                      BlockPos anchorPos, Direction anchorFacing,
                                      java.util.UUID ownerId, String ownerName,
                                      ResourceHandler<ItemResource> inventory) {

        BlockPos worldPos = LocalSpace.toWorld(action.localPos(), anchorPos, anchorFacing);

        if (!worldPos.closerThan(anchorPos, ChronoclonesConfig.MAX_RADIUS.getAsInt())) {
            return Result.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }
        if (!level.isLoaded(worldPos)) {
            return Result.fail(FailureReason.UNLOADED, action.localPos());
        }

        // Never build over another anchor, or over anything on the protected tag.
        BlockState existing = level.getBlockState(worldPos);
        if (existing.typeHolder().is(ModTags.ANCHOR_UNBREAKABLE) || existing.hasBlockEntity()) {
            return Result.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        Item item = action.item().value();
        if (!(item instanceof BlockItem blockItem)) {
            // The recording captured a non-placeable item for a place action; nothing sensible to do.
            return Result.fail(FailureReason.NOT_PERMITTED, action.localPos());
        }

        int slot = findSlotWith(inventory, item);
        if (slot < 0) {
            return Result.fail(FailureReason.NO_ITEM, action.localPos());
        }

        ItemStack toPlace = new ItemStack(item);
        Direction face = LocalSpace.toWorld(action.localFace(), anchorFacing);

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                Vec3.atCenterOf(worldPos), 0.0f, 0.0f, toPlace);
        try {
            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(worldPos), face, worldPos, false);
            BlockPlaceContext context = new BlockPlaceContext(
                    level, owner, InteractionHand.MAIN_HAND, owner.getMainHandItem(), hit);

            if (!existing.canBeReplaced(context)) {
                return Result.fail(FailureReason.OBSTRUCTED, action.localPos());
            }

            // Consume first, inside a transaction, so a placement that succeeds can never be free
            // and one that fails can never eat the item.
            try (Transaction tx = Transaction.openRoot()) {
                int taken = inventory.extract(slot, ItemResource.of(toPlace), 1, tx);
                if (taken != 1) {
                    return Result.fail(FailureReason.NO_ITEM, action.localPos());
                }

                // Placing through the BlockItem path gives correct orientation, waterlogging,
                // block-place events and sounds for free.
                InteractionResult placed = blockItem.place(context);
                if (!placed.consumesAction()) {
                    return Result.fail(FailureReason.OBSTRUCTED, action.localPos());
                }
                tx.commit();
            }

            return Result.OK;
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    private static int findSlotWith(ResourceHandler<ItemResource> inventory, Item item) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemResource resource = inventory.getResource(slot);
            if (!resource.isEmpty() && resource.getItem() == item && inventory.getAmountAsInt(slot) > 0) {
                return slot;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ attack

    public static Result executeAttack(ServerLevel level, ChronoAction.AttackEntity action,
                                       BlockPos anchorPos, Direction anchorFacing,
                                       java.util.UUID ownerId, String ownerName) {

        Vec3 worldPos = LocalSpace.toWorld(action.localPos(), anchorPos, anchorFacing);
        BlockPos blockPos = BlockPos.containing(worldPos);
        // Attack positions are continuous; diagnostics report the containing block.
        BlockPos localBlock = BlockPos.containing(action.localPos());

        if (!blockPos.closerThan(anchorPos, ChronoclonesConfig.MAX_RADIUS.getAsInt())) {
            return Result.fail(FailureReason.OUT_OF_RANGE, localBlock);
        }
        if (!level.isLoaded(blockPos)) {
            return Result.fail(FailureReason.UNLOADED, localBlock);
        }

        boolean allowPvp = ChronoclonesConfig.ALLOW_PVP.get();
        AABB box = new AABB(worldPos, worldPos).inflate(ATTACK_RADIUS);

        // Ghosts are excluded for free: ChronoCloneEntity is a bare Entity, not a LivingEntity, so
        // it can never appear in this query. That is deliberate — a visual-only clone must never be
        // a target, and keeping it off LivingEntity makes that structural rather than a filter.
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, entity ->
                entity.isAlive()
                        && !entity.getUUID().equals(ownerId)
                        && (allowPvp || !(entity instanceof Player)));

        if (candidates.isEmpty()) {
            return Result.fail(FailureReason.NO_TARGET, localBlock);
        }

        // Prefer the recorded type; the spec treats it as a hint, so fall back to the nearest
        // living thing rather than refusing to act.
        EntityType<?> expected = action.expectedType().value();
        LivingEntity target = candidates.stream()
                .filter(e -> e.getType() == expected)
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPos)))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPos)))
                        .orElseThrow());

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                worldPos, 0.0f, 0.0f, action.weaponTemplate());
        try {
            // Attributed to the owner so XP, loot tables and looting all resolve as if they had
            // swung the weapon themselves.
            float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE);
            boolean hurt = target.hurtServer(level, level.damageSources().playerAttack(owner), damage);

            return hurt ? Result.OK : Result.fail(FailureReason.NO_TARGET, localBlock);
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    private static final double ATTACK_RADIUS = 1.5;
}
