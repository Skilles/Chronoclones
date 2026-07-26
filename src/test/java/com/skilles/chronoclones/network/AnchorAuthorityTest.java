package com.skilles.chronoclones.network;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may retune somebody else's anchor: nobody.
 *
 * <p>This is a security rule rather than a convenience one, and it fails quietly in both directions.
 * Too strict and a fresh anchor cannot be aimed before it is imprinted, which is the one time aiming
 * it is easiest. Too loose and anyone who can walk up to your farm can redirect it — and because the
 * anchor keeps running under its owner's identity, the damage arrives with your name on it.
 */
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
        // Deliberate, not an oversight. Aiming and tuning an anchor before committing a routine to
        // it is the point of being able to do either early, and there is no one yet to protect it
        // from — ownership arrives with the imprint.
        assertTrue(AnchorAuthority.mayRetune(null, STRANGER));
    }
}
