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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Executes a single {@link ChronoAction} against the world, or explains why it could not.
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

    /**
     * Whether a break may begin, without beginning it.
     *
     * @return the failure, or {@code null} if mining may proceed
     */
    public static @org.jspecify.annotations.Nullable Result canBreak(
            ServerLevel level, ChronoAction.BreakBlock action, Placement placement) {

        BlockPos worldPos = placement.toWorld(action.localPos());

        // 1. Radius, re-checked here: a recording is untrusted once it can be traded. Measured
        //    from the anchor block, never the routine's origin.
        if (!placement.withinRadius(worldPos)) {
            return Result.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }

        // 2. Loaded chunks only: no force-loading, no catch-up.
        if (!level.isLoaded(worldPos)) {
            return Result.fail(FailureReason.UNLOADED, action.localPos());
        }

        BlockState state = level.getBlockState(worldPos);
        if (state.isAir()) {
            return Result.fail(FailureReason.NO_BLOCK, action.localPos());
        }

        // 3. What the block IS is not checked; the clone swings the recorded tool at whatever
        //    is standing there.
        //
        // 4. Blacklist, plus a blanket refusal to touch block entities.
        if (state.typeHolder().is(ModTags.ANCHOR_UNBREAKABLE) || state.hasBlockEntity()) {
            return Result.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        // 5. Indestructible blocks refuse regardless of tags.
        if (state.getDestroySpeed(level, worldPos) < 0.0f) {
            return Result.fail(FailureReason.BLACKLISTED, action.localPos());
        }
        return null;
    }

    /**
     * How much of a block one tick of mining removes, as a fraction of the whole.
     */
    public static float breakProgressPerTick(ServerLevel level, ChronoAction.BreakBlock action,
                                             Placement placement, java.util.UUID ownerId,
                                             String ownerName) {
        BlockPos worldPos = placement.toWorld(action.localPos());
        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                Vec3.atCenterOf(worldPos), 0.0f, 0.0f, action.toolTemplate());
        try {
            return level.getBlockState(worldPos).getDestroyProgress(owner, level, worldPos);
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    /**
     * Finishes a break whose mining is complete: permission, drops, removal.
     */
    public static Result finishBreak(ServerLevel level, ChronoAction.BreakBlock action,
                                     Placement placement,
                                     java.util.UUID ownerId, String ownerName,
                                     ResourceHandler<ItemResource> inventory) {

        BlockPos worldPos = placement.toWorld(action.localPos());
        BlockState state = level.getBlockState(worldPos);

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                Vec3.atCenterOf(worldPos), 0.0f, 0.0f, action.toolTemplate());
        try {
            // 6. Protection mods and land claims get their say, as the owner.
            var breakEvent = CommonHooks.fireBlockBreak(level, GameType.SURVIVAL, owner, worldPos, state);
            if (breakEvent.isCanceled()) {
                return Result.fail(FailureReason.PROTECTED, action.localPos());
            }

            // 7. The normal loot path, so fortune and silk touch on the recorded tool apply.
            List<ItemStack> drops = Block.getDrops(state, level, worldPos, null, owner, action.toolTemplate());

            // 8. Insert everything or nothing, so a full anchor never destroys what it cannot
            //    store and the action is simply re-runnable once emptied.
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

            // 9. Remove the block only once its drops are stored.
            level.destroyBlock(worldPos, false, owner);
            return Result.OK;
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    // ------------------------------------------------------------------ place

    public static Result executePlace(ServerLevel level, ChronoAction.PlaceBlock action,
                                      Placement placement,
                                      java.util.UUID ownerId, String ownerName,
                                      ResourceHandler<ItemResource> inventory) {

        BlockPos worldPos = placement.toWorld(action.localPos());

        if (!placement.withinRadius(worldPos)) {
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
        Direction face = placement.toWorld(action.localFace());

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
                                       Placement placement,
                                       java.util.UUID ownerId, String ownerName) {

        Vec3 worldPos = placement.toWorld(action.localPos());
        BlockPos blockPos = BlockPos.containing(worldPos);
        // Attack positions are continuous; diagnostics report the containing block.
        BlockPos localBlock = BlockPos.containing(action.localPos());

        if (!placement.withinRadius(blockPos)) {
            return Result.fail(FailureReason.OUT_OF_RANGE, localBlock);
        }
        if (!level.isLoaded(blockPos)) {
            return Result.fail(FailureReason.UNLOADED, localBlock);
        }

        boolean allowPvp = ChronoclonesConfig.ALLOW_PVP.get();
        AABB box = new AABB(worldPos, worldPos).inflate(ATTACK_RADIUS);

        // ChronoCloneEntity is a bare Entity, not a LivingEntity, so it cannot appear here.
        // Structural rather than a filter.
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, entity ->
                entity.isAlive()
                        && !entity.getUUID().equals(ownerId)
                        && (allowPvp || !(entity instanceof Player)));

        if (candidates.isEmpty()) {
            return Result.fail(FailureReason.NO_TARGET, localBlock);
        }

        // Prefer the recorded type, treating it as a hint, and fall back to the nearest
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

    /**
     * How far from the recorded point an attack looks for something to hit.
     */
    public static final double ATTACK_RADIUS = 1.5;

    // ------------------------------------------------------------------ interaction

    /**
     * Right-clicking a block, run through the server's own entry point.
     */
    public static Result executeUseOnBlock(ServerLevel level, ChronoAction.UseOnBlock action,
                                           Placement placement,
                                           java.util.UUID ownerId, String ownerName,
                                           ResourceHandler<ItemResource> inventory) {

        BlockPos worldPos = placement.toWorld(action.localPos());

        if (!placement.withinRadius(worldPos)) {
            return Result.fail(FailureReason.OUT_OF_RANGE, action.localPos());
        }
        if (!level.isLoaded(worldPos)) {
            return Result.fail(FailureReason.UNLOADED, action.localPos());
        }
        // An anchor must never operate another anchor: that is how a routine
        // reconfigures its neighbours.
        if (level.getBlockState(worldPos).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return Result.fail(FailureReason.BLACKLISTED, action.localPos());
        }

        HeldItemLoan.Loan loan = HeldItemLoan.take(inventory, action.item().value());
        if (loan == null) {
            return Result.fail(FailureReason.NO_ITEM, action.localPos());
        }

        // The sub-block hit point rotates with the anchor, as the block position does, so a
        // rotated routine still clicks the same corner of the same face.
        Direction face = placement.toWorld(action.localFace());
        Vec3 hit = Vec3.atCenterOf(worldPos)
                .add(LocalSpace.rotateY(action.localHitOffset(), LocalSpace.stepsFromNorth(placement.facing())));

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                Vec3.atCenterOf(worldPos), face.getOpposite().toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.gameMode.useItemOn(owner, level,
                    owner.getMainHandItem(), action.hand(),
                    new BlockHitResult(hit, face, worldPos, action.inside()));

            return finishInteraction(level, placement.anchorPos(), inventory, owner, loan, result, action.localPos());
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    /** Right-clicking with nothing targeted. Same pipeline, no hit result. */
    public static Result executeUseItem(ServerLevel level, ChronoAction.UseItem action,
                                        Placement placement,
                                        java.util.UUID ownerId, String ownerName,
                                        ResourceHandler<ItemResource> inventory) {

        HeldItemLoan.Loan loan = HeldItemLoan.take(inventory, action.item().value());
        if (loan == null) {
            return Result.fail(FailureReason.NO_ITEM, BlockPos.ZERO);
        }
        if (loan.stack().isEmpty()) {
            // Right-clicking air with an empty hand does nothing.
            return Result.OK;
        }

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                Vec3.atCenterOf(placement.anchorPos()).add(0.0, 1.0, 0.0), placement.facing().toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.gameMode.useItem(owner, level,
                    owner.getMainHandItem(), action.hand());
            return finishInteraction(level, placement.anchorPos(), inventory, owner, loan, result, BlockPos.ZERO);
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    /** Right-clicking an entity: shearing, milking, feeding, or a mod's own interaction. */
    public static Result executeInteractEntity(ServerLevel level, ChronoAction.InteractEntity action,
                                               Placement placement,
                                               java.util.UUID ownerId, String ownerName,
                                               ResourceHandler<ItemResource> inventory) {

        Vec3 worldPos = placement.toWorld(action.localPos());
        BlockPos localBlock = BlockPos.containing(action.localPos());

        if (!placement.withinRadius(worldPos)) {
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
                worldPos, placement.facing().toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.interactOn(target, action.hand(),
                    worldPos.subtract(target.position()));
            return finishInteraction(level, placement.anchorPos(), inventory, owner, loan, result, localBlock);
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }

    /**
     * Returns the borrowed item and reports whether the interaction did anything.
     */
    private static Result finishInteraction(ServerLevel level, BlockPos anchorPos,
                                            ResourceHandler<ItemResource> inventory,
                                            FakePlayer owner, HeldItemLoan.Loan loan,
                                            InteractionResult result, BlockPos localPos) {
        HeldItemLoan.giveBack(level, anchorPos, inventory, loan, owner.getMainHandItem().copy());

        // PASS means nothing was interactable, the same shape of failure as swinging at empty air,
        // and reported so a routine that has drifted out of alignment says so.
        return result.consumesAction() ? Result.OK : Result.fail(FailureReason.NO_TARGET, localPos);
    }

    /** Same story as {@link #ATTACK_RADIUS}, for right-clicking a mob. */
    public static final double INTERACT_RADIUS = 2.0;

    // ------------------------------------------------------------------ transfer

    /**
     * Replays a container session by opening the block's real menu and clicking it.
     */
    public static Result executeUseContainer(ServerLevel level, ChronoAction.UseContainer action,
                                             Placement placement,
                                             java.util.UUID ownerId, String ownerName,
                                             ItemStacksResourceHandler inventory) {

        BlockPos worldPos = placement.toWorld(action.localPos());

        if (!placement.withinRadius(worldPos)) {
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

        MenuProvider provider = level.getBlockState(worldPos).getMenuProvider(level, worldPos);
        if (provider == null) {
            return Result.fail(FailureReason.NO_TARGET, action.localPos());
        }

        FakePlayer owner = AnchorFakePlayer.acquire(level, ownerId, ownerName,
                Vec3.atCenterOf(worldPos), placement.facing().toYRot(), 0.0f, ItemStack.EMPTY);
        try {
            AbstractContainerMenu menu = provider.createMenu(1, owner.getInventory(), owner);
            if (menu == null) {
                return Result.fail(FailureReason.NO_TARGET, action.localPos());
            }
            // Slot indices mean nothing outside the menu that produced them, so a differently
            // shaped menu would be clicked at random.
            if (menu.slots.size() != action.menuSize()) {
                return Result.fail(FailureReason.WRONG_BLOCK, action.localPos());
            }

            if (!ContainerCarrier.load(inventory, owner, menu, action.carrier())) {
                ContainerCarrier.drain(level, placement.anchorPos(), inventory, owner, menu);
                return Result.fail(FailureReason.NO_ITEM, action.localPos());
            }
            try {
                for (ChronoAction.UseContainer.Click click : action.clicks()) {
                    if (click.slot() >= menu.slots.size()) {
                        return Result.fail(FailureReason.NO_TARGET, action.localPos());
                    }
                    // The square the player clicked, whatever is in it now.
                    menu.clicked(click.slot(), click.button(), click.input(), owner);
                }
            } finally {
                // Returns whatever is on the cursor to the player, then everything the player is
                // holding to the anchor, in a finally, because a mod's slot throwing mid-session
                // must not leave a routine's items inside a fake player nobody can open.
                menu.removed(owner);
                ContainerCarrier.drain(level, placement.anchorPos(), inventory, owner, menu);
            }
            return Result.OK;
        } finally {
            AnchorFakePlayer.release(owner);
        }
    }
}
