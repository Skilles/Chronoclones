package com.skilles.chronoclones.menu.client;

/**
 * Where the precision drawer sits relative to the anchor window.
 */
final class DrawerLayout {

    private DrawerLayout() {}

    /**
     * Open width of the drawer body, not counting the tab.
     */
    static final int WIDTH = 96;

    /**
     * Width of the handle, including the part that hides behind the window.
     */
    static final int TAB_WIDTH = 15;

    /**
     * How far the handle tucks under the window's edge, so it reads as growing out of the window
     * rather than as a button parked against it.
     */
    static final int TAB_OVERLAP = 3;

    /**
     * Which side to open on.
     */
    static boolean opensLeft(int screenWidth, int leftPos, int imageWidth) {
        int needed = TAB_WIDTH + WIDTH;
        int roomRight = screenWidth - (leftPos + imageWidth);
        if (roomRight >= needed) {
            return false;
        }
        return leftPos >= needed || leftPos > roomRight;
    }

    /** The tab, always flush against the window's edge so it does not move as the drawer slides. */
    static int tabX(boolean onLeft, int leftPos, int imageWidth) {
        return onLeft
                ? leftPos - TAB_WIDTH + TAB_OVERLAP
                : leftPos + imageWidth - TAB_OVERLAP;
    }

    /**
     * The left edge of the drawer body, given how far open it currently is.
     */
    static int bodyX(boolean onLeft, int leftPos, int imageWidth, int openWidth) {
        return onLeft
                ? leftPos - TAB_WIDTH - openWidth
                : leftPos + imageWidth + TAB_WIDTH;
    }
}
