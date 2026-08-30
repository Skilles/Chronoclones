package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.item.RecordingItemData;
import com.skilles.chronoclones.registry.ModItems;
import com.skilles.chronoclones.registry.RecordingProgress;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * What the recorder captures, as loader-neutral entry points. The per-loader event bridges
 * translate their own gameplay events into these calls; each expects to run after cancellable
 * interactions have survived cancellation (NeoForge LOWEST priority, Fabric AFTER-flavoured
 * callbacks or mixin tails).
 */
public final class RecordingCapture {

    private RecordingCapture() {}

    public static void tickPlayer(ServerPlayer player) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        ItemStack recorder = findSessionRecorder(player, session);
        if (recorder == null) {
            RecordingSessions.discard(player);
            ContainerWatch.forget(player);
            InteractionWatch.forget(player);
            return;
        }

        RecordingSession.StopReason stop = session.tickAndSample(player);

        RecordingItemData.setProgress(recorder, new RecordingProgress(
                session.sessionId(), session.tick(), session.actionCount(), session.outOfRangeWarning()));
        session.clearOutOfRangeWarning();

        if (stop != null) {
            ChronoRecorderItem.stopRecording(player, recorder, stop);
        }

        InteractionWatch.expire(player);
    }

    public static void blockBroken(ServerPlayer player, BlockPos pos, BlockState state) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        capture(player, session, new ChronoAction.BreakBlock(
                        session.toLocal(pos),
                        BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock()),
                        toolTemplateOf(player)),
                Vec3.atCenterOf(pos));
    }

    /**
     * The recorder in a creative fist breaks blocks like any other item would, but demanding one
     * from the anchor at playback could only ever fail; a control item records as bare hands.
     */
    private static ItemStack toolTemplateOf(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        return isControlInput(held) ? ItemStack.EMPTY : held.copy();
    }

    public static void blockPlaced(ServerPlayer player, BlockPos pos, BlockState placed) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        ItemStack held = player.getMainHandItem();

        // A BlockItem and only a BlockItem. Anything else that changes a block does so as the effect
        // of using it -- a hoe tilling dirt, a bucket emptying -- and this event fires for those too,
        // which recorded a "Use Hoe" and then a "Place Hoe" that could only ever fail.
        if (!(held.getItem() instanceof BlockItem)) {
            return;
        }

        ChronoAction clicked = InteractionWatch.armedAction(player);
        InteractionWatch.claim(player);

        Optional<ChronoAction.PlaceContext> context = Optional.empty();
        Direction face = session.toLocal(Direction.UP);
        if (clicked instanceof ChronoAction.UseOnBlock use) {
            context = Optional.of(new ChronoAction.PlaceContext(
                    use.localPos(), use.localHitOffset(), use.inside(), use.hand(), poseOf(player, session)));
            face = use.localFace();
        }

        capture(player, session, new ChronoAction.PlaceBlock(
                        session.toLocal(pos),
                        face,
                        RecordedItem.of(held),
                        placed,
                        context),
                Vec3.atCenterOf(pos));
    }

    public static void entityAttacked(ServerPlayer player, Entity target) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        Vec3 targetPos = target.position();

        capture(player, session, new ChronoAction.AttackEntity(
                        session.toLocal(targetPos),
                        BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()),
                        toolTemplateOf(player)),
                targetPos, target.getUUID());
    }

    public static void entityDied(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        RecordingSessions.forEach(session -> session.noteDeath(entity.getUUID()));
    }

    public static void rightClickBlock(ServerPlayer player, InteractionHand hand,
                                       ItemStack stack, BlockHitResult hit) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        BlockPos pos = hit.getBlockPos();

        // A control item's own click is not part of the routine, but what the click opens is: the
        // chest does not care what the player was holding.
        if (!isControlInput(stack)) {
            Vec3 offset = hit.getLocation().subtract(Vec3.atCenterOf(pos));

            InteractionWatch.arm(player, hand, new ChronoAction.UseOnBlock(
                            session.toLocal(pos),
                            session.toLocal(hit.getDirection()),
                            LocalSpace.rotateY(offset,
                                    -LocalSpace.stepsFromNorth(session.originFacing())),
                            hit.isInside(),
                            hand,
                            RecordedItem.of(stack),
                            Optional.of(BuiltInRegistries.BLOCK.wrapAsHolder(
                                    player.level().getBlockState(pos).getBlock()))),
                    Vec3.atCenterOf(pos));
        }

        ContainerWatch.noteInteraction(player, pos, session);
    }

    /** A clone's storage has no off-hand square, so an off-hand action names none. */
    private static int reachedInto(ServerPlayer player, ChronoAction action) {
        return action.heldHand() == InteractionHand.OFF_HAND
                ? ActionSettings.SlotRule.NONE
                //? if >=26 {
                : player.getInventory().getSelectedSlot();
                //?} else {
                /*: player.getInventory().selected;
                *///?}
    }

    private static ActionPose poseOf(ServerPlayer player, RecordingSession session) {
        return new ActionPose(
                session.toLocal(player.position()),
                LocalSpace.toLocalYaw(player.getYRot(), session.originFacing()),
                player.getXRot());
    }

    private static boolean isControlInput(ItemStack stack) {
        return stack.is(ModItems.CHRONO_RECORDER.get()) || stack.is(ModItems.CHRONO_SHARD.get());
    }

    public static void rightClickItem(ServerPlayer player, InteractionHand hand, ItemStack stack) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        if (stack.isEmpty() || isControlInput(stack)) {
            return;
        }

        InteractionWatch.arm(player, hand, new ChronoAction.UseItem(
                        hand,
                        RecordedItem.of(stack),
                        0,
                        Optional.of(poseOf(player, session))),
                player.position());
    }

    public static void entityInteracted(ServerPlayer player, InteractionHand hand,
                                        ItemStack stack, Entity target) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        Vec3 targetPos = target.position();

        if (!isControlInput(stack)) {
            InteractionWatch.arm(player, hand, new ChronoAction.InteractEntity(
                            session.toLocal(targetPos),
                            BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType()),
                            hand,
                            RecordedItem.of(stack)),
                    targetPos);
        }

        ContainerWatch.noteInteraction(player, target, session);
    }

    private static final java.util.Map<UUID, Integer> USE_STARTED_AT = new java.util.HashMap<>();

    public static void useItemStarted(ServerPlayer player, int duration) {
        if (RecordingSessions.get(player) != null) {
            USE_STARTED_AT.put(player.getUUID(), duration);
        }
    }

    public static void useItemEnded(ServerPlayer player, int remainingDuration) {
        Integer startedAt = USE_STARTED_AT.remove(player.getUUID());
        RecordingSession session = RecordingSessions.get(player);
        if (startedAt == null || session == null) {
            return;
        }
        // What is left, subtracted from what there was: a bow drawn for twenty ticks reports 72000
        // at the start and 71980 at release.
        int held = startedAt - remainingDuration;
        if (held > 0) {
            session.noteHeldFor(held);
        }
    }

    public static void containerOpened(ServerPlayer player) {
        RecordingSession session = RecordingSessions.get(player);
        if (session != null) {
            ContainerWatch.onContainerOpened(player, session);
        }
    }

    public static void containerClosed(ServerPlayer player) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        BlockPos containerPos = ContainerWatch.openPosition(player);
        ChronoAction.UseContainer containerSession = ContainerWatch.onContainerClosed(player, session);
        if (containerPos != null && containerSession != null) {
            capture(player, session, containerSession, Vec3.atCenterOf(containerPos));
        }
    }

    public static void loggedOut(ServerPlayer player) {
        RecordingSessions.discard(player);
        ContainerWatch.forget(player);
        InteractionWatch.forget(player);
        USE_STARTED_AT.remove(player.getUUID());
    }

    public static void changedDimension(ServerPlayer player) {
        abandon(player);
    }

    public static void respawned(ServerPlayer player) {
        abandon(player);
    }

    public static void serverStopped() {
        RecordingSessions.clear();
        ContainerWatch.clear();
        InteractionWatch.clear();
        USE_STARTED_AT.clear();
        com.skilles.chronoclones.network.SkinPayloads.clear();
    }

    private static void capture(ServerPlayer player, RecordingSession session,
                                ChronoAction action, Vec3 worldPos) {
        capture(player, session, action, worldPos, null);
    }

    private static void capture(ServerPlayer player, RecordingSession session,
                                ChronoAction action, Vec3 worldPos, @Nullable UUID target) {
        RecordingSession.StopReason stop = session.record(
                action, worldPos, reachedInto(player, action), target);
        if (stop != null) {
            stop(player, session, stop);
        }
    }

    static void commit(ServerPlayer player, RecordingSession session, ChronoAction action,
                       Vec3 worldPos) {
        capture(player, session, action, worldPos);
    }

    static void stop(ServerPlayer player, RecordingSession session,
                     RecordingSession.StopReason reason) {
        ItemStack recorder = findSessionRecorder(player, session);
        if (recorder != null) {
            ChronoRecorderItem.stopRecording(player, recorder, reason);
        }
    }

    private static void abandon(ServerPlayer player) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }
        RecordingSessions.discard(player);
        ContainerWatch.forget(player);
        InteractionWatch.forget(player);

        ItemStack recorder = findSessionRecorder(player, session);
        if (recorder != null) {
            RecordingItemData.clearProgress(recorder);
        }
    }

    /** The specific recorder this session is bound to, hands included, or null if it is gone. */
    public static @Nullable ItemStack findSessionRecorder(Player player, RecordingSession session) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (belongsTo(stack, session)) {
                return stack;
            }
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (belongsTo(stack, session)) {
                return stack;
            }
        }
        return null;
    }

    private static boolean belongsTo(ItemStack stack, RecordingSession session) {
        if (!stack.is(ModItems.CHRONO_RECORDER.get())) {
            return false;
        }
        RecordingProgress progress = RecordingItemData.progress(stack);
        return progress != null && progress.sessionId().equals(session.sessionId());
    }
}
