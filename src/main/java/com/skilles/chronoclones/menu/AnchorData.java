package com.skilles.chronoclones.menu;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;

/**
 * The slots of the anchor's synced {@code ContainerData}, by name.
 *
 * <p>Written as bare numbers in two files, these drifted once and a client read past the end
 * of its buffer.
 */
public final class AnchorData {

    private AnchorData() {}

    public static final int LENGTH_TICKS = 0;
    public static final int ACTION_COUNT = 1;
    public static final int FAILURE_REASON = 2;
    public static final int ACTIVE_CLONES = 3;
    public static final int CHARGE = 4;
    public static final int CHARGE_CAPACITY = 5;
    public static final int TICKS_PER_STEP = 6;
    public static final int FAILURE_X = 7;
    public static final int FAILURE_Y = 8;
    public static final int FAILURE_Z = 9;

    /** One playhead per clone, so the timeline can mark them all. */
    public static final int PLAYHEAD = 10;

    public static int playhead(int clone) {
        return PLAYHEAD + clone;
    }

    /** How many ints the menu syncs. Derived, so it cannot disagree with the list above. */
    public static final int COUNT = PLAYHEAD + ChronoAnchorBlockEntity.CLONE_INVENTORIES;
}
