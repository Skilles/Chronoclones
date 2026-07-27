package com.skilles.chronoclones.replay;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The packed form, which is a stored format rather than an internal detail.
 *
 * <p>{@code pack()} is what goes into an anchor's NBT and across the wire, so the bit layout is a
 * promise to every anchor already saved in somebody's world. Change it and they all quietly come
 * back set to something else — an anchor that was specific about items reloads specific about
 * squares, and starts putting things where they do not go. Nothing about that failure looks like a
 * version mismatch, so the layout is pinned here.
 *
 * <p>What an anchor <em>does</em> with each axis is asserted in {@code PrecisionGameTest}, where
 * there is a container to move things into.
 */
class TransferPrecisionTest {

    @Test
    @DisplayName("the stored bit layout is what it has always been")
    void bitLayoutIsFixed() {
        assertEquals(new TransferPrecision(true, false, false), TransferPrecision.unpack(1));
        assertEquals(new TransferPrecision(false, true, false), TransferPrecision.unpack(2));
        assertEquals(new TransferPrecision(false, false, true), TransferPrecision.unpack(4));
    }

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
    @DisplayName("an anchor with nothing stored reads as specific about nothing")
    void absentMeansNone() {
        // loadAdditional defaults the tag to zero, so this is what every anchor saved before the
        // setting existed will come back as — and the loose end is the right answer for them.
        assertEquals(TransferPrecision.NONE, TransferPrecision.unpack(0));
    }

    @Test
    @DisplayName("bits nobody has claimed are ignored, not rejected")
    void unknownBitsAreDropped() {
        // unpack decodes a packet a client sent. A fourth axis reaching an older server must read as
        // "not set" rather than throwing a payload handler off mid-tick.
        assertEquals(new TransferPrecision(true, true, true), TransferPrecision.unpack(0b1111));
    }
}
