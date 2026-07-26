package com.skilles.chronoclones.replay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The packing, which is the part with something to get wrong.
 *
 * <p>Three booleans travel to the client as one synced int and come back from the drawer as one
 * payload field. If the bits were to shift — a fourth axis inserted in the middle, say — an anchor
 * saved as "specific about items" would silently reload as "specific about slots" and start putting
 * things somewhere else. Nothing about that failure looks like a bug until items are in the wrong
 * chest, so the mapping is pinned here rather than left to be obvious.
 */
class TransferPrecisionTest {

    @Test
    @DisplayName("all eight combinations survive the round trip")
    void everyCombinationRoundTrips() {
        // Eight and not five: item and quantity say what gets staged, which is a fact about the
        // anchor's own inventory and holds whether or not the destination square is pinned. Nothing
        // is normalised away on the way through.
        for (int bits = 0; bits < 8; bits++) {
            TransferPrecision decoded = TransferPrecision.unpack(bits);
            assertEquals(bits, decoded.pack(), "bits " + bits + " decoded to " + decoded);
        }
    }

    @Test
    @DisplayName("each axis owns its own bit")
    void axesAreIndependent() {
        assertEquals(new TransferPrecision(true, false, false), TransferPrecision.unpack(1));
        assertEquals(new TransferPrecision(false, true, false), TransferPrecision.unpack(2));
        assertEquals(new TransferPrecision(false, false, true), TransferPrecision.unpack(4));
    }

    @Test
    @DisplayName("a fresh anchor is specific about nothing")
    void noneIsTheDefault() {
        // The default has to be the loose end. An anchor that started out demanding the exact stack
        // in the exact square would stop working the first time anything in the world moved, and
        // would look broken rather than strict.
        assertEquals(0, TransferPrecision.NONE.pack());
        assertFalse(TransferPrecision.NONE.slot());
        assertFalse(TransferPrecision.NONE.item());
        assertFalse(TransferPrecision.NONE.quantity());
    }

    @Test
    @DisplayName("bits nobody has claimed are ignored, not rejected")
    void unknownBitsAreDropped() {
        // unpack decodes a packet a client sent. A future fourth axis reaching an older server must
        // read as "not set" rather than throwing a payload handler off mid-tick.
        assertEquals(TransferPrecision.NONE, TransferPrecision.unpack(0b1000));
        assertEquals(new TransferPrecision(true, true, true), TransferPrecision.unpack(0b1111));
    }
}
