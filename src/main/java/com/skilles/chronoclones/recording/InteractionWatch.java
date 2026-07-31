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
 * Holds an interaction between the click that started it and the result it got.
 *
 * <p>The interaction events all fire before the work happens, so they report intent. The
 * vanilla methods settle it on their way out.
 */
public final class InteractionWatch {

    private InteractionWatch() {}

    private static final class Pending {

        private final long tick;
        private final InteractionHand hand;
        private final ChronoAction action;
        private final Vec3 worldPos;

        private boolean claimed;

        Pending(long tick, InteractionHand hand, ChronoAction action, Vec3 worldPos) {
            this.tick = tick;
            this.hand = hand;
            this.action = action;
            this.worldPos = worldPos;
        }
    }

    private static final Map<UUID, Pending> ARMED = new ConcurrentHashMap<>();

    public static void arm(ServerPlayer player, InteractionHand hand, ChronoAction action,
                           Vec3 worldPos) {
        ARMED.put(player.getUUID(), new Pending(now(player), hand, action, worldPos));
    }

    public static void claim(ServerPlayer player) {
        Pending pending = ARMED.get(player.getUUID());
        if (pending != null) {
            pending.claimed = true;
        }
    }

    public static @Nullable ChronoAction armedAction(ServerPlayer player) {
        Pending pending = ARMED.get(player.getUUID());
        return pending == null ? null : pending.action;
    }

    public static void settle(ServerPlayer player, InteractionHand hand, InteractionResult result) {
        Pending pending = ARMED.get(player.getUUID());
        if (pending == null) {
            return;
        }
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
