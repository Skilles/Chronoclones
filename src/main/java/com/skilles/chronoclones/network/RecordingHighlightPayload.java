package com.skilles.chronoclones.network;

import java.util.List;

import com.skilles.chronoclones.Chronoclones;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: which slots of the open container the recording has picked up so far.
 *
 * <p>Sent rather than worked out on the client, because the client's view of a click is a guess. It
 * does not know whether this container is being recorded at all (an anchor's own menu is skipped),
 * which player slots the session will end up demanding, or how a swap's hotbar button resolves. The
 * point of showing this is to see what the recording <em>actually</em> captured; a client-side
 * approximation of that would be worse than useless, because it would be believable.
 *
 * <p>Two lists rather than one because they answer different questions. {@code touched} is what the
 * routine reaches for; {@code carried} is the subset the anchor will have to be stocked with, and a
 * session that quietly demands half your inventory is the failure this makes visible.
 *
 * @param containerId the menu these refer to, so a highlight cannot outlive the screen it describes
 */
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
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
