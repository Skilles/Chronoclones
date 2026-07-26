package com.skilles.chronoclones.client.preview;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlock;
import com.skilles.chronoclones.network.AnchorPreviewPayloads;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.replay.Placement;
import com.skilles.chronoclones.replay.TransferPrecision;
import com.skilles.chronoclones.registry.ModDataComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Decides what the player is currently being shown a preview of, and fetches it when needed.
 *
 * <p>Two sources, one display. A routine held in hand — an inscribed shard, or a recorder that has
 * finished — is already on the client as an item component, so pointing at an anchor while holding
 * one previews <em>what would happen if you imprinted it here</em>, with no packet at all. That is
 * the case the spec cares about: inspection before committing. Pointing at an anchor empty-handed
 * previews what it is already doing, which needs one request.
 *
 * <p>Client-only, single-threaded, and holds at most one routine.
 */
public final class PreviewCache {

    private PreviewCache() {}

    /** How long a fetched routine stays good. Long enough to look around, short enough to notice edits. */
    private static final long TTL_TICKS = 60;
    /** Never more than one request in flight per this many ticks, however hard the player stares. */
    private static final long REQUEST_INTERVAL_TICKS = 10;

    private static @Nullable BlockPos cachedFor;
    private static @Nullable Recording cached;
    private static DiagnosticState cachedFailure = DiagnosticState.NONE;
    private static BlockPos cachedOffset = BlockPos.ZERO;
    private static TransferPrecision cachedPrecision = TransferPrecision.NONE;
    private static long cachedAtTick = Long.MIN_VALUE;
    private static final RequestClock CLOCK = new RequestClock();

    /**
     * The anchor being looked at and the routine to draw there, or null.
     *
     * @param failure what that anchor is currently stuck on. Always NONE for a routine held in hand:
     *                a shard has never run anywhere, so it has nothing to be stuck on.
     */
    public record Target(BlockPos anchorPos, Direction facing, Recording recording, boolean fromHand,
                         DiagnosticState failure, BlockPos originOffset,
                         TransferPrecision precision) {

        /** Where the routine is actually drawn from, which is the anchor plus its nudge. */
        public Placement placement() {
            return Placement.of(anchorPos, facing, originOffset);
        }
    }

    public static @Nullable Target current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChronoAnchorBlock)) {
            forget();
            return null;
        }
        Direction facing = state.getValue(ChronoAnchorBlock.FACING);

        long now = minecraft.level.getGameTime();
        boolean fresh = pos.equals(cachedFor) && cachedAtTick != Long.MIN_VALUE
                && now - cachedAtTick <= TTL_TICKS;
        if (!fresh && CLOCK.claim(now, REQUEST_INTERVAL_TICKS)) {
            ClientPacketDistributor.sendToServer(new AnchorPreviewPayloads.Request(pos));
        }

        // The offset belongs to the anchor, not to the routine, so it applies to a shard being
        // lined up as much as to one already imprinted — that is what makes aiming before you
        // commit possible at all. Zero until the reply lands, which is also the right answer for an
        // anchor nobody has nudged.
        BlockPos offset = fresh ? cachedOffset : BlockPos.ZERO;
        // Same argument as the offset: it belongs to the anchor rather than to the routine, so a
        // shard being lined up is shown the settings of the anchor it would go into.
        TransferPrecision precision = fresh ? cachedPrecision : TransferPrecision.NONE;

        // A routine in hand wins: the player is asking "what would this do here", and answering with
        // what the anchor already holds would be a different question.
        Recording held = heldRecording(minecraft.player);
        if (held != null) {
            return new Target(pos, facing, held, true, DiagnosticState.NONE, offset, precision);
        }

        if (fresh) {
            return cached == null ? null
                    : new Target(pos, facing, cached, false, cachedFailure, offset, precision);
        }
        // Nothing to draw until the reply lands. One frame of nothing beats a stale routine drawn
        // over a different anchor.
        return null;
    }

    /** Called on the main thread by the payload handler. */
    public static void accept(AnchorPreviewPayloads.Reply reply) {
        Minecraft minecraft = Minecraft.getInstance();
        cachedFor = reply.pos();
        cached = reply.recording().orElse(null);
        cachedFailure = reply.failure();
        cachedOffset = reply.originOffset();
        cachedPrecision = TransferPrecision.unpack(reply.precision());
        cachedAtTick = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
    }

    public static void forget() {
        cachedFor = null;
        cached = null;
        cachedFailure = DiagnosticState.NONE;
        cachedOffset = BlockPos.ZERO;
        cachedPrecision = TransferPrecision.NONE;
        cachedAtTick = Long.MIN_VALUE;
    }

    private static @Nullable Recording heldRecording(net.minecraft.world.entity.player.Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            Recording recording = stack.get(ModDataComponents.RECORDING.get());
            if (recording != null) {
                return recording;
            }
        }
        return null;
    }
}
