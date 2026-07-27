package com.skilles.chronoclones.network;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.replay.TransferPrecision;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server: how specific an anchor should be about its item transfers.
 *
 * <p>Sent by the drawer in the anchor screen, one packet per checkbox click.
 *
 * @param packed the three flags, as {@link TransferPrecision#pack()}
 */
public record AnchorPrecisionPayload(BlockPos anchorPos, int packed) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AnchorPrecisionPayload> TYPE =
            new CustomPacketPayload.Type<>(Chronoclones.id("set_precision"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnchorPrecisionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), AnchorPrecisionPayload::anchorPos,
                    ByteBufCodecs.VAR_INT, AnchorPrecisionPayload::packed,
                    AnchorPrecisionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Applies the setting, if the sender has any business setting it.
     *
     * <p>The gate is <b>having that anchor's screen open</b>, not standing near it. The menu was
     * opened through the block's own {@code MenuProvider}, which already ran every reach and
     * permission check the game has; reusing that is both stricter than a distance comparison and
     * free of a second constant to keep in step with the first.
     *
     * <p>Ownership on top, matching {@link AnchorNudgePayload}: an anchor nobody has imprinted is
     * fair game, and after that only the owner may retune it. Otherwise anyone who can open your
     * anchor can make its routine start moving whatever is nearest into whichever square is free.
     *
     * <p>Refusals are silent — there is nothing a legitimate client can do about one.
     */
    public static void handle(AnchorPrecisionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, payload.anchorPos(), payload.packed());
        }
    }

    /**
     * The decision, separated from the packet that carries it.
     *
     * <p>Split out to be testable: this is the half of the feature with rules in it, and until it
     * had a test the only way to find out whether a checkbox reached the anchor was to click one.
     *
     * @return whether the setting was applied, for a caller that wants to know
     */
    public static boolean apply(ServerPlayer player, BlockPos anchorPos, int packed) {
        if (!(player.containerMenu instanceof ChronoAnchorMenu menu)
                || !menu.anchorPos().equals(anchorPos)) {
            return false;
        }
        if (!(player.level().getBlockEntity(anchorPos) instanceof ChronoAnchorBlockEntity anchor)) {
            return false;
        }
        if (!AnchorAuthority.mayRetune(anchor.getOwnerId(), player.getUUID())) {
            return false;
        }

        anchor.setPrecision(TransferPrecision.unpack(packed));
        return true;
    }
}
