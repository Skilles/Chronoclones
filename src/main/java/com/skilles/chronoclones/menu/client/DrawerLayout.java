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
     * Width of the handle.
     */
    static final int TAB_WIDTH = 10;

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
        return onLeft ? leftPos - TAB_WIDTH : leftPos + imageWidth;
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
