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
import org.jspecify.annotations.NonNull;

public final class AnchorPreviewPayloads {

    private AnchorPreviewPayloads() {}

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

    public record Reply(BlockPos pos, Optional<Recording> recording, DiagnosticState failure,
                        BlockPos originOffset, int rotationSteps) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Reply> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("anchor_preview"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC.cast(), Reply::pos,
                        ByteBufCodecs.optional(RecordingCodecs.RECORDING_STREAM), Reply::recording,
                        ByteBufCodecs.fromCodec(DiagnosticState.CODEC).cast(), Reply::failure,
                        BlockPos.STREAM_CODEC.cast(), Reply::originOffset,
                        ByteBufCodecs.VAR_INT.cast(), Reply::rotationSteps,
                        Reply::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handleRequest(Request request, ServerPlayer player) {
        BlockPos pos = request.pos();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > MAX_REQUEST_DISTANCE_SQR) {
            return;
        }
        if (!player.level().isLoaded(pos)
                || !(player.level().getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor)) {
            return;
        }

        com.skilles.chronoclones.platform.PlatformNetwork.sendToPlayer(player,
                new Reply(pos, Optional.ofNullable(anchor.getRecording()),
                        anchor.getLastFailure(), anchor.getOriginOffset(),
                        anchor.getRotationSteps()));
    }

    private static final double MAX_REQUEST_DISTANCE_SQR = 12.0 * 12.0;
}
