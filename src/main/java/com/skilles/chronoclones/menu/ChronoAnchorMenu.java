package com.skilles.chronoclones.menu;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.UpgradeState;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;
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
import org.jspecify.annotations.Nullable;

public class ChronoAnchorMenu extends AbstractContainerMenu {

    private static final int ANCHOR_SLOTS = ChronoAnchorBlockEntity.CLONE_INVENTORY_SLOTS;
    private static final int CLONES = ChronoAnchorBlockEntity.CLONE_INVENTORIES;

    /** Defined alongside the slot names, so the buffer and the readers cannot drift apart. */
    public static final int DATA_COUNT = AnchorData.COUNT;

    /** Every clone's storage, plus fuel and the three modules. */
    private static final int TOTAL_ANCHOR_SLOTS =
            ANCHOR_SLOTS * CLONES + 1 + ChronoAnchorBlockEntity.UPGRADE_SLOTS;

    private final ChronoAnchorBlockEntity anchor;
    private final ContainerData data;

    /** Which clone's squares are the visible ones. Each side sets its own on the same click. */
    private int selectedClone;

    /** The tick each recorded action falls on, for the timeline. Empty on the server side. */
    private final int[] actionTicks;

    /**
     * Client-side constructor, reached via the extra data written when the menu is opened.
     */
    public ChronoAnchorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, resolve(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT), readTimeline(extraData));
    }

    public ChronoAnchorMenu(int containerId, Inventory playerInventory, ChronoAnchorBlockEntity anchor, ContainerData data) {
        this(containerId, playerInventory, anchor, data, NO_TIMELINE);
    }

    private static final int[] NO_TIMELINE = new int[0];

    public static void writeTimeline(RegistryFriendlyByteBuf buffer, @Nullable Recording recording) {
        List<TimedAction> actions = recording == null ? List.of() : recording.actions();
        buffer.writeVarInt(actions.size());
        for (TimedAction action : actions) {
            buffer.writeVarInt(action.tick());
        }
    }

    private static int[] readTimeline(RegistryFriendlyByteBuf buffer) {
        int[] ticks = new int[buffer.readVarInt()];
        for (int i = 0; i < ticks.length; i++) {
            ticks[i] = buffer.readVarInt();
        }
        return ticks;
    }

    /** The tick each action falls on, in order. Only the client is given these. */
    public int[] getActionTicks() {
        return actionTicks;
    }

    public ChronoAnchorMenu(int containerId, Inventory playerInventory, ChronoAnchorBlockEntity anchor,
                            ContainerData data, int[] actionTicks) {
        super(ModMenus.CHRONO_ANCHOR.get(), containerId);
        this.anchor = anchor;
        this.data = data;
        this.actionTicks = actionTicks;

        // Every clone's squares, stacked on the same coordinates. Laid out like a player's own
        // inventory, the storage rows above the hotbar row.
        for (int clone = 0; clone < CLONES; clone++) {
            ItemStacksResourceHandler storage = anchor.getCloneInventory(clone);
            int page = clone;
            for (int index = 0; index < ANCHOR_SLOTS; index++) {
                addSlot(new ClonePageSlot(storage, storage::set, index,
                        Layout.GRID_X + Layout.storageColumn(index) * 18,
                        Layout.STORAGE_Y + Layout.storageRow(index) * 18,
                        page, this::getSelectedClone));
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
                addSlot(new Slot(playerInventory, col + row * 9 + 9, Layout.GRID_X + col * 18, Layout.PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, Layout.GRID_X + col * 18, Layout.HOTBAR_Y));
        }
    }

    /** Clamped on read, so a clone going away takes its page with it. */
    public int getSelectedClone() {
        return Math.min(selectedClone, Math.max(0, getActiveClones() - 1));
    }

    /**
     * The tab strip, arriving as a menu button so no payload of our own is needed.
     */
    @Override
    public boolean clickMenuButton(@NonNull Player player, int buttonId) {
        // Against the synced count, not the anchor's own: UpgradeState is only ever recomputed on
        // the server tick, so a client asking the block entity is always told there is one clone.
        // A page with no clone behind it is never drawn again, so anything moved into it would be
        // stranded there.
        if (buttonId < 0 || buttonId >= Math.min(CLONES, getActiveClones())) {
            return false;
        }
        selectedClone = buttonId;
        return true;
    }

    /** Where the selected clone's squares start, for a shift-click that must not leave the page. */
    private int selectedPageStart() {
        return getSelectedClone() * ANCHOR_SLOTS;
    }

    /** Menu index of the fuel slot, which follows every clone's storage. */
    public static final int FUEL_SLOT = ANCHOR_SLOTS * CLONES;

    public int getPlayhead(int clone) {
        return data.get(AnchorData.playhead(clone));
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
     * Slot geometry, shared by the menu and the screen. Every y is the top of its band.
     */
    public static final class Layout {
        public static final int WIDTH = 184;
        public static final int HEIGHT = 261;

        /** A line of text, and the border a slot box draws outside itself. Both eat into spacing. */
        public static final int LINE_HEIGHT = 9;
        public static final int SLOT_BORDER = 1;

        /** Panels and the grids inside them run between these. */
        public static final int MARGIN = 7;
        public static final int CONTENT_WIDTH = WIDTH - 2 * MARGIN;

        /** Derived, so the nine columns stay centred whatever the window's width becomes. */
        public static final int GRID_X = (WIDTH - 9 * 18) / 2;

        public static final int TITLE_Y = 3;

        public static final int TIMELINE_Y = 15;
        public static final int TIMELINE_HEIGHT = 5;

        public static final int PILLS_Y = 23;
        public static final int PILLS_HEIGHT = 14;

        public static final int STORAGE_PANEL_Y = 40;
        public static final int STORAGE_PANEL_HEIGHT = 86;
        public static final int STORAGE_HEADER_Y = 43;
        public static final int STORAGE_Y = 53;
        public static final int STORAGE_ROWS = 4;

        /** The clone tabs share the storage header row, right-aligned. */
        public static final int TAB_Y = STORAGE_HEADER_Y - 1;
        public static final int TAB_RIGHT_EDGE = WIDTH - MARGIN - 4;

        public static final int MODULE_PANEL_Y = 129;
        public static final int MODULE_PANEL_HEIGHT = 34;
        public static final int CHARGE_PANEL_WIDTH = 97;
        public static final int MODULE_PANEL_X = MARGIN + CHARGE_PANEL_WIDTH + 4;

        /** "Charge" and "Modules", above the row they describe. */
        public static final int SECTION_LABEL_Y = 132;

        public static final int MODULE_Y = 142;
        public static final int FUEL_X = 12;
        public static final int UPGRADE_X = 115;

        public static final int CHARGE_X = 36;
        public static final int CHARGE_Y = 143;
        public static final int CHARGE_WIDTH = 60;
        public static final int CHARGE_HEIGHT = 6;
        public static final int CHARGE_TEXT_Y = 151;

        public static final int DIAGNOSTIC_Y = 166;
        public static final int DIAGNOSTIC_HEIGHT = 11;

        public static final int PLAYER_Y = 181;
        public static final int HOTBAR_Y = 238;

        /** The hotbar last, so a clone's storage reads exactly like the player inventory below it. */
        public static int storageRow(int inventorySlot) {
            return Inventory.isHotbarSlot(inventorySlot) ? 3 : (inventorySlot - 9) / 9;
        }

        public static int storageColumn(int inventorySlot) {
            return inventorySlot % 9;
        }

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
            // Only the visible clone's squares: moveItemStackTo does not check isActive, so
            // without this a shift-click scatters across pages nobody can see.
            int page = selectedPageStart();
            int pageEnd = page + ANCHOR_SLOTS;
            int fuel = ANCHOR_SLOTS * CLONES;

            // Upgrades and fuel route to their own slots first.
            boolean moved;
            if (UpgradeState.isUpgrade(stack.getItem())) {
                moved = moveItemStackTo(stack, fuel + 1, TOTAL_ANCHOR_SLOTS, false);
            } else if (player.level().fuelValues().burnDuration(stack) > 0) {
                moved = moveItemStackTo(stack, fuel, fuel + 1, false)
                        || moveItemStackTo(stack, page, pageEnd, false);
            } else {
                moved = moveItemStackTo(stack, page, pageEnd, false);
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
