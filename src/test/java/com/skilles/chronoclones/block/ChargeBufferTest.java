package com.skilles.chronoclones.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skilles.chronoclones.recording.ChronoActionType;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Charge is the spec's primary balance lever, so the arithmetic that decides whether an
 * upgraded anchor is a tradeoff or a free win is worth pinning down.
 */
class ChargeBufferTest {

    @Test
    @DisplayName("spending removes exactly the cost")
    void spendingDeducts() {
        ChargeBuffer buffer = new ChargeBuffer(100, 1000);
        assertEquals(90, buffer.spend(10).stored());
    }

    @Test
    @DisplayName("an unaffordable action leaves the buffer untouched rather than going negative")
    void unaffordableSpendIsANoOp() {
        ChargeBuffer buffer = new ChargeBuffer(5, 1000);

        assertFalse(buffer.canAfford(10));
        assertSame(buffer, buffer.spend(10));
        assertEquals(5, buffer.spend(10).stored());
    }

    @Test
    @DisplayName("charge never exceeds capacity")
    void refillClampsToCapacity() {
        ChargeBuffer buffer = new ChargeBuffer(900, 1000);
        assertEquals(1000, buffer.refill(500).stored());
    }

    @Test
    @DisplayName("charge never goes below zero, even from a corrupt saved value")
    void storedIsClamped() {
        assertEquals(0, new ChargeBuffer(-50, 1000).stored());
        assertEquals(1000, new ChargeBuffer(99999, 1000).stored());
    }

    @Test
    @DisplayName("capacity is never zero, so the scaled bar cannot divide by zero")
    void capacityIsNeverZero() {
        assertTrue(new ChargeBuffer(0, 0).capacity() >= 1);
        assertEquals(0, new ChargeBuffer(0, 0).scaled(78));
    }

    @Test
    @DisplayName("headroom reports the room left, so fuel is not wasted on a nearly-full buffer")
    void headroomIsAccurate() {
        assertEquals(100, new ChargeBuffer(900, 1000).headroom());
        assertEquals(0, new ChargeBuffer(1000, 1000).headroom());
    }

    @Test
    @DisplayName("the scaled bar spans empty to full")
    void scaledSpansTheBar() {
        assertEquals(0, new ChargeBuffer(0, 1000).scaled(78));
        assertEquals(78, new ChargeBuffer(1000, 1000).scaled(78));
        assertEquals(39, new ChargeBuffer(500, 1000).scaled(78));
    }

    @Test
    @DisplayName("round trips through its codec")
    void roundTrips() {
        ChargeBuffer original = new ChargeBuffer(1234, 5000);

        var encoded = ChargeBuffer.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError(msg));
        ChargeBuffer decoded = ChargeBuffer.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError(msg));

        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("a full buffer funds a bounded number of breaks, so upgrades cost something real")
    void fullBufferIsFinite() {
        ChargeBuffer buffer = new ChargeBuffer(ChargeBuffer.DEFAULT_CAPACITY, ChargeBuffer.DEFAULT_CAPACITY);
        int cost = ChronoActionType.BREAK_BLOCK.chargeCost();

        int breaks = 0;
        while (buffer.canAfford(cost)) {
            buffer = buffer.spend(cost);
            breaks++;
        }

        assertEquals(ChargeBuffer.DEFAULT_CAPACITY / cost, breaks);
        assertTrue(buffer.isEmpty());
    }

    @Test
    @DisplayName("attacking costs more than breaking, which costs more than placing")
    void costsAreOrderedByImpact() {
        assertTrue(ChronoActionType.ATTACK_ENTITY.chargeCost() > ChronoActionType.BREAK_BLOCK.chargeCost());
        assertTrue(ChronoActionType.BREAK_BLOCK.chargeCost() > ChronoActionType.PLACE_BLOCK.chargeCost());
    }
}
