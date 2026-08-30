package com.skilles.chronoclones.network;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's payload table, as loader-neutral data. Each loader's bridge walks these lists and
 * registers them its own way; handlers run on the main thread with the sending player resolved.
 */
public final class ChronoclonesNetwork {

    private ChronoclonesNetwork() {}

    public record ToServer<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, ServerPlayer> handler) {}

    public record ToClient<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            Consumer<T> handler) {}

    public static volatile Consumer<AnchorPreviewPayloads.Reply> clientReplyHandler = reply -> { };

    public static volatile Consumer<RecordingHighlightPayload> clientHighlightHandler = highlight -> { };

    public static volatile Consumer<GogglePayloads.Reply> clientGoggleHandler = reply -> { };

    public static volatile Consumer<RoutinePayloads.Open> clientRoutineHandler = open -> { };

    public static volatile Consumer<SkinPayloads.Reply> clientSkinHandler = reply -> { };

    public static List<ToServer<?>> toServer() {
        return List.of(
                new ToServer<>(AnchorPreviewPayloads.Request.TYPE,
                        AnchorPreviewPayloads.Request.STREAM_CODEC,
                        AnchorPreviewPayloads::handleRequest),
                new ToServer<>(AnchorNudgePayload.TYPE,
                        AnchorNudgePayload.STREAM_CODEC,
                        AnchorNudgePayload::handle),
                new ToServer<>(AnchorRotatePayload.TYPE,
                        AnchorRotatePayload.STREAM_CODEC,
                        AnchorRotatePayload::handle),
                new ToServer<>(GogglePayloads.Request.TYPE,
                        GogglePayloads.Request.STREAM_CODEC,
                        GogglePayloads::handleRequest),
                new ToServer<>(RoutinePayloads.Request.TYPE,
                        RoutinePayloads.Request.STREAM_CODEC,
                        RoutinePayloads::handleRequest),
                new ToServer<>(RoutinePayloads.EditAction.TYPE,
                        RoutinePayloads.EditAction.STREAM_CODEC,
                        RoutinePayloads::handleEdit),
                new ToServer<>(RoutinePayloads.RemoveAction.TYPE,
                        RoutinePayloads.RemoveAction.STREAM_CODEC,
                        RoutinePayloads::handleRemove),
                new ToServer<>(RoutinePayloads.Reopen.TYPE,
                        RoutinePayloads.Reopen.STREAM_CODEC,
                        RoutinePayloads::handleReopen),
                new ToServer<>(RoutinePayloads.Discard.TYPE,
                        RoutinePayloads.Discard.STREAM_CODEC,
                        RoutinePayloads::handleDiscard),
                new ToServer<>(SkinPayloads.Request.TYPE,
                        SkinPayloads.Request.STREAM_CODEC,
                        SkinPayloads::handleRequest));
    }

    public static List<ToClient<?>> toClient() {
        return List.of(
                new ToClient<>(AnchorPreviewPayloads.Reply.TYPE,
                        AnchorPreviewPayloads.Reply.STREAM_CODEC,
                        payload -> clientReplyHandler.accept(payload)),
                new ToClient<>(RecordingHighlightPayload.TYPE,
                        RecordingHighlightPayload.STREAM_CODEC,
                        payload -> clientHighlightHandler.accept(payload)),
                new ToClient<>(GogglePayloads.Reply.TYPE,
                        GogglePayloads.Reply.STREAM_CODEC,
                        payload -> clientGoggleHandler.accept(payload)),
                new ToClient<>(RoutinePayloads.Open.TYPE,
                        RoutinePayloads.Open.STREAM_CODEC,
                        payload -> clientRoutineHandler.accept(payload)),
                new ToClient<>(SkinPayloads.Reply.TYPE,
                        SkinPayloads.Reply.STREAM_CODEC,
                        payload -> clientSkinHandler.accept(payload)));
    }
}
