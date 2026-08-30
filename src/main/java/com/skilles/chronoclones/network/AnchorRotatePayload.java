package com.skilles.chronoclones.network;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;

public record AnchorRotatePayload(BlockPos anchorPos, int quarterTurns) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnchorRotatePayload> TYPE =
            new CustomPacketPayload.Type<>(Chronoclones.id("rotate_origin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnchorRotatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), AnchorRotatePayload::anchorPos,
                    ByteBufCodecs.VAR_INT.cast(), AnchorRotatePayload::quarterTurns,
                    AnchorRotatePayload::new);

    @Override
    public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnchorRotatePayload payload, ServerPlayer player) {
        BlockPos pos = payload.anchorPos();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_ROTATE_DISTANCE_SQR) {
            return;
        }
        if (!player.level().isLoaded(pos)
                || !(player.level().getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor)) {
            return;
        }
        if (!AnchorAuthority.mayRetune(anchor.getOwnerId(), player.getUUID())) {
            return;
        }

        anchor.rotateOrigin(payload.quarterTurns());
    }

    private static final double MAX_ROTATE_DISTANCE_SQR = 12.0 * 12.0;
}
