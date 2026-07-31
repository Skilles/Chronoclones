package com.skilles.chronoclones.network;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoggleVisibilityTest {

    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID SOMEONE_ELSE = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Test
    @DisplayName("your own anchors are always visible")
    void ownAnchorsAlwaysVisible() {
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
        assertTrue(GogglePayloads.visibleTo(null, VIEWER, true));
        assertTrue(GogglePayloads.visibleTo(null, VIEWER, false));
    }
}
