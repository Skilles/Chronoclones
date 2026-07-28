package com.skilles.chronoclones.recording;

/**
 * An {@link ChronoAction} stamped with the tick it happened on and the inventory slot the player
 * was holding it in.
 *
 * @param heldSlot {@link #ANY_SLOT} for a recording made before slots were captured
 */
public record TimedAction(int tick, ChronoAction action, int heldSlot) {

    /** No slot was recorded, so replay searches the whole inventory instead. */
    public static final int ANY_SLOT = -1;

    public TimedAction(int tick, ChronoAction action) {
        this(tick, action, ANY_SLOT);
    }
}
