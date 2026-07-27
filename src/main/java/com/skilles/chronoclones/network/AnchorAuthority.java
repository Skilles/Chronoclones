package com.skilles.chronoclones.network;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Who may change how somebody's anchor behaves.
 */
public final class AnchorAuthority {

    private AnchorAuthority() {}

    /**
     * Whether {@code actor} may retune the anchor owned by {@code ownerId}.
     */
    public static boolean mayRetune(@Nullable UUID ownerId, UUID actor) {
        return ownerId == null || ownerId.equals(actor);
    }
}
