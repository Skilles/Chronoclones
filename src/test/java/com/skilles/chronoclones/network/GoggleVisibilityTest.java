package com.skilles.chronoclones.network;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who the goggles are allowed to show you.
 *
 * <p>What a routine does is information about the person who imprinted it — where they mine, what
 * they farm, which chests they use. Whether that is visible to a passer-by wearing goggles is a
 * server owner's decision, so it is a config, and the rule that reads it is asserted here rather
 * than left implicit in a loop over chunks.
 */
class GoggleVisibilityTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SOMEONE_ELSE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    @DisplayName("your own anchors are always visible")
    void ownAnchorsAlwaysVisible() {
        // The config is about other people's routines. Hiding your own would make the goggles
        // useless on the only anchors you can do anything about.
        assertTrue(GogglePayloads.visibleTo(VIEWER, VIEWER, true));
        assertTrue(GogglePayloads.visibleTo(VIEWER, VIEWER, false));
    }

    @Test
    @DisplayName("someone else's anchor is hidden when the config says so")
    void othersHiddenWhenConfigured() {
        assertTrue(GogglePayloads.visibleTo(SOMEONE_ELSE, VIEWER, true));
        assertFalse(GogglePayloads.visibleTo(SOMEONE_ELSE, VIEWER, false),
                "with gogglesShowOthers off, another player's routine must not be sent at all");
    }

    @Test
    @DisplayName("an unowned anchor is visible either way")
    void unownedIsVisible() {
        // No owner means it was never imprinted by anybody, so there is nobody it could be private
        // from — and refusing it would hide anchors placed by a datapack or spawned in a structure.
        assertTrue(GogglePayloads.visibleTo(null, VIEWER, true));
        assertTrue(GogglePayloads.visibleTo(null, VIEWER, false));
    }
}
