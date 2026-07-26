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

/**
 * Preview traffic: a routine sent to one client, on request, for as long as they are looking at it.
 *
 * <p>Deliberately request/response rather than syncing the routine in the anchor's update tag. A
 * recording is kilobytes; an update tag goes to every client in view distance, on every block update,
 * whether or not anyone is looking. A server with a hundred anchors would pay that continuously so
 * that somebody might occasionally glance at one.
 *
 * <p>The reply carries the whole {@link Recording} rather than pre-computed geometry, because the
 * client needs exactly the same code path for the other preview source — a shard held in hand, whose
 * recording is already on the client as an item component and needs no packet at all.
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
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: the routine, or empty if that anchor has none.
     *
     * <p>The diagnostic rides along because the preview is where it is most useful. The anchor GUI
     * has always said <em>why</em> the last action failed; drawing the same information in the world
     * says <em>which</em> of fourteen identical-looking breaks is the one that cannot run, which is
     * the part you otherwise have to work out by counting.
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
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Answers a request, if the player could plausibly be looking at that anchor.
     *
     * <p>The distance check is not politeness. Without it this is a remote read of any anchor in the
     * world by coordinate — which would leak what every routine on a server does, to anyone willing
     * to send packets, including the ones whose author never handed out a shard.
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
