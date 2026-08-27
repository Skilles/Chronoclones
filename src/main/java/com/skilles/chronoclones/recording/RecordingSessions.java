package com.skilles.chronoclones.recording;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

public final class RecordingSessions {

    private static final Map<UUID, RecordingSession> ACTIVE = new ConcurrentHashMap<>();

    private RecordingSessions() {}

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

    public static void forEach(java.util.function.Consumer<RecordingSession> action) {
        ACTIVE.values().forEach(action);
    }

    private static boolean isReal(ServerPlayer player) {
        return !com.skilles.chronoclones.platform.ClonePlayer.isFake(player);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
