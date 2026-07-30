package com.skilles.chronoclones.block;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A clone's experience bank, whose readings have to agree with the bar a player already knows.
 */
class ExperienceStoreTest {

    @Test
    @DisplayName("nothing banked is level zero at the start of the bar")
    void emptyIsLevelZero() {
        assertEquals(0, ExperienceStore.EMPTY.level());
        assertEquals(0.0f, ExperienceStore.EMPTY.progress());
        assertTrue(ExperienceStore.EMPTY.isEmpty());
    }

    @Test
    @DisplayName("the level boundaries are vanilla's own")
    void levelsMatchVanilla() {
        // Vanilla's first three bands are 7, 9 and 11 points wide.
        assertEquals(0, ExperienceStore.levelOf(6));
        assertEquals(1, ExperienceStore.levelOf(7));
        assertEquals(1, ExperienceStore.levelOf(15));
        assertEquals(2, ExperienceStore.levelOf(16));
        assertEquals(3, ExperienceStore.levelOf(27));
    }

    @Test
    @DisplayName("a level boundary sits at the very start of the bar, never at the end")
    void progressResetsAtEachLevel() {
        assertEquals(0.0f, ExperienceStore.progressOf(7), "level 1 exactly should be an empty bar");
        assertEquals(0.0f, ExperienceStore.progressOf(16));

        float part = ExperienceStore.progressOf(11);
        assertTrue(part > 0.0f && part < 1.0f, "four points into a nine-point band, got " + part);
    }

    @Test
    @DisplayName("the bands widen at fifteen and again at thirty, as vanilla's do")
    void bandsWidenWithLevel() {
        assertEquals(7, ExperienceStore.neededForNextLevel(0));
        assertEquals(37, ExperienceStore.neededForNextLevel(15));
        assertEquals(112, ExperienceStore.neededForNextLevel(30));
    }

    @Test
    @DisplayName("points for levels is the sum of the bands below it")
    void pointsForLevelsSumsTheBands() {
        assertEquals(0, ExperienceStore.pointsForLevels(0));
        assertEquals(7, ExperienceStore.pointsForLevels(1));
        assertEquals(7 + 9, ExperienceStore.pointsForLevels(2));
        assertEquals(ExperienceStore.pointsForLevels(3),
                ExperienceStore.pointsForLevels(2) + ExperienceStore.neededForNextLevel(2));
    }

    @Test
    @DisplayName("adding and spending never take the bank below nothing")
    void bankNeverGoesNegative() {
        ExperienceStore store = ExperienceStore.EMPTY.add(20);

        assertEquals(20, store.points());
        assertEquals(0, store.spend(50).points(), "spending more than it holds should empty it");
        assertEquals(20, store.add(-5).points(), "a negative gain is not a spend");
        assertEquals(0, new ExperienceStore(-1).points());
    }

    @Test
    @DisplayName("a bar reading converts back to the points behind it")
    void pointsSurviveABarReading() {
        // The fake player only exposes a level and a fraction, so this is how a clone's bank is read
        // back after an anvil or a table has charged it.
        for (int points = 0; points < 400; points++) {
            int level = ExperienceStore.levelOf(points);
            assertEquals(points, ExperienceStore.pointsFor(level, ExperienceStore.progressOf(points)),
                    "reading back " + points + " points at level " + level);
        }
    }

    @Test
    @DisplayName("affording is asked in levels, because that is what enchanting costs")
    void affordingIsInLevels() {
        ExperienceStore store = new ExperienceStore(ExperienceStore.pointsForLevels(3));

        assertTrue(store.canAfford(3));
        assertFalse(store.canAfford(4));
    }
}
