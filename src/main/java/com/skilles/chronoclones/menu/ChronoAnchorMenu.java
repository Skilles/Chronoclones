package com.skilles.chronoclones.menu;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.item.ActionIcons;
import com.skilles.chronoclones.block.RunState;
import com.skilles.chronoclones.network.AnchorAuthority;
import com.skilles.chronoclones.block.UpgradeState;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.registry.ModBlocks;
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
import com.skilles.chronoclones.inventory.StackInventory;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class ChronoAnchorMenu extends AbstractContainerMenu {

    private static final int ANCHOR_SLOTS = ChronoAnchorBlockEntity.CLONE_INVENTORY_SLOTS;
    private static final int CLONES = ChronoAnchorBlockEntity.CLONE_INVENTORIES;

    public static final int DATA_COUNT = AnchorData.COUNT;

    private static final int TOTAL_ANCHOR_SLOTS =
            ANCHOR_SLOTS * CLONES + 1 + ChronoAnchorBlockEntity.UPGRADE_SLOTS;

    private final ChronoAnchorBlockEntity anchor;
    private final ContainerData data;

    private int selectedClone;

    private final List<Mark> actionMarks;

    public ChronoAnchorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, resolve(playerInventory, extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT), readTimeline(extraData));
    }

    public ChronoAnchorMenu(int containerId, Inventory playerInventory, ChronoAnchorBlockEntity anchor, ContainerData data) {
        this(containerId, playerInventory, anchor, data, NO_TIMELINE);
    }

    private static final List<Mark> NO_TIMELINE = List.of();

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

    public List<Mark> getActionMarks() {
        return actionMarks;
    }

    public ChronoAnchorMenu(int containerId, Inventory playerInventory, ChronoAnchorBlockEntity anchor,
                            ContainerData data, List<Mark> actionMarks) {
        super(ModMenus.CHRONO_ANCHOR.get(), containerId);
        this.anchor = anchor;
        this.data = data;
        this.actionMarks = actionMarks;

        for (int clone = 0; clone < CLONES; clone++) {
            StackInventory storage = anchor.getCloneInventory(clone);
            int page = clone;
            for (int index = 0; index < ANCHOR_SLOTS; index++) {
                addSlot(new ClonePageSlot(storage, index,
                        Layout.STORAGE_X + Layout.storageColumn(index) * 18,
                        Layout.STORAGE_Y + Layout.storageRow(index) * 18,
                        page, this::getSelectedClone, this::hasStorage));
            }
        }

        StackInventory fuel = anchor.getFuelHandler();
        addSlot(new Slot(fuel, 0, Layout.FUEL_X, Layout.MODULE_Y) {
            @Override
            public boolean mayPlace(@NonNull ItemStack stack) {
                return isAnchorFuel(playerInventory.player.level(), stack) && super.mayPlace(stack);
            }
        });

        StackInventory upgrades = anchor.getUpgradeHandler();
        for (int i = 0; i < ChronoAnchorBlockEntity.UPGRADE_SLOTS; i++) {
            addSlot(new Slot(upgrades, i,
                    Layout.UPGRADE_X, Layout.MODULE_Y + (i + 1) * 18) {
                @Override
                public boolean mayPlace(@NonNull ItemStack stack) {
                    return UpgradeState.isUpgrade(stack.getItem()) && super.mayPlace(stack);
                }
            });
        }

        addPlayerInventory(playerInventory);
        addDataSlots(this.data);
    }

    private static ChronoAnchorBlockEntity resolve(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof ChronoAnchorBlockEntity be) {
            return be;
        }
        // The open packet can outrun the chunk on a dedicated server. A detached stand-in gives
        // the slots somewhere to live; the server's copies fill them over normal slot sync.
        return new ChronoAnchorBlockEntity(pos,
                ModBlocks.CHRONO_ANCHOR.get().defaultBlockState());
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

    public BlockPos getAnchorPos() {
        return anchor.getBlockPos();
    }

    public boolean hasStorage() {
        return getLengthTicks() > 0;
    }

    /** What consumeFuel will actually take: burnables, or the creative cell. */
    public static boolean isAnchorFuel(net.minecraft.world.level.Level level, ItemStack stack) {
        return stack.is(com.skilles.chronoclones.registry.ModItems.CREATIVE_CHARGE_CELL.get())
                || stack.getBurnTime(null, level.fuelValues()) > 0;
    }

    /** Clamped on read, so a clone going away takes its page with it. */
    public int getSelectedClone() {
        return Math.min(selectedClone, Math.max(0, getActiveClones() - 1));
    }

    @Override
    public boolean clickMenuButton(@NonNull Player player, int buttonId) {
        if (buttonId == REDSTONE_BUTTON) {
            return toggleRedstone(player);
        }
        if (buttonId >= RUN_STATE_BUTTON) {
            return setRunState(player, buttonId - RUN_STATE_BUTTON);
        }
        // The synced count, not the anchor's: UpgradeState is only recomputed on the server
        // tick, so a client asking the block entity is always told there is one clone.
        if (buttonId < 0 || buttonId >= Math.min(CLONES, getActiveClones())) {
            return false;
        }
        selectedClone = buttonId;
        return true;
    }

    public static final int RUN_STATE_BUTTON = 16;

    public static final int REDSTONE_BUTTON = RUN_STATE_BUTTON + 3;

    private boolean toggleRedstone(Player player) {
        if (com.skilles.chronoclones.platform.ClonePlayer.isFake(player)
                || !AnchorAuthority.mayRetune(anchor.getOwnerId(), player.getUUID())) {
            return false;
        }
        anchor.setObeysRedstone(!anchor.obeysRedstone());
        return true;
    }

    public boolean isObeyingRedstone() {
        return data.get(AnchorData.REDSTONE_MODE) != 0;
    }

    private boolean setRunState(Player player, int ordinal) {
        // A clone acts under its owner's name, so the ownership check below would pass it.
        if (com.skilles.chronoclones.platform.ClonePlayer.isFake(player)) {
            return false;
        }
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

    private int selectedPageStart() {
        return getSelectedClone() * ANCHOR_SLOTS;
    }

    public static final int FUEL_SLOT = ANCHOR_SLOTS * CLONES;

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

    public BlockPos getFailurePos() {
        return new BlockPos(data.get(AnchorData.FAILURE_X), data.get(AnchorData.FAILURE_Y),
                data.get(AnchorData.FAILURE_Z));
    }

    public int getReportOk() {
        return data.get(AnchorData.REPORT_OK);
    }

    public int getReportSkipped() {
        return data.get(AnchorData.REPORT_SKIPPED);
    }

    public static final class Layout {

        public static final int WIDTH = 230;
        public static final int HEIGHT = 259;

        public static final int LINE_HEIGHT = 9;
        public static final int SLOT_BORDER = 1;

        public static final int MARGIN = 7;
        public static final int CONTENT_WIDTH = WIDTH - 2 * MARGIN;

        public static final int PANEL_INSET = 5;

        public static final int LEGEND_X = MARGIN + 8;
        public static final int LEGEND_RISE = 4;

        public static final int TITLE_Y = 6;

        public static final int BAND_GAP = 6;

        public static final int TIMELINE_Y = 22;
        public static final int TIMELINE_HEIGHT = 7;

        public static final int TRANSPORT_SIZE = 14;
        public static final int TRANSPORT_GAP = 2;
        public static final int TRANSPORT_COUNT = 3;
        public static final int TRANSPORT_WIDTH =
                TRANSPORT_COUNT * TRANSPORT_SIZE + (TRANSPORT_COUNT - 1) * TRANSPORT_GAP;
        public static final int TRANSPORT_X = WIDTH - MARGIN - TRANSPORT_WIDTH;
        public static final int TRANSPORT_Y = TIMELINE_Y - (TRANSPORT_SIZE - TIMELINE_HEIGHT) / 2 - 1;

        public static final int REDSTONE_X = TRANSPORT_X - TRANSPORT_SIZE - 6;

        public static final int TIMELINE_WIDTH = REDSTONE_X - MARGIN - 6;

        public static int transportX(int index) {
            return TRANSPORT_X + index * (TRANSPORT_SIZE + TRANSPORT_GAP);
        }

        public static final int PILLS_Y = 37;
        public static final int PILLS_HEIGHT = 18;

        public static final int BAND_Y = 65;
        public static final int BAND_HEIGHT = 91;

        public static final int RAIL_X = MARGIN;
        public static final int RAIL_WIDTH = 40;
        public static final int RAIL_SLOT_X = RAIL_X + PANEL_INSET;

        public static final int MODULE_Y = BAND_Y + PANEL_INSET + 4;
        public static final int FUEL_X = RAIL_SLOT_X;
        public static final int UPGRADE_X = RAIL_SLOT_X;

        public static final int CHARGE_X = RAIL_SLOT_X + 22;
        public static final int CHARGE_Y = MODULE_Y;
        public static final int CHARGE_WIDTH = 7;
        public static final int CHARGE_HEIGHT = 72;

        public static final int STORAGE_PANEL_X = RAIL_X + RAIL_WIDTH + 4;
        public static final int STORAGE_PANEL_WIDTH = WIDTH - MARGIN - STORAGE_PANEL_X;
        public static final int STORAGE_Y = BAND_Y + PANEL_INSET;
        public static final int STORAGE_ROWS = 4;

        public static final int CLONE_XP_Y = STORAGE_Y + STORAGE_ROWS * 18 + 3;
        public static final int CLONE_XP_HEIGHT = 6;
        public static final int STORAGE_X = STORAGE_PANEL_X + PANEL_INSET;

        public static final int TAB_Y = BAND_Y - 5;
        public static final int TAB_RIGHT_EDGE = WIDTH - MARGIN - 6;

        public static final int INVENTORY_PANEL_Y = 166;
        public static final int INVENTORY_PANEL_HEIGHT = 87;

        public static final int GRID_X = (WIDTH - 9 * 18) / 2;
        public static final int PLAYER_Y = 171;
        public static final int HOTBAR_Y = 230;

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
            if (!moveItemStackTo(stack, TOTAL_ANCHOR_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int page = selectedPageStart();
            int pageEnd = page + ANCHOR_SLOTS;
            int fuel = ANCHOR_SLOTS * CLONES;

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
