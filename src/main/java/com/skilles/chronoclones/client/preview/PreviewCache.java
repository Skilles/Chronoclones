package com.skilles.chronoclones.client.preview;

import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ChronoAnchorBlock;
import com.skilles.chronoclones.network.AnchorPreviewPayloads;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.replay.Placement;
import com.skilles.chronoclones.item.RecordingItemData;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.skilles.chronoclones.platform.PlatformClientNetwork;
import org.jspecify.annotations.Nullable;

public final class PreviewCache {

    private PreviewCache() {}

    private static final long TTL_TICKS = 60;
    private static final long REQUEST_INTERVAL_TICKS = 10;

    private static @Nullable BlockPos cachedFor;
    private static @Nullable Recording cached;
    private static DiagnosticState cachedFailure = DiagnosticState.NONE;
    private static BlockPos cachedOffset = BlockPos.ZERO;
    private static int cachedRotation;
    private static long cachedAtTick = Long.MIN_VALUE;
    private static final RequestClock CLOCK = new RequestClock();
    private static final NudgeLedger LEDGER = new NudgeLedger();

    public record Target(BlockPos anchorPos, Direction facing, Recording recording, boolean fromHand,
                         DiagnosticState failure, BlockPos originOffset, int rotationSteps) {
        public Placement placement() {
            return Placement.of(anchorPos, facing, originOffset, rotationSteps);
        }

        /** The anchor's facing with the routine's extra quarter turns applied. */
        public Direction effectiveFacing() {
            return LocalSpace.rotateY(facing, rotationSteps);
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
        // Stamped in the future means a different world, and a difference that stays negative would
        // read as fresh forever: nothing drawn, nothing asked for, until the anchor is looked away
        // from.
        boolean fresh = pos.equals(cachedFor) && cachedAtTick != Long.MIN_VALUE
                && now >= cachedAtTick && now - cachedAtTick <= TTL_TICKS;
        if (!fresh && CLOCK.claim(now, REQUEST_INTERVAL_TICKS)) {
            LEDGER.asked();
            PlatformClientNetwork.sendToServer(new AnchorPreviewPayloads.Request(pos));
        }

        // The last known offset, even gone stale, beats flashing back to the origin for the
        // frames a refresh takes.
        BlockPos offset = pos.equals(cachedFor) ? cachedOffset : BlockPos.ZERO;
        int rotation = pos.equals(cachedFor) ? cachedRotation : 0;

        Recording held = heldRecording(minecraft.player);
        if (held != null) {
            return new Target(pos, facing, held, true, DiagnosticState.NONE, offset, rotation);
        }

        if (fresh) {
            return cached == null ? null
                    : new Target(pos, facing, cached, false, cachedFailure, offset, rotation);
        }
        return null;
    }

    public static void accept(AnchorPreviewPayloads.Reply reply) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean nudgedSinceAsked = reply.pos().equals(cachedFor) && !LEDGER.replyKnowsTheOrigin();
        cachedFor = reply.pos();
        cached = reply.recording().orElse(null);
        cachedFailure = reply.failure();
        if (!nudgedSinceAsked) {
            cachedOffset = reply.originOffset();
            cachedRotation = reply.rotationSteps();
        }
        cachedAtTick = minecraft.level == null ? Long.MIN_VALUE : minecraft.level.getGameTime();
    }

    public static void nudged(BlockPos delta) {
        if (delta.equals(BlockPos.ZERO)) {
            cachedOffset = BlockPos.ZERO;
            cachedRotation = 0;
        } else {
            cachedOffset = cachedOffset.offset(delta);
        }
        LEDGER.nudged();
        // The goggle overlay draws the same anchor from its own cache, and would otherwise show
        // the old origin until its interval came round.
        GoggleCache.refreshSoon();
    }

    public static void rotated(int quarterTurns) {
        cachedRotation = Math.floorMod(cachedRotation + quarterTurns, 4);
        LEDGER.nudged();
        GoggleCache.refreshSoon();
    }

    public static void forget() {
        cachedFor = null;
        cached = null;
        cachedFailure = DiagnosticState.NONE;
        cachedOffset = BlockPos.ZERO;
        cachedRotation = 0;
        cachedAtTick = Long.MIN_VALUE;
    }

    private static @Nullable Recording heldRecording(net.minecraft.world.entity.player.Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            Recording recording = RecordingItemData.recording(stack);
            if (recording != null) {
                return recording;
            }
        }
        return null;
    }
}
