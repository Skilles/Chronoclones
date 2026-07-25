package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Turns "the player rummaged in a chest" into a net movement of items.
 *
 * <p>Recording slot clicks was never an option. Clicks are raw input — the thing the whole action
 * model exists to avoid — and replaying them means driving a real {@code AbstractContainerMenu},
 * whose behaviour every mod may override and which does not exist server-side without a client
 * driving it. What a routine is actually <em>for</em> is the net effect: this run takes 32
 * cobblestone out of that barrel. That statement survives the player having shuffled the stack
 * around three times while deciding, and it replays through the item-handler capability, which every
 * vanilla container and every automatable mod machine already exposes.
 *
 * <p>Matching is by item, not by components. The rest of the action model works the same way, and an
 * anchor sorting enchanted books by their enchantment is not a thing this mod is trying to be.
 */
public final class ContainerWatch {

    private ContainerWatch() {}

    /** What was in the container when it was opened, and where it is. */
    private record Watch(BlockPos pos, Map<Item, Integer> contents) {}

    /** A block right-clicked this tick, held only until we learn whether it opened a menu. */
    private record Pending(BlockPos pos, int actionIndex) {}

    private static final Map<UUID, Watch> OPEN = new ConcurrentHashMap<>();
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /**
     * Notes a block the player just right-clicked, along with the index of the action recorded for
     * it, so that {@link #onContainerOpened} can retract it if a menu opens.
     */
    public static void noteInteraction(ServerPlayer player, BlockPos pos, int actionIndex) {
        PENDING.put(player.getUUID(), new Pending(pos, actionIndex));
    }

    /**
     * A container opened: snapshot it, and retract the click that opened it.
     *
     * <p>The retraction is why this is driven from the open event rather than guessed at click time.
     * A block having an item handler does not mean right-clicking it opens anything — bone-mealing a
     * composter is a use, not a transfer — so the click can only be safely dropped once a menu has
     * actually appeared.
     */
    public static void onContainerOpened(ServerPlayer player, RecordingSession session) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) {
            return;
        }

        ResourceHandler<ItemResource> handler = handlerAt(player.level(), pending.pos());
        if (handler == null) {
            return;
        }

        session.dropActionAt(pending.actionIndex());
        OPEN.put(player.getUUID(), new Watch(pending.pos(), snapshot(handler)));
    }

    /** Everything the player took out or put in, as actions. Empty if nothing moved. */
    public static List<ChronoAction.TransferItems> onContainerClosed(ServerPlayer player,
                                                                  RecordingSession session) {
        PENDING.remove(player.getUUID());
        Watch watch = OPEN.remove(player.getUUID());
        if (watch == null) {
            return List.of();
        }

        ResourceHandler<ItemResource> handler = handlerAt(player.level(), watch.pos());
        if (handler == null) {
            // The container was broken or unloaded while open. Nothing trustworthy to diff against.
            return List.of();
        }

        Map<Item, Integer> after = snapshot(handler);
        BlockPos localPos = session.toLocal(watch.pos());

        List<ChronoAction.TransferItems> actions = new ArrayList<>();
        for (Item item : union(watch.contents(), after)) {
            int delta = after.getOrDefault(item, 0) - watch.contents().getOrDefault(item, 0);
            if (delta == 0) {
                continue;
            }
            actions.add(new ChronoAction.TransferItems(
                    localPos,
                    BuiltInRegistries.ITEM.wrapAsHolder(item),
                    Math.abs(delta),
                    delta < 0));
        }
        return actions;
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

    // ------------------------------------------------------------------ helpers

    private static @Nullable ResourceHandler<ItemResource> handlerAt(net.minecraft.world.level.Level level,
                                                                    BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.isLoaded(pos)) {
            return null;
        }
        return serverLevel.getCapability(Capabilities.Item.BLOCK, pos, null);
    }

    private static Map<Item, Integer> snapshot(ResourceHandler<ItemResource> handler) {
        Map<Item, Integer> totals = new HashMap<>();
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            int amount = handler.getAmountAsInt(slot);
            if (amount > 0) {
                totals.merge(resource.getItem(), amount, Integer::sum);
            }
        }
        return totals;
    }

    private static Iterable<Item> union(Map<Item, Integer> before, Map<Item, Integer> after) {
        Map<Item, Integer> all = new HashMap<>(before);
        all.putAll(after);
        return all.keySet();
    }
}
