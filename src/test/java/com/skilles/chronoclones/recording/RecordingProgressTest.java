package com.skilles.chronoclones.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.skilles.chronoclones.registry.RecordingProgress;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression cover for the session-binding rule.
 */
class RecordingProgressTest {

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void captureRegistries() {
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    @DisplayName("the progress stamp carries a session id, so it can be matched to one recorder")
    void stampCarriesSessionId() {
        UUID session = UUID.randomUUID();
        RecordingProgress progress = new RecordingProgress(session, 40, 3, false);
        assertEquals(session, progress.sessionId());
    }

    @Test
    @DisplayName("stamps from different sessions never compare equal")
    void differentSessionsAreDistinguishable() {
        RecordingProgress a = new RecordingProgress(UUID.randomUUID(), 40, 3, false);
        RecordingProgress b = new RecordingProgress(UUID.randomUUID(), 40, 3, false);

        assertNotEquals(a.sessionId(), b.sessionId());
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("session ids are unique across many sessions")
    void sessionIdsAreUnique() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(seen.add(UUID.randomUUID()), "duplicate session id at " + i);
        }
    }

    @Test
    @DisplayName("the stamp round trips through its codec with the session id intact")
    void roundTripsThroughCodec() {
        RecordingProgress original = new RecordingProgress(UUID.randomUUID(), 123, 7, true);

        var encoded = RecordingProgress.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError(msg));
        RecordingProgress decoded = RecordingProgress.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError(msg));

        assertEquals(original, decoded);
        assertEquals(original.sessionId(), decoded.sessionId());
    }

    @Test
    @DisplayName("the stamp round trips over the network with the session id intact")
    void roundTripsThroughStreamCodec() {
        RecordingProgress original = new RecordingProgress(UUID.randomUUID(), 600, 128, true);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        RecordingProgress.STREAM_CODEC.encode(buf, original);
        RecordingProgress decoded = RecordingProgress.STREAM_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes(), "stream codec left bytes unread");
        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("out-of-range warning survives serialization, since it drives the HUD")
    void warningFlagSurvives() {
        RecordingProgress warned = new RecordingProgress(UUID.randomUUID(), 10, 1, true);

        var encoded = RecordingProgress.CODEC.encodeStart(JsonOps.INSTANCE, warned)
                .getOrThrow(msg -> new AssertionError(msg));
        RecordingProgress decoded = RecordingProgress.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError(msg));

        assertTrue(decoded.outOfRangeWarning());
    }
}
