package com.skilles.chronoclones.menu.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the precision drawer opens.
 *
 * <p>Worth asserting because the failure is invisible from the code and total from the player's
 * side: a drawer that opens past the edge of the window is a settings panel that cannot be reached,
 * and the anchor screen is centred, so which side has room depends on the GUI scale the player
 * happens to be using rather than on anything this code can see.
 *
 * <p>The window is 176 wide, so the numbers below are chosen around that: a centred window on a
 * 400px screen leaves 112 either side, which is enough for the drawer's 104 plus its 10px tab.
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
        // Wide enough on the left, deliberately one pixel short on the right.
        int leftPos = NEEDED;
        int screen = leftPos + WINDOW + NEEDED - 1;
        assertTrue(DrawerLayout.opensLeft(screen, leftPos, WINDOW),
                "the drawer would have opened off the right edge, where nobody can click it");
    }

    @Test
    @DisplayName("with room on neither side it takes the larger")
    void takesTheLargerWhenNeitherFits() {
        // Both sides clipped, so the choice is only about clipping less. Left has 40, right has 20.
        int leftPos = 40;
        int screen = leftPos + WINDOW + 20;
        assertTrue(DrawerLayout.opensLeft(screen, leftPos, WINDOW));

        // And the mirror image, so this is not passing by always answering left.
        assertFalse(DrawerLayout.opensLeft(20 + WINDOW + 40, 20, WINDOW));
    }

    @Test
    @DisplayName("the tab sits against the window on whichever side it opens")
    void tabHugsTheWindow() {
        assertEquals(100 + WINDOW, DrawerLayout.tabX(false, 100, WINDOW));
        assertEquals(100 - DrawerLayout.TAB_WIDTH, DrawerLayout.tabX(true, 100, WINDOW));
    }

    @Test
    @DisplayName("opening right grows away from a fixed edge; opening left moves the edge")
    void bodyGrowsAwayFromTheTab() {
        // The asymmetry is the whole point. Rightwards, the body's left edge is where the tab ends
        // and stays there while the panel widens. Leftwards, the panel's right edge is pinned
        // instead, so its left edge is what moves — mirroring the first case would have made a
        // left-hand drawer slide out from under the window rather than away from it.
        assertEquals(100 + WINDOW + DrawerLayout.TAB_WIDTH,
                DrawerLayout.bodyX(false, 100, WINDOW, 0));
        assertEquals(100 + WINDOW + DrawerLayout.TAB_WIDTH,
                DrawerLayout.bodyX(false, 100, WINDOW, DrawerLayout.WIDTH));

        assertEquals(100 - DrawerLayout.TAB_WIDTH, DrawerLayout.bodyX(true, 100, WINDOW, 0));
        assertEquals(100 - DrawerLayout.TAB_WIDTH - DrawerLayout.WIDTH,
                DrawerLayout.bodyX(true, 100, WINDOW, DrawerLayout.WIDTH));
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

    @Test
    @DisplayName("at the narrowest window the game allows, nothing fits either side")
    void theFloorIsInherentlyClipped() {
        // Minecraft clamps the GUI scale so the effective width bottoms out around 320, where a
        // centred window leaves 72 a side. Recorded rather than worked around: the clipping there is
        // arithmetic, not a bug to chase, and the sides are exactly equal so which one it picks
        // carries no information. If narrowing the drawer ever makes this pass, this test has done
        // its job and should go — along with the caveat in fullyOpenFitsOnScreen.
        int screen = 320;
        int leftPos = centred(screen);
        int roomRight = screen - (leftPos + WINDOW);

        assertTrue(NEEDED > leftPos, "the left now fits — the floor moved, so revisit both tests");
        assertTrue(NEEDED > roomRight, "the right now fits — the floor moved, so revisit both tests");
    }
}
