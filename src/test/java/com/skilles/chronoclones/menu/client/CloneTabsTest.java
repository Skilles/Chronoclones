package com.skilles.chronoclones.menu.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloneTabsTest {

    private static final int RIGHT_EDGE = 168;
    private static final int TOP = 28;

    @Test
    @DisplayName("a lone clone gets no tabs")
    void oneCloneHasNoTabs() {
        assertEquals(0, CloneTabs.count(1));
        assertEquals(0, CloneTabs.count(0));
        assertEquals(0, CloneTabs.stripWidth(CloneTabs.count(1)));
    }

    @Test
    @DisplayName("one tab per clone once there is a choice")
    void oneTabPerClone() {
        assertEquals(2, CloneTabs.count(2));
        assertEquals(4, CloneTabs.count(4));
    }

    @Test
    @DisplayName("the strip is right-aligned and leaves the row's left to the readout")
    void stripIsRightAligned() {
        int tabs = CloneTabs.count(4);
        assertEquals(RIGHT_EDGE, CloneTabs.xOf(tabs - 1, tabs, RIGHT_EDGE) + CloneTabs.WIDTH,
                "the last tab does not end at the right edge");

        assertTrue(CloneTabs.xOf(0, tabs, RIGHT_EDGE) > 80,
                "four tabs reach back across the readout");
    }

    @Test
    @DisplayName("a click finds the tab it landed on")
    void clickFindsItsTab() {
        int tabs = CloneTabs.count(3);
        for (int tab = 0; tab < tabs; tab++) {
            int x = CloneTabs.xOf(tab, tabs, RIGHT_EDGE);
            assertEquals(tab, CloneTabs.at(x, TOP, tabs, RIGHT_EDGE, TOP));
            assertEquals(tab, CloneTabs.at(x + CloneTabs.WIDTH - 1, TOP + CloneTabs.HEIGHT - 1,
                    tabs, RIGHT_EDGE, TOP));
        }
    }

    @Test
    @DisplayName("a click off the strip selects nothing")
    void clickOffTheStripSelectsNothing() {
        int tabs = CloneTabs.count(3);
        int first = CloneTabs.xOf(0, tabs, RIGHT_EDGE);

        assertEquals(-1, CloneTabs.at(first - 1, TOP, tabs, RIGHT_EDGE, TOP), "left of the strip");
        assertEquals(-1, CloneTabs.at(RIGHT_EDGE, TOP, tabs, RIGHT_EDGE, TOP), "right of the strip");
        assertEquals(-1, CloneTabs.at(first, TOP - 1, tabs, RIGHT_EDGE, TOP), "above the row");
        assertEquals(-1, CloneTabs.at(first, TOP + CloneTabs.HEIGHT, tabs, RIGHT_EDGE, TOP),
                "below the row");
        assertEquals(-1, CloneTabs.at(first, TOP, 0, RIGHT_EDGE, TOP), "with no tabs drawn");
    }
}
