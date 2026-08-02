package com.skilles.chronoclones.network;

import java.util.List;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.replay.RunReport;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/** The editor polls for what happened to each action the last time a clone reached it. */
public final class ReportPayloads {

    private ReportPayloads() {}

    private static final double MAX_REQUEST_DISTANCE_SQR = 12.0 * 12.0;

    public record Request(BlockPos anchor) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Request> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("request_run_report"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC.cast(), Request::anchor, Request::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Reply(BlockPos anchor, long now, List<RunReport.Entry> entries)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Reply> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("run_report"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC.cast(), Reply::anchor,
                        ByteBufCodecs.VAR_LONG.cast(), Reply::now,
                        RunReport.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).cast(),
                        Reply::entries,
                        Reply::new);

        public Reply {
            entries = List.copyOf(entries);
        }

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handleRequest(Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = request.anchor();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_REQUEST_DISTANCE_SQR) {
            return;
        }
        if (!player.level().isLoaded(pos)
                || !(player.level().getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor)) {
            return;
        }
        context.reply(new Reply(pos, player.level().getGameTime(),
                anchor.getRunReport().snapshot()));
    }
}
