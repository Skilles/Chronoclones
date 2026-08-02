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
import com.skilles.chronoclones.replay.RunReport;
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

public class ChronoAnchorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int CLONE_INVENTORY_SLOTS = AnchorStorage.CLONE_INVENTORY_SLOTS;

    public static final int CLONE_INVENTORIES = AnchorStorage.CLONE_INVENTORIES;

    public static final int UPGRADE_SLOTS = AnchorStorage.UPGRADE_SLOTS;

    private final AnchorStorage storage = new AnchorStorage(this::setChanged);

    private final AnchorFakePlayer actor = new AnchorFakePlayer(worldPosition);

    private @Nullable Recording recording;
    private @Nullable MotionTrack motionTrack;

    private @Nullable UUID ownerId;
    private String ownerName = "";

    private final List<CloneRuntime> runtimes = new ArrayList<>();
    private BlockPos originOffset = BlockPos.ZERO;
    private RunState runState = RunState.RUNNING;
    private DiagnosticState lastFailure = DiagnosticState.NONE;

    // Not saved: the routine loops, so a fresh report fills back in within one cycle.
    private final RunReport report = new RunReport();

    private boolean obeysRedstone;
    // Persisted so a reload does not read the world's standing power as a fresh edge.
    private boolean redstonePowered;
    private boolean finishing;

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
                case AnchorData.REPORT_OK -> report.count(RunReport.Outcome.OK);
                case AnchorData.REPORT_SKIPPED -> report.count(RunReport.Outcome.SKIPPED)
                        + report.count(RunReport.Outcome.HALTED);
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

    public @Nullable UUID getOwnerId() {
        return ownerId;
    }

    public @Nullable Recording getRecording() {
        return recording;
    }

    public AnchorFakePlayer getActor() {
        return actor;
    }

    public DiagnosticState getLastFailure() {
        return lastFailure;
    }

    public RunReport getRunReport() {
        return report;
    }

    public RunState getRunState() {
        return runState;
    }

    public void setRunState(RunState state) {
        if (runState == state) {
            return;
        }
        runState = state;
        finishing = false;
        setChanged();
        notifyComparators();
    }

    public boolean obeysRedstone() {
        return obeysRedstone;
    }

    public void setObeysRedstone(boolean obeys) {
        if (obeysRedstone == obeys) {
            return;
        }
        obeysRedstone = obeys;
        setChanged();
    }

    /** A rising edge starts the routine; losing the signal lets the cycle finish first. */
    public void onRedstoneSignal(boolean powered) {
        boolean was = redstonePowered;
        redstonePowered = powered;
        if (powered == was) {
            return;
        }
        setChanged();
        if (!obeysRedstone) {
            return;
        }
        if (powered) {
            setRunState(RunState.RUNNING);
        } else if (runState == RunState.RUNNING) {
            finishing = true;
            setChanged();
        }
    }

    public int comparatorSignal() {
        return RedstoneStatus.signalOf(runState, recording != null, lastFailure.halts(), finishing);
    }

    private void notifyComparators() {
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public BlockPos getOriginOffset() {
        return originOffset;
    }

    public Placement placement() {
        return Placement.of(worldPosition, getBlockState().getValue(ChronoAnchorBlock.FACING),
                originOffset);
    }

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

    public void imprint(Recording recording, ServerPlayer imprinter) {
        this.recording = recording;
        this.motionTrack = new MotionTrack(recording.motion());
        this.ownerId = imprinter.getUUID();
        this.ownerName = imprinter.getGameProfile().name();
        this.lastFailure = DiagnosticState.NONE;
        report.resize(recording.actions().size());

        rebuildRuntimes();
        setChanged();
    }

    public void adopt(ServerPlayer placer) {
        this.ownerId = placer.getUUID();
        this.ownerName = placer.getGameProfile().name();
        this.lastFailure = DiagnosticState.NONE;
        setChanged();
    }

    /** Swaps in the same performance read differently, leaving the clones mid-stride. */
    public void reinterpret(Recording routine) {
        this.recording = routine;
        report.resize(routine.actions().size());
        setChanged();
    }

    /** The storage belongs to the routine that filled it, so it is handed back. */
    public void clearRecording() {
        discardClones();
        runtimes.clear();
        recording = null;
        motionTrack = null;
        lastFailure = DiagnosticState.NONE;
        report.resize(0);
        if (level != null) {
            storage.spillClones(level, worldPosition);
        }
        setActive(false);
        setChanged();
    }

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

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

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
                discardClones();
                setActive(false);
                return;
            }
            lastFailure = DiagnosticState.NONE;
            setChanged();
            notifyComparators();
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
            if (runtime.finished()) {
                continue;
            }
            runtime.advance(storage.upgrades().ticksPerStep());
        // Looping mid-use would reset the cursor and strand the borrowed item.
            if (runtime.playhead() >= length && !runtime.isUsing()) {
                if (finishing) {
                    runtime.finish();
                    continue;
                }
                runtime.loop(length);
            }

            syncClone(serverLevel, runtime, facing);
            runDueActions(serverLevel, runtime);

            if (lastFailure.halts()) {
                return;
            }
        }

        if (finishing && runtimes.stream().allMatch(CloneRuntime::finished)) {
            setRunState(RunState.STOPPED);
        }
    }

    private boolean mineOneTick(ServerLevel serverLevel, CloneRuntime runtime,
                                ChronoAction.BreakBlock action, ActionSettings settings,
                                Placement placement, Direction facing, int cost) {
        int actionIndex = runtime.actionCursor();
        ActionContext probe = contextFor(serverLevel, runtime, placement, settings);
        ActionResult refusal = BreakActionExecutor.canBreak(probe, action);
        if (refusal != null) {
            ClonePresentation.stopMining(serverLevel, runtime, worldPosition);
            runtime.consumeAction();
            report.record(actionIndex, runtime.index(), serverLevel.getGameTime(), refusal);
            recordFailure(serverLevel, refusal.reason(), refusal.localPos(), runtime.playhead(), facing);
            return true;
        }

        BlockPos worldPos = placement.toWorld(action.localPos());
        boolean instant = recording != null && recording.creative();

        if (!instant) {
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
        report.record(actionIndex, runtime.index(), serverLevel.getGameTime(), result);
        if (result.succeeded()) {
            storage.spendCharge(cost);
        } else {
            recordFailure(serverLevel, result.reason(), result.localPos(), runtime.playhead(), facing);
        }
        return true;
    }

    private boolean attackOneTick(ServerLevel serverLevel, CloneRuntime runtime,
                                  ChronoAction.AttackEntity action, ActionSettings settings,
                                  Placement placement, Direction facing, int cost) {
        int actionIndex = runtime.actionCursor();
        ActionSettings.TargetRule rule = settings.target();
        LivingEntity sticky = rule.locksTarget() ? runtime.target(serverLevel) : null;

        ActionContext ctx = contextFor(serverLevel, runtime, placement, settings);
        AttackOutcome attack = AttackActionExecutor.execute(ctx, action, sticky);
        settle(runtime, ctx.operator());
        runtime.setTarget(attack.targetId());

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
            report.record(actionIndex, runtime.index(), serverLevel.getGameTime(), attack.result());
            recordFailure(serverLevel, attack.result().reason(), attack.result().localPos(),
                    runtime.playhead(), facing);
        } else if (unfinished) {
            report.record(actionIndex, runtime.index(), serverLevel.getGameTime(),
                    ActionResult.fail(FailureReason.UNFINISHED, BlockPos.containing(action.localPos())));
            recordFailure(serverLevel, FailureReason.UNFINISHED,
                    BlockPos.containing(action.localPos()), runtime.playhead(), facing);
        } else {
            report.record(actionIndex, runtime.index(), serverLevel.getGameTime(), ActionResult.OK);
        }
        return true;
    }

    private boolean useOneTick(ServerLevel serverLevel, CloneRuntime runtime,
                               ChronoAction.UseItem action, ActionSettings settings,
                               Placement placement, Direction facing, int cost) {
        int actionIndex = runtime.actionCursor();
        ActionContext ctx = contextFor(serverLevel, runtime, placement, settings);
        UseItemActionExecutor.Progress progress = UseItemActionExecutor.tick(ctx, action, runtime);
        settle(runtime, ctx.operator());

        boolean outOfPatience = runtime.usingTicks() >= ChronoclonesConfig.MAX_ACTION_TICKS.getAsInt();
        if (!progress.finished() && !outOfPatience) {
            return false;
        }

        runtime.clearUse();
        runtime.consumeAction();

        if (progress.result().succeeded() && !outOfPatience) {
            report.record(actionIndex, runtime.index(), serverLevel.getGameTime(), ActionResult.OK);
            storage.spendCharge(cost);
        } else if (outOfPatience) {
            report.record(actionIndex, runtime.index(), serverLevel.getGameTime(),
                    ActionResult.fail(FailureReason.UNFINISHED, BlockPos.ZERO));
            recordFailure(serverLevel, FailureReason.UNFINISHED, BlockPos.ZERO,
                    runtime.playhead(), facing);
        } else {
            report.record(actionIndex, runtime.index(), serverLevel.getGameTime(), progress.result());
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

            int cost = action.chargeCost();
            if (!storage.canAfford(cost)) {
                report.record(runtime.actionCursor(), runtime.index(), serverLevel.getGameTime(),
                        ActionResult.fail(FailureReason.NO_CHARGE, localPosOf(action)));
                recordFailure(serverLevel, FailureReason.NO_CHARGE, localPosOf(action),
                        runtime.playhead(), facing);
                return;
            }

            if (action instanceof ChronoAction.BreakBlock breaking) {
                if (!mineOneTick(serverLevel, runtime, breaking, timed.settings(), placement, facing, cost)) {
                    return;
                }
                continue;
            }

            if (action instanceof ChronoAction.AttackEntity attacking) {
                if (!attackOneTick(serverLevel, runtime, attacking, timed.settings(), placement,
                        facing, cost)) {
                    return;
                }
                continue;
            }

            if (action instanceof ChronoAction.UseItem using
                    && (using.isHeld() || runtime.isUsing())) {
                if (!useOneTick(serverLevel, runtime, using, timed.settings(), placement, facing,
                        cost)) {
                    return;
                }
                continue;
            }

            int actionIndex = runtime.actionCursor();
            runtime.consumeAction();

            ActionContext ctx = contextFor(serverLevel, runtime, placement, timed.settings());
            ActionResult result = switch (action) {
                case ChronoAction.BreakBlock a -> throw new IllegalStateException("handled above");
                case ChronoAction.PlaceBlock a -> PlaceActionExecutor.execute(ctx, a);
                case ChronoAction.AttackEntity a -> throw new IllegalStateException("handled above");
                case ChronoAction.UseOnBlock a -> UseBlockActionExecutor.execute(ctx, a);
                case ChronoAction.UseItem a -> UseItemActionExecutor.tick(ctx, a, runtime).result();
                case ChronoAction.InteractEntity a -> InteractEntityActionExecutor.execute(ctx, a);
                case ChronoAction.UseContainer a -> ContainerActionExecutor.execute(ctx, a);
            };

            settle(runtime, ctx.operator());
            report.record(actionIndex, runtime.index(), serverLevel.getGameTime(), result);

            if (result.succeeded()) {
                storage.spendCharge(cost);
            }

            if (!result.succeeded()) {
                recordFailure(serverLevel, result.reason(), result.localPos(), runtime.playhead(), facing);
                if (result.reason().halts()) {
                    return;
                }
            } else if (lastFailure.isFailure()) {
                lastFailure = DiagnosticState.NONE;
                setChanged();
                notifyComparators();
            }
        }
    }

    private ItemStacksResourceHandler inventoryOf(CloneRuntime runtime) {
        return storage.cloneInventory(runtime.index());
    }

    private ActionContext contextFor(ServerLevel serverLevel, CloneRuntime runtime,
                                     Placement placement, ActionSettings settings) {
        return new ActionContext(serverLevel, placement, operatorFor(runtime),
                inventoryOf(runtime), settings, actor, runtime.index());
    }

    private Operator operatorFor(CloneRuntime runtime) {
        return new Operator(ownerId, ownerName, storage.cloneExperience(runtime.index()));
    }

    private void settle(CloneRuntime runtime, Operator operator) {
        if (!operator.store().equals(storage.cloneExperience(runtime.index()))) {
            storage.setCloneExperience(runtime.index(), operator.store());
        }
    }

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

    private void recordFailure(ServerLevel serverLevel, FailureReason reason, BlockPos localPos,
                               int tick, Direction facing) {
        boolean wasRunning = !lastFailure.halts();
        lastFailure = DiagnosticState.of(reason, localPos, tick);
        setChanged();
        notifyComparators();

        ClonePresentation.failureParticles(serverLevel, placement().toWorld(localPos));

        if (wasRunning && lastFailure.halts()) {
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.BLOCKS, 0.5f, 1.4f);
        }
    }

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
        actor.discard();
    }

    @Override
    public void preRemoveSideEffects(@NonNull BlockPos pos, @NonNull BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            storage.spillEverything(level, pos);
        }
    }

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
            report.resize(carried.actions().size());
            rebuildRuntimes();
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        output.discard("recording");
    }

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
        output.putBoolean("obeys_redstone", obeysRedstone);
        output.putBoolean("redstone_powered", redstonePowered);
        output.putBoolean("finishing", finishing);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        storage.load(input);

        recording = input.read("recording", RecordingCodecs.RECORDING).orElse(null);
        motionTrack = recording == null ? null : new MotionTrack(recording.motion());
        report.resize(recording == null ? 0 : recording.actions().size());

        ownerId = input.read("owner_id", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        runState = input.getString("run_state")
                .map(RunState::byName)
                .orElseGet(() -> input.getBooleanOr("enabled", true)
                        ? RunState.RUNNING
                        : RunState.STOPPED);
        originOffset = input.read("origin_offset", BlockPos.CODEC).orElse(BlockPos.ZERO);
        lastFailure = input.read("last_failure", DiagnosticState.CODEC).orElse(DiagnosticState.NONE);
        obeysRedstone = input.getBooleanOr("obeys_redstone", false);
        redstonePowered = input.getBooleanOr("redstone_powered", false);
        finishing = input.getBooleanOr("finishing", false);

        runtimes.clear();
    }

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
