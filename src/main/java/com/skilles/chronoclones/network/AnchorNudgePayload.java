package com.skilles.chronoclones.network;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: move an anchor's routine by one step, or put it back where it started.
 *
 * <p>The delta is in anchor-local space. The client does the camera-relative part — "the arrow I
 * pressed points away from me" — because that is a question about where the player is standing, and
 * the server has no business guessing at it. What arrives here is already a routine-space vector.
 *
 * @param delta how far to move, or {@link BlockPos#ZERO} to reset the offset entirely
 */
public record AnchorNudgePayload(BlockPos anchorPos, BlockPos delta) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnchorNudgePayload> TYPE =
            new CustomPacketPayload.Type<>(Chronoclones.id("nudge_origin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnchorNudgePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), AnchorNudgePayload::anchorPos,
                    BlockPos.STREAM_CODEC.cast(), AnchorNudgePayload::delta,
                    AnchorNudgePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Applies a nudge, if the sender has any business nudging that anchor.
     *
     * <p>Ownership is the check that matters once there is an owner — see {@link AnchorAuthority}.
     *
     * <p>Refusals are silent. There is nothing a legitimate client can do about them, and a message
     * would only tell someone probing for anchors that they found one.
     */
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

    /** The same reach the preview request allows, since you nudge what you are looking at. */
    private static final double MAX_NUDGE_DISTANCE_SQR = 12.0 * 12.0;
}
