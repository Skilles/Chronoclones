package com.skilles.chronoclones.recording;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Active recording sessions, keyed by player.
 *
 * <p>Server-side only. Sessions are intentionally not persisted: a recording interrupted by logout,
 * death, or a dimension change is discarded rather than resumed, which avoids a whole class of
 * "where did my origin go" bugs for a feature nobody asked for.
 */
public final class RecordingSessions {

    private static final Map<UUID, RecordingSession> ACTIVE = new ConcurrentHashMap<>();

    private RecordingSessions() {}

    public static RecordingSession start(ServerPlayer player) {
        RecordingSession session = new RecordingSession(player);
        ACTIVE.put(player.getUUID(), session);
        return session;
    }

    public static @Nullable RecordingSession get(ServerPlayer player) {
        return ACTIVE.get(player.getUUID());
    }

    public static boolean isRecording(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    public static @Nullable RecordingSession end(ServerPlayer player) {
        return ACTIVE.remove(player.getUUID());
    }

    public static void discard(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
