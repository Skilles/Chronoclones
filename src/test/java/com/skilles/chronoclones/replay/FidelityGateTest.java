package com.skilles.chronoclones.replay;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import com.skilles.chronoclones.recording.ChronoActionType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fidelity ladder is a progression gate, not a cosmetic one: it keeps combat behind it so
 * an early-game anchor can only mine and build. These tests pin the ladder so a reordering of the
 * enum cannot quietly hand a fresh anchor the ability to attack.
 */
class FidelityGateTest {

    private static Set<ChronoActionType> permittedAt(int tier) {
        Set<ChronoActionType> permitted = EnumSet.noneOf(ChronoActionType.class);
        for (ChronoActionType type : ChronoActionType.values()) {
            if (type.permittedAt(tier)) {
                permitted.add(type);
            }
        }
        return permitted;
    }

    @Test
    @DisplayName("a fresh anchor can only break blocks")
    void tierZeroIsBreakOnly() {
        assertTrue(permittedAt(0).equals(EnumSet.of(ChronoActionType.BREAK_BLOCK)),
                "tier 0 permitted: " + permittedAt(0));
    }

    @Test
    @DisplayName("placing unlocks before combat does")
    void placingUnlocksBeforeCombat() {
        Set<ChronoActionType> tierOne = permittedAt(1);

        assertTrue(tierOne.contains(ChronoActionType.PLACE_BLOCK));
        assertFalse(tierOne.contains(ChronoActionType.ATTACK_ENTITY),
                "combat must stay locked at the placing tier");
    }

    @Test
    @DisplayName("combat unlocks at tier 2")
    void combatUnlocksAtTierTwo() {
        assertTrue(permittedAt(2).contains(ChronoActionType.ATTACK_ENTITY));
        assertFalse(permittedAt(2).contains(ChronoActionType.USE_ITEM));
    }

    @Test
    @DisplayName("the top tier permits everything")
    void topTierPermitsEverything() {
        assertTrue(permittedAt(3).containsAll(EnumSet.allOf(ChronoActionType.class)));
    }

    @Test
    @DisplayName("permissions only ever widen as the tier rises")
    void permissionsAreMonotonic() {
        for (int tier = 0; tier < 4; tier++) {
            assertTrue(permittedAt(tier + 1).containsAll(permittedAt(tier)),
                    "tier " + (tier + 1) + " lost a permission that tier " + tier + " had");
        }
    }

    @Test
    @DisplayName("a negative tier permits nothing, so a corrupt value cannot unlock actions")
    void negativeTierPermitsNothing() {
        assertTrue(permittedAt(-1).isEmpty());
    }
}
