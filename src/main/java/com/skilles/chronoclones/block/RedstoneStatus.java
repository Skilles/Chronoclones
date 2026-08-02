package com.skilles.chronoclones.block;

/**
 * What a comparator reads off an anchor. A monotone "how alive is it" scale, so plain
 * thresholds answer the two questions contraptions ask: zero means done, and anything
 * caught between zero and full means somebody should come look.
 */
public final class RedstoneStatus {

    public static final int STOPPED = 0;
    public static final int PAUSED = 3;
    public static final int STALLED = 7;
    public static final int FINISHING = 11;
    public static final int RUNNING = 15;

    private RedstoneStatus() {}

    public static int signalOf(RunState state, boolean hasRecording, boolean stalled,
                               boolean finishing) {
        if (!hasRecording || state == RunState.STOPPED) {
            return STOPPED;
        }
        if (state == RunState.PAUSED) {
            return PAUSED;
        }
        if (stalled) {
            return STALLED;
        }
        return finishing ? FINISHING : RUNNING;
    }
}
