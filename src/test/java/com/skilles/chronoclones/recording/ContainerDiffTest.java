package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The withdraw/deposit sign convention.
 *
 * <p>Worth its own test because it is invisible when wrong. A routine that hauls the wrong way still
 * runs, still reports success, and empties the chest it was supposed to fill — and the only place
 * the direction is decided is one comparison.
 */
class ContainerDiffTest {

    private static final BlockPos LOCAL = new BlockPos(2, 0, -3);

    @Test
    @DisplayName("a container that lost items records a withdrawal")
    void lossIsWithdrawal() {
        List<ChronoAction.TransferItems> actions = ContainerWatch.diff(
                Map.of(Items.COBBLESTONE, 64),
                Map.of(Items.COBBLESTONE, 32),
                LOCAL);

        assertEquals(1, actions.size());
        ChronoAction.TransferItems transfer = actions.get(0);
        assertEquals(Items.COBBLESTONE, transfer.item().value());
        assertEquals(32, transfer.amount());
        assertTrue(transfer.withdraw(), "the container lost items, so the routine took them out");
        assertEquals(LOCAL, transfer.localPos());
    }

    @Test
    @DisplayName("a container that gained items records a deposit")
    void gainIsDeposit() {
        List<ChronoAction.TransferItems> actions = ContainerWatch.diff(
                Map.of(),
                Map.of(Items.DIAMOND, 5),
                LOCAL);

        assertEquals(1, actions.size());
        assertEquals(5, actions.get(0).amount());
        assertEquals(false, actions.get(0).withdraw(),
                "the container gained items, so the routine put them in");
    }

    @Test
    @DisplayName("shuffling a stack around without changing totals records nothing")
    void noNetChangeRecordsNothing() {
        // The whole reason capture is a diff rather than a click log: a player who picks a stack up,
        // puts it back, and reorganises two slots has not taught the routine anything.
        assertTrue(ContainerWatch.diff(
                Map.of(Items.COBBLESTONE, 64, Items.DIAMOND, 3),
                Map.of(Items.DIAMOND, 3, Items.COBBLESTONE, 64),
                LOCAL).isEmpty());
    }

    @Test
    @DisplayName("both directions at once are recorded separately")
    void mixedTrafficIsSplit() {
        List<ChronoAction.TransferItems> actions = ContainerWatch.diff(
                Map.of(Items.COBBLESTONE, 64),
                Map.of(Items.COBBLESTONE, 16, Items.DIAMOND, 2),
                LOCAL);

        assertEquals(2, actions.size());
        ChronoAction.TransferItems cobble = find(actions, Items.COBBLESTONE);
        ChronoAction.TransferItems diamond = find(actions, Items.DIAMOND);

        assertEquals(48, cobble.amount());
        assertTrue(cobble.withdraw());
        assertEquals(2, diamond.amount());
        assertEquals(false, diamond.withdraw());
    }

    private static ChronoAction.TransferItems find(List<ChronoAction.TransferItems> actions, Item item) {
        return actions.stream()
                .filter(a -> a.item().value() == item)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no transfer recorded for " + item));
    }
}
