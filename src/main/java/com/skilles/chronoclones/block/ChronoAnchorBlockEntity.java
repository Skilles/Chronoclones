package com.skilles.chronoclones.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.menu.AnchorData;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The Chrono Anchor: holds an imprinted recording and replays it on a loop.
 */
public class ChronoAnchorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INVENTORY_SLOTS = 18;
    public static final int UPGRADE_SLOTS = 3;

    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(INVENTORY_SLOTS) {
        @Override
        protected void onContentsChanged(int index, @NonNull ItemStack previousContents) {
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
        protected void onContentsChanged(int index, @NonNull ItemStack previousContents) {
            setChanged();
        }
    };

    private final ItemStacksResourceHandler upgradeSlots = new ItemStacksResourceHandler(UPGRADE_SLOTS) {
        @Override
        protected void onContentsChanged(int index, @NonNull ItemStack previousContents) {
            setChanged();
        }
    };

    private final List<CloneRuntime> runtimes = new ArrayList<>();
    private BlockPos originOffset = BlockPos.ZERO;
    private UpgradeState upgrades = UpgradeState.BASE;
    private ChargeBuffer charge = ChargeBuffer.EMPTY;
    private boolean enabled = true;
    private DiagnosticState lastFailure = DiagnosticState.NONE;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case AnchorData.PLAYHEAD -> runtimes.isEmpty() ? 0 : runtimes.get(0).playhead();
                case AnchorData.LENGTH_TICKS -> recording == null ? 0 : recording.lengthTicks();
                case AnchorData.ACTION_COUNT -> recording == null ? 0 : recording.actions().size();
                case AnchorData.FAILURE_REASON -> lastFailure.reason().ordinal();
                case AnchorData.ACTIVE_CLONES -> runtimes.size();
                case AnchorData.CHARGE -> charge.stored();
                case AnchorData.CHARGE_CAPACITY -> charge.capacity();
                case AnchorData.TICKS_PER_STEP -> upgrades.ticksPerStep();
                case AnchorData.FAILURE_X -> lastFailure.localPos().getX();
                case AnchorData.FAILURE_Y -> lastFailure.localPos().getY();
                case AnchorData.FAILURE_Z -> lastFailure.localPos().getZ();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return AnchorData.COUNT;
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

    /** The imprinting player: the identity behind every event this anchor causes. */
    public @Nullable UUID getOwnerId() {
        return ownerId;
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

    /**
     * Where this anchor's routine lands, and where its radius is measured from.
     */
    public Placement placement() {
        return Placement.of(worldPosition, getBlockState().getValue(ChronoAnchorBlock.FACING),
                originOffset);
    }

    /**
     * Moves the routine's origin, clamped so a nudge cannot extend reach.
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
     */
    public void adopt(ServerPlayer placer) {
        this.ownerId = placer.getUUID();
        this.ownerName = placer.getGameProfile().name();
        this.lastFailure = DiagnosticState.NONE;
        setChanged();
    }

    public void clearRecording() {
        discardClones();
        runtimes.clear();
        recording = null;
        motionTrack = null;
        lastFailure = DiagnosticState.NONE;
        setActive(false);
        setChanged();
    }

    private void rebuildRuntimes() {
        discardClones();
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
                discardClones();
                runtimes.clear();
                setActive(false);
            }
            return;
        }

        // Before the halt check: both halting reasons name a resource the player restores.
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
                // Clones stay faded so the state is visible outside the GUI.
                discardClones();
                setActive(false);
                return;
            }
            // Cause cleared; resume.
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

            syncClone(serverLevel, runtime, facing);
            runDueActions(serverLevel, runtime);

            if (lastFailure.halts()) {
                return;
            }
        }
    }

    /**
     * One tick of mining, or the whole break for a creative routine.
     *
     * @return true if the action finished this tick
     */
    private boolean mineOneTick(ServerLevel serverLevel, CloneRuntime runtime,
                                ChronoAction.BreakBlock action, Placement placement,
                                Direction facing, int cost) {
        ActionExecutor.Result refusal =
                ActionExecutor.canBreak(serverLevel, action, placement);
        if (refusal != null) {
            // Also covers the block vanishing mid-dig.
            stopMining(serverLevel, runtime);
            runtime.consumeAction();
            recordFailure(serverLevel, refusal.reason(), refusal.localPos(), runtime.playhead(), facing);
            return true;
        }

        BlockPos worldPos = placement.toWorld(action.localPos());
        boolean instant = recording != null && recording.creative();

        if (!instant) {
            // Rate upgrades speed mining too: swinging is part of the routine.
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
     * The cracking overlay, keyed to the clone doing the digging.
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
            // -1 is vanilla's "no longer breaking this"; without it the cracks persist.
            serverLevel.destroyBlockProgress(breakerIdOf(runtime), was, -1);
        }
        runtime.clearMining();
    }

    private int breakerIdOf(CloneRuntime runtime) {
        ChronoCloneEntity clone = runtime.cloneEntity();
        return clone != null ? clone.getId() : worldPosition.hashCode();
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

            // Fidelity gates which action types this anchor may run.
            if (!action.type().permittedAt(upgrades.fidelityTier())) {
                runtime.consumeAction();
                recordFailure(serverLevel, FailureReason.NOT_PERMITTED, localPosOf(action),
                        runtime.playhead(), facing);
                continue;
            }

            // Leave the cursor so the action retries next tick.
            if (!LevelActionBudget.tryClaim(serverLevel)) {
                return;
            }

            // More clones at a higher rate burn charge proportionally faster.
            int cost = action.chargeCost();
            if (!charge.canAfford(cost)) {
                recordFailure(serverLevel, FailureReason.NO_CHARGE, localPosOf(action),
                        runtime.playhead(), facing);
                return;
            }

            // Breaking holds the cursor until the block is gone.
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
                        serverLevel, a, placement, ownerId, ownerName, inventory);
            };

            if (result.succeeded()) {
                // Only pay for work that happened.
                charge = charge.spend(cost);
                setChanged();
            }

            if (!result.succeeded()) {
                recordFailure(serverLevel, result.reason(), result.localPos(), runtime.playhead(), facing);
                if (result.reason().halts()) {
                    return;
                }
            } else if (lastFailure.isFailure()) {
                // A success clears a previous non-halting failure.
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
     */
    private void consumeFuel() {
        if (level == null || charge.headroom() <= 0) {
            return;
        }
        ItemResource resource = fuelSlot.getResource(0);
        if (resource.isEmpty() || fuelSlot.getAmountAsInt(0) <= 0) {
            return;
        }

        // Creative cell: top up, never consume.
        if (resource.getItem() == ModItems.CREATIVE_CHARGE_CELL.get()) {
            charge = charge.refill(charge.headroom());
            setChanged();
            return;
        }

        ItemStack probe = resource.toStack(1);
        int burnTicks = probe.getBurnTime(null, level.fuelValues());
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

        // Only on the transition into halted: this runs every tick while stuck.
        if (wasRunning && lastFailure.halts()) {
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 0.5f, 1.4f);
        }
    }

    /**
     * Idle particles above a running anchor.
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

    private void syncClone(ServerLevel serverLevel, CloneRuntime runtime, Direction facing) {
        if (motionTrack == null || motionTrack.isEmpty()) {
            return;
        }
        ChronoCloneEntity clone = runtime.cloneEntity();
        if (clone == null || clone.isRemoved()) {
            clone = ChronoCloneEntity.create(serverLevel);
            // The author, not the owner: authorship only decides the skin.
            if (recording != null) {
                clone.setAuthor(recording.authorId(), recording.authorName());
            }
            runtime.setClone(clone);
            serverLevel.addFreshEntity(clone);
        }

        Vec3 pos = motionTrack.worldPositionAt(runtime.playhead(), placement().origin(), facing);
        float yaw = motionTrack.worldYawAt(runtime.playhead(), facing);
        clone.driveTo(pos, yaw, motionTrack.pitchAt(runtime.playhead()));
        clone.setHeldItem(upcomingHeldItem(runtime));
    }

    /**
     * The item for the action the clone is walking towards.
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

    private void discardClones() {
        runtimes.forEach(CloneRuntime::discardClone);
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
        discardClones();
    }

    /**
     * Spills everything the anchor is holding when it is broken.
     */
    @Override
    public void preRemoveSideEffects(@NonNull BlockPos pos, @NonNull BlockState state) {
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
     */
    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        if (recording != null) {
            builder.set(ModDataComponents.RECORDING.get(), recording);
        }
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.@NonNull DataComponentGetter getter) {
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
        // The routine lives on the stack now; a copy in the tag would duplicate it.
        output.discard("recording");
    }

    // ------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
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
        output.store("last_failure", DiagnosticState.CODEC, lastFailure);
        // Playheads are not saved: no catch-up on chunk load.
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
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
        lastFailure = input.read("last_failure", DiagnosticState.CODEC).orElse(DiagnosticState.NONE);

        // Clones are not persisted; runtimes rebuild from phase offsets on first tick.
        runtimes.clear();
    }

    // ------------------------------------------------------------------- menu

    @Override
    @NonNull
    public Component getDisplayName() {
        return Component.translatable("block.chronoclones.chrono_anchor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, @NonNull Inventory playerInventory, @NonNull Player player) {
        return new ChronoAnchorMenu(containerId, playerInventory, this, data);
    }
}
