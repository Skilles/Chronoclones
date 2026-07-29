package com.skilles.chronoclones.network;

import java.util.function.Consumer;

import com.skilles.chronoclones.Chronoclones;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration.
 *
 * <p>The client handlers are settable because naming a client class here would put it in the
 * bytecode of a class the dedicated server loads.
 */
@EventBusSubscriber(modid = Chronoclones.MODID)
public final class ChronoclonesNetwork {

    private ChronoclonesNetwork() {}

    /** Installed by the client entrypoint. Stays a no-op on a dedicated server. */
    public static volatile Consumer<AnchorPreviewPayloads.Reply> clientReplyHandler = reply -> { };

    /** Likewise, for the slot highlights drawn over an open container while recording. */
    public static volatile Consumer<RecordingHighlightPayload> clientHighlightHandler = highlight -> { };

    /** And for the anchors the goggles reveal. */
    public static volatile Consumer<GogglePayloads.Reply> clientGoggleHandler = reply -> { };

    /** And for a routine arriving to be edited. */
    public static volatile Consumer<RoutinePayloads.Open> clientRoutineHandler = open -> { };

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

        registrar.playToServer(RoutinePayloads.Discard.TYPE,
                RoutinePayloads.Discard.STREAM_CODEC,
                RoutinePayloads::handleDiscard);

        registrar.playToClient(RoutinePayloads.Open.TYPE,
                RoutinePayloads.Open.STREAM_CODEC,
                (payload, context) -> clientRoutineHandler.accept(payload));
    }
}
