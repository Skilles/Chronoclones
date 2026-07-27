package com.skilles.chronoclones.network;

import java.util.function.Consumer;

import com.skilles.chronoclones.Chronoclones;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration. Both directions of the preview exchange, and nothing else so far.
 *
 * <p>Note the shape of {@link #clientReplyHandler}: payloads must be registered on both sides, but
 * the code that <em>handles</em> a play-to-client payload is client code. Naming the client class
 * here — even inside a {@code dist == CLIENT} branch — would put it in the bytecode of a class the
 * dedicated server loads. 26.x removed the runtime member-stripping that used to make that safe, so
 * side isolation has to be structural, and a settable hook is the smallest structure that does it.
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
    }
}
