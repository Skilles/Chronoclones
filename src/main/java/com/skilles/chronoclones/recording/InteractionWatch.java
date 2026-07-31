package com.skilles.chronoclones.recording;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Holds an interaction between the click that started it and the answer it got.
 *
 * <p>The events a mod can hear -- {@code RightClickBlock}, {@code RightClickItem},
 * {@code EntityInteract} -- all fire <em>before</em> the interaction happens. They say what was
 * attempted, and nothing at all about whether it worked. Recording from them meant a routine
 * captured intent: eating while full, clicking a block that ignored you, a main hand that passed
 * before the off hand did the actual work, all recorded as things to replay forever.
 *
 * <p>So a click arms an interaction here, and it is written into the recording only once something
 * has said it happened -- the vanilla method returning a result that consumed the action, a block
 * actually being placed, or a menu actually opening. Every one of those settles the interaction one
 * way or the other, and anything still armed at the end of the tick is discarded.
 *
 * @see com.skilles.chronoclones.mixin.UseItemOnMixin for where the answers come from
 */
public final class InteractionWatch {

    private InteractionWatch() {}

    /**
     * A click waiting to find out whether it did anything.
     *
     * <p>The action is built up front, while the world still looks the way the player found it: a
     * bucket emptied into a block has already changed what it was used on by the time the result
     * comes back.
     */
    private static final class Pending {

        private final long tick;
        private final InteractionHand hand;
        private final ChronoAction action;
        private final Vec3 worldPos;

        /** Set once something else has taken responsibility for this click. */
        private boolean claimed;

        Pending(long tick, InteractionHand hand, ChronoAction action, Vec3 worldPos) {
            this.tick = tick;
            this.hand = hand;
            this.action = action;
            this.worldPos = worldPos;
        }
    }

    private static final Map<UUID, Pending> ARMED = new ConcurrentHashMap<>();

    /**
     * Notes a click, to be written down only if it turns out to have done something.
     */
    public static void arm(ServerPlayer player, InteractionHand hand, ChronoAction action,
                           Vec3 worldPos) {
        ARMED.put(player.getUUID(), new Pending(now(player), hand, action, worldPos));
    }

    /**
     * Takes responsibility for the armed click, so it is not also recorded as an interaction.
     *
     * <p>Used by the two things that are a better description of what happened than "an interaction
     * succeeded": a block being placed, and a menu opening. Both of those record something of their
     * own, and the click that caused them is that thing rather than a second action beside it.
     */
    public static void claim(ServerPlayer player) {
        Pending pending = ARMED.get(player.getUUID());
        if (pending != null) {
            pending.claimed = true;
        }
    }

    /** What the armed click was, or null if nothing is armed. */
    public static @Nullable ChronoAction armedAction(ServerPlayer player) {
        Pending pending = ARMED.get(player.getUUID());
        return pending == null ? null : pending.action;
    }

    /**
     * The interaction returned. Writes it down if it did something, and forgets it either way.
     *
     * <p>Only a result that consumed the action counts. A pass means this hand did nothing and the
     * off hand may be about to; a fail means the item refused, which is exactly the case that used
     * to be recorded as a thing worth repeating.
     */
    public static void settle(ServerPlayer player, InteractionHand hand, InteractionResult result) {
        Pending pending = ARMED.get(player.getUUID());
        if (pending == null) {
            return;
        }
        // The same click: a different hand is a different call, and will have armed its own.
        if (pending.hand != hand || pending.tick != now(player)) {
            return;
        }
        ARMED.remove(player.getUUID());

        if (pending.claimed || !result.consumesAction()) {
            return;
        }
        RecordingSession session = RecordingSessions.get(player);
        if (session != null) {
            RecordingCapture.commit(player, session, pending.action, pending.worldPos);
        }
    }

    /**
     * Drops anything still armed, which is a click whose result never came back.
     *
     * <p>Run at the end of every tick a session is running, because an interaction that is not
     * settled by the method that started it is one nothing will ever settle.
     */
    public static void expire(ServerPlayer player) {
        Pending pending = ARMED.get(player.getUUID());
        if (pending != null && pending.tick != now(player)) {
            ARMED.remove(player.getUUID());
        }
    }

    public static void forget(ServerPlayer player) {
        ARMED.remove(player.getUUID());
    }

    public static void clear() {
        ARMED.clear();
    }

    private static long now(ServerPlayer player) {
        return player.level().getGameTime();
    }
}
