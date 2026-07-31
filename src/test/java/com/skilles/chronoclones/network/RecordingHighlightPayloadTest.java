package com.skilles.chronoclones.network;

import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordingHighlightPayloadTest {

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void captureRegistries() {
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static RecordingHighlightPayload roundTrip(RecordingHighlightPayload original) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        RecordingHighlightPayload.STREAM_CODEC.encode(buf, original);
        return RecordingHighlightPayload.STREAM_CODEC.decode(buf);
    }

    @Test
    @DisplayName("touched and carried survive the wire, and stay apart")
    void survivesTheWire() {
        RecordingHighlightPayload original =
                new RecordingHighlightPayload(7, List.of(0, 4, 31, 60), List.of(60));

        RecordingHighlightPayload decoded = roundTrip(original);

        assertEquals(7, decoded.containerId());
        assertEquals(List.of(0, 4, 31, 60), decoded.touched());
        assertEquals(List.of(60), decoded.carried());
    }

    @Test
    @DisplayName("the clear sent when a watch ends survives too")
    void clearSurvives() {
        RecordingHighlightPayload decoded =
                roundTrip(new RecordingHighlightPayload(-1, List.of(), List.of()));

        assertEquals(-1, decoded.containerId());
        assertEquals(List.of(), decoded.touched());
        assertEquals(List.of(), decoded.carried());
    }
}
