package com.skilles.chronoclones.network;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

public record AnchorNudgePayload(BlockPos anchorPos, BlockPos delta) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnchorNudgePayload> TYPE =
            new CustomPacketPayload.Type<>(Chronoclones.id("nudge_origin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnchorNudgePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), AnchorNudgePayload::anchorPos,
                    BlockPos.STREAM_CODEC.cast(), AnchorNudgePayload::delta,
                    AnchorNudgePayload::new);

    @Override
    public CustomPacketPayload.@NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnchorNudgePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = payload.anchorPos();
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_NUDGE_DISTANCE_SQR) {
            return;
        }
        if (!player.level().isLoaded(pos)
                || !(player.level().getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor)) {
            return;
        }
        if (!AnchorAuthority.mayRetune(anchor.getOwnerId(), player.getUUID())) {
            return;
        }

        if (payload.delta().equals(BlockPos.ZERO)) {
            anchor.resetOrigin();
        } else {
            anchor.nudgeOrigin(payload.delta());
        }
    }

    private static final double MAX_NUDGE_DISTANCE_SQR = 12.0 * 12.0;
}
