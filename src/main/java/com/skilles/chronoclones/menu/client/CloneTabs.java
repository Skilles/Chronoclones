package com.skilles.chronoclones.menu.client;

final class CloneTabs {

    private CloneTabs() {}

    static final int WIDTH = 12;
    static final int HEIGHT = 10;
    static final int GAP = 1;

    static int count(int activeClones) {
        return activeClones > 1 ? activeClones : 0;
    }

    static int stripWidth(int tabs) {
        return tabs <= 0 ? 0 : tabs * WIDTH + (tabs - 1) * GAP;
    }

    static int xOf(int tab, int tabs, int rightEdge) {
        return rightEdge - stripWidth(tabs) + tab * (WIDTH + GAP);
    }

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
