package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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

    /** An open container and the clicks made in it so far. */
    private record Watch(BlockPos pos, List<ChronoAction.UseContainer.Click> clicks, int menuSize,
                         List<ChronoAction.UseContainer.CarrierSlot> carrier) {}

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
        OPEN.put(player.getUUID(), new Watch(pending.pos(), new ArrayList<>(),
                player.containerMenu.slots.size(), carrierLayout(player)));
    }

    /**
     * The player's own half of the menu, as they opened it.
     *
     * <p>Recorded because a click on a player slot names a place whose contents are pure accident —
     * wherever that player keeps things. Replay has to put the anchor's supply in the same squares or
     * every deposit clicks an empty one.
     *
     * <p>Slots are identified by whether they are backed by the player's inventory, which is true of
     * vanilla menus and of any mod menu that builds its player rows the normal way.
     */
    private static List<ChronoAction.UseContainer.CarrierSlot> carrierLayout(ServerPlayer player) {
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
        if (watch != null) {
            watch.clicks().add(new ChronoAction.UseContainer.Click(slot, button, input));
        }
    }

    /** The session, or null if nothing was clicked. */
    public static ChronoAction.@Nullable UseContainer onContainerClosed(ServerPlayer player,
                                                                     RecordingSession session) {
        PENDING.remove(player.getUUID());
        Watch watch = OPEN.remove(player.getUUID());
        if (watch == null || watch.clicks().isEmpty()) {
            return null;
        }
        return new ChronoAction.UseContainer(
                session.toLocal(watch.pos()), watch.menuSize(), watch.carrier(), watch.clicks());
    }

    /** The world position of the container currently open for this player, if any. */
    public static @Nullable BlockPos openPosition(ServerPlayer player) {
        Watch watch = OPEN.get(player.getUUID());
        return watch == null ? null : watch.pos();
    }

    public static void forget(ServerPlayer player) {
        OPEN.remove(player.getUUID());
        PENDING.remove(player.getUUID());
    }

    public static void clear() {
        OPEN.clear();
        PENDING.clear();
    }
}
