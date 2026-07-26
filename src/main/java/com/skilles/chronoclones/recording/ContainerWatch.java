package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.skilles.chronoclones.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Watches an open container and records what the player did in it.
 *
 * <p>Snapshots the block's item handler slot by slot on open, and the player's own totals alongside
 * it; on close, {@link ContainerDiff} turns the two pairs of snapshots into moves. The judgement all
 * lives there — this class only decides when to look and what to look at.
 *
 * <p>Matching is by item, not by components. The rest of the action model works the same way, and an
 * anchor sorting enchanted books by their enchantment is not a thing this mod is trying to be.
 */
public final class ContainerWatch {

    private ContainerWatch() {}

    /** What was where when the container was opened. */
    private record Watch(BlockPos pos, List<ContainerDiff.SlotContent> contents,
                         Map<Item, Integer> carrier) {}

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

        // An anchor's own slots are machinery, not part of the task. Replay refuses to reach into
        // one anyway, so recording the attempt would only bake in a step that can never run.
        if (player.level().getBlockState(pending.pos()).typeHolder().is(ModTags.ANCHOR_UNBREAKABLE)) {
            return;
        }

        ResourceHandler<ItemResource> handler = handlerAt(player.level(), pending.pos());
        if (handler == null) {
            return;
        }

        session.dropActionAt(pending.actionIndex());
        OPEN.put(player.getUUID(), new Watch(pending.pos(), snapshot(handler), carrierTotals(player)));
    }

    /** Everything the player moved, as actions. Empty if nothing moved. */
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

        return ContainerDiff.between(
                watch.contents(), snapshot(handler),
                watch.carrier(), carrierTotals(player),
                session.toLocal(watch.pos()));
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
        // Unsided, deliberately, and the executor reads it the same way. A furnace's sided handlers
        // expose different subsets per face with their own indices, so capturing through one face and
        // replaying through another would silently renumber every slot in the recording.
        return serverLevel.getCapability(Capabilities.Item.BLOCK, pos, null);
    }

    private static List<ContainerDiff.SlotContent> snapshot(ResourceHandler<ItemResource> handler) {
        List<ContainerDiff.SlotContent> slots = new ArrayList<>(handler.size());
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            int amount = handler.getAmountAsInt(slot);
            slots.add(resource.isEmpty() || amount <= 0
                    ? ContainerDiff.SlotContent.EMPTY
                    : new ContainerDiff.SlotContent(resource.getItem(), amount));
        }
        return slots;
    }

    /**
     * The player's own totals per item.
     *
     * <p>Totals rather than slots: a player has thirty-six slots and an anchor eighteen, so which
     * one an item sat in is not a fact that survives the trip.
     */
    private static Map<Item, Integer> carrierTotals(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ContainerDiff.SlotContent> slots = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            slots.add(stack.isEmpty()
                    ? ContainerDiff.SlotContent.EMPTY
                    : new ContainerDiff.SlotContent(stack.getItem(), stack.getCount()));
        }
        // What is on the cursor mid-drag belongs to the player as much as anything in a slot, and
        // leaving it out would make a stack picked up but not yet placed look like it vanished.
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            slots.add(new ContainerDiff.SlotContent(carried.getItem(), carried.getCount()));
        }
        return ContainerDiff.totals(slots);
    }
}
