package com.skilles.chronoclones.replay;

import java.util.Comparator;
import java.util.List;

import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ActionSettings.TargetRule;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;
import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.Items;
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
                                             Placement placement, Operator operator) {
        BlockPos worldPos = placement.toWorld(action.localPos());
        FakePlayer owner = AnchorFakePlayer.acquire(level, operator, Vec3.atCenterOf(worldPos), 0.0f, 0.0f, action.toolTemplate());
        try {
            return level.getBlockState(worldPos).getDestroyProgress(owner, level, worldPos);
        } finally {
            AnchorFakePlayer.release(operator, owner);
        }
    }

    /**
     * Finishes a break whose mining is complete: permission, drops, removal.
     */
    public static Result finishBreak(ServerLevel level, ChronoAction.BreakBlock action,
                                     Placement placement,
                                     Operator operator,
                                     ResourceHandler<ItemResource> inventory) {

        BlockPos worldPos = placement.toWorld(action.localPos());
        BlockState state = level.getBlockState(worldPos);

        FakePlayer owner = AnchorFakePlayer.acquire(level, operator, Vec3.atCenterOf(worldPos), 0.0f, 0.0f, action.toolTemplate());
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

            // 9. The experience the block owes, which destroyBlock will not pay: it never runs
            //    playerDestroy, so an ore mined by a clone dropped none at all.
            int experience = state.getExpDrop(level, worldPos, level.getBlockEntity(worldPos),
                    owner, action.toolTemplate());
            if (experience > 0) {
                owner.giveExperiencePoints(experience);
            }

            // 10. Remove the block only once its drops are stored.
            level.destroyBlock(worldPos, false, owner);
            return Result.OK;
        } finally {
            AnchorFakePlayer.release(operator, owner);
        }
    }

    // ------------------------------------------------------------------ place

    public static Result executePlace(ServerLevel level, ChronoAction.PlaceBlock action,
                                      Placement placement,
                                      Operator operator,
                                     ResourceHandler<ItemResource> inventory, SlotRule slot) {

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
            return Result.fail(FailureReason.NOT_PLACEABLE, action.localPos());
        }

        int found = findSlotWith(inventory, item, slot);
        if (found < 0) {
            return Result.fail(FailureReason.NO_ITEM, action.localPos());
        }

        ItemStack toPlace = new ItemStack(item);
        Direction face = placement.toWorld(action.localFace());

        FakePlayer owner = AnchorFakePlayer.acquire(level, operator, Vec3.atCenterOf(worldPos), 0.0f, 0.0f, toPlace);
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
                int taken = inventory.extract(found, ItemResource.of(toPlace), 1, tx);
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
            AnchorFakePlayer.release(operator, owner);
        }
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

    // ------------------------------------------------------------------ attack

    /**
     * One swing, and what it found.
     *
     * @param targetId    the entity swung at, so a sticky action can stay on it
     * @param targetAlive whether it is still standing, which is what an until-dead action waits on
     * @param hitLanded   false while a target is inside its invulnerability window, which is not a
     *                    failure and must not be charged for
     */
    public record AttackResult(Result result, int targetId, boolean targetAlive, boolean hitLanded) {

        public static final int NO_TARGET = -1;

        static AttackResult missed(FailureReason reason, BlockPos localPos) {
            return new AttackResult(Result.fail(reason, localPos), NO_TARGET, false, false);
        }
    }

    /**
     * Swings at whatever the rule admits nearest the recorded point.
     *
     * @param sticky the entity this clone was already working on, or null to pick afresh
     */
    public static AttackResult executeAttack(ServerLevel level, ChronoAction.AttackEntity action,
                                             Placement placement,
                                             Operator operator,
                                     TargetRule rule,
                                             @org.jspecify.annotations.Nullable LivingEntity sticky) {

        Vec3 worldPos = placement.toWorld(action.localPos());
        BlockPos blockPos = BlockPos.containing(worldPos);
        // Attack positions are continuous; diagnostics report the containing block.
        BlockPos localBlock = BlockPos.containing(action.localPos());

        if (!placement.withinRadius(blockPos)) {
            return AttackResult.missed(FailureReason.OUT_OF_RANGE, localBlock);
        }
        if (!level.isLoaded(blockPos)) {
            return AttackResult.missed(FailureReason.UNLOADED, localBlock);
        }

        LivingEntity target = chooseTarget(level, action, worldPos, operator, rule, sticky);
        if (target == null) {
            return AttackResult.missed(FailureReason.NO_TARGET, localBlock);
        }

        FakePlayer owner = AnchorFakePlayer.acquire(level, operator, worldPos, 0.0f, 0.0f, action.weaponTemplate());
        try {
            // Attributed to the owner so XP, loot tables and looting all resolve as if they had
            // swung the weapon themselves.
            float damage = (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE);
            boolean hurt = target.hurtServer(level, level.damageSources().playerAttack(owner), damage);

            // A swing absorbed by invulnerability frames still found its target, so it is neither a
            // failure nor worth charging for; the action simply waits and swings again.
            return new AttackResult(Result.OK, target.getId(), target.isAlive(), hurt);
        } finally {
            AnchorFakePlayer.release(operator, owner);
        }
    }

    /**
     * The entity already being worked on if it is still valid, else the recorded type nearest the
     * point, else the nearest thing at all.
     */
    private static @org.jspecify.annotations.Nullable LivingEntity chooseTarget(
            ServerLevel level, ChronoAction.AttackEntity action, Vec3 worldPos,
            Operator operator, TargetRule rule,
            @org.jspecify.annotations.Nullable LivingEntity sticky) {

        double radius = rule.radiusWithin(ChronoclonesConfig.MAX_RADIUS.getAsInt());
        boolean allowPvp = ChronoclonesConfig.ALLOW_PVP.get();
        AABB box = new AABB(worldPos, worldPos).inflate(radius);

        if (sticky != null && sticky.isAlive() && box.contains(sticky.position())) {
            return sticky;
        }

        // ChronoCloneEntity is a bare Entity, not a LivingEntity, so it cannot appear here.
        // Structural rather than a filter.
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, entity ->
                entity.isAlive()
                        && !entity.getUUID().equals(operator.id())
                        && (allowPvp || !(entity instanceof Player))
                        && rule.accepts(entity.getType()));

        if (candidates.isEmpty()) {
            return null;
        }

        // Prefer the recorded type, treating it as a hint, and fall back to the nearest
        // living thing rather than refusing to act.
        EntityType<?> expected = action.expectedType().value();
        return candidates.stream()
                .filter(e -> e.getType() == expected)
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPos)))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPos)))
                        .orElseThrow());
    }

    // ------------------------------------------------------------------ interaction

    /**
     * Right-clicking a block, run through the server's own entry point.
     */
    public static Result executeUseOnBlock(ServerLevel level, ChronoAction.UseOnBlock action,
                                           Placement placement,
                                           Operator operator,
                                     ResourceHandler<ItemResource> inventory, SlotRule slot) {

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

        HeldItemLoan.Loan loan = HeldItemLoan.take(inventory, action.item().value(), slot);
        if (loan == null) {
            return Result.fail(FailureReason.NO_ITEM, action.localPos());
        }

        // The sub-block hit point rotates with the anchor, as the block position does, so a
        // rotated routine still clicks the same corner of the same face.
        Direction face = placement.toWorld(action.localFace());
        Vec3 hit = Vec3.atCenterOf(worldPos)
                .add(LocalSpace.rotateY(action.localHitOffset(), LocalSpace.stepsFromNorth(placement.facing())));

        FakePlayer owner = AnchorFakePlayer.acquire(level, operator, Vec3.atCenterOf(worldPos), face.getOpposite().toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.gameMode.useItemOn(owner, level,
                    owner.getMainHandItem(), action.hand(),
                    new BlockHitResult(hit, face, worldPos, action.inside()));

            return finishInteraction(level, placement.anchorPos(), inventory, owner, loan, result, action.localPos());
        } finally {
            AnchorFakePlayer.release(operator, owner);
        }
    }

    /** Right-clicking with nothing targeted. Same pipeline, no hit result. */
    public static Result executeUseItem(ServerLevel level, ChronoAction.UseItem action,
                                        Placement placement,
                                        Operator operator,
                                     ResourceHandler<ItemResource> inventory, SlotRule slot) {

        HeldItemLoan.Loan loan = HeldItemLoan.take(inventory, action.item().value(), slot);
        if (loan == null) {
            return Result.fail(FailureReason.NO_ITEM, BlockPos.ZERO);
        }
        if (loan.stack().isEmpty()) {
            // Right-clicking air with an empty hand does nothing.
            return Result.OK;
        }

        FakePlayer owner = AnchorFakePlayer.acquire(level, operator, Vec3.atCenterOf(placement.anchorPos()).add(0.0, 1.0, 0.0), placement.facing().toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.gameMode.useItem(owner, level,
                    owner.getMainHandItem(), action.hand());
            return finishInteraction(level, placement.anchorPos(), inventory, owner, loan, result, BlockPos.ZERO);
        } finally {
            AnchorFakePlayer.release(operator, owner);
        }
    }

    /** Right-clicking an entity: shearing, milking, feeding, or a mod's own interaction. */
    public static Result executeInteractEntity(ServerLevel level, ChronoAction.InteractEntity action,
                                               Placement placement,
                                               Operator operator,
                                     ResourceHandler<ItemResource> inventory,
                                               ActionSettings settings) {

        Vec3 worldPos = placement.toWorld(action.localPos());
        BlockPos localBlock = BlockPos.containing(action.localPos());

        if (!placement.withinRadius(worldPos)) {
            return Result.fail(FailureReason.OUT_OF_RANGE, localBlock);
        }
        if (!level.isLoaded(BlockPos.containing(worldPos))) {
            return Result.fail(FailureReason.UNLOADED, localBlock);
        }

        boolean allowPvp = ChronoclonesConfig.ALLOW_PVP.get();
        TargetRule rule = settings.target();
        AABB box = new AABB(worldPos, worldPos)
                .inflate(rule.radiusWithin(ChronoclonesConfig.MAX_RADIUS.getAsInt()));
        List<Entity> candidates = level.getEntitiesOfClass(Entity.class, box, entity ->
                entity.isAlive()
                        && !(entity instanceof ChronoCloneEntity)
                        && !entity.getUUID().equals(operator.id())
                        && (allowPvp || !(entity instanceof Player))
                        && rule.accepts(entity.getType()));

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

        HeldItemLoan.Loan loan = HeldItemLoan.take(inventory, action.item().value(), settings.slot());
        if (loan == null) {
            return Result.fail(FailureReason.NO_ITEM, localBlock);
        }

        FakePlayer owner = AnchorFakePlayer.acquire(level, operator, worldPos, placement.facing().toYRot(), 0.0f, loan.stack());
        try {
            InteractionResult result = owner.interactOn(target, action.hand(),
                    worldPos.subtract(target.position()));
            return finishInteraction(level, placement.anchorPos(), inventory, owner, loan, result, localBlock);
        } finally {
            AnchorFakePlayer.release(operator, owner);
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



    // ------------------------------------------------------------------ transfer

    /**
     * Replays a container session by opening the block's real menu and clicking it.
     */
    public static Result executeUseContainer(ServerLevel level, ChronoAction.UseContainer action,
                                             Placement placement,
                                             Operator operator,
                                     ItemStacksResourceHandler inventory,
                                             ActionSettings settings) {

        BlockPos localBlock = action.target().localBlock();
        Vec3 worldPoint = action.target().toWorld(placement.origin(), placement.facing());
        BlockPos worldPos = BlockPos.containing(worldPoint);

        if (!placement.withinRadius(worldPos)) {
            return Result.fail(FailureReason.OUT_OF_RANGE, localBlock);
        }
        if (!level.isLoaded(worldPos)) {
            return Result.fail(FailureReason.UNLOADED, localBlock);
        }
        // Anchors may not reach into other anchors, for the same reason they may not right-click
        // them: a routine that loots its neighbours is a routine that loots its owner's other farms.
        if (action.target() instanceof MenuTarget.Block
                && level.getBlockState(worldPos).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return Result.fail(FailureReason.BLACKLISTED, localBlock);
        }

        FakePlayer owner = AnchorFakePlayer.acquire(level, operator, worldPoint,
                placement.facing().toYRot(), 0.0f, ItemStack.EMPTY);
        try {
            Session session = openMenu(level, action, worldPoint, operator, settings, owner);
            if (session == null) {
                return Result.fail(FailureReason.NO_MENU, localBlock);
            }
            AbstractContainerMenu menu = session.menu();
            // Slot indices mean nothing outside the menu that produced them, so a differently
            // shaped menu would be clicked at random.
            if (menu.slots.size() != action.menuSize()) {
                return Result.fail(FailureReason.WRONG_BLOCK, localBlock);
            }

            ContainerCarrier.load(inventory, owner, menu, settings);
            try {
                for (int index = 0; index < action.steps().size(); index++) {
                    ActionSettings.StepSettings rule = settings.step(index);
                    if (!rule.enabled()) {
                        continue;
                    }
                    FailureReason refusal = runStep(menu, owner, action.steps().get(index), rule);
                    if (refusal != FailureReason.NONE) {
                        return Result.fail(refusal, localBlock);
                    }
                }
            } finally {
                // Returns whatever is on the cursor to the player, then everything the player is
                // holding to the anchor, in a finally, because a mod's slot throwing mid-session
                // must not leave a routine's items inside a fake player nobody can open.
                menu.removed(owner);
                ContainerCarrier.drain(level, placement.anchorPos(), inventory, owner, menu);
                session.close();
            }
            return Result.OK;
        } finally {
            AnchorFakePlayer.release(operator, owner);
        }
    }

    /**
     * An open menu, and whatever has to be let go of once it closes.
     */
    private record Session(AbstractContainerMenu menu, Runnable release) {

        static Session of(AbstractContainerMenu menu) {
            return new Session(menu, () -> { });
        }

        void close() {
            release.run();
        }
    }

    /**
     * Opens the menu the session was recorded against.
     *
     * <p>The menu is built directly rather than through the interaction that opened it, because a
     * fake player's {@code openMenu} is a no-op: nothing would ever be open to click.
     */
    private static @org.jspecify.annotations.Nullable Session openMenu(
            ServerLevel level, ChronoAction.UseContainer action, Vec3 worldPoint,
            Operator operator, ActionSettings settings, FakePlayer owner) {

        if (action.target() instanceof MenuTarget.Entity target) {
            Entity entity = findEntity(level, target, worldPoint, operator, settings.target());
            return entity == null ? null : openEntityMenu(entity, owner);
        }

        BlockPos worldPos = BlockPos.containing(worldPoint);
        MenuProvider provider = level.getBlockState(worldPos).getMenuProvider(level, worldPos);
        if (provider == null) {
            return null;
        }
        AbstractContainerMenu menu = provider.createMenu(1, owner.getInventory(), owner);
        return menu == null ? null : Session.of(menu);
    }

    /**
     * The recorded kind of entity nearest the recorded point, else the nearest thing the rule admits.
     */
    private static @org.jspecify.annotations.Nullable Entity findEntity(
            ServerLevel level, MenuTarget.Entity target, Vec3 worldPoint, Operator operator,
            TargetRule rule) {

        AABB box = new AABB(worldPoint, worldPoint)
                .inflate(rule.radiusWithin(ChronoclonesConfig.MAX_RADIUS.getAsInt()));
        List<Entity> candidates = level.getEntitiesOfClass(Entity.class, box, entity ->
                entity.isAlive()
                        && !(entity instanceof ChronoCloneEntity)
                        && !(entity instanceof Player)
                        && !entity.getUUID().equals(operator.id())
                        && rule.accepts(entity.getType()));

        if (candidates.isEmpty()) {
            return null;
        }
        EntityType<?> expected = target.expectedType().value();
        return candidates.stream()
                .filter(e -> e.getType() == expected)
                .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPoint)))
                .orElseGet(() -> candidates.stream()
                        .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(worldPoint)))
                        .orElseThrow());
    }

    /**
     * The menus an entity can carry: a merchant's offers, a mount's saddlebags, or anything that is
     * its own {@link MenuProvider}, which covers chest vehicles and a mod's own entities.
     */
    private static @org.jspecify.annotations.Nullable Session openEntityMenu(Entity entity, FakePlayer owner) {
        if (entity instanceof Merchant merchant) {
            // A merchant will not trade with two customers at once, and it awards its experience to
            // whoever it thinks it is trading with.
            if (merchant.getTradingPlayer() != null) {
                return null;
            }
            merchant.setTradingPlayer(owner);
            MerchantMenu menu = new MerchantMenu(1, owner.getInventory(), merchant);
            menu.setOffers(merchant.getOffers());
            return new Session(menu, () -> merchant.setTradingPlayer(null));
        }
        if (entity instanceof AbstractHorse horse) {
            return Session.of(new HorseInventoryMenu(1, owner.getInventory(), horse.getInventory(),
                    horse, horse.getInventoryColumns()));
        }
        if (entity instanceof MenuProvider provider) {
            AbstractContainerMenu menu = provider.createMenu(1, owner.getInventory(), owner);
            return menu == null ? null : Session.of(menu);
        }
        return null;
    }

    /**
     * One step of a session.
     *
     * @return {@link FailureReason#NONE} if it ran, and otherwise why the session stops here. A step
     *         that names a square this menu does not have means the menu is not the one recorded, so
     *         nothing further should be clicked in it.
     */
    private static FailureReason runStep(AbstractContainerMenu menu, FakePlayer owner,
                                         SessionStep step, ActionSettings.StepSettings rule) {
        int levels = experienceCost(menu, step);
        if (levels > 0 && owner.experienceLevel < levels && !drinkUpTo(owner, levels)) {
            return FailureReason.NO_EXPERIENCE;
        }

        return switch (step) {
            case SessionStep.Move move -> runMove(menu, owner, move, rule)
                    ? FailureReason.NONE
                    // A square this menu does not have means it is not the menu that was recorded.
                    : FailureReason.WRONG_BLOCK;
            case SessionStep.RawClick raw -> {
                if (raw.slot() >= menu.slots.size()) {
                    yield FailureReason.WRONG_BLOCK;
                }
                // The square the player clicked, whatever is in it now.
                menu.clicked(raw.slot(), raw.button(), raw.input(), owner);
                yield FailureReason.NONE;
            }
            // A refused button is not a broken session: a menu that declines one simply does not
            // do that thing, and the steps after it may still have work to do.
            case SessionStep.Button button -> {
                menu.clickMenuButton(owner, button.id());
                yield FailureReason.NONE;
            }
            case SessionStep.Trade trade -> runTrade(menu, trade);
            case SessionStep.Rename rename -> {
                if (menu instanceof AnvilMenu anvil) {
                    anvil.setItemName(rename.text());
                    yield FailureReason.NONE;
                }
                yield FailureReason.WRONG_BLOCK;
            }
        };
    }

    /**
     * What this step will charge in levels, before it is attempted.
     *
     * <p>Asked in advance because the menus that charge simply decline when they cannot: an
     * enchantment the clone cannot afford does nothing at all, and nothing says why.
     */
    private static int experienceCost(AbstractContainerMenu menu, SessionStep step) {
        if (menu instanceof EnchantmentMenu table && step instanceof SessionStep.Button button) {
            int id = button.id();
            return id >= 0 && id < table.costs.length ? table.costs[id] : 0;
        }
        // The anvil charges when the work is taken out, not when it is set up.
        if (menu instanceof AnvilMenu anvil && step instanceof SessionStep.Move move
                && move.from() == AnvilMenu.RESULT_SLOT) {
            return anvil.getCost();
        }
        return 0;
    }

    /**
     * Drinks bottles o' enchanting out of the clone's own stock until it can afford the work.
     *
     * <p>Consumed rather than thrown: a bottle that has to land somewhere would make this a thing
     * that happens over several ticks, in the middle of an open menu.
     */
    private static boolean drinkUpTo(FakePlayer owner, int levels) {
        while (owner.experienceLevel < levels) {
            int slot = findInInventory(owner, Items.EXPERIENCE_BOTTLE);
            if (slot < 0) {
                return false;
            }
            owner.getInventory().removeItem(slot, 1);
            // Vanilla's own spread for a thrown bottle: 3 to 11.
            RandomSource random = owner.level().getRandom();
            owner.giveExperiencePoints(3 + random.nextInt(5) + random.nextInt(5));
        }
        return true;
    }

    private static int findInInventory(FakePlayer owner, Item item) {
        Inventory inventory = owner.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Selects the offer the recording named, by what it offers.
     *
     * <p>Never by index: a villager's trades reorder as it levels, so the fifth trade of that day is
     * not the same promise as the fifth trade today. Counts are not matched either, because a price
     * moves with demand and reputation and the player wanted the trade, not the price.
     */
    private static FailureReason runTrade(AbstractContainerMenu menu, SessionStep.Trade trade) {
        if (!(menu instanceof MerchantMenu merchant)) {
            return FailureReason.WRONG_BLOCK;
        }
        MerchantOffers offers = merchant.getOffers();
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            if (!matches(offer, trade)) {
                continue;
            }
            // Sold out is worth saying: the payment would go in, no result would come out, and the
            // session would look like it had simply decided not to work today.
            if (offer.isOutOfStock()) {
                return FailureReason.OUT_OF_STOCK;
            }
            merchant.setSelectionHint(index);
            merchant.tryMoveItems(index);
            return FailureReason.NONE;
        }
        // The merchant no longer offers it, so there is nothing to buy and nothing to guess at.
        return FailureReason.NO_OFFER;
    }

    private static boolean matches(MerchantOffer offer, SessionStep.Trade trade) {
        return offer.getCostA().getItem() == trade.costA().getItem()
                && offer.getCostB().getItem() == trade.costB().getItem()
                // Components on the result, so Mending is not bought as Unbreaking.
                && ItemStack.isSameItemSameComponents(offer.getResult(), trade.result())
                && offer.getResult().getCount() == trade.result().getCount();
    }

    /**
     * Moves an item as the player moved it: take, put, and put back whatever was not wanted.
     *
     * <p>Through {@code clicked} rather than by writing the slots, so a mod's own slot rules, its
     * crafting result and its refusals all apply exactly as they do to a player.
     */
    private static boolean runMove(AbstractContainerMenu menu, FakePlayer owner, SessionStep.Move move,
                                   ActionSettings.StepSettings rule) {
        if (move.from() < 0 || move.from() >= menu.slots.size()) {
            return false;
        }
        if (!move.quick() && (move.to() < 0 || move.to() >= menu.slots.size())) {
            return false;
        }

        int from = sourceFor(menu, move, rule.slot());
        // Nothing to take: the same outcome as clicking an empty square, without the clicks. A
        // chest that has not refilled yet is the ordinary case, not a broken routine.
        if (from < 0) {
            return true;
        }
        if (!rule.transfer().allows(menu.getSlot(from).getItem().getItem())) {
            return true;
        }

        if (move.quick()) {
            // A shift-click's amount was always the menu's business, so a cap cannot apply to one.
            menu.clicked(from, 0, ContainerInput.QUICK_MOVE, owner);
            return true;
        }

        menu.clicked(from, move.observed() == SessionStep.Amount.HALF ? 1 : 0,
                ContainerInput.PICKUP, owner);

        int carried = menu.getCarried().getCount();
        int wanted = move.observed() == SessionStep.Amount.ONE
                ? 1
                : Math.min(carried, rule.transfer().quantity().budget());

        if (wanted >= carried) {
            menu.clicked(move.to(), 0, ContainerInput.PICKUP, owner);
        } else {
            // One at a time, which is the only way a menu will take part of what is held.
            for (int placed = 0; placed < wanted; placed++) {
                menu.clicked(move.to(), 1, ContainerInput.PICKUP, owner);
            }
        }

        // What the destination would not take, or what a cap held back.
        if (!menu.getCarried().isEmpty()) {
            menu.clicked(from, 0, ContainerInput.PICKUP, owner);
        }
        return true;
    }

    /**
     * The square this move takes from, which is the recorded one unless told to look further.
     *
     * <p>A looser rule only ever looks on the same side of the menu: slot indices say nothing about
     * what they belong to, so searching the whole menu for a chest's coal would happily find the
     * clone's own.
     */
    private static int sourceFor(AbstractContainerMenu menu, SessionStep.Move move, SlotRule rule) {
        Item item = move.item().value();
        if (rule.mode() != SlotRule.Mode.ANY && menu.getSlot(move.from()).getItem().is(item)) {
            return move.from();
        }
        if (rule.mode() == SlotRule.Mode.EXACT) {
            return -1;
        }
        Container side = menu.getSlot(move.from()).container;
        for (int slot = 0; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).container == side && menu.getSlot(slot).getItem().is(item)) {
                return slot;
            }
        }
        return -1;
    }
}
