package com.skilles.chronoclones.menu.client;

/**
 * Where the precision drawer sits relative to the anchor window.
 *
 * <p>Its own class, and pure, because it is the part with an answer that can be wrong. The window is
 * centred, so on a wide screen there is room either side and the drawer opens right like every
 * side panel in the game — but a small window or a large GUI scale can leave the right edge off
 * screen entirely, and a settings panel nobody can reach is worse than one on the unusual side.
 *
 * <p>Everything here is in absolute screen coordinates. Widgets and {@code extractBackground} both
 * work in those; only {@code extractLabels} runs inside the window's own translation.
 */
final class DrawerLayout {

    private DrawerLayout() {}

    /**
     * Open width of the drawer body, not counting the tab.
     *
     * <p>Sized down to what the three rows actually need. The window is 176 and centred, so every
     * pixel here costs twice: a drawer wide enough for a comfortable title stopped fitting beside a
     * 400-pixel-wide window, which is an ordinary result of a large GUI scale rather than an
     * unreasonable one.
     */
    static final int WIDTH = 96;

    /**
     * Width of the handle.
     *
     * <p>Here rather than on {@link DrawerTab} so that this class touches no GUI type at all, and can
     * therefore be asserted without standing up a client.
     */
    static final int TAB_WIDTH = 10;

    /**
     * Which side to open on.
     *
     * <p>Right when it fits, left when it does not and the left does, and otherwise whichever side
     * has more room — at which point the drawer is clipped either way and the only useful thing left
     * to do is clip it less.
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
     *
     * <p>Opening leftwards means the body's left edge moves, so this is not simply a mirror: the
     * panel grows away from the tab in both directions and the tab stays put.
     */
    static int bodyX(boolean onLeft, int leftPos, int imageWidth, int openWidth) {
        return onLeft
                ? leftPos - TAB_WIDTH - openWidth
                : leftPos + imageWidth + TAB_WIDTH;
    }
}
