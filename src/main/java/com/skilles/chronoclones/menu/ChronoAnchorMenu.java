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
import org.jspecify.annotations.NonNull;

public class ChronoAnchorMenu extends AbstractContainerMenu {

    private static final int ANCHOR_SLOTS = ChronoAnchorBlockEntity.CLONE_INVENTORY_SLOTS;

    /** Defined alongside the slot names, so the buffer and the readers cannot drift apart. */
    public static final int DATA_COUNT = AnchorData.COUNT;

    /** One clone's storage, plus fuel and the three modules. */
    private static final int TOTAL_ANCHOR_SLOTS = ANCHOR_SLOTS + 1 + ChronoAnchorBlockEntity.UPGRADE_SLOTS;

    private final ChronoAnchorBlockEntity anchor;
    private final ContainerData data;

    /**
     * Client-side constructor, reached via the extra data written by {@code openMenu(provider, pos)}.
     */
    public ChronoAnchorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, resolve(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public ChronoAnchorMenu(int containerId, Inventory playerInventory, ChronoAnchorBlockEntity anchor, ContainerData data) {
        super(ModMenus.CHRONO_ANCHOR.get(), containerId);
        this.anchor = anchor;
        this.data = data;

        // Laid out like a player's own inventory, the storage rows above the hotbar row.
        ItemStacksResourceHandler storage = anchor.getCloneInventory(0);
        for (int index = 0; index < ANCHOR_SLOTS; index++) {
            addSlot(new ResourceHandlerSlot(storage, storage::set, index,
                    8 + Layout.storageColumn(index) * 18,
                    Layout.STORAGE_Y + Layout.storageRow(index) * 18));
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
        return data.get(AnchorData.PLAYHEAD);
    }

    public int getLengthTicks() {
        return data.get(AnchorData.LENGTH_TICKS);
    }

    public int getActionCount() {
        return data.get(AnchorData.ACTION_COUNT);
    }

    public int getFailureOrdinal() {
        return data.get(AnchorData.FAILURE_REASON);
    }

    public int getActiveClones() {
        return data.get(AnchorData.ACTIVE_CLONES);
    }

    public int getCharge() {
        return data.get(AnchorData.CHARGE);
    }

    public int getChargeCapacity() {
        return Math.max(1, data.get(AnchorData.CHARGE_CAPACITY));
    }

    public int getTicksPerStep() {
        return data.get(AnchorData.TICKS_PER_STEP);
    }

    /** Anchor-local position of the last failure, for the diagnostic line. */
    public BlockPos getFailurePos() {
        return new BlockPos(data.get(AnchorData.FAILURE_X), data.get(AnchorData.FAILURE_Y),
                data.get(AnchorData.FAILURE_Z));
    }

    /**
     * Slot geometry, shared by the menu and the screen.
     */
    public static final class Layout {
        public static final int WIDTH = 176;
        public static final int HEIGHT = 255;

        /**
         * The readout lines, each on its own row.
         */
        public static final int STATUS_Y = 18;
        public static final int UPGRADE_INFO_Y = 28;

        public static final int STORAGE_Y = 40;

        /** The hotbar last, so a clone's storage reads exactly like the player inventory below it. */
        public static int storageRow(int inventorySlot) {
            return Inventory.isHotbarSlot(inventorySlot) ? 3 : (inventorySlot - 9) / 9;
        }

        public static int storageColumn(int inventorySlot) {
            return inventorySlot % 9;
        }

        /** "Fuel", "Charge" and "Modules", above the row they describe. */
        public static final int SECTION_LABEL_Y = 114;

        /** A line of text, and the border a slot box draws outside itself. Both eat into spacing. */
        public static final int LINE_HEIGHT = 9;
        public static final int SLOT_BORDER = 1;

        public static final int MODULE_Y = 126;
        public static final int FUEL_X = 8;
        public static final int UPGRADE_X = 116;

        public static final int CHARGE_X = 30;
        public static final int CHARGE_Y = 130;
        public static final int CHARGE_WIDTH = 78;
        public static final int CHARGE_HEIGHT = 8;

        public static final int DIAGNOSTIC_Y = 148;
        public static final int PLAYER_LABEL_Y = 160;
        public static final int PLAYER_Y = 172;
        public static final int HOTBAR_Y = 230;

        private Layout() {}
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
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
            // Upgrades and fuel route to their own slots first.
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
    public boolean stillValid(@NonNull Player player) {
        return !anchor.isRemoved()
                && player.level().getBlockEntity(anchor.getBlockPos()) == anchor
                && player.distanceToSqr(
                        anchor.getBlockPos().getX() + 0.5,
                        anchor.getBlockPos().getY() + 0.5,
                        anchor.getBlockPos().getZ() + 0.5) < 64.0;
    }
}
