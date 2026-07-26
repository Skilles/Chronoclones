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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Records what a player did inside a container, as the clicks they made.
 *
 * <p>An earlier version diffed the container's contents on open against close and emitted net
 * movements. That could not express the thing players actually do: right-clicking a stack to split
 * it means <em>take half of whatever is there</em>, and "the cursor gained 32" records the arithmetic
 * of one particular chest on one particular day. Buttons are the intent; amounts are a consequence.
 *
 * <p>The clicks arrive from a mixin on {@code AbstractContainerMenu.clicked} — see there for why no
 * event exists and why the click type cannot be inferred from what changed.
 */
public final class ContainerWatch {

    private ContainerWatch() {}

    /**
     * An open container and the clicks made in it so far.
     *
     * @param snapshot every occupied player slot as the menu opened — a working set, narrowed to what
     *                 the session actually touches when it closes
     * @param touched  the player slots the clicks name, collected as they happen because resolving a
     *                 swap's hotbar button needs the live menu
     */
    private record Watch(BlockPos pos, List<ChronoAction.UseContainer.Click> clicks, int menuSize,
                         List<ChronoAction.UseContainer.CarrierSlot> snapshot, Set<Integer> touched) {}

    /** A block right-clicked this tick, held only until we learn whether it opened a menu. */
    private record Pending(BlockPos pos, int actionIndex) {}

    private static final Map<UUID, Watch> OPEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /**
     * Notes a block the player just right-clicked, along with the index of the action recorded for
     * it, so that {@link #onContainerOpened} can retract it if a menu opens.
     *
     * <p>{@code actionIndex} may be -1, or point past the end, when no use action was recorded for
     * the click. Both retract nothing.
     */
    public static void noteInteraction(ServerPlayer player, BlockPos pos, int actionIndex) {
        PENDING.put(player.getUUID(), new Pending(pos, actionIndex));
    }

    /**
     * A container opened: start collecting clicks, and retract the click that opened it.
     *
     * <p>The retraction is why this is driven from the open event rather than guessed at click time.
     * A block having a menu does not mean right-clicking it opens one — bone-mealing a composter is
     * a use, not a container session — so the click can only be safely dropped once a menu has
     * actually appeared.
     */
    public static void onContainerOpened(ServerPlayer player, RecordingSession session) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }

        // An anchor's own slots are machinery, not part of the task. Replay refuses to reach into
        // one anyway, so recording the attempt would only bake in a step that can never run.
        if (player.level().getBlockState(pending.pos()).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return;
        }
        // Only blocks whose menu can be reopened later are worth recording. An inventory or a
        // crafting grid opened from a keybind belongs to the player, not to a place in the world.
        if (player.level().getBlockState(pending.pos()).getMenuProvider(player.level(), pending.pos()) == null) {
            return;
        }

        session.dropActionAt(pending.actionIndex());
        Watch watch = new Watch(pending.pos(), new ArrayList<>(),
                player.containerMenu.slots.size(), snapshot(player), new LinkedHashSet<>());
        OPEN.put(player.getUUID(), watch);
        // Nothing to highlight yet, but sending it anyway is the signal that this container is one
        // the recorder is watching — which is worth knowing before you start clicking in it.
        publish(player, watch);
    }

    /**
     * Tells the client which slots the session has picked up, for the highlight drawn over the menu.
     *
     * <p>Pushed on every click rather than polled, because the interesting moment is the one right
     * after a click: whether that click landed where you meant it to is exactly what you cannot see
     * from the item moving.
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
     *
     * <p>Taken at open rather than at close because the clicks are about to change it, and what
     * matters is the state the clicks were made against. Most of it will be thrown away — see
     * {@link #carried}, which keeps only the squares the session reaches for.
     *
     * <p>Slots are identified by whether they are backed by the player's inventory, which is true of
     * vanilla menus and of any mod menu that builds its player rows the normal way.
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
            layout.add(new ChronoAction.UseContainer.CarrierSlot(
                    index, BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()), stack.getCount()));
        }
        return layout;
    }

    /**
     * One click, from the mixin.
     *
     * <p>No-ops are kept. A click that did nothing still took the player a moment and is part of what
     * they did; filtering by effect would mean diffing, which is exactly what this replaced.
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

        // A slot index of -1 means outside the window, and a click can name a container slot, which
        // the carrier has nothing to say about. Both are filtered out by the snapshot anyway.
        watch.touched().add(slot);
        if (input == ContainerInput.SWAP) {
            // The only click whose target is not the slot it names: the button is a hotbar index, and
            // the item it exchanges with lives there rather than under the pointer.
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
     *
     * <p>Recorded because a click on a player slot names a place whose contents are pure accident —
     * wherever that player keeps things. Replay has to put the anchor's supply in the same squares or
     * every deposit clicks an empty one.
     *
     * <p>Only the squares the clicks reach for, though. A recording is a description of a task, and
     * the forty-odd stacks a player happened to be carrying are not part of one: listing them makes
     * the tooltip unreadable, and — worse — makes the routine demand items it never touches, since
     * replay refuses a session whose carried items the anchor is not stocked with.
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
     *
     * <p>The clear matters on the path where a recording is stopped with the container still open:
     * nothing else will fire until that screen closes, and a highlight left on screen would be
     * claiming a session is still collecting clicks that nothing is collecting.
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
