package com.skilles.chronoclones.network;

import java.util.Optional;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingCodecs;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * Preview traffic: a routine sent to one client, on request, for as long as they are looking at it.
 */
public final class AnchorPreviewPayloads {

    private AnchorPreviewPayloads() {}

    /** Client → server: "I am looking at the anchor here, what does it do?" */
    public record Request(BlockPos pos) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Request> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("request_preview"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC.cast(), Request::pos, Request::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: the routine, or empty if that anchor has none.
     */
    public record Reply(BlockPos pos, Optional<Recording> recording, DiagnosticState failure,
                        BlockPos originOffset) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Reply> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("anchor_preview"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC.cast(), Reply::pos,
                        ByteBufCodecs.optional(RecordingCodecs.RECORDING_STREAM), Reply::recording,
                        ByteBufCodecs.fromCodec(DiagnosticState.CODEC).cast(), Reply::failure,
                        BlockPos.STREAM_CODEC.cast(), Reply::originOffset,
                        Reply::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Answers a request, if the player could plausibly be looking at that anchor.
     */
    public static void handleRequest(Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = request.pos();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_REQUEST_DISTANCE_SQR) {
            return;
        }
        if (!player.level().isLoaded(pos)
                || !(player.level().getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor)) {
            return;
        }

        context.reply(new Reply(pos, Optional.ofNullable(anchor.getRecording()),
                anchor.getLastFailure(), anchor.getOriginOffset()));
    }

    /** A little beyond any reasonable reach, so a laggy client is not refused its own preview. */
    private static final double MAX_REQUEST_DISTANCE_SQR = 12.0 * 12.0;
}
