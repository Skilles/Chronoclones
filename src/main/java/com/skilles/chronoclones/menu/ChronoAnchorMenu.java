package com.skilles.chronoclones.menu;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.UpgradeState;
import com.skilles.chronoclones.registry.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

public class ChronoAnchorMenu extends AbstractContainerMenu {

    private static final int ANCHOR_SLOTS = ChronoAnchorBlockEntity.INVENTORY_SLOTS;

    /** Keep in sync with the block entity's ContainerData. */
    public static final int DATA_COUNT = 14;

    /** 18 storage + 1 fuel + 3 upgrade. */
    private static final int TOTAL_ANCHOR_SLOTS =
            ChronoAnchorBlockEntity.INVENTORY_SLOTS + 1 + ChronoAnchorBlockEntity.UPGRADE_SLOTS;

    private final ChronoAnchorBlockEntity anchor;
    private final ContainerData data;

    /**
     * Client-side constructor, reached via the extra data written by {@code openMenu(provider, pos)}.
     *
     * <p>The ContainerData here MUST be a fresh {@link SimpleContainerData}, not the block entity's
     * own instance. The client-side block entity is never ticked (the block's ticker returns null on
     * the client) so its fields sit at their defaults forever; reading them would show a frozen
     * readout no matter what the server is doing. {@link #addDataSlots} is what actually carries the
     * values across, and it needs a client-side buffer to write into.
     */
    public ChronoAnchorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, resolve(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public ChronoAnchorMenu(int containerId, Inventory playerInventory, ChronoAnchorBlockEntity anchor, ContainerData data) {
        super(ModMenus.CHRONO_ANCHOR.get(), containerId);
        this.anchor = anchor;
        this.data = data;

        ItemStacksResourceHandler storage = anchor.getInventoryHandler();
        // 18 storage slots, 9 across x 2
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                addSlot(new ResourceHandlerSlot(storage, storage::set, index, 8 + col * 18, Layout.STORAGE_Y + row * 18));
            }
        }

        // Fuel, then three upgrades, on the row below the storage grid.
        ItemStacksResourceHandler fuel = anchor.getFuelHandler();
        addSlot(new ResourceHandlerSlot(fuel, fuel::set, 0, Layout.FUEL_X, Layout.MODULE_Y));

        ItemStacksResourceHandler upgrades = anchor.getUpgradeHandler();
        for (int i = 0; i < ChronoAnchorBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new ResourceHandlerSlot(upgrades, upgrades::set, i, Layout.UPGRADE_X + i * 18, Layout.MODULE_Y));
        }

        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    private static ChronoAnchorBlockEntity resolve(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof ChronoAnchorBlockEntity be) {
            return be;
        }
        throw new IllegalStateException("No clone anchor at " + pos);
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, Layout.PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, Layout.HOTBAR_Y));
        }
    }

    public int getPlayhead() {
        return data.get(0);
    }

    public int getLengthTicks() {
        return data.get(1);
    }

    public int getActionCount() {
        return data.get(2);
    }

    public int getFailureOrdinal() {
        return data.get(3);
    }

    public boolean isAnchorEnabled() {
        return data.get(4) != 0;
    }

    public int getActiveClones() {
        return data.get(5);
    }

    public int getCharge() {
        return data.get(6);
    }

    public int getChargeCapacity() {
        return Math.max(1, data.get(7));
    }

    public int getCloneCount() {
        return data.get(8);
    }

    public int getTicksPerStep() {
        return data.get(9);
    }

    public int getFidelityTier() {
        return data.get(10);
    }

    public int getCoherenceTier() {
        return data.get(14);
    }

    /** Anchor-local position of the last failure, for the diagnostic line. */
    public net.minecraft.core.BlockPos getFailurePos() {
        return new net.minecraft.core.BlockPos(data.get(11), data.get(12), data.get(13));
    }

    /**
     * Slot geometry, shared by the menu and the screen.
     *
     * <p>They have to agree exactly: the menu decides where clicks land and the screen decides
     * where the boxes are drawn, so a mismatch is invisible until someone clicks empty air.
     */
    public static final class Layout {
        public static final int WIDTH = 176;
        public static final int HEIGHT = 208;

        public static final int STATUS_Y = 18;
        public static final int UPGRADE_INFO_Y = 28;

        public static final int STORAGE_Y = 40;
        public static final int MODULE_Y = 82;
        public static final int FUEL_X = 8;
        public static final int UPGRADE_X = 116;

        public static final int CHARGE_X = 30;
        public static final int CHARGE_Y = 86;
        public static final int CHARGE_WIDTH = 78;
        public static final int CHARGE_HEIGHT = 8;

        public static final int DIAGNOSTIC_Y = 104;
        public static final int PLAYER_LABEL_Y = 114;
        public static final int PLAYER_Y = 126;
        public static final int HOTBAR_Y = 184;

        private Layout() {}
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < TOTAL_ANCHOR_SLOTS) {
            // Out of the anchor and into the player.
            if (!moveItemStackTo(stack, TOTAL_ANCHOR_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Into the anchor. Route upgrades and fuel to their own slots first, so shift-clicking
            // a splitter installs it rather than burying it in storage.
            boolean moved;
            if (UpgradeState.isUpgrade(stack.getItem())) {
                moved = moveItemStackTo(stack, ANCHOR_SLOTS + 1, TOTAL_ANCHOR_SLOTS, false);
            } else if (player.level().fuelValues().burnDuration(stack) > 0) {
                moved = moveItemStackTo(stack, ANCHOR_SLOTS, ANCHOR_SLOTS + 1, false)
                        || moveItemStackTo(stack, 0, ANCHOR_SLOTS, false);
            } else {
                moved = moveItemStackTo(stack, 0, ANCHOR_SLOTS, false);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return !anchor.isRemoved()
                && player.level().getBlockEntity(anchor.getBlockPos()) == anchor
                && player.distanceToSqr(
                        anchor.getBlockPos().getX() + 0.5,
                        anchor.getBlockPos().getY() + 0.5,
                        anchor.getBlockPos().getZ() + 0.5) < 64.0;
    }
}
