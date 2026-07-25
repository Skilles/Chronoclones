package com.skilles.chronoclones.block;

import java.util.List;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.registry.ModBlockEntities;
import com.skilles.chronoclones.replay.MotionPath;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * DAY 1 SPIKE. This is not the real replay engine — it drives one ghost around a hardcoded
 * waypoint loop to answer the single question the design hangs on: does lerped motion accumulate
 * positional drift over many loops?
 *
 * <p>The answer is designed to be "no, structurally": {@link #posFor(int)} is a pure function of an
 * integer playhead. Nothing is ever added to a previous position. {@link #playhead} is the only
 * mutable motion state and it is an int that resets exactly to 0.
 */
public class ChronoAnchorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INVENTORY_SLOTS = 18;

    /** Spike route, in anchor-local space. Replaced by the real motion track on Day 2. */
    private static final List<Vec3> WAYPOINTS = List.of(
            new Vec3(0.5, 0.0, 1.5),
            new Vec3(3.5, 0.0, 1.5),
            new Vec3(3.5, 0.0, 4.5),
            new Vec3(0.5, 1.0, 4.5),
            new Vec3(-2.5, 1.0, 4.5),
            new Vec3(-2.5, 0.0, 1.5));

    /** Ticks spent travelling between consecutive waypoints. */
    private static final int TICKS_PER_LEG = 20;

    /** Pure function of the playhead — see {@link MotionPath} and {@code MotionPathTest}. */
    private static final MotionPath PATH = new MotionPath(WAYPOINTS, TICKS_PER_LEG);
    private static final int LENGTH_TICKS = PATH.lengthTicks();

    private static final int SPIKE_TARGET_LOOPS = 10;

    private final ItemStacksResourceHandler inventory = new ItemStacksResourceHandler(INVENTORY_SLOTS) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            setChanged();
        }
    };

    private int playhead;
    private int loopsCompleted;
    private boolean spikeReported;
    private @Nullable ChronoCloneEntity ghost;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> playhead;
                case 1 -> LENGTH_TICKS;
                case 2 -> loopsCompleted;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 3;
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

    // ------------------------------------------------------------------ spike

    /**
     * Position as a pure function of the playhead. Never {@code pos += delta}.
     *
     * <p>Called with the same int, this returns the same Vec3 forever — which is what makes drift
     * structurally impossible rather than merely unlikely.
     */
    private Vec3 posFor(int tick) {
        Vec3 local = PATH.positionAt(tick);
        return new Vec3(
                worldPosition.getX() + local.x,
                worldPosition.getY() + local.y,
                worldPosition.getZ() + local.z);
    }

    private float yawFor(int tick) {
        return PATH.yawAt(tick);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (ghost == null || ghost.isRemoved()) {
            ghost = ChronoCloneEntity.create(serverLevel);
            Vec3 start = posFor(0);
            ghost.driveTo(start, yawFor(0), 0.0f);
            serverLevel.addFreshEntity(ghost);
            if (!getBlockState().getValue(ChronoAnchorBlock.ACTIVE)) {
                serverLevel.setBlockAndUpdate(worldPosition,
                        getBlockState().setValue(ChronoAnchorBlock.ACTIVE, true));
            }
        }

        playhead++;
        if (playhead >= LENGTH_TICKS) {
            playhead = 0;
            loopsCompleted++;
            reportDrift();
        }

        ghost.driveTo(posFor(playhead), yawFor(playhead), 0.0f);
    }

    /**
     * The actual spike measurement: at every loop boundary, compare where the ghost physically is
     * against where tick 0 says it should be. If these ever diverge, the design is in trouble and
     * we need to know on Day 1.
     */
    private void reportDrift() {
        if (ghost == null) {
            return;
        }
        Vec3 expected = posFor(0);
        Vec3 actual = ghost.position();
        double drift = actual.distanceTo(expected);

        Chronoclones.LOGGER.info("[drift-spike] loop {} complete — drift {} (expected {}, actual {})",
                loopsCompleted, String.format("%.9f", drift), expected, actual);

        if (loopsCompleted >= SPIKE_TARGET_LOOPS && !spikeReported) {
            spikeReported = true;
            if (drift < 1.0e-3) {
                Chronoclones.LOGGER.info("[drift-spike] PASS — {} loops, final drift {} < 1e-3",
                        loopsCompleted, String.format("%.9f", drift));
            } else {
                Chronoclones.LOGGER.error("[drift-spike] FAIL — {} loops, drift {} >= 1e-3",
                        loopsCompleted, String.format("%.9f", drift));
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        discardGhost();
    }

    /** Ghosts never outlive their anchor. */
    private void discardGhost() {
        if (ghost != null) {
            ghost.discard();
            ghost = null;
        }
    }

    // ------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("inventory"));
        // playhead is deliberately NOT saved: no catch-up on chunk load.
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("inventory").ifPresent(inventory::deserialize);
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
