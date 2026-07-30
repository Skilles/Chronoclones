package com.skilles.chronoclones.menu;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.item.ActionIcons;
import com.skilles.chronoclones.block.RunState;
import com.skilles.chronoclones.network.AnchorAuthority;
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

    /** A mark per recorded action, for the timeline. Empty on the server side. */
    private final List<Mark> actionMarks;

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

    private static final List<Mark> NO_TIMELINE = List.of();

    /**
     * One action as the anchor's timeline needs it: when it happens, and what it is about.
     *
     * <p>The whole action would do, but the screen only ever draws a mark and the recording is the
     * one thing the anchor does not otherwise send to a client that is merely looking at it.
     */
    public record Mark(int tick, java.util.Optional<net.minecraft.core.Holder<net.minecraft.world.item.Item>> icon) {}

    private static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,
            java.util.Optional<net.minecraft.core.Holder<net.minecraft.world.item.Item>>> ICON_STREAM =
            net.minecraft.network.codec.ByteBufCodecs.optional(
                    net.minecraft.network.codec.ByteBufCodecs.holderRegistry(
                            net.minecraft.core.registries.Registries.ITEM));

    public static void writeTimeline(RegistryFriendlyByteBuf buffer, @Nullable Recording recording) {
        List<TimedAction> actions = recording == null ? List.of() : recording.actions();
        buffer.writeVarInt(actions.size());
        for (TimedAction action : actions) {
            buffer.writeVarInt(action.tick());
            ICON_STREAM.encode(buffer, ActionIcons.of(action.action()));
        }
    }

    private static List<Mark> readTimeline(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<Mark> marks = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            marks.add(new Mark(buffer.readVarInt(), ICON_STREAM.decode(buffer)));
        }
        return List.copyOf(marks);
    }

    /** Each action's mark, in order. Only the client is given these. */
    public List<Mark> getActionMarks() {
        return actionMarks;
    }

    public ChronoAnchorMenu(int containerId, Inventory playerInventory, ChronoAnchorBlockEntity anchor,
                            ContainerData data, List<Mark> actionMarks) {
        super(ModMenus.CHRONO_ANCHOR.get(), containerId);
        this.anchor = anchor;
        this.data = data;
        this.actionMarks = actionMarks;

        // Every clone's squares, stacked on the same coordinates. Laid out like a player's own
        // inventory, the storage rows above the hotbar row.
        for (int clone = 0; clone < CLONES; clone++) {
            ItemStacksResourceHandler storage = anchor.getCloneInventory(clone);
            int page = clone;
            for (int index = 0; index < ANCHOR_SLOTS; index++) {
                addSlot(new ClonePageSlot(storage, storage::set, index,
                        Layout.STORAGE_X + Layout.storageColumn(index) * 18,
                        Layout.STORAGE_Y + Layout.storageRow(index) * 18,
                        page, this::getSelectedClone, this::hasStorage));
            }
        }

        // Fuel, then three upgrades, on the row below the storage grid.
        ItemStacksResourceHandler fuel = anchor.getFuelHandler();
        addSlot(new ResourceHandlerSlot(fuel, fuel::set, 0, Layout.FUEL_X, Layout.MODULE_Y));

        ItemStacksResourceHandler upgrades = anchor.getUpgradeHandler();
        for (int i = 0; i < ChronoAnchorBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new ResourceHandlerSlot(upgrades, upgrades::set, i,
                    Layout.UPGRADE_X, Layout.MODULE_Y + (i + 1) * 18));
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

    /** Where this menu's anchor stands, for anything that has to name it over the network. */
    public BlockPos getAnchorPos() {
        return anchor.getBlockPos();
    }

    /** Whether the storage can be reached: a blank anchor has nothing to keep in it. */
    public boolean hasStorage() {
        return getLengthTicks() > 0;
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
        if (buttonId >= RUN_STATE_BUTTON) {
            return setRunState(player, buttonId - RUN_STATE_BUTTON);
        }
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

    /**
     * The first transport button's id. Past the clone tabs, which take the ids below it.
     */
    public static final int RUN_STATE_BUTTON = 16;

    /**
     * Unlike picking a tab, this changes what the anchor does, so it is asked whose anchor it is.
     */
    private boolean setRunState(Player player, int ordinal) {
        if (ordinal < 0 || ordinal >= RunState.values().length
                || !AnchorAuthority.mayRetune(anchor.getOwnerId(), player.getUUID())) {
            return false;
        }
        anchor.setRunState(RunState.byOrdinal(ordinal));
        return true;
    }

    public RunState getRunState() {
        return RunState.byOrdinal(data.get(AnchorData.RUN_STATE));
    }

    /** Where the selected clone's squares start, for a shift-click that must not leave the page. */
    private int selectedPageStart() {
        return getSelectedClone() * ANCHOR_SLOTS;
    }

    /** Menu index of the fuel slot, which follows every clone's storage. */
    public static final int FUEL_SLOT = ANCHOR_SLOTS * CLONES;

    /** Points banked by one clone, for the bar under its storage tab. */
    public int getCloneExperience(int clone) {
        return data.get(AnchorData.experience(clone));
    }

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
        public static final int WIDTH = 230;
        public static final int HEIGHT = 259;

        /** A line of text, and the border a slot box draws outside itself. Both eat into spacing. */
        public static final int LINE_HEIGHT = 9;
        public static final int SLOT_BORDER = 1;

        public static final int MARGIN = 7;
        public static final int CONTENT_WIDTH = WIDTH - 2 * MARGIN;

        /** Panel border plus its inner padding: where a panel's contents may start. */
        public static final int PANEL_INSET = 5;

        /**
         * Section names sit astride their panel's top border, which is a row of height the window
         * does not have to find from anywhere.
         */
        public static final int LEGEND_X = MARGIN + 8;
        public static final int LEGEND_RISE = 4;

        public static final int TITLE_Y = 6;

        /** A gap every band keeps, so a section name never lands on its neighbour's border. */
        public static final int BAND_GAP = 6;

        public static final int TIMELINE_Y = 22;
        public static final int TIMELINE_HEIGHT = 7;

        /**
         * Play, pause and stop, at the right-hand end of the timeline's row.
         *
         * <p>Taller than the track they stand beside, which they can afford: the row has slack above
         * and below that nothing else was using, so the controls cost the window no height at all.
         */
        public static final int TRANSPORT_SIZE = 14;
        public static final int TRANSPORT_GAP = 2;
        public static final int TRANSPORT_COUNT = 3;
        public static final int TRANSPORT_WIDTH =
                TRANSPORT_COUNT * TRANSPORT_SIZE + (TRANSPORT_COUNT - 1) * TRANSPORT_GAP;
        public static final int TRANSPORT_X = WIDTH - MARGIN - TRANSPORT_WIDTH;
        public static final int TRANSPORT_Y = TIMELINE_Y - (TRANSPORT_SIZE - TIMELINE_HEIGHT) / 2 - 1;

        /** What the track has left once the controls have taken their end of the row. */
        public static final int TIMELINE_WIDTH = TRANSPORT_X - MARGIN - 6;

        public static int transportX(int index) {
            return TRANSPORT_X + index * (TRANSPORT_SIZE + TRANSPORT_GAP);
        }

        public static final int PILLS_Y = 37;
        public static final int PILLS_HEIGHT = 18;

        // ---------------------------------------------------------- the storage band

        public static final int BAND_Y = 65;
        public static final int BAND_HEIGHT = 91;

        /**
         * Fuel, the charge column and the modules stand beside the storage grid rather than under
         * it. Four squares stacked are exactly as tall as four rows of nine, so the band costs the
         * window nothing that the grid was not already spending.
         */
        public static final int RAIL_X = MARGIN;
        public static final int RAIL_WIDTH = 40;
        public static final int RAIL_SLOT_X = RAIL_X + PANEL_INSET;

        public static final int MODULE_Y = BAND_Y + PANEL_INSET + 4;
        public static final int FUEL_X = RAIL_SLOT_X;
        public static final int UPGRADE_X = RAIL_SLOT_X;

        /** Upright, so the bar is as tall as the squares it stands next to. */
        public static final int CHARGE_X = RAIL_SLOT_X + 22;
        public static final int CHARGE_Y = MODULE_Y;
        public static final int CHARGE_WIDTH = 7;
        public static final int CHARGE_HEIGHT = 72;

        public static final int STORAGE_PANEL_X = RAIL_X + RAIL_WIDTH + 4;
        public static final int STORAGE_PANEL_WIDTH = WIDTH - MARGIN - STORAGE_PANEL_X;
        public static final int STORAGE_Y = BAND_Y + PANEL_INSET;
        public static final int STORAGE_ROWS = 4;

        /** Under the fourth row: what the clone whose tab is showing has banked. */
        public static final int CLONE_XP_Y = STORAGE_Y + STORAGE_ROWS * 18 + 3;
        public static final int CLONE_XP_HEIGHT = 6;
        public static final int STORAGE_X = STORAGE_PANEL_X + PANEL_INSET;

        /** The clone tabs straddle the storage panel's top border, at the other end from its name. */
        public static final int TAB_Y = BAND_Y - 5;
        public static final int TAB_RIGHT_EDGE = WIDTH - MARGIN - 6;

        // ---------------------------------------------------------- below the band

        public static final int INVENTORY_PANEL_Y = 166;
        public static final int INVENTORY_PANEL_HEIGHT = 87;

        /** Centred in its own panel, which the storage grid cannot be with the rail beside it. */
        public static final int GRID_X = (WIDTH - 9 * 18) / 2;
        public static final int PLAYER_Y = 171;
        public static final int HOTBAR_Y = 230;

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
