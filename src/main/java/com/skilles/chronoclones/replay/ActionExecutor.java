package com.skilles.chronoclones.replay;

import java.util.Comparator;
import java.util.List;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
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
import net.neoforged.neoforge.capabilities.Capabilities;
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

    // ------------------------------------------------------------------ interaction

    /**
     * Right-clicking a block, run through the server's own entry point.
     *
     * <p>This is the whole answer to "support other mods' blocks". {@code useItemOn} is what the
     * server calls when a real player right-clicks: it fires {@code RightClickBlock}, consults the
     * item, calls {@code BlockState.useItemOn} and {@code useWithoutItem}, and honours every
     * override a mod has written. Nothing here knows what a lever, a furnace or somebody else's
     * machine is, and nothing here needs to.
     */
    public static Result executeUseOnBlock(ServerLevel level, ChronoAction.UseOnBlock action,
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
        // An anchor must never be able to operate another anchor — that is how you build a routine
        // that reconfigures its neighbours, and there is no version of it worth supporting.
        if (level.getBlockState(worldPos).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return Result.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        HeldItemLoan.Loan loan = HeldItemLoan.take(inventory, action.item().value());
        if (loan == null) {
            return Result.fail(FailureReason.NO_ITEM, action.localPos());
        }

        // The sub-block hit point rotates with the anchor, exactly as the block position does, so a
        // rotated routine still clicks the same corner of the same face.
        Direction face = LocalSpace.toWorld(action.localFace(), anchorFacing);
        Vec3 hit = Vec3.atCenterOf(worldPos)
                .add(LocalSpace.rotateY(action.localHitOffset(), LocalSpace.stepsFromNorth(anchorFacing)));

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                Vec3.atCenterOf(worldPos), face.getOpposite().toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.gameMode.useItemOn(owner, level,
                    owner.getMainHandItem(), action.hand(),
                    new BlockHitResult(hit, face, worldPos, action.inside()));

            return finishInteraction(level, anchorPos, inventory, owner, loan, result, action.localPos());
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    /** Right-clicking with nothing targeted. Same pipeline, no hit result. */
    public static Result executeUseItem(ServerLevel level, ChronoAction.UseItem action,
                                        BlockPos anchorPos, Direction anchorFacing,
                                        java.util.UUID ownerId, String ownerName,
                                        ResourceHandler<ItemResource> inventory) {

        HeldItemLoan.Loan loan = HeldItemLoan.take(inventory, action.item().value());
        if (loan == null) {
            return Result.fail(FailureReason.NO_ITEM, BlockPos.ZERO);
        }
        if (loan.stack().isEmpty()) {
            // Right-clicking air with an empty hand does nothing worth replaying.
            return Result.OK;
        }

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                Vec3.atCenterOf(anchorPos).add(0.0, 1.0, 0.0), anchorFacing.toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.gameMode.useItem(owner, level,
                    owner.getMainHandItem(), action.hand());
            return finishInteraction(level, anchorPos, inventory, owner, loan, result, BlockPos.ZERO);
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    /** Right-clicking an entity: shearing, milking, feeding, or a mod's own interaction. */
    public static Result executeInteractEntity(ServerLevel level, ChronoAction.InteractEntity action,
                                               BlockPos anchorPos, Direction anchorFacing,
                                               java.util.UUID ownerId, String ownerName,
                                               ResourceHandler<ItemResource> inventory) {

        Vec3 worldPos = LocalSpace.toWorld(action.localPos(), anchorPos, anchorFacing);
        BlockPos localBlock = BlockPos.containing(action.localPos());

        if (!BlockPos.containing(worldPos).closerThan(anchorPos, ChronoclonesConfig.MAX_RADIUS.getAsInt())) {
            return Result.fail(FailureReason.OUT_OF_RANGE, localBlock);
        }
        if (!level.isLoaded(BlockPos.containing(worldPos))) {
            return Result.fail(FailureReason.UNLOADED, localBlock);
        }

        boolean allowPvp = ChronoclonesConfig.ALLOW_PVP.get();
        AABB box = new AABB(worldPos, worldPos).inflate(INTERACT_RADIUS);
        List<Entity> candidates = level.getEntitiesOfClass(Entity.class, box, entity ->
                entity.isAlive()
                        && !(entity instanceof ChronoCloneEntity)
                        && !entity.getUUID().equals(ownerId)
                        && (allowPvp || !(entity instanceof Player)));

        if (candidates.isEmpty()) {
            return Result.fail(FailureReason.NO_TARGET, localBlock);
        }

        EntityType<?> expected = action.expectedType().value();
        Entity target = candidates.stream()
                .filter(e -> e.getType() == expected)
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPos)))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPos)))
                        .orElseThrow());

        HeldItemLoan.Loan loan = HeldItemLoan.take(inventory, action.item().value());
        if (loan == null) {
            return Result.fail(FailureReason.NO_ITEM, localBlock);
        }

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                worldPos, anchorFacing.toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.interactOn(target, action.hand(),
                    worldPos.subtract(target.position()));
            return finishInteraction(level, anchorPos, inventory, owner, loan, result, localBlock);
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    /**
     * Returns the borrowed item and reports whether the interaction did anything.
     *
     * <p>The item goes back regardless of the outcome. An interaction that declined to act may still
     * have changed what it was holding — and even if it did not, keeping the item hostage because
     * the routine hit an unexpected world state would drain an anchor one failed loop at a time.
     */
    private static Result finishInteraction(ServerLevel level, BlockPos anchorPos,
                                            ResourceHandler<ItemResource> inventory,
                                            FakePlayer owner, HeldItemLoan.Loan loan,
                                            InteractionResult result, BlockPos localPos) {
        HeldItemLoan.giveBack(level, anchorPos, inventory, loan, owner.getMainHandItem().copy());

        // PASS means nothing was interactable — the same shape of failure as swinging at empty air,
        // and worth reporting so a routine that has drifted out of alignment says so.
        return result.consumesAction() ? Result.OK : Result.fail(FailureReason.NO_TARGET, localPos);
    }

    private static final double INTERACT_RADIUS = 2.0;

    // ------------------------------------------------------------------ transfer

    /**
     * Moves items between the anchor and a container, through the item-handler capability.
     *
     * <p>Capability rather than menu simulation, which is what makes this work with mods at all: a
     * chest, a barrel, a furnace's output slot and somebody else's machine all expose the same
     * handler, and none of them have to know this mod exists. A block that exposes nothing is simply
     * not automatable by an anchor, which is the same answer hoppers give.
     */
    public static Result executeTransfer(ServerLevel level, ChronoAction.TransferItems action,
                                         BlockPos anchorPos, Direction anchorFacing,
                                         ResourceHandler<ItemResource> inventory) {

        BlockPos worldPos = LocalSpace.toWorld(action.localPos(), anchorPos, anchorFacing);

        if (!worldPos.closerThan(anchorPos, ChronoclonesConfig.MAX_RADIUS.getAsInt())) {
            return Result.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }
        if (!level.isLoaded(worldPos)) {
            return Result.fail(FailureReason.UNLOADED, action.localPos());
        }
        // Anchors may not reach into other anchors, for the same reason they may not right-click
        // them: a routine that loots its neighbours is a routine that loots its owner's other farms.
        if (level.getBlockState(worldPos).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return Result.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        ResourceHandler<ItemResource> container =
                level.getCapability(Capabilities.Item.BLOCK, worldPos, null);
        if (container == null) {
            return Result.fail(FailureReason.NO_TARGET, action.localPos());
        }

        ResourceHandler<ItemResource> from = action.withdraw() ? container : inventory;
        ResourceHandler<ItemResource> to = action.withdraw() ? inventory : container;
        ItemResource resource = ItemResource.of(action.item().value());

        try (Transaction tx = Transaction.openRoot()) {
            int available = from.extract(resource, action.amount(), tx);
            if (available <= 0) {
                return Result.fail(action.withdraw() ? FailureReason.NO_ITEM : FailureReason.NO_ITEM,
                        action.localPos());
            }
            int moved = to.insert(resource, available, tx);
            if (moved < available) {
                // Partial moves are refused outright rather than committed. A transfer that half
                // happens leaves the routine's next loop working from a different world than the one
                // it was recorded in, and the failure is exactly the one the player needs to see.
                return Result.fail(action.withdraw()
                        ? FailureReason.INVENTORY_FULL : FailureReason.OBSTRUCTED, action.localPos());
            }
            tx.commit();
        }

        return Result.OK;
    }
}
