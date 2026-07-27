package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.skilles.chronoclones.network.RecordingHighlightPayload;
import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Records what a player did inside a container, as the clicks they made.
 */
public final class ContainerWatch {

    private ContainerWatch() {}

    /**
     * An open container and the clicks made in it so far.
     *
     * @param snapshot every occupied player slot as the menu opened, narrowed on close
     * @param touched  the player slots the clicks name, collected live because a swap's
     *                 hotbar button needs the open menu to resolve
     */
    private record Watch(BlockPos pos, List<ChronoAction.UseContainer.Click> clicks, int menuSize,
                         List<ChronoAction.UseContainer.CarrierSlot> snapshot, Set<Integer> touched) {}

    /** A block right-clicked this tick, held only until we learn whether it opened a menu. */
    private record Pending(BlockPos pos, int actionIndex) {}

    private static final Map<UUID, Watch> OPEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /** The action index lets {@link #onContainerOpened} retract the click if a menu opens. */
    public static void noteInteraction(ServerPlayer player, BlockPos pos, int actionIndex) {
        PENDING.put(player.getUUID(), new Pending(pos, actionIndex));
    }

    /**
     * A container opened: start collecting clicks, and retract the click that opened it.
     */
    public static void onContainerOpened(ServerPlayer player, RecordingSession session) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }

        // An anchor's own slots are machinery; replay refuses to reach into one anyway.
        if (player.level().getBlockState(pending.pos()).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return;
        }
        // Only blocks whose menu can be reopened later; an inventory belongs to the player.
        if (player.level().getBlockState(pending.pos()).getMenuProvider(player.level(), pending.pos()) == null) {
            return;
        }

        session.dropActionAt(pending.actionIndex());
        Watch watch = new Watch(pending.pos(), new ArrayList<>(),
                player.containerMenu.slots.size(), snapshot(player), new LinkedHashSet<>());
        OPEN.put(player.getUUID(), watch);
        // Nothing to highlight yet, but this signals that the container is being watched.
        publish(player, watch);
    }

    /**
     * Tells the client which slots the session has picked up, for the highlight drawn over the menu.
     */
    private static void publish(ServerPlayer player, Watch watch) {
        List<Integer> carried = new ArrayList<>();
        for (ChronoAction.UseContainer.CarrierSlot slot : carried(watch.snapshot(), watch.touched())) {
            carried.add(slot.menuSlot());
        }
        send(player, new RecordingHighlightPayload(
                player.containerMenu.containerId, List.copyOf(watch.touched()), carried));
    }

    /**
     * Every occupied player slot as the menu opened.
     */
    private static List<ChronoAction.UseContainer.CarrierSlot> snapshot(ServerPlayer player) {
        List<ChronoAction.UseContainer.CarrierSlot> layout = new ArrayList<>();
        AbstractContainerMenu menu = player.containerMenu;

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.container != player.getInventory()) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            layout.add(new ChronoAction.UseContainer.CarrierSlot(index, stack));
        }
        return layout;
    }

    /**
     * One click, from the mixin.
     */
    public static void onClick(ServerPlayer player, int slot, int button, ContainerInput input) {
        if (RecordingSessions.get(player) == null) {
            return;
        }
        Watch watch = OPEN.get(player.getUUID());
        if (watch == null) {
            return;
        }
        watch.clicks().add(new ChronoAction.UseContainer.Click(slot, button, input));

        // -1 is outside the window and container slots are not the carrier's business; the
        // snapshot filters both.
        watch.touched().add(slot);
        if (input == ContainerInput.SWAP) {
            // The one click whose target is not the slot it names: the button is a hotbar index.
            int swapped = menuSlotOf(player, button);
            if (swapped >= 0) {
                watch.touched().add(swapped);
            }
        }
        publish(player, watch);
    }

    /** Where a player-inventory index sits in the open menu, or -1 if this menu does not show it. */
    private static int menuSlotOf(ServerPlayer player, int inventorySlot) {
        AbstractContainerMenu menu = player.containerMenu;
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) {
                return index;
            }
        }
        return -1;
    }

    /**
     * The player's own items that this session actually depends on.
     */
    static List<ChronoAction.UseContainer.CarrierSlot> carried(
            List<ChronoAction.UseContainer.CarrierSlot> snapshot, Set<Integer> touched) {
        List<ChronoAction.UseContainer.CarrierSlot> carried = new ArrayList<>();
        for (ChronoAction.UseContainer.CarrierSlot slot : snapshot) {
            if (touched.contains(slot.menuSlot())) {
                carried.add(slot);
            }
        }
        return carried;
    }

    /** The session, or null if nothing was clicked. */
    public static ChronoAction.@Nullable UseContainer onContainerClosed(ServerPlayer player,
                                                                     RecordingSession session) {
        Watch watch = OPEN.get(player.getUUID());
        forget(player);
        if (watch == null || watch.clicks().isEmpty()) {
            return null;
        }
        return new ChronoAction.UseContainer(session.toLocal(watch.pos()), watch.menuSize(),
                carried(watch.snapshot(), watch.touched()), watch.clicks());
    }

    /** The world position of the container currently open for this player, if any. */
    public static @Nullable BlockPos openPosition(ServerPlayer player) {
        Watch watch = OPEN.get(player.getUUID());
        return watch == null ? null : watch.pos();
    }

    /**
     * Stops watching this player, and takes the highlight down with it.
     */
    public static void forget(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        send(player, new RecordingHighlightPayload(NO_CONTAINER, List.of(), List.of()));
    }

    /** Forgetting a watch also happens on the way out of a dimension, and on respawn. */
    private static void send(ServerPlayer player, RecordingHighlightPayload payload) {
        if (player.connection != null && !player.hasDisconnected()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** A container id no menu has, which the client reads as "draw nothing". */
    private static final int NO_CONTAINER = -1;

    public static void clear() {
        OPEN.clear();
        PENDING.clear();
    }
}
