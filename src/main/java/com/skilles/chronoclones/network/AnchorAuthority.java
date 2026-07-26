package com.skilles.chronoclones.network;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Who may change how somebody's anchor behaves.
 *
 * <p>One rule, in one place, because two payloads need it and they must not drift: nudging a routine
 * and retuning its precision are the same kind of act, and an anchor that refuses one while allowing
 * the other is protecting nothing. Both are worse than breaking the anchor would be — it keeps
 * running, and it is the owner's name on every block it touches.
 *
 * <p>Its own class so it can be asserted without a world, a player and two open menus, and so that
 * the rule is a thing with a name rather than a condition repeated in two handlers.
 */
public final class AnchorAuthority {

    private AnchorAuthority() {}

    /**
     * Whether {@code actor} may retune the anchor owned by {@code ownerId}.
     *
     * <p>An anchor nobody has imprinted has no owner yet, and aiming one before committing to it is
     * the whole point of being able to set it up early. Once imprinted, only the owner.
     */
    public static boolean mayRetune(@Nullable UUID ownerId, UUID actor) {
        return ownerId == null || ownerId.equals(actor);
    }
}
