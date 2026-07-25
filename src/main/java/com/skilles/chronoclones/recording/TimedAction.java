package com.skilles.chronoclones.recording;

/**
 * An {@link ChronoAction} stamped with the tick it happened on.
 *
 * <p>Serialization lives in {@link RecordingCodecs}.
 */
public record TimedAction(int tick, ChronoAction action) {}
