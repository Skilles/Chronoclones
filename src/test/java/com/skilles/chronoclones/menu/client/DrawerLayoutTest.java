package com.skilles.chronoclones.menu.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the precision drawer opens.
 */
class DrawerLayoutTest {

    private static final int WINDOW = 176;
    private static final int NEEDED = DrawerLayout.WIDTH + DrawerLayout.TAB_WIDTH;

    /** Where the window lands, given the game's own centring. */
    private static int centred(int screenWidth) {
        return (screenWidth - WINDOW) / 2;
    }

    @Test
    @DisplayName("it opens right whenever the right has room")
    void prefersTheRight() {
        int screen = 640;
        assertFalse(DrawerLayout.opensLeft(screen, centred(screen), WINDOW));
    }

    @Test
    @DisplayName("it opens left when the right would run off the edge")
    void flipsWhenTheRightIsShort() {
        // Wide enough on the left, one pixel short on the right.
        int leftPos = NEEDED;
        int screen = leftPos + WINDOW + NEEDED - 1;
        assertTrue(DrawerLayout.opensLeft(screen, leftPos, WINDOW),
                "the drawer would have opened off the right edge, where nobody can click it");
    }

    @Test
    @DisplayName("with room on neither side it takes the larger")
    void takesTheLargerWhenNeitherFits() {
        // Both sides clipped, so the choice is only about clipping less.
        int leftPos = 40;
        int screen = leftPos + WINDOW + 20;
        assertTrue(DrawerLayout.opensLeft(screen, leftPos, WINDOW));

        // And the mirror image, so this is not passing by always answering left.
        assertFalse(DrawerLayout.opensLeft(20 + WINDOW + 40, 20, WINDOW));
    }

    @Test
    @DisplayName("the drawer never slides across the window, at any width, on either side")
    void bodyNeverCoversTheWindow() {
        // The asymmetry this guards is easy to get wrong and invisible in the code. Opening right,
        // the body's left edge is where the tab ends and stays put as the panel widens. Opening
        // left, the body's *right* edge is what is pinned, so its left edge moves. Writing
        // that as a mirror of the first case makes a left-hand drawer grow out from under the
        // window, over the slots, instead of away from it.
        int leftPos = 300;
        for (boolean onLeft : new boolean[] {false, true}) {
            for (int open = 0; open <= DrawerLayout.WIDTH; open++) {
                int bodyX = DrawerLayout.bodyX(onLeft, leftPos, WINDOW, open);
                boolean overlaps = bodyX < leftPos + WINDOW && bodyX + open > leftPos;
                assertFalse(overlaps, "drawer at width " + open + (onLeft ? " (left)" : " (right)")
                        + " covers the window: body " + bodyX + ".." + (bodyX + open)
                        + ", window " + leftPos + ".." + (leftPos + WINDOW));
            }
        }
    }

    @Test
    @DisplayName("a fully open drawer stays on screen at every ordinary window width")
    void fullyOpenFitsOnScreen() {
        // 400 is the interesting one and the reason the drawer is as narrow as it is: the window is
        // centred, so a centred 176 leaves 112 either side and the drawer plus its tab has to live
        // inside that. Anything wider is easy.
        for (int screen : new int[] {400, 640, 854, 1280, 1920}) {
            int leftPos = centred(screen);
            boolean onLeft = DrawerLayout.opensLeft(screen, leftPos, WINDOW);
            int bodyX = DrawerLayout.bodyX(onLeft, leftPos, WINDOW, DrawerLayout.WIDTH);

            assertTrue(bodyX >= 0, "drawer starts off the left edge at width " + screen);
            assertTrue(bodyX + DrawerLayout.WIDTH <= screen,
                    "drawer runs past the right edge at width " + screen);
        }
    }

}
