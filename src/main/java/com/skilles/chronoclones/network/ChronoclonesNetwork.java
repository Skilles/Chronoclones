package com.skilles.chronoclones.network;

import java.util.function.Consumer;

import com.skilles.chronoclones.Chronoclones;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Chronoclones.MODID)
public final class ChronoclonesNetwork {

    private ChronoclonesNetwork() {}

    public static volatile Consumer<AnchorPreviewPayloads.Reply> clientReplyHandler = reply -> { };

    public static volatile Consumer<RecordingHighlightPayload> clientHighlightHandler = highlight -> { };

    public static volatile Consumer<GogglePayloads.Reply> clientGoggleHandler = reply -> { };

    public static volatile Consumer<RoutinePayloads.Open> clientRoutineHandler = open -> { };

    public static volatile Consumer<SkinPayloads.Reply> clientSkinHandler = reply -> { };

    public static volatile Consumer<ReportPayloads.Reply> clientReportHandler = reply -> { };

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(AnchorPreviewPayloads.Request.TYPE,
                AnchorPreviewPayloads.Request.STREAM_CODEC,
                AnchorPreviewPayloads::handleRequest);

        registrar.playToServer(AnchorNudgePayload.TYPE,
                AnchorNudgePayload.STREAM_CODEC,
                AnchorNudgePayload::handle);

        registrar.playToClient(AnchorPreviewPayloads.Reply.TYPE,
                AnchorPreviewPayloads.Reply.STREAM_CODEC,
                (payload, context) -> clientReplyHandler.accept(payload));

        registrar.playToClient(RecordingHighlightPayload.TYPE,
                RecordingHighlightPayload.STREAM_CODEC,
                (payload, context) -> clientHighlightHandler.accept(payload));

        registrar.playToServer(GogglePayloads.Request.TYPE,
                GogglePayloads.Request.STREAM_CODEC,
                GogglePayloads::handleRequest);

        registrar.playToClient(GogglePayloads.Reply.TYPE,
                GogglePayloads.Reply.STREAM_CODEC,
                (payload, context) -> clientGoggleHandler.accept(payload));

        registrar.playToServer(RoutinePayloads.Request.TYPE,
                RoutinePayloads.Request.STREAM_CODEC,
                RoutinePayloads::handleRequest);

        registrar.playToServer(RoutinePayloads.EditAction.TYPE,
                RoutinePayloads.EditAction.STREAM_CODEC,
                RoutinePayloads::handleEdit);

        registrar.playToServer(RoutinePayloads.RemoveAction.TYPE,
                RoutinePayloads.RemoveAction.STREAM_CODEC,
                RoutinePayloads::handleRemove);

        registrar.playToServer(RoutinePayloads.Reopen.TYPE,
                RoutinePayloads.Reopen.STREAM_CODEC,
                RoutinePayloads::handleReopen);

        registrar.playToServer(RoutinePayloads.Discard.TYPE,
                RoutinePayloads.Discard.STREAM_CODEC,
                RoutinePayloads::handleDiscard);

        registrar.playToClient(RoutinePayloads.Open.TYPE,
                RoutinePayloads.Open.STREAM_CODEC,
                (payload, context) -> clientRoutineHandler.accept(payload));

        registrar.playToServer(SkinPayloads.Request.TYPE,
                SkinPayloads.Request.STREAM_CODEC,
                SkinPayloads::handleRequest);

        registrar.playToClient(SkinPayloads.Reply.TYPE,
                SkinPayloads.Reply.STREAM_CODEC,
                (payload, context) -> clientSkinHandler.accept(payload));

        registrar.playToServer(ReportPayloads.Request.TYPE,
                ReportPayloads.Request.STREAM_CODEC,
                ReportPayloads::handleRequest);

        registrar.playToClient(ReportPayloads.Reply.TYPE,
                ReportPayloads.Reply.STREAM_CODEC,
                (payload, context) -> clientReportHandler.accept(payload));
    }
}
