package com.skilles.chronoclones.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingCodecs;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Goggle traffic: every anchor near a player, in one exchange.
 *
 * <p>A radius query rather than one request per anchor, and that is the safer shape as well as the
 * cheaper one. The single-anchor request has to check that the requester is plausibly near the
 * coordinate they named, because without it the packet is a remote read of any anchor in the world.
 * A radius computed from the player's own position cannot be pointed anywhere else — the property
 * holds by construction instead of by a check somebody has to remember.
 *
 * <p>Two more gates, both server-side. The player must actually be wearing the goggles, because a
 * client asking nicely is not a permission system. And {@link ChronoclonesConfig#GOGGLES_SHOW_OTHERS}
 * decides whether anchors belonging to other people come back at all.
 */
public final class GogglePayloads {

    private GogglePayloads() {}

    /**
     * How many anchors one reply may carry.
     *
     * <p>Each carries a whole {@link Recording}, which is kilobytes. Eight nearest is enough to see
     * a working base at a glance and bounded enough not to matter; the client is told the cap was hit
     * so a partial view never reads as a complete one.
     */
    public static final int MAX_ANCHORS = 8;

    /** Client → server: "what is around me?" The position comes from the server's own view. */
    public record Request() implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Request> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("goggle_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.unit(new Request());

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One anchor, as much as the preview needs to draw it. */
    public record Entry(BlockPos pos, Direction facing, BlockPos originOffset,
                        Recording recording, DiagnosticState failure, int precision) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC.cast(), Entry::pos,
                        Direction.STREAM_CODEC.cast(), Entry::facing,
                        BlockPos.STREAM_CODEC.cast(), Entry::originOffset,
                        RecordingCodecs.RECORDING_STREAM, Entry::recording,
                        ByteBufCodecs.fromCodec(DiagnosticState.CODEC).cast(), Entry::failure,
                        // Packed rather than three booleans: it is one synced int everywhere else,
                        // and unpacking it in one place keeps the bit layout in one place too.
                        ByteBufCodecs.VAR_INT, Entry::precision,
                        Entry::new);
    }

    /**
     * Server → client: the anchors in range.
     *
     * @param truncated whether {@link #MAX_ANCHORS} cut the list short, so the client can say so
     */
    public record Reply(List<Entry> anchors, boolean truncated) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Reply> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("goggle_reply"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> STREAM_CODEC =
                StreamCodec.composite(
                        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), Reply::anchors,
                        ByteBufCodecs.BOOL.cast(), Reply::truncated,
                        Reply::new);

        public Reply {
            anchors = List.copyOf(anchors);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handleRequest(Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        // Worn, not merely owned. Otherwise the packet is a base-wide scan available to any client
        // that feels like sending it.
        if (!player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CHRONO_GOGGLES.get())) {
            return;
        }
        context.reply(gather(player));
    }

    private static Reply gather(ServerPlayer player) {
        int radius = ChronoclonesConfig.GOGGLE_RADIUS.getAsInt();
        boolean showOthers = ChronoclonesConfig.GOGGLES_SHOW_OTHERS.get();
        BlockPos centre = player.blockPosition();

        List<Entry> found = new ArrayList<>();
        // Chunk-wise rather than block-wise: a 24-block radius is a quarter of a million positions,
        // and block entities are already indexed per chunk.
        int minChunkX = (centre.getX() - radius) >> 4;
        int maxChunkX = (centre.getX() + radius) >> 4;
        int minChunkZ = (centre.getZ() - radius) >> 4;
        int maxChunkZ = (centre.getZ() + radius) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = player.level().getChunkSource()
                        .getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    Entry entry = entryFor(blockEntity, centre, radius, player.getUUID(), showOthers);
                    if (entry != null) {
                        found.add(entry);
                    }
                }
            }
        }

        found.sort(Comparator.comparingDouble(e -> e.pos().distSqr(centre)));
        boolean truncated = found.size() > MAX_ANCHORS;
        return new Reply(truncated ? found.subList(0, MAX_ANCHORS) : found, truncated);
    }

    private static @Nullable Entry entryFor(BlockEntity blockEntity, BlockPos centre, int radius,
                                            UUID viewer, boolean showOthers) {
        if (!(blockEntity instanceof ChronoAnchorBlockEntity anchor)) {
            return null;
        }
        if (!blockEntity.getBlockPos().closerThan(centre, radius)) {
            return null;
        }
        Recording recording = anchor.getRecording();
        if (recording == null) {
            return null;
        }
        if (!visibleTo(anchor.getOwnerId(), viewer, showOthers)) {
            return null;
        }
        return new Entry(blockEntity.getBlockPos(),
                anchor.getBlockState().getValue(
                        com.skilles.chronoclones.block.ChronoAnchorBlock.FACING),
                anchor.getOriginOffset(), recording, anchor.getLastFailure(),
                anchor.getPrecision().pack());
    }

    /**
     * Whether one anchor may be shown to one viewer.
     *
     * <p>Its own method because it is the rule the config exists to express, and because it is worth
     * asserting without standing up a world full of chunks to iterate.
     *
     * <p>An anchor with no owner has never been imprinted by anybody, so there is nobody for it to be
     * private from.
     */
    public static boolean visibleTo(@Nullable UUID ownerId, UUID viewer, boolean showOthers) {
        return showOthers || ownerId == null || ownerId.equals(viewer);
    }
}
