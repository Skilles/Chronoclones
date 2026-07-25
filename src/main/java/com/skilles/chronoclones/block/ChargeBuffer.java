package com.skilles.chronoclones.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The anchor's charge buffer.
 *
 * <p>Charge is the mod's primary balance lever: every executed action costs some, so upgrades read
 * as a tradeoff rather than pure gain. Four clones mining at triple rate burn charge twelve times
 * as fast as a bare anchor, which is what stops "more upgrades" from being a free win.
 *
 * <p>Immutable so the block entity swaps one value rather than mutating shared state, which keeps
 * "did this action actually get paid for" impossible to get half-right.
 */
public record ChargeBuffer(int stored, int capacity) {

    public static final int DEFAULT_CAPACITY = 10_000;

    /** Charge produced per tick of vanilla furnace burn time. */
    public static final int CHARGE_PER_BURN_TICK = 2;

    public static final ChargeBuffer EMPTY = new ChargeBuffer(0, DEFAULT_CAPACITY);

    public static final Codec<ChargeBuffer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("stored").forGetter(ChargeBuffer::stored),
            Codec.INT.optionalFieldOf("capacity", DEFAULT_CAPACITY).forGetter(ChargeBuffer::capacity)
    ).apply(i, ChargeBuffer::new));

    public ChargeBuffer {
        capacity = Math.max(1, capacity);
        stored = Math.clamp(stored, 0, capacity);
    }

    public boolean canAfford(int cost) {
        return stored >= cost;
    }

    public boolean isEmpty() {
        return stored <= 0;
    }

    /** Spends {@code cost}, or returns this unchanged if it cannot be afforded. */
    public ChargeBuffer spend(int cost) {
        if (cost <= 0) {
            return this;
        }
        if (!canAfford(cost)) {
            return this;
        }
        return new ChargeBuffer(stored - cost, capacity);
    }

    /** Adds charge, discarding any overflow past capacity. */
    public ChargeBuffer refill(int amount) {
        if (amount <= 0) {
            return this;
        }
        return new ChargeBuffer(Math.min(stored + amount, capacity), capacity);
    }

    /** How much room is left, for deciding whether consuming another fuel item is worthwhile. */
    public int headroom() {
        return capacity - stored;
    }

    /** 0..scale, for a GUI bar. */
    public int scaled(int scale) {
        return capacity <= 0 ? 0 : stored * scale / capacity;
    }
}
