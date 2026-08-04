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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class GogglePayloads {

    private GogglePayloads() {}

    public static final int MAX_ANCHORS = 8;

    public record Request() implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<Request> TYPE =
                new CustomPacketPayload.Type<>(Chronoclones.id("goggle_request"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.unit(new Request());

        @Override
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Entry(BlockPos pos, Direction facing, BlockPos originOffset,
                        Recording recording, DiagnosticState failure) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC.cast(), Entry::pos,
                        Direction.STREAM_CODEC.cast(), Entry::facing,
                        BlockPos.STREAM_CODEC.cast(), Entry::originOffset,
                        RecordingCodecs.RECORDING_STREAM, Entry::recording,
                        ByteBufCodecs.fromCodec(DiagnosticState.CODEC).cast(), Entry::failure,
                        Entry::new);
    }

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
        public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handleRequest(Request request, ServerPlayer player) {
        if (!player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CHRONO_GOGGLES.get())) {
            return;
        }
        com.skilles.chronoclones.platform.PlatformNetwork.sendToPlayer(player, gather(player));
    }

    private static Reply gather(ServerPlayer player) {
        int radius = ChronoclonesConfig.goggleRadius();
        boolean showOthers = ChronoclonesConfig.gogglesShowOthers();
        BlockPos centre = player.blockPosition();

        List<Entry> found = new ArrayList<>();
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
                anchor.getOriginOffset(), recording, anchor.getLastFailure());
    }

    public static boolean visibleTo(@Nullable UUID ownerId, UUID viewer, boolean showOthers) {
        return showOthers || ownerId == null || ownerId.equals(viewer);
    }
}
