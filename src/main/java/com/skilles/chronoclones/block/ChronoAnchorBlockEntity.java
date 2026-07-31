package com.skilles.chronoclones.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.menu.AnchorData;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingCodecs;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.registry.ModBlockEntities;
import com.skilles.chronoclones.registry.ModDataComponents;
import com.skilles.chronoclones.replay.ActionContext;
import com.skilles.chronoclones.replay.AnchorFakePlayer;
import com.skilles.chronoclones.replay.ActionResult;
import com.skilles.chronoclones.replay.AttackActionExecutor;
import com.skilles.chronoclones.replay.AttackOutcome;
import com.skilles.chronoclones.replay.BreakActionExecutor;
import com.skilles.chronoclones.replay.CloneRuntime;
import com.skilles.chronoclones.replay.ContainerActionExecutor;
import com.skilles.chronoclones.replay.InteractEntityActionExecutor;
import com.skilles.chronoclones.replay.Operator;
import com.skilles.chronoclones.replay.LevelActionBudget;
import com.skilles.chronoclones.replay.MotionTrack;
import com.skilles.chronoclones.replay.PlaceActionExecutor;
import com.skilles.chronoclones.replay.Placement;
import com.skilles.chronoclones.replay.UseBlockActionExecutor;
import com.skilles.chronoclones.replay.UseItemActionExecutor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The Chrono Anchor: holds an imprinted recording and replays it on a loop.
 */
public class ChronoAnchorBlockEntity extends BlockEntity implements MenuProvider {

    /** Shaped like a player's, so a recorded slot means the same thing on both sides. */
    public static final int CLONE_INVENTORY_SLOTS = AnchorStorage.CLONE_INVENTORY_SLOTS;

    /** One per possible clone, allocated up front so a splitter coming and going resizes nothing. */
    public static final int CLONE_INVENTORIES = AnchorStorage.CLONE_INVENTORIES;

    public static final int UPGRADE_SLOTS = AnchorStorage.UPGRADE_SLOTS;

    /** Everything the anchor holds. The block entity decides when any of it is spent. */
    private final AnchorStorage storage = new AnchorStorage(this::setChanged);

    /**
     * The player this anchor acts as. Its own, so nothing one anchor's action leaves set can reach
     * another's -- including another owned by the same player.
     */
    private final AnchorFakePlayer actor = new AnchorFakePlayer(worldPosition);

    private @Nullable Recording recording;
    private @Nullable MotionTrack motionTrack;

    private @Nullable UUID ownerId;
    private String ownerName = "";

    private final List<CloneRuntime> runtimes = new ArrayList<>();
    private BlockPos originOffset = BlockPos.ZERO;
    private RunState runState = RunState.RUNNING;
    private DiagnosticState lastFailure = DiagnosticState.NONE;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index >= AnchorData.EXPERIENCE) {
                return storage.cloneExperience(index - AnchorData.EXPERIENCE).points();
            }
            if (index >= AnchorData.PLAYHEAD) {
                int clone = index - AnchorData.PLAYHEAD;
                return clone < runtimes.size() ? runtimes.get(clone).playhead() : 0;
            }
            return switch (index) {
                case AnchorData.LENGTH_TICKS -> recording == null ? 0 : recording.lengthTicks();
                case AnchorData.ACTION_COUNT -> recording == null ? 0 : recording.actions().size();
                case AnchorData.FAILURE_REASON -> lastFailure.reason().ordinal();
                case AnchorData.ACTIVE_CLONES -> storage.upgrades().cloneCount();
                case AnchorData.RUN_STATE -> runState.ordinal();
                case AnchorData.CHARGE -> storage.charge().stored();
                case AnchorData.CHARGE_CAPACITY -> storage.charge().capacity();
                case AnchorData.TICKS_PER_STEP -> storage.upgrades().ticksPerStep();
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
        return storage.combined();
    }

    public ItemStacksResourceHandler getCloneInventory(int clone) {
        return storage.cloneInventory(clone);
    }

    public ExperienceStore getCloneExperience(int clone) {
        return storage.cloneExperience(clone);
    }

    public void setCloneExperience(int clone, ExperienceStore store) {
        storage.setCloneExperience(clone, store);
    }

    public ContainerData getContainerData() {
        return data;
    }

    public ItemStacksResourceHandler getFuelHandler() {
        return storage.fuel();
    }

    public ItemStacksResourceHandler getUpgradeHandler() {
        return storage.upgradeSlots();
    }

    public ChargeBuffer getCharge() {
        return storage.charge();
    }

    public UpgradeState getUpgrades() {
        return storage.upgrades();
    }

    /** The imprinting player: the identity behind every event this anchor causes. */
    public @Nullable UUID getOwnerId() {
        return ownerId;
    }

    public @Nullable Recording getRecording() {
        return recording;
    }

    /** This anchor's own actor, for the tests that check nothing leaks between anchors. */
    public AnchorFakePlayer getActor() {
        return actor;
    }

    public DiagnosticState getLastFailure() {
        return lastFailure;
    }

    public RunState getRunState() {
        return runState;
    }

    /**
     * Stopping takes the clones away and forgets where they were, so running again starts at the
     * top. Pausing leaves them standing, so it carries on.
     */
    public void setRunState(RunState state) {
        if (runState == state) {
            return;
        }
        runState = state;
        setChanged();
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

    /**
     * Swaps in the same performance read differently, leaving the clones mid-stride.
     *
     * <p>Only the settings may change this way, so the motion track and the playheads still mean
     * what they meant; rebuilding the runtimes would restart every clone on an edit.
     */
    public void reinterpret(Recording routine) {
        this.recording = routine;
        setChanged();
    }

    /**
     * Forgets the recording, and hands back everything the clones were holding for it.
     *
     * <p>The storage belongs to the routine that filled it: leaving a heap of ore and a bank of
     * experience inside an anchor that no longer does anything is a way to lose both.
     */
    public void clearRecording() {
        discardClones();
        runtimes.clear();
        recording = null;
        motionTrack = null;
        lastFailure = DiagnosticState.NONE;
        if (level != null) {
            storage.spillClones(level, worldPosition);
        }
        setActive(false);
        setChanged();
    }

    /**
     * Hands the recording back to whoever asked for it, leaving the anchor blank.
     *
     * @return what it was holding, or null if it was already blank
     */
    public @Nullable Recording extractRecording() {
        Recording held = recording;
        if (held != null) {
            clearRecording();
        }
        return held;
    }

    private void rebuildRuntimes() {
        discardClones();
        runtimes.clear();
        if (recording == null) {
            return;
        }
        int count = storage.upgrades().cloneCount();
        for (int i = 0; i < count; i++) {
            runtimes.add(new CloneRuntime(i,
                    CloneRuntime.phaseOffsetFor(i, count, recording.lengthTicks())));
        }
    }

    // ------------------------------------------------------------------ replay

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Above the early return: pulling a splitter spills, routine or no routine.
        if (storage.reconcileUpgrades(serverLevel, worldPosition)) {
            rebuildRuntimes();
        }

        if (runState == RunState.STOPPED || recording == null || motionTrack == null
                || ownerId == null) {
            if (!runtimes.isEmpty()) {
                discardClones();
                runtimes.clear();
                setActive(false);
            }
            return;
        }

        if (runState == RunState.PAUSED) {
            // Held, not put away: the clones keep standing where they got to, so resuming carries
            // on rather than starting the recording again.
            for (CloneRuntime runtime : runtimes) {
                syncClone(serverLevel, runtime, getBlockState().getValue(ChronoAnchorBlock.FACING));
            }
            setActive(false);
            return;
        }

        storage.consumeFuel(serverLevel);

        if (lastFailure.halts()) {
            if (!DiagnosticState.canResume(lastFailure.reason(), !storage.charge().isEmpty(),
                    storage.hasRoom())) {
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
        ClonePresentation.idleParticles(serverLevel, worldPosition);

        Direction facing = getBlockState().getValue(ChronoAnchorBlock.FACING);
        Placement placement = placement();
        int length = Math.max(recording.lengthTicks(), 1);

        for (CloneRuntime runtime : runtimes) {
            runtime.advance(storage.upgrades().ticksPerStep());
            // A clone still holding something down is mid-action, and looping would reset the
            // cursor out from under it and strand the item it borrowed.
            if (runtime.playhead() >= length && !runtime.isUsing()) {
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
                                ChronoAction.BreakBlock action, ActionSettings settings,
                                Placement placement, Direction facing, int cost) {
        ActionContext probe = contextFor(serverLevel, runtime, placement, settings);
        ActionResult refusal = BreakActionExecutor.canBreak(probe, action);
        if (refusal != null) {
            // Also covers the block vanishing mid-dig.
            ClonePresentation.stopMining(serverLevel, runtime, worldPosition);
            runtime.consumeAction();
            recordFailure(serverLevel, refusal.reason(), refusal.localPos(), runtime.playhead(), facing);
            return true;
        }

        BlockPos worldPos = placement.toWorld(action.localPos());
        boolean instant = recording != null && recording.creative();

        if (!instant) {
            // Rate upgrades speed mining too: swinging is part of the routine.
            float perTick = BreakActionExecutor.progressPerTick(
                    contextFor(serverLevel, runtime, placement, settings), action)
                    * storage.upgrades().ticksPerStep();
            float progress = runtime.mine(worldPos, perTick);

            if (progress < 1.0f) {
                ClonePresentation.showCracks(serverLevel, runtime, worldPosition, worldPos, progress);
                return false;
            }
        }

        ClonePresentation.stopMining(serverLevel, runtime, worldPosition);
        runtime.consumeAction();

        ActionContext ctx = contextFor(serverLevel, runtime, placement, settings);
        ActionResult result = BreakActionExecutor.finish(ctx, action);
        settle(runtime, ctx.operator());
        if (result.succeeded()) {
            storage.spendCharge(cost);
        } else {
            recordFailure(serverLevel, result.reason(), result.localPos(), runtime.playhead(), facing);
        }
        return true;
    }

    /**
     * One swing, or as many as it takes when the player's swings ended in a kill.
     *
     * @return true if the action finished this tick
     */
    private boolean attackOneTick(ServerLevel serverLevel, CloneRuntime runtime,
                                  ChronoAction.AttackEntity action, ActionSettings settings,
                                  Placement placement, Direction facing, int cost) {
        ActionSettings.TargetRule rule = settings.target();
        LivingEntity sticky = rule.locksTarget() ? runtime.target(serverLevel) : null;

        ActionContext ctx = contextFor(serverLevel, runtime, placement, settings);
        AttackOutcome attack = AttackActionExecutor.execute(ctx, action, sticky);
        settle(runtime, ctx.operator());
        runtime.setTarget(attack.targetId());

        // A swing absorbed by invulnerability frames did no work, so it buys none.
        if (attack.hitLanded()) {
            storage.spendCharge(cost);
        }

        boolean unfinished = rule.completion() == ActionSettings.TargetRule.Completion.UNTIL_DEAD
                && attack.targetAlive();
        if (unfinished && runtime.targetTicks() < ChronoclonesConfig.MAX_ACTION_TICKS.getAsInt()) {
            runtime.awaitTarget();
            return false;
        }

        runtime.releaseTarget();
        runtime.consumeAction();

        if (!attack.result().succeeded()) {
            recordFailure(serverLevel, attack.result().reason(), attack.result().localPos(),
                    runtime.playhead(), facing);
        } else if (unfinished) {
            // Out of patience rather than out of targets, which is a different thing to report.
            recordFailure(serverLevel, FailureReason.UNFINISHED,
                    BlockPos.containing(action.localPos()), runtime.playhead(), facing);
        }
        return true;
    }

    /**
     * One tick of an item held down, which is one action spread over as many ticks as it was held.
     *
     * @return true if the action finished this tick
     */
    private boolean useOneTick(ServerLevel serverLevel, CloneRuntime runtime,
                               ChronoAction.UseItem action, ActionSettings settings,
                               Placement placement, Direction facing, int cost) {
        ActionContext ctx = contextFor(serverLevel, runtime, placement, settings);
        UseItemActionExecutor.Progress progress = UseItemActionExecutor.tick(ctx, action, runtime);
        settle(runtime, ctx.operator());

        // A held item that never lets go would stall the routine for good, the same way an attack
        // on something that cannot die would; both give up on the same cap.
        boolean outOfPatience = runtime.usingTicks() >= ChronoclonesConfig.MAX_ACTION_TICKS.getAsInt();
        if (!progress.finished() && !outOfPatience) {
            return false;
        }

        runtime.clearUse();
        runtime.consumeAction();

        if (progress.result().succeeded() && !outOfPatience) {
            storage.spendCharge(cost);
        } else if (outOfPatience) {
            recordFailure(serverLevel, FailureReason.UNFINISHED, BlockPos.ZERO,
                    runtime.playhead(), facing);
        } else {
            recordFailure(serverLevel, progress.result().reason(), progress.result().localPos(),
                    runtime.playhead(), facing);
        }
        return true;
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

            // Leave the cursor so the action retries next tick.
            if (!LevelActionBudget.tryClaim(serverLevel, worldPosition)) {
                return;
            }

            // More clones at a higher rate burn charge proportionally faster.
            int cost = action.chargeCost();
            if (!storage.canAfford(cost)) {
                recordFailure(serverLevel, FailureReason.NO_CHARGE, localPosOf(action),
                        runtime.playhead(), facing);
                return;
            }

            // Breaking holds the cursor until the block is gone.
            if (action instanceof ChronoAction.BreakBlock breaking) {
                if (!mineOneTick(serverLevel, runtime, breaking, timed.settings(), placement, facing, cost)) {
                    return;
                }
                continue;
            }

            // So does an attack the player finished, until its target is finished too.
            if (action instanceof ChronoAction.AttackEntity attacking) {
                if (!attackOneTick(serverLevel, runtime, attacking, timed.settings(), placement,
                        facing, cost)) {
                    return;
                }
                continue;
            }

            // And so does an item held down, for as long as the player held it.
            if (action instanceof ChronoAction.UseItem using
                    && (using.isHeld() || runtime.isUsing())) {
                if (!useOneTick(serverLevel, runtime, using, timed.settings(), placement, facing,
                        cost)) {
                    return;
                }
                continue;
            }

            runtime.consumeAction();

            ActionContext ctx = contextFor(serverLevel, runtime, placement, timed.settings());
            ActionResult result = switch (action) {
                case ChronoAction.BreakBlock a -> throw new IllegalStateException("handled above");
                case ChronoAction.PlaceBlock a -> PlaceActionExecutor.execute(ctx, a);
                case ChronoAction.AttackEntity a -> throw new IllegalStateException("handled above");
                case ChronoAction.UseOnBlock a -> UseBlockActionExecutor.execute(ctx, a);
                // Only the instant ones reach here; anything held was taken above and holds the
                // cursor until it lets go.
                case ChronoAction.UseItem a -> UseItemActionExecutor.tick(ctx, a, runtime).result();
                case ChronoAction.InteractEntity a -> InteractEntityActionExecutor.execute(ctx, a);
                case ChronoAction.UseContainer a -> ContainerActionExecutor.execute(ctx, a);
            };

            settle(runtime, ctx.operator());

            if (result.succeeded()) {
                // Only pay for work that happened.
                storage.spendCharge(cost);
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

    private ItemStacksResourceHandler inventoryOf(CloneRuntime runtime) {
        return storage.cloneInventory(runtime.index());
    }

    /**
     * Everything one action needs from the anchor running it.
     *
     * <p>Built fresh per action rather than kept, because the operator carries that clone's banked
     * experience out and back: a stale one would spend experience the clone no longer has.
     */
    private ActionContext contextFor(ServerLevel serverLevel, CloneRuntime runtime,
                                     Placement placement, ActionSettings settings) {
        return new ActionContext(serverLevel, placement, operatorFor(runtime),
                inventoryOf(runtime), settings, actor, runtime.index());
    }

    /**
     * Who the anchor acts as for one action, carrying that clone's banked experience out and back.
     */
    private Operator operatorFor(CloneRuntime runtime) {
        return new Operator(ownerId, ownerName, storage.cloneExperience(runtime.index()));
    }

    /** Banks whatever the action left the operator holding. */
    private void settle(CloneRuntime runtime, Operator operator) {
        if (!operator.store().equals(storage.cloneExperience(runtime.index()))) {
            storage.setCloneExperience(runtime.index(), operator.store());
        }
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
            case ChronoAction.UseContainer a -> a.target().localBlock();
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

        ClonePresentation.failureParticles(serverLevel, placement().toWorld(localPos));

        // Only on the transition into halted: this runs every tick while stuck.
        if (wasRunning && lastFailure.halts()) {
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 0.5f, 1.4f);
        }
    }

    /** Puts a clone where its playhead says it should be, spawning it if it is not there. */
    private void syncClone(ServerLevel serverLevel, CloneRuntime runtime, Direction facing) {
        if (motionTrack == null) {
            return;
        }
        ClonePresentation.sync(serverLevel, runtime, motionTrack, placement(), facing, recording);
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
        // The fake player goes with the anchor: one that outlived it would be a player-shaped
        // entity nothing owns, holding whatever the last action left in it.
        actor.discard();
    }

    /**
     * Spills everything the anchor is holding when it is broken.
     */
    @Override
    public void preRemoveSideEffects(@NonNull BlockPos pos, @NonNull BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            storage.spillEverything(level, pos);
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
        storage.save(output);

        if (recording != null) {
            output.store("recording", RecordingCodecs.RECORDING, recording);
        }
        if (ownerId != null) {
            output.store("owner_id", UUIDUtil.CODEC, ownerId);
            output.putString("owner_name", ownerName);
        }
        output.putString("run_state", runState.getSerializedName());
        if (!originOffset.equals(BlockPos.ZERO)) {
            output.store("origin_offset", BlockPos.CODEC, originOffset);
        }
        output.store("last_failure", DiagnosticState.CODEC, lastFailure);
        // Playheads are not saved: no catch-up on chunk load.
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        storage.load(input);

        recording = input.read("recording", RecordingCodecs.RECORDING).orElse(null);
        motionTrack = recording == null ? null : new MotionTrack(recording.motion());

        ownerId = input.read("owner_id", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        // "enabled" is only ever read: anchors saved before there was anything but running
        // and stopped carry their state as a boolean.
        runState = input.getString("run_state")
                .map(RunState::byName)
                .orElseGet(() -> input.getBooleanOr("enabled", true)
                        ? RunState.RUNNING
                        : RunState.STOPPED);
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
