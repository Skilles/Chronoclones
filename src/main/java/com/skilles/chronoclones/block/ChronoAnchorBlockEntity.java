package com.skilles.chronoclones.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingCodecs;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.registry.ModBlockEntities;
import com.skilles.chronoclones.registry.ModDataComponents;
import com.skilles.chronoclones.registry.ModItems;
import com.skilles.chronoclones.replay.ActionExecutor;
import com.skilles.chronoclones.replay.CloneRuntime;
import com.skilles.chronoclones.replay.LevelActionBudget;
import com.skilles.chronoclones.replay.MotionTrack;
import com.skilles.chronoclones.replay.Placement;
import com.skilles.chronoclones.replay.TransferPrecision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Chrono Anchor: holds an imprinted recording and replays it on a loop.
 *
 * <p><b>Owner and author are different fields and are never conflated</b>. The owner
 * is whoever imprinted this anchor and is the identity behind every block break, damage source and
 * permission check. The author travels with the {@link Recording} and only decides whose skin the
 * ghost wears. Getting this backwards is a griefing vector, so the owner is stored here — on the
 * block — and the recording carries no owner field at all.
 */
public class ChronoAnchorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INVENTORY_SLOTS = 18;
    /**
     * How many ints the menu syncs.
     *
     * <p>Lives here, next to the switch that produces them, because the client builds a buffer of
     * exactly this size and reads it by index. When the two drifted apart the readouts past the end
     * did not degrade — they threw, on a client, in a code path no game test reaches.
     */
    public static final int DATA_COUNT = 19;
    public static final int UPGRADE_SLOTS = 3;

    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(INVENTORY_SLOTS) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    private @Nullable Recording recording;
    private @Nullable MotionTrack motionTrack;

    private @Nullable UUID ownerId;
    private String ownerName = "";

    /** One fuel item at a time; charge is drawn from it as it burns. */
    private final ItemStacksResourceHandler fuelSlot = new ItemStacksResourceHandler(1) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    /** Three upgrade slots. */
    private final ItemStacksResourceHandler upgradeSlots = new ItemStacksResourceHandler(UPGRADE_SLOTS) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    private final List<CloneRuntime> runtimes = new ArrayList<>();
    private BlockPos originOffset = BlockPos.ZERO;
    /** How specific this anchor is about item transfers. Set in the GUI, not bought. */
    private TransferPrecision precision = TransferPrecision.NONE;
    private UpgradeState upgrades = UpgradeState.BASE;
    private ChargeBuffer charge = ChargeBuffer.EMPTY;
    private boolean enabled = true;
    private DiagnosticState lastFailure = DiagnosticState.NONE;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> runtimes.isEmpty() ? 0 : runtimes.get(0).playhead();
                case 1 -> recording == null ? 0 : recording.lengthTicks();
                case 2 -> recording == null ? 0 : recording.actions().size();
                case 3 -> lastFailure.reason().ordinal();
                case 4 -> enabled ? 1 : 0;
                case 5 -> runtimes.size();
                case 6 -> charge.stored();
                case 7 -> charge.capacity();
                case 8 -> upgrades.cloneCount();
                case 9 -> upgrades.ticksPerStep();
                case 10 -> upgrades.fidelityTier();
                case 11 -> lastFailure.localPos().getX();
                case 12 -> lastFailure.localPos().getY();
                case 13 -> lastFailure.localPos().getZ();
                case 14 -> upgrades.coherenceTier();
                case 15 -> originOffset.getX();
                case 16 -> originOffset.getY();
                case 17 -> originOffset.getZ();
                case 18 -> precision.pack();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ChronoAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHRONO_ANCHOR.get(), pos, state);
    }

    public ResourceHandler<ItemResource> getInventory() {
        return inventory;
    }

    public ItemStacksResourceHandler getInventoryHandler() {
        return inventory;
    }

    public ContainerData getContainerData() {
        return data;
    }

    public ItemStacksResourceHandler getFuelHandler() {
        return fuelSlot;
    }

    public ItemStacksResourceHandler getUpgradeHandler() {
        return upgradeSlots;
    }

    public ChargeBuffer getCharge() {
        return charge;
    }

    public UpgradeState getUpgrades() {
        return upgrades;
    }

    /** The imprinting player — the identity behind every event this anchor causes. */
    public @Nullable UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public @Nullable Recording getRecording() {
        return recording;
    }

    public DiagnosticState getLastFailure() {
        return lastFailure;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** How far the routine has been nudged from the anchor, in anchor-local space. */
    public BlockPos getOriginOffset() {
        return originOffset;
    }

    /** How specific this anchor is about item transfers (see {@link TransferPrecision}). */
    public TransferPrecision getPrecision() {
        return precision;
    }

    public void setPrecision(TransferPrecision precision) {
        this.precision = precision;
        setChanged();
    }

    /**
     * Where this anchor's routine lands, and where its radius is measured from.
     *
     * <p>Built here rather than at each call site so the two positions are decided once. See
     * {@link Placement} for why conflating them would hand out unlimited reach.
     */
    public Placement placement() {
        return Placement.of(worldPosition, getBlockState().getValue(ChronoAnchorBlock.FACING),
                originOffset);
    }

    /**
     * Moves the routine's origin, clamped so it cannot become a way to extend reach.
     *
     * <p>The clamp is belt-and-braces — every action is radius-checked against the anchor block
     * regardless, so an offset beyond the radius makes a routine fail rather than reach further.
     * Bounding it anyway keeps absurd values out of the save file and keeps the preview on screen.
     */
    public void nudgeOrigin(BlockPos delta) {
        int limit = com.skilles.chronoclones.ChronoclonesConfig.MAX_RADIUS.getAsInt();
        originOffset = new BlockPos(
                Math.clamp(originOffset.getX() + delta.getX(), -limit, limit),
                Math.clamp(originOffset.getY() + delta.getY(), -limit, limit),
                Math.clamp(originOffset.getZ() + delta.getZ(), -limit, limit));
        setChanged();
    }

    public void resetOrigin() {
        originOffset = BlockPos.ZERO;
        setChanged();
    }

    // ------------------------------------------------------------------ imprint

    /**
     * Imprints a recording, taking ownership for the imprinting player.
     *
     * <p>The owner comes from {@code imprinter} and the author stays inside {@code recording}. They
     * are set from two different sources here on purpose — see the class docs.
     */
    public void imprint(Recording recording, ServerPlayer imprinter) {
        this.recording = recording;
        this.motionTrack = new MotionTrack(recording.motion());
        this.ownerId = imprinter.getUUID();
        this.ownerName = imprinter.getGameProfile().name();
        this.lastFailure = DiagnosticState.NONE;

        rebuildRuntimes();
        setChanged();
    }

    /**
     * Takes ownership without touching the routine, for an anchor placed from a stack that already
     * carries one.
     *
     * <p>A routine survives being mined and re-placed — losing one to a pickaxe would be a miserable
     * way to lose an afternoon's recording. Ownership does <em>not</em> survive: it is reassigned to
     * whoever places the block, exactly as imprinting reassigns it. Mining someone's anchor and
     * putting it down elsewhere must not let it keep acting in their name, and an
     * anchor placed by a dispenser gets no owner at all, which leaves it inert.
     */
    public void adopt(ServerPlayer placer) {
        this.ownerId = placer.getUUID();
        this.ownerName = placer.getGameProfile().name();
        this.lastFailure = DiagnosticState.NONE;
        setChanged();
    }

    public void clearRecording() {
        discardGhosts();
        runtimes.clear();
        recording = null;
        motionTrack = null;
        lastFailure = DiagnosticState.NONE;
        setActive(false);
        setChanged();
    }

    private void rebuildRuntimes() {
        discardGhosts();
        runtimes.clear();
        if (recording == null) {
            return;
        }
        int count = upgrades.cloneCount();
        for (int i = 0; i < count; i++) {
            runtimes.add(new CloneRuntime(
                    CloneRuntime.phaseOffsetFor(i, count, recording.lengthTicks())));
        }
    }

    // ------------------------------------------------------------------ replay

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!enabled || recording == null || motionTrack == null || ownerId == null) {
            if (!runtimes.isEmpty()) {
                discardGhosts();
                runtimes.clear();
                setActive(false);
            }
            return;
        }

        // Upgrades and refuelling run BEFORE the halt check, and unconditionally.
        //
        // Both halting reasons describe a resource the player can restore — charge, or inventory
        // space — so an anchor that stops refuelling while halted can never recover from the very
        // condition that halted it. Putting these after the halt check deadlocked a no-charge
        // anchor permanently: fuel sat in the slot untouched until the anchor was re-imprinted,
        // which reset the failure by accident rather than by design.
        UpgradeState current = UpgradeState.from(upgradeSlots);
        if (current.cloneCount() != upgrades.cloneCount()) {
            upgrades = current;
            rebuildRuntimes();
        } else {
            upgrades = current;
        }

        consumeFuel();

        if (lastFailure.halts()) {
            if (!DiagnosticState.canResume(lastFailure.reason(), !charge.isEmpty(), hasInventoryRoom())) {
                // Still stuck. Ghosts stay faded so the stopped state reads from across the base
                // rather than only in the GUI.
                discardGhosts();
                setActive(false);
                return;
            }
            // The cause cleared, so pick up where the routine left off.
            lastFailure = DiagnosticState.NONE;
            setChanged();
        }

        if (runtimes.isEmpty()) {
            rebuildRuntimes();
        }
        setActive(true);
        emitIdleParticles(serverLevel);

        Direction facing = getBlockState().getValue(ChronoAnchorBlock.FACING);
        Placement placement = placement();
        int length = Math.max(recording.lengthTicks(), 1);

        for (CloneRuntime runtime : runtimes) {
            runtime.advance(upgrades.ticksPerStep());
            if (runtime.playhead() >= length) {
                runtime.loop(length);
            }

            syncGhost(serverLevel, runtime, facing);
            runDueActions(serverLevel, runtime);

            if (lastFailure.halts()) {
                return;
            }
        }
    }

    /**
     * One tick of mining, or the whole break at once for a creative routine.
     *
     * <p>The player did not remove a block; they held a button until it gave way, and everything
     * else they were doing waited. Replaying that as an instant deletion loses the only part of a
     * mining routine you can actually watch, and loses the constraint that makes tool choice mean
     * anything — a wooden pickaxe on obsidian should be a bad idea, not merely a slower way to the
     * same result.
     *
     * <p>A creative recording breaks instantly, because that is what the author did. It is a
     * property of the recording rather than of the anchor, so a creative-built routine stays
     * instant wherever it is imprinted, and a survival one stays slow.
     *
     * @return true if the action finished this tick and the cursor has moved on
     */
    private boolean mineOneTick(ServerLevel serverLevel, CloneRuntime runtime,
                                ChronoAction.BreakBlock action, Placement placement,
                                Direction facing, int cost) {
        ActionExecutor.Result refusal =
                ActionExecutor.canBreak(serverLevel, action, placement, upgrades.coherenceTier());
        if (refusal != null) {
            // Includes the block vanishing mid-dig: somebody else got there first, so stop digging
            // at nothing and move on.
            stopMining(serverLevel, runtime);
            runtime.consumeAction();
            recordFailure(serverLevel, refusal.reason(), refusal.localPos(), runtime.playhead(), facing);
            return true;
        }

        BlockPos worldPos = placement.toWorld(action.localPos());
        boolean instant = recording != null && recording.creative();

        if (!instant) {
            // Rate upgrades speed mining too. An accelerated clone replays more of the routine per
            // real tick, and swinging is part of the routine.
            float perTick = ActionExecutor.breakProgressPerTick(serverLevel, action, placement,
                    ownerId, ownerName) * upgrades.ticksPerStep();
            float progress = runtime.mine(worldPos, perTick);

            if (progress < 1.0f) {
                showCracks(serverLevel, runtime, worldPos, progress);
                return false;
            }
        }

        stopMining(serverLevel, runtime);
        runtime.consumeAction();

        ActionExecutor.Result result =
                ActionExecutor.finishBreak(serverLevel, action, placement, ownerId, ownerName, inventory);
        if (result.succeeded()) {
            charge = charge.spend(cost);
            setChanged();
        } else {
            recordFailure(serverLevel, result.reason(), result.localPos(), runtime.playhead(), facing);
        }
        return true;
    }

    /**
     * The cracking overlay, keyed to the ghost doing the digging.
     *
     * <p>Vanilla identifies a destruction animation by the entity id of whoever is breaking, which
     * is what keeps four clones mining four blocks from overwriting each other's cracks. Falling
     * back to the anchor's own hash covers the tick before a ghost exists.
     */
    private void showCracks(ServerLevel serverLevel, CloneRuntime runtime, BlockPos worldPos,
                            float progress) {
        int stage = Math.min((int) (progress * 10.0f), 9);
        serverLevel.destroyBlockProgress(breakerIdOf(runtime), worldPos, stage);
    }

    /** Takes the cracks back off, whether the block was finished or abandoned. */
    private void stopMining(ServerLevel serverLevel, CloneRuntime runtime) {
        BlockPos was = runtime.miningPos();
        if (was != null) {
            // -1 is vanilla's "no longer breaking this", and without it the cracks stay on screen
            // until a chunk reload.
            serverLevel.destroyBlockProgress(breakerIdOf(runtime), was, -1);
        }
        runtime.clearMining();
    }

    private int breakerIdOf(CloneRuntime runtime) {
        ChronoCloneEntity ghost = runtime.ghost();
        return ghost != null ? ghost.getId() : worldPosition.hashCode();
    }

    private void runDueActions(ServerLevel serverLevel, CloneRuntime runtime) {
        if (recording == null || ownerId == null) {
            return;
        }
        List<TimedAction> actions = recording.actions();
        Direction facing = getBlockState().getValue(ChronoAnchorBlock.FACING);
        Placement placement = placement();

        while (runtime.actionCursor() < actions.size()
                && actions.get(runtime.actionCursor()).tick() <= runtime.playhead()) {

            TimedAction timed = actions.get(runtime.actionCursor());
            ChronoAction action = timed.action();

            // Fidelity gates which action types this anchor may run at all.
            if (!action.type().permittedAt(upgrades.fidelityTier())) {
                runtime.consumeAction();
                recordFailure(serverLevel, FailureReason.NOT_PERMITTED, localPosOf(action),
                        runtime.playhead(), facing);
                continue;
            }

            // Out of level budget this tick. Leave the cursor alone so the action is retried next
            // tick rather than silently dropped.
            if (!LevelActionBudget.tryClaim(serverLevel)) {
                return;
            }

            // Charge is the balance lever: more clones at a higher rate burn it
            // proportionally faster, so upgrades cost something rather than being pure gain.
            int cost = action.chargeCost();
            if (!charge.canAfford(cost)) {
                recordFailure(serverLevel, FailureReason.NO_CHARGE, localPosOf(action),
                        runtime.playhead(), facing);
                return;
            }

            // Breaking is the one action that takes time. It keeps the cursor until the block is
            // gone, so the rest of the routine waits for it exactly as the player did.
            if (action instanceof ChronoAction.BreakBlock breaking) {
                if (!mineOneTick(serverLevel, runtime, breaking, placement, facing, cost)) {
                    return;
                }
                continue;
            }

            runtime.consumeAction();

            ActionExecutor.Result result = switch (action) {
                case ChronoAction.BreakBlock a -> throw new IllegalStateException("handled above");
                case ChronoAction.PlaceBlock a -> ActionExecutor.executePlace(
                        serverLevel, a, placement, ownerId, ownerName, inventory);
                case ChronoAction.AttackEntity a -> ActionExecutor.executeAttack(
                        serverLevel, a, placement, ownerId, ownerName);
                case ChronoAction.UseOnBlock a -> ActionExecutor.executeUseOnBlock(
                        serverLevel, a, placement, ownerId, ownerName, inventory);
                case ChronoAction.UseItem a -> ActionExecutor.executeUseItem(
                        serverLevel, a, placement, ownerId, ownerName, inventory);
                case ChronoAction.InteractEntity a -> ActionExecutor.executeInteractEntity(
                        serverLevel, a, placement, ownerId, ownerName, inventory);
                case ChronoAction.UseContainer a -> ActionExecutor.executeUseContainer(
                        serverLevel, a, placement, ownerId, ownerName, inventory, precision);
            };

            if (result.succeeded()) {
                // Only pay for work that actually happened; a skipped action is free.
                charge = charge.spend(cost);
                setChanged();
            }

            if (!result.succeeded()) {
                recordFailure(serverLevel, result.reason(), result.localPos(), runtime.playhead(), facing);
                if (result.reason().halts()) {
                    return;
                }
            } else if (lastFailure.isFailure()) {
                // A success clears a previous non-halting complaint, so the GUI reflects now
                // rather than the last thing that ever went wrong.
                lastFailure = DiagnosticState.NONE;
                setChanged();
            }
        }
    }

    /** True if any storage slot could accept something, used to clear an INVENTORY_FULL halt. */
    private boolean hasInventoryRoom() {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getResource(slot).isEmpty()
                    || inventory.getAmountAsInt(slot) < inventory.getCapacityAsInt(slot, inventory.getResource(slot))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Burns one fuel item if there is room for the charge it would produce.
     *
     * <p>Deliberately only consumes when the whole burn fits, so a nearly-full anchor never eats a
     * block of coal to gain twenty charge.
     */
    private void consumeFuel() {
        if (level == null || charge.headroom() <= 0) {
            return;
        }
        ItemResource resource = fuelSlot.getResource(0);
        if (resource.isEmpty() || fuelSlot.getAmountAsInt(0) <= 0) {
            return;
        }

        // Creative cell: top up and never consume, so charge stops being a variable while testing.
        if (resource.getItem() == ModItems.CREATIVE_CHARGE_CELL.get()) {
            charge = charge.refill(charge.headroom());
            setChanged();
            return;
        }

        ItemStack probe = resource.toStack(1);
        int burnTicks = level.fuelValues().burnDuration(probe);
        if (burnTicks <= 0) {
            return;
        }

        int gained = burnTicks * ChargeBuffer.CHARGE_PER_BURN_TICK;
        if (gained > charge.headroom()) {
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            if (fuelSlot.extract(0, resource, 1, tx) != 1) {
                return;
            }
            tx.commit();
        }
        charge = charge.refill(gained);
        setChanged();
    }

    /** Diagnostic position for any action variant, for the failure marker. */
    private static BlockPos localPosOf(ChronoAction action) {
        return switch (action) {
            case ChronoAction.BreakBlock a -> a.localPos();
            case ChronoAction.PlaceBlock a -> a.localPos();
            case ChronoAction.AttackEntity a -> BlockPos.containing(a.localPos());
            case ChronoAction.UseOnBlock a -> a.localPos();
            case ChronoAction.UseItem ignored -> BlockPos.ZERO;
            case ChronoAction.InteractEntity a -> BlockPos.containing(a.localPos());
            case ChronoAction.UseContainer a -> a.localPos();
        };
    }

    /**
     * Records why an action was skipped and marks the spot in-world.
     *
     * <p>The particle is the point: a routine that quietly stops doing one of its steps is the most
     * confusing failure this mod can produce, so it gets a visible marker at the exact block.
     */
    private void recordFailure(ServerLevel serverLevel, FailureReason reason, BlockPos localPos,
                               int tick, Direction facing) {
        boolean wasRunning = !lastFailure.halts();
        lastFailure = DiagnosticState.of(reason, localPos, tick);
        setChanged();

        BlockPos worldPos = placement().toWorld(localPos);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5,
                6, 0.2, 0.2, 0.2, 0.01);

        // Only on the transition into halted. A halted anchor re-enters this method every tick, and
        // a sound per tick would be unbearable — which is precisely why it is worth one sound once:
        // a stopped anchor is otherwise silent and looks no different from a finished job.
        if (wasRunning && lastFailure.halts()) {
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 0.5f, 1.4f);
        }
    }

    /**
     * A slow drift of sculk soul above a running anchor.
     *
     * <p>The lit face already says "running" up close, but a block face is invisible from behind and
     * from above. One particle every second and a half is enough to spot a working anchor across a
     * base, and cheap enough to leave on for every anchor on a server.
     */
    private void emitIdleParticles(ServerLevel serverLevel) {
        if (serverLevel.getGameTime() % IDLE_PARTICLE_INTERVAL_TICKS != 0) {
            return;
        }
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5,
                1, 0.15, 0.0, 0.15, 0.0);
    }

    private static final int IDLE_PARTICLE_INTERVAL_TICKS = 30;

    private void syncGhost(ServerLevel serverLevel, CloneRuntime runtime, Direction facing) {
        if (motionTrack == null || motionTrack.isEmpty()) {
            return;
        }
        ChronoCloneEntity ghost = runtime.ghost();
        if (ghost == null || ghost.isRemoved()) {
            ghost = ChronoCloneEntity.create(serverLevel);
            // The AUTHOR, never the owner. This is the one place authorship is used for anything,
            // and it is purely cosmetic: whose skin the clone wears.
            if (recording != null) {
                ghost.setAuthor(recording.authorId(), recording.authorName());
            }
            runtime.setGhost(ghost);
            serverLevel.addFreshEntity(ghost);
        }

        Vec3 pos = motionTrack.worldPositionAt(runtime.playhead(), placement().origin(), facing);
        float yaw = motionTrack.worldYawAt(runtime.playhead(), facing);
        ghost.driveTo(pos, yaw, motionTrack.pitchAt(runtime.playhead()));
        ghost.setHeldItem(upcomingHeldItem(runtime));
    }

    /**
     * What the ghost should be holding right now: whatever the action it is walking towards needs.
     *
     * <p>Read from the action cursor rather than the last action performed, so the clone picks up the
     * pickaxe on the way to the block instead of a tick after breaking it.
     */
    private ItemStack upcomingHeldItem(CloneRuntime runtime) {
        if (recording == null) {
            return ItemStack.EMPTY;
        }
        List<TimedAction> actions = recording.actions();
        int cursor = runtime.actionCursor();
        if (cursor >= actions.size()) {
            return ItemStack.EMPTY;
        }
        return actions.get(cursor).action().heldTemplate();
    }

    private void discardGhosts() {
        runtimes.forEach(CloneRuntime::discardGhost);
    }

    private void setActive(boolean active) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(ChronoAnchorBlock.ACTIVE) != active) {
            level.setBlockAndUpdate(worldPosition, state.setValue(ChronoAnchorBlock.ACTIVE, active));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        discardGhosts();
    }

    /**
     * Spills everything the anchor is holding when it is broken.
     *
     * <p>The base implementation covers {@code Container} block entities only, and this one stores
     * through the transfer API instead — so without this override a full anchor deletes its contents
     * silently. That is the same item-loss failure the transactional break ordering exists to
     * prevent, arriving through a different door.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        spill(level, pos, inventory);
        spill(level, pos, fuelSlot);
        spill(level, pos, upgradeSlots);
    }

    private static void spill(net.minecraft.world.level.Level level, BlockPos pos,
                              ItemStacksResourceHandler handler) {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            int amount = handler.getAmountAsInt(slot);
            if (resource.isEmpty() || amount <= 0) {
                continue;
            }
            net.minecraft.world.Containers.dropItemStack(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, resource.toStack(amount));
            handler.set(slot, ItemResource.EMPTY, 0);
        }
    }

    // --------------------------------------------------------------- components

    /**
     * Carries the routine onto the dropped item, and back off it on placement.
     *
     * <p>Only the routine. The owner is deliberately not a component: it is reassigned by
     * {@link #adopt}, so an anchor cannot be picked up and re-placed while still acting as its
     * previous owner.
     */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (recording != null) {
            builder.set(ModDataComponents.RECORDING.get(), recording);
        }
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        Recording carried = getter.get(ModDataComponents.RECORDING.get());
        if (carried != null) {
            this.recording = carried;
            this.motionTrack = new MotionTrack(carried.motion());
            rebuildRuntimes();
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        // The routine now lives on the stack; leaving a copy in the saved tag would double it up.
        output.discard("recording");
    }

    // ------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        fuelSlot.serialize(output.child("fuel"));
        upgradeSlots.serialize(output.child("upgrades"));
        output.store("charge", ChargeBuffer.CODEC, charge);

        if (recording != null) {
            output.store("recording", RecordingCodecs.RECORDING, recording);
        }
        if (ownerId != null) {
            output.store("owner_id", UUIDUtil.CODEC, ownerId);
            output.putString("owner_name", ownerName);
        }
        output.putBoolean("enabled", enabled);
        if (!originOffset.equals(BlockPos.ZERO)) {
            output.store("origin_offset", BlockPos.CODEC, originOffset);
        }
        if (!precision.equals(TransferPrecision.NONE)) {
            output.putInt("precision", precision.pack());
        }
        output.store("last_failure", DiagnosticState.CODEC, lastFailure);
        // Playheads are deliberately NOT saved: no catch-up on chunk load.
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("inventory").ifPresent(inventory::deserialize);
        input.child("fuel").ifPresent(fuelSlot::deserialize);
        input.child("upgrades").ifPresent(upgradeSlots::deserialize);
        charge = input.read("charge", ChargeBuffer.CODEC).orElse(ChargeBuffer.EMPTY);

        recording = input.read("recording", RecordingCodecs.RECORDING).orElse(null);
        motionTrack = recording == null ? null : new MotionTrack(recording.motion());

        ownerId = input.read("owner_id", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        enabled = input.getBooleanOr("enabled", true);
        originOffset = input.read("origin_offset", BlockPos.CODEC).orElse(BlockPos.ZERO);
        precision = TransferPrecision.unpack(input.getIntOr("precision", 0));
        lastFailure = input.read("last_failure", DiagnosticState.CODEC).orElse(DiagnosticState.NONE);

        // Ghosts are never persisted; runtimes rebuild from their phase offsets on first tick.
        runtimes.clear();
    }

    // ------------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.chronoclones.chrono_anchor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ChronoAnchorMenu(containerId, playerInventory, this, data);
    }
}
