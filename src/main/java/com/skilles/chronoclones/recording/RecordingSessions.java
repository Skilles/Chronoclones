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
 *
 * <p><b>Fake players never resolve a session</b>, and that is not a nicety. Anchors act through a
 * fake player carrying the <em>owner's</em> UUID, so while a player records, every
 * block their own anchors break arrives on the event bus under the identity they are recording as.
 * A map keyed by UUID alone cannot tell the two apart, and the result is a recording that quietly
 * absorbs its own clones — each replay feeding the next routine, compounding. The key is the
 * identity; the filter below is what makes the identity mean "the human at the keyboard".
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

    /**
     * Whether this player is the human the session belongs to, rather than something acting in their
     * name.
     *
     * <p>Uses NeoForge's {@code isFakePlayer()} rather than {@code instanceof FakePlayer}: it is the
     * declared contract, and other mods' automation players override it without extending NeoForge's
     * class. Anything acting on a player's behalf — our anchors, a neighbouring mod's autominer —
     * is excluded on the same footing.
     */
    private static boolean isReal(ServerPlayer player) {
        return !player.isFakePlayer();
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
