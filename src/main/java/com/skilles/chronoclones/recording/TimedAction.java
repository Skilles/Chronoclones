package com.skilles.chronoclones.recording;

/**
 * An {@link ChronoAction} stamped with the tick it happened on and how it should be interpreted.
 */
public record TimedAction(int tick, ChronoAction action, ActionSettings settings) {

    public TimedAction(int tick, ChronoAction action) {
        this(tick, action, ActionSettings.DEFAULT);
    }

    /** The same action, recorded as if the player held the item in {@code heldSlot}. */
    public TimedAction(int tick, ChronoAction action, int heldSlot) {
        this(tick, action, ActionSettings.DEFAULT.withSlot(ActionSettings.SlotRule.prefer(heldSlot)));
    }

    public TimedAction withSettings(ActionSettings settings) {
        return new TimedAction(tick, action, settings);
    }
}
