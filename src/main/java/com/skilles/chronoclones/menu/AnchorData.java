package com.skilles.chronoclones.menu;

/**
 * The slots of the anchor's synced {@code ContainerData}, by name.
 *
 * <p>A {@code ContainerData} is an array of ints addressed by position, and the two halves of it sit
 * in different files: the block entity fills the array and the menu reads it. Written as bare
 * numbers, those two lists have to be kept aligned by eye — and they have already come apart once.
 * A row removed from the middle silently shifts every readout below it to the wrong value, and the
 * time the count itself drifted, the client threw reading past the end of a buffer it had sized
 * from the other constant.
 *
 * <p>So the positions live here, once, and {@link #COUNT} is derived from them rather than typed
 * out. Adding a row is one line; removing one cannot leave a gap.
 */
public final class AnchorData {

    private AnchorData() {}

    public static final int PLAYHEAD = 0;
    public static final int LENGTH_TICKS = 1;
    public static final int ACTION_COUNT = 2;
    public static final int FAILURE_REASON = 3;
    public static final int ACTIVE_CLONES = 4;
    public static final int CHARGE = 5;
    public static final int CHARGE_CAPACITY = 6;
    public static final int TICKS_PER_STEP = 7;
    public static final int FAILURE_X = 8;
    public static final int FAILURE_Y = 9;
    public static final int FAILURE_Z = 10;

    /** How many ints the menu syncs. Derived, so it cannot disagree with the list above. */
    public static final int COUNT = FAILURE_Z + 1;
}
