package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * Turns two snapshots of a container into the moves that must have happened between them.
 *
 * <p>Pure, and deliberately separated from everything that needs a world, because this is where all
 * the judgement lives. The rest of container capture is bookkeeping.
 *
 * <p><b>Why a diff and not a click log.</b> Clicks are raw input. A player who picks a stack up,
 * drops half back, changes their mind and reorganises two slots has performed a dozen clicks and
 * taught the routine one thing. Replaying clicks also means driving a real container menu, whose
 * behaviour every mod may override. The diff records what the session was <em>for</em>.
 *
 * <p><b>Why slots and not totals.</b> "The furnace gained two logs" cannot be replayed: one belongs
 * in the input and one in the fuel slot, and an insert that merely looks for room puts both in the
 * first slot that accepts them and smelts nothing. In a machine, a slot is a meaning.
 *
 * <p>Sources are paired to destinations greedily, lowest slot first, which makes the output a
 * deterministic function of the two snapshots. Ordering <em>between</em> the resulting moves is not
 * recoverable from a diff and is not preserved — within one container session it does not matter,
 * because nothing here observes anything else mid-session.
 */
public final class ContainerDiff {

    private ContainerDiff() {}

    /** One slot's contents. A null item means empty. */
    public record SlotContent(@Nullable Item item, int count) {
        public static final SlotContent EMPTY = new SlotContent(null, 0);

        public int countOf(Item wanted) {
            return item == wanted ? count : 0;
        }
    }

    /**
     * @param before        container contents, per slot, when the player opened it
     * @param after         container contents, per slot, when they closed it
     * @param carrierBefore the player's own totals per item on open
     * @param carrierAfter  the same on close
     */
    public static List<ChronoAction.TransferItems> between(
            List<SlotContent> before, List<SlotContent> after,
            Map<Item, Integer> carrierBefore, Map<Item, Integer> carrierAfter,
            BlockPos localPos) {

        List<ChronoAction.TransferItems> moves = new ArrayList<>();

        for (Item item : itemsInvolved(before, after, carrierBefore, carrierAfter)) {
            // Slots that lost this item, and slots that gained it. The player's inventory is one
            // more participant on each side, under the CARRIER index.
            List<Endpoint> sources = new ArrayList<>();
            List<Endpoint> sinks = new ArrayList<>();

            int carrierDelta = carrierAfter.getOrDefault(item, 0) - carrierBefore.getOrDefault(item, 0);
            add(carrierDelta, ChronoAction.TransferItems.CARRIER, sources, sinks);

            int slots = Math.max(before.size(), after.size());
            for (int slot = 0; slot < slots; slot++) {
                int delta = at(after, slot).countOf(item) - at(before, slot).countOf(item);
                add(delta, slot, sources, sinks);
            }

            pair(sources, sinks, item, localPos, moves);
        }

        return moves;
    }

    /** A slot with items to give or room that got filled. Mutable so pairing can draw it down. */
    private static final class Endpoint {
        final int slot;
        int remaining;

        Endpoint(int slot, int remaining) {
            this.slot = slot;
            this.remaining = remaining;
        }
    }

    private static void add(int delta, int slot, List<Endpoint> sources, List<Endpoint> sinks) {
        if (delta < 0) {
            sources.add(new Endpoint(slot, -delta));
        } else if (delta > 0) {
            sinks.add(new Endpoint(slot, delta));
        }
    }

    /**
     * Matches what left against what arrived.
     *
     * <p>Anything left over is dropped rather than guessed at. The two sides do not have to balance:
     * a furnace burns fuel and produces output while the player is looking at it, and the player can
     * drop or craft with what they took. An unmatched change is something the routine did not do,
     * and inventing a move for it would put a step in the recording that never happened.
     */
    private static void pair(List<Endpoint> sources, List<Endpoint> sinks, Item item,
                             BlockPos localPos, List<ChronoAction.TransferItems> out) {
        int s = 0;
        int d = 0;
        while (s < sources.size() && d < sinks.size()) {
            Endpoint source = sources.get(s);
            Endpoint sink = sinks.get(d);

            int moved = Math.min(source.remaining, sink.remaining);
            if (moved > 0 && source.slot != sink.slot) {
                out.add(new ChronoAction.TransferItems(
                        localPos, BuiltInRegistries.ITEM.wrapAsHolder(item), moved,
                        source.slot, sink.slot));
            }
            source.remaining -= moved;
            sink.remaining -= moved;

            if (source.remaining == 0) {
                s++;
            }
            if (sink.remaining == 0) {
                d++;
            }
        }
    }

    private static SlotContent at(List<SlotContent> slots, int index) {
        return index < slots.size() ? slots.get(index) : SlotContent.EMPTY;
    }

    /** Every item type that appears on either side, so nothing is missed by iterating one snapshot. */
    private static Set<Item> itemsInvolved(List<SlotContent> before, List<SlotContent> after,
                                           Map<Item, Integer> carrierBefore,
                                           Map<Item, Integer> carrierAfter) {
        Set<Item> items = new HashSet<>();
        for (SlotContent content : before) {
            if (content.item() != null) {
                items.add(content.item());
            }
        }
        for (SlotContent content : after) {
            if (content.item() != null) {
                items.add(content.item());
            }
        }
        items.addAll(carrierBefore.keySet());
        items.addAll(carrierAfter.keySet());
        return items;
    }

    /** Totals per item, for the carrier side where slot indices carry no meaning. */
    public static Map<Item, Integer> totals(List<SlotContent> slots) {
        Map<Item, Integer> totals = new HashMap<>();
        for (SlotContent content : slots) {
            if (content.item() != null && content.count() > 0) {
                totals.merge(content.item(), content.count(), Integer::sum);
            }
        }
        return totals;
    }
}
