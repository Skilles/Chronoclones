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
import com.skilles.chronoclones.replay.ActionExecutor;
import com.skilles.chronoclones.replay.CloneRuntime;
import com.skilles.chronoclones.replay.LevelActionBudget;
import com.skilles.chronoclones.replay.MotionTrack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    private final List<CloneRuntime> runtimes = new ArrayList<>();
    private int cloneCount = 1;
    /** Which action types this anchor may run. Raised by the fidelity upgrade. */
    private int fidelityTier = 3;
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
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 6;
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

    public @Nullable Recording getRecording() {
        return recording;
    }

    public DiagnosticState getLastFailure() {
        return lastFailure;
    }

    public boolean isEnabled() {
        return enabled;
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
        for (int i = 0; i < cloneCount; i++) {
            runtimes.add(new CloneRuntime(
                    CloneRuntime.phaseOffsetFor(i, cloneCount, recording.lengthTicks())));
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

        if (lastFailure.halts()) {
            // A halting failure freezes the anchor until the player intervenes. Ghosts fade so the
            // stopped state is visible from across the base rather than only in the GUI.
            discardGhosts();
            setActive(false);
            return;
        }

        if (runtimes.isEmpty()) {
            rebuildRuntimes();
        }
        setActive(true);

        Direction facing = getBlockState().getValue(ChronoAnchorBlock.FACING);
        int length = Math.max(recording.lengthTicks(), 1);

        for (CloneRuntime runtime : runtimes) {
            runtime.advance(1);
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

    private void runDueActions(ServerLevel serverLevel, CloneRuntime runtime) {
        if (recording == null || ownerId == null) {
            return;
        }
        List<TimedAction> actions = recording.actions();
        Direction facing = getBlockState().getValue(ChronoAnchorBlock.FACING);

        while (runtime.actionCursor() < actions.size()
                && actions.get(runtime.actionCursor()).tick() <= runtime.playhead()) {

            TimedAction timed = actions.get(runtime.actionCursor());
            ChronoAction action = timed.action();

            // Fidelity gates which action types this anchor may run at all.
            if (!action.type().permittedAt(fidelityTier)) {
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

            runtime.consumeAction();

            ActionExecutor.Result result = switch (action) {
                case ChronoAction.BreakBlock a -> ActionExecutor.executeBreak(
                        serverLevel, a, worldPosition, facing, ownerId, ownerName, inventory);
                case ChronoAction.PlaceBlock a -> ActionExecutor.executePlace(
                        serverLevel, a, worldPosition, facing, ownerId, ownerName, inventory);
                case ChronoAction.AttackEntity a -> ActionExecutor.executeAttack(
                        serverLevel, a, worldPosition, facing, ownerId, ownerName);
                // UseItem is the spec's first cut and is not executed; it is skipped visibly
                // rather than silently so the GUI can say why.
                case ChronoAction.UseItem a -> ActionExecutor.Result.fail(
                        FailureReason.NOT_PERMITTED, a.localPos().orElse(BlockPos.ZERO));
            };

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

    /** Diagnostic position for any action variant, for the failure marker. */
    private static BlockPos localPosOf(ChronoAction action) {
        return switch (action) {
            case ChronoAction.BreakBlock a -> a.localPos();
            case ChronoAction.PlaceBlock a -> a.localPos();
            case ChronoAction.AttackEntity a -> BlockPos.containing(a.localPos());
            case ChronoAction.UseItem a -> a.localPos().orElse(BlockPos.ZERO);
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
        lastFailure = DiagnosticState.of(reason, localPos, tick);
        setChanged();

        BlockPos worldPos = com.skilles.chronoclones.recording.LocalSpace.toWorld(localPos, worldPosition, facing);
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                worldPos.getX() + 0.5, worldPos.getY() + 0.5, worldPos.getZ() + 0.5,
                6, 0.2, 0.2, 0.2, 0.01);
    }

    private void syncGhost(ServerLevel serverLevel, CloneRuntime runtime, Direction facing) {
        if (motionTrack == null || motionTrack.isEmpty()) {
            return;
        }
        ChronoCloneEntity ghost = runtime.ghost();
        if (ghost == null || ghost.isRemoved()) {
            ghost = ChronoCloneEntity.create(serverLevel);
            runtime.setGhost(ghost);
            serverLevel.addFreshEntity(ghost);
        }

        Vec3 pos = motionTrack.worldPositionAt(runtime.playhead(), worldPosition, facing);
        float yaw = motionTrack.worldYawAt(runtime.playhead(), facing);
        ghost.driveTo(pos, yaw, motionTrack.pitchAt(runtime.playhead()));
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

    // ------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));

        if (recording != null) {
            output.store("recording", RecordingCodecs.RECORDING, recording);
        }
        if (ownerId != null) {
            output.store("owner_id", UUIDUtil.CODEC, ownerId);
            output.putString("owner_name", ownerName);
        }
        output.putBoolean("enabled", enabled);
        output.putInt("chrono_count", cloneCount);
        output.putInt("fidelity", fidelityTier);
        output.store("last_failure", DiagnosticState.CODEC, lastFailure);
        // Playheads are deliberately NOT saved: no catch-up on chunk load.
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("inventory").ifPresent(inventory::deserialize);

        recording = input.read("recording", RecordingCodecs.RECORDING).orElse(null);
        motionTrack = recording == null ? null : new MotionTrack(recording.motion());

        ownerId = input.read("owner_id", UUIDUtil.CODEC).orElse(null);
        ownerName = input.getStringOr("owner_name", "");
        enabled = input.getBooleanOr("enabled", true);
        cloneCount = Math.max(1, input.getIntOr("chrono_count", 1));
        fidelityTier = input.getIntOr("fidelity", 3);
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
