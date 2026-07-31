package com.skilles.chronoclones.network;

import java.util.Optional;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingLimits;
import com.skilles.chronoclones.recording.RecordingCodecs;
import com.skilles.chronoclones.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Editing a routine's interpretation, from a recorder in hand or an anchor in the world.
 */
public final class RoutinePayloads {

    private RoutinePayloads() {}

    /**
     * Where the routine being edited lives.
     *
     * @param anchor the anchor holding it, or empty for the stack in {@code hand}
     */
    public record Source(Optional<BlockPos> anchor, InteractionHand hand) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Source> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.optional(BlockPos.STREAM_CODEC).cast(), Source::anchor,
                        ByteBufCodecs.idMapper(id -> InteractionHand.values()[id], Enum::ordinal),
                        Source::hand,
                        Source::new);

        public static Source ofAnchor(BlockPos pos) {
            return new Source(Optional.of(pos), InteractionHand.MAIN_HAND);
        }

        public static Source ofHand(InteractionHand hand) {
            return new Source(Optional.empty(), hand);
        }
    }

    /** Client → server: "let me edit the routine here." */
    public record Request(Source source) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Request> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("request_routine"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.composite(Source.STREAM_CODEC, Request::source, Request::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: the routine, which is the client's cue to open the editor. */
    public record Open(Source source, Recording recording) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Open> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("open_routine_editor"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC =
                StreamCodec.composite(
                        Source.STREAM_CODEC, Open::source,
                        RecordingCodecs.RECORDING_STREAM, Open::recording,
                        Open::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: one action's interpretation has changed. */
    public record EditAction(Source source, int index, ActionSettings settings)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<EditAction> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("edit_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, EditAction> STREAM_CODEC =
                StreamCodec.composite(
                        Source.STREAM_CODEC, EditAction::source,
                        ByteBufCodecs.VAR_INT, EditAction::index,
                        RecordingCodecs.ACTION_SETTINGS_STREAM, EditAction::settings,
                        EditAction::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: drop one action from the routine. */
    public record RemoveAction(Source source, int index) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<RemoveAction> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("remove_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveAction> STREAM_CODEC =
                StreamCodec.composite(
                        Source.STREAM_CODEC, RemoveAction::source,
                        ByteBufCodecs.VAR_INT, RemoveAction::index,
                        RemoveAction::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: put the anchor's own screen back up. */
    public record Reopen(BlockPos anchor) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Reopen> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("reopen_anchor"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Reopen> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC.cast(), Reopen::anchor, Reopen::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: throw this routine away. */
    public record Discard(Source source) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Discard> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("discard_routine"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Discard> STREAM_CODEC =
                StreamCodec.composite(Source.STREAM_CODEC, Discard::source, Discard::new);

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------ handling

    public static void handleRequest(Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Recording routine = read(player, request.source());
        // Opening the editor sends the whole routine down one packet, which is the one place an
        // oversized recording that got in some other way would become everybody's problem.
        if (routine != null && RecordingLimits.accepts(routine, player.registryAccess())) {
            context.reply(new Open(request.source(), routine));
        }
    }

    /**
     * Rewrites one action's settings, re-checking on the way in that this player may.
     */
    public static void handleEdit(EditAction edit, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Recording routine = read(player, edit.source());
        if (routine == null || edit.index() < 0 || edit.index() >= routine.actions().size()) {
            return;
        }
        write(player, edit.source(), routine.withSettings(edit.index(), edit.settings()));
    }

    /**
     * Opens the anchor's screen again, which is what leaving the editor by the inventory key means.
     *
     * <p>The same menu the block itself opens, through the same checks: the editor being open is no
     * reason to trust a client asking for a screen onto somebody's storage.
     */
    public static void handleReopen(Reopen reopen, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ChronoAnchorBlockEntity anchor = anchorFor(player, reopen.anchor());
        if (anchor == null) {
            return;
        }
        player.openMenu(anchor, buffer -> {
            buffer.writeBlockPos(reopen.anchor());
            ChronoAnchorMenu.writeTimeline(buffer, anchor.getRecording());
        });
    }

    /**
     * Drops one action, subject to the same checks as an edit.
     */
    public static void handleRemove(RemoveAction remove, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Recording routine = read(player, remove.source());
        if (routine == null || remove.index() < 0 || remove.index() >= routine.actions().size()) {
            return;
        }
        write(player, remove.source(), routine.without(remove.index()));
    }

    /**
     * Throws a routine away, subject to the same checks as an edit.
     */
    public static void handleDiscard(Discard discard, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (read(player, discard.source()) == null) {
            return;
        }

        if (discard.source().anchor().isEmpty()) {
            ChronoRecorderItem.clear(player.getItemInHand(discard.source().hand()));
            return;
        }
        ChronoAnchorBlockEntity anchor = anchorFor(player, discard.source().anchor().get());
        if (anchor != null) {
            anchor.clearRecording();
        }
    }

    /**
     * The routine at a source, or null if this player may not have it.
     *
     * <p>Re-checked on every edit rather than trusted from the open: the editor is a screen, and a
     * screen is only as honest as the client running it.
     */
    private static @Nullable Recording read(ServerPlayer player, Source source) {
        if (source.anchor().isEmpty()) {
            return player.getItemInHand(source.hand()).get(ModDataComponents.RECORDING.get());
        }

        ChronoAnchorBlockEntity anchor = anchorFor(player, source.anchor().get());
        return anchor == null ? null : anchor.getRecording();
    }

    private static void write(ServerPlayer player, Source source, Recording routine) {
        // An edit arrives from a client, so the thing being written back is as untrusted as
        // anything else off the wire: a screen that sent one enormous routine would be storing it
        // in item data and handing it straight back out again.
        if (!RecordingLimits.accepts(routine, player.registryAccess())) {
            return;
        }
        if (source.anchor().isEmpty()) {
            ItemStack held = player.getItemInHand(source.hand());
            if (held.has(ModDataComponents.RECORDING.get())) {
                held.set(ModDataComponents.RECORDING.get(), routine);
            }
            return;
        }

        ChronoAnchorBlockEntity anchor = anchorFor(player, source.anchor().get());
        if (anchor != null) {
            anchor.reinterpret(routine);
        }
    }

    /** An anchor this player is standing next to and owns, or null. */
    private static @Nullable ChronoAnchorBlockEntity anchorFor(ServerPlayer player, BlockPos pos) {
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_REACH_SQR) {
            return null;
        }
        if (!(player.level().getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor)) {
            return null;
        }
        return AnchorAuthority.mayRetune(anchor.getOwnerId(), player.getUUID()) ? anchor : null;
    }

    /** A little beyond any reasonable reach, so a laggy client is not refused its own anchor. */
    private static final double MAX_REACH_SQR = 12.0 * 12.0;
}
