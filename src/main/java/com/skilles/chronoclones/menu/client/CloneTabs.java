package com.skilles.chronoclones.menu.client;

/**
 * Where the clone tabs sit along the storage header row.
 *
 * <p>They share that row rather than taking one of their own: {@code imageHeight} is fixed before
 * the clone count has reached the client, so a strip that came and went would shift everything
 * below it inside a window that cannot grow.
 */
final class CloneTabs {

    private CloneTabs() {}

    static final int WIDTH = 12;
    static final int HEIGHT = 10;
    static final int GAP = 1;

    /** No tabs at all for a lone clone: there is nothing to choose between. */
    static int count(int activeClones) {
        return activeClones > 1 ? activeClones : 0;
    }

    static int stripWidth(int tabs) {
        return tabs <= 0 ? 0 : tabs * WIDTH + (tabs - 1) * GAP;
    }

    /** Right-aligned to {@code rightEdge}, so the header text keeps the left of the row. */
    static int xOf(int tab, int tabs, int rightEdge) {
        return rightEdge - stripWidth(tabs) + tab * (WIDTH + GAP);
    }

    /** The tab under the pointer, or -1. Coordinates are window-local, as the row's are. */
    static int at(int x, int y, int tabs, int rightEdge, int top) {
        if (y < top || y >= top + HEIGHT) {
            return -1;
        }
        for (int tab = 0; tab < tabs; tab++) {
            int left = xOf(tab, tabs, rightEdge);
            if (x >= left && x < left + WIDTH) {
                return tab;
            }
        }
        return -1;
    }
}
