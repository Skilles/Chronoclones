package com.skilles.chronoclones.recording;

/**
 * An {@link ChronoAction} stamped with the tick it happened on.
 */
public record TimedAction(int tick, ChronoAction action) {}
