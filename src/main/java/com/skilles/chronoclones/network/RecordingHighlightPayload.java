package com.skilles.chronoclones.network;

import java.util.List;

import com.skilles.chronoclones.Chronoclones;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

public record RecordingHighlightPayload(int containerId, List<Integer> touched, List<Integer> carried)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecordingHighlightPayload> TYPE =
            new CustomPacketPayload.Type<>(Chronoclones.id("recording_highlight"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Integer>> SLOTS =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).cast();

    public static final StreamCodec<RegistryFriendlyByteBuf, RecordingHighlightPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.cast(), RecordingHighlightPayload::containerId,
                    SLOTS, RecordingHighlightPayload::touched,
                    SLOTS, RecordingHighlightPayload::carried,
                    RecordingHighlightPayload::new);

    public RecordingHighlightPayload {
        touched = List.copyOf(touched);
        carried = List.copyOf(carried);
    }

    @Override
    public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
