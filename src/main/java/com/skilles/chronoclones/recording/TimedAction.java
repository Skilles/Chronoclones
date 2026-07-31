package com.skilles.chronoclones.recording;

/** An action stamped with the tick it happened on and how it should be interpreted. */
public record TimedAction(int tick, ChronoAction action, ActionSettings settings) {

    public TimedAction(int tick, ChronoAction action) {
        this(tick, action, ActionSettings.DEFAULT);
    }

    public TimedAction(int tick, ChronoAction action, int heldSlot) {
        this(tick, action, ActionSettings.DEFAULT.withSlot(ActionSettings.SlotRule.prefer(heldSlot)));
    }

    public TimedAction withSettings(ActionSettings settings) {
        return new TimedAction(tick, action, settings);
    }
}
