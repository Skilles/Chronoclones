package com.skilles.chronoclones.network;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnchorAuthorityTest {

    private static final UUID OWNER = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
    private static final UUID STRANGER = UUID.fromString("c0000000-0000-0000-0000-00000000000c");

    @Test
    @DisplayName("the owner may retune their own anchor")
    void ownerMayRetune() {
        assertTrue(AnchorAuthority.mayRetune(OWNER, OWNER));
    }

    @Test
    @DisplayName("nobody else may")
    void strangersMayNot() {
        assertFalse(AnchorAuthority.mayRetune(OWNER, STRANGER));
    }

    @Test
    @DisplayName("an anchor nobody has imprinted is open to whoever is standing at it")
    void unownedIsOpen() {
        assertTrue(AnchorAuthority.mayRetune(null, STRANGER));
    }
}
