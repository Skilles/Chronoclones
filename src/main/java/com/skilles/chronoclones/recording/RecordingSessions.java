package com.skilles.chronoclones.recording;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Active recording sessions, keyed by player.
 */
public final class RecordingSessions {

    private static final Map<UUID, RecordingSession> ACTIVE = new ConcurrentHashMap<>();

    private RecordingSessions() {}

    /**
     * A new session starts from nothing watched: whatever a previous one left open belonged to a
     * recording that is over, and its clicks must not be inherited by this one.
     */
    public static RecordingSession start(ServerPlayer player) {
        ContainerWatch.forget(player);
        RecordingSession session = new RecordingSession(player);
        ACTIVE.put(player.getUUID(), session);
        return session;
    }

    public static @Nullable RecordingSession get(ServerPlayer player) {
        return isReal(player) ? ACTIVE.get(player.getUUID()) : null;
    }

    public static boolean isRecording(ServerPlayer player) {
        return isReal(player) && ACTIVE.containsKey(player.getUUID());
    }

    public static @Nullable RecordingSession end(ServerPlayer player) {
        return isReal(player) ? ACTIVE.remove(player.getUUID()) : null;
    }

    public static void discard(ServerPlayer player) {
        if (isReal(player)) {
            ACTIVE.remove(player.getUUID());
        }
    }

    /** For events that belong to no one player, such as something dying near two recorders. */
    public static void forEach(java.util.function.Consumer<RecordingSession> action) {
        ACTIVE.values().forEach(action);
    }

    /**
     * Whether this player is the human the session belongs to, rather than something acting in their
     * name.
     */
    private static boolean isReal(ServerPlayer player) {
        return !player.isFakePlayer();
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
