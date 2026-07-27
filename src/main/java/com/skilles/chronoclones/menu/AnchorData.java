package com.skilles.chronoclones.menu;

/**
 * The slots of the anchor's synced {@code ContainerData}, by name.
 *
 * <p>Written as bare numbers in two files, these drifted once and a client read past the end
 * of its buffer.
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
