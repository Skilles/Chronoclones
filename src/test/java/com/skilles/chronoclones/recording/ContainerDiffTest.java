package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Map;

import com.skilles.chronoclones.recording.ContainerDiff.SlotContent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container diff, which is where every judgement about container capture lives.
 *
 * <p>Worth testing hard because all of it is invisible when wrong. A routine that hauls the wrong
 * way still runs, still reports success, and empties the chest it was meant to fill; one that loses
 * the slot still runs and silently smelts nothing.
 */
class ContainerDiffTest {

    private static final BlockPos LOCAL = new BlockPos(2, 0, -3);
    private static final int CARRIER = ChronoAction.TransferItems.CARRIER;

    private static SlotContent slot(Item item, int count) {
        return new SlotContent(item, count);
    }

    private static final SlotContent EMPTY = SlotContent.EMPTY;

    @Test
    @DisplayName("a container that lost items records a withdrawal from that slot")
    void lossIsWithdrawal() {
        List<ChronoAction.TransferItems> moves = ContainerDiff.between(
                List.of(EMPTY, EMPTY, slot(Items.COBBLESTONE, 64)),
                List.of(EMPTY, EMPTY, slot(Items.COBBLESTONE, 32)),
                Map.of(), Map.of(Items.COBBLESTONE, 32),
                LOCAL);

        assertEquals(1, moves.size());
        ChronoAction.TransferItems move = moves.get(0);
        assertEquals(Items.COBBLESTONE, move.item().value());
        assertEquals(32, move.amount());
        assertEquals(2, move.fromSlot(), "the slot the items actually came out of");
        assertEquals(CARRIER, move.toSlot());
        assertTrue(move.isWithdrawal());
        assertEquals(LOCAL, move.localPos());
    }

    /**
     * The case the whole slot-level rework exists for.
     *
     * <p>Two logs into a furnace, one to the input and one to the fuel slot. Both slots accept a
     * log, so a diff that only tracked totals would say "the furnace gained two logs" and replay
     * would put both wherever they first fit — producing a furnace that never smelts, silently.
     */
    @Test
    @DisplayName("loading a furnace records which slot each item went to")
    void furnaceSlotsAreDistinguished() {
        List<ChronoAction.TransferItems> moves = ContainerDiff.between(
                List.of(EMPTY, EMPTY, EMPTY),
                List.of(slot(Items.OAK_LOG, 1), slot(Items.OAK_LOG, 1), EMPTY),
                Map.of(Items.OAK_LOG, 8), Map.of(Items.OAK_LOG, 6),
                LOCAL);

        assertEquals(2, moves.size());
        assertTrue(moves.stream().allMatch(m -> m.fromSlot() == CARRIER && m.amount() == 1),
                "both came out of the player, one each");

        assertTrue(moves.stream().anyMatch(m -> m.toSlot() == 0), "one went to the input slot");
        assertTrue(moves.stream().anyMatch(m -> m.toSlot() == 1), "one went to the fuel slot");
    }

    @Test
    @DisplayName("moving a stack between two slots of one container records neither a take nor a put")
    void slotToSlotStaysInside() {
        List<ChronoAction.TransferItems> moves = ContainerDiff.between(
                List.of(EMPTY, EMPTY, EMPTY, slot(Items.COBBLESTONE, 16), EMPTY),
                List.of(EMPTY, EMPTY, EMPTY, EMPTY, slot(Items.COBBLESTONE, 16)),
                Map.of(), Map.of(),
                LOCAL);

        assertEquals(1, moves.size());
        ChronoAction.TransferItems move = moves.get(0);
        assertEquals(3, move.fromSlot());
        assertEquals(4, move.toSlot());
        assertEquals(16, move.amount());
        assertEquals(false, move.isWithdrawal());
        assertEquals(false, move.isDeposit(), "nothing ever touched the player's inventory");
    }

    @Test
    @DisplayName("shuffling a stack around without changing anything records nothing")
    void noNetChangeRecordsNothing() {
        // The whole reason capture is a diff rather than a click log: a player who picks a stack up,
        // puts it back and changes their mind has not taught the routine anything.
        List<SlotContent> same = List.of(slot(Items.COBBLESTONE, 64), slot(Items.DIAMOND, 3));
        assertTrue(ContainerDiff.between(same, same,
                Map.of(Items.STICK, 1), Map.of(Items.STICK, 1), LOCAL).isEmpty());
    }

    /**
     * A furnace burns fuel and produces output while the player stands there looking at it. Those
     * changes are not moves the player made, and inventing one would put a step in the recording
     * that never happened.
     */
    @Test
    @DisplayName("changes the player did not cause are dropped rather than guessed at")
    void unmatchedChangesAreDropped() {
        List<ChronoAction.TransferItems> moves = ContainerDiff.between(
                List.of(slot(Items.OAK_LOG, 2), EMPTY, EMPTY),
                List.of(slot(Items.OAK_LOG, 1), EMPTY, slot(Items.CHARCOAL, 1)),
                Map.of(), Map.of(),
                LOCAL);

        assertTrue(moves.isEmpty(),
                "a log burning into charcoal is the furnace working, not the player hauling");
    }

    @Test
    @DisplayName("a take and a put in the same session are recorded separately")
    void mixedTrafficIsSplit() {
        List<ChronoAction.TransferItems> moves = ContainerDiff.between(
                List.of(slot(Items.COBBLESTONE, 64), EMPTY),
                List.of(slot(Items.COBBLESTONE, 16), slot(Items.DIAMOND, 2)),
                Map.of(Items.DIAMOND, 2), Map.of(Items.COBBLESTONE, 48),
                LOCAL);

        assertEquals(2, moves.size());
        ChronoAction.TransferItems cobble = find(moves, Items.COBBLESTONE);
        ChronoAction.TransferItems diamond = find(moves, Items.DIAMOND);

        assertEquals(48, cobble.amount());
        assertTrue(cobble.isWithdrawal());
        assertEquals(0, cobble.fromSlot());

        assertEquals(2, diamond.amount());
        assertTrue(diamond.isDeposit());
        assertEquals(1, diamond.toSlot());
    }

    @Test
    @DisplayName("one source feeding several destinations is split across them")
    void oneSourceManySinks() {
        // Taking a stack out of one chest slot and spreading it over three slots of the same chest.
        List<ChronoAction.TransferItems> moves = ContainerDiff.between(
                List.of(slot(Items.COBBLESTONE, 30), EMPTY, EMPTY, EMPTY),
                List.of(EMPTY, slot(Items.COBBLESTONE, 10), slot(Items.COBBLESTONE, 10),
                        slot(Items.COBBLESTONE, 10)),
                Map.of(), Map.of(),
                LOCAL);

        assertEquals(3, moves.size());
        assertTrue(moves.stream().allMatch(m -> m.fromSlot() == 0 && m.amount() == 10));
        assertEquals(30, moves.stream().mapToInt(ChronoAction.TransferItems::amount).sum());
    }

    private static ChronoAction.TransferItems find(List<ChronoAction.TransferItems> moves, Item item) {
        return moves.stream()
                .filter(m -> m.item().value() == item)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no transfer recorded for " + item));
    }
}
