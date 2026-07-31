package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.registry.ModDataComponents;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = Chronoclones.MODID)
public final class RecordingCapture {

    private RecordingCapture() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
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

        recorder.set(ModDataComponents.PROGRESS.get(), new RecordingProgress(
                session.sessionId(), session.tick(), session.actionCount(), session.outOfRangeWarning()));
        session.clearOutOfRangeWarning();

        if (stop != null) {
            ChronoRecorderItem.stopRecording(player, recorder, stop);
        }

        InteractionWatch.expire(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        capture(player, session, new ChronoAction.BreakBlock(
                        session.toLocal(pos),
                        BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock()),
                        player.getMainHandItem().copy()),
                Vec3.atCenterOf(pos));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState placed = event.getPlacedBlock();
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        Vec3 target = event.getTarget().position();

        capture(player, session, new ChronoAction.AttackEntity(
                        session.toLocal(target),
                        BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(event.getTarget().getType()),
                        player.getMainHandItem().copy()),
                target, event.getTarget().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        RecordingSessions.forEach(session -> session.noteDeath(event.getEntity().getUUID()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        BlockPos pos = event.getPos();
        ItemStack stack = event.getItemStack();
        if (isControlInput(stack)) {
            return;
        }

        BlockHitResult hit = event.getHitVec();
        Vec3 offset = hit.getLocation().subtract(Vec3.atCenterOf(pos));

        InteractionWatch.arm(player, event.getHand(), new ChronoAction.UseOnBlock(
                        session.toLocal(pos),
                        session.toLocal(hit.getDirection()),
                        LocalSpace.rotateY(offset, -LocalSpace.stepsFromNorth(session.originFacing())),
                        hit.isInside(),
                        event.getHand(),
                        RecordedItem.of(stack),
                        Optional.of(BuiltInRegistries.BLOCK.wrapAsHolder(
                                player.level().getBlockState(pos).getBlock()))),
                Vec3.atCenterOf(pos));

        ContainerWatch.noteInteraction(player, pos, session);
    }

    /** A clone's storage has no off-hand square, so an off-hand action names none. */
    private static int reachedInto(ServerPlayer player, ChronoAction action) {
        return action.heldHand() == InteractionHand.OFF_HAND
                ? ActionSettings.SlotRule.NONE
                : player.getInventory().getSelectedSlot();
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || isControlInput(stack)) {
            return;
        }

        InteractionWatch.arm(player, event.getHand(), new ChronoAction.UseItem(
                        event.getHand(),
                        RecordedItem.of(stack),
                        0,
                        Optional.of(poseOf(player, session))),
                player.position());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (isControlInput(stack)) {
            return;
        }

        Vec3 target = event.getTarget().position();
        InteractionWatch.arm(player, event.getHand(), new ChronoAction.InteractEntity(
                        session.toLocal(target),
                        BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(event.getTarget().getType()),
                        event.getHand(),
                        RecordedItem.of(stack)),
                target);

        ContainerWatch.noteInteraction(player, event.getTarget(), session);
    }

    private static final java.util.Map<UUID, Integer> USE_STARTED_AT = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player
                && RecordingSessions.get(player) != null) {
            USE_STARTED_AT.put(player.getUUID(), event.getDuration());
        }
    }

    @SubscribeEvent
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        noteHeld(event);
    }

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        noteHeld(event);
    }

    private static void noteHeld(LivingEntityUseItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Integer startedAt = USE_STARTED_AT.remove(player.getUUID());
        RecordingSession session = RecordingSessions.get(player);
        if (startedAt == null || session == null) {
            return;
        }
        // What is left, subtracted from what there was: a bow drawn for twenty ticks reports 72000
        // at the start and 71980 at release.
        int held = startedAt - event.getDuration();
        if (held > 0) {
            session.noteHeldFor(held);
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RecordingSession session = RecordingSessions.get(player);
        if (session != null) {
            ContainerWatch.onContainerOpened(player, session);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
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

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingSessions.discard(player);
            ContainerWatch.forget(player);
            InteractionWatch.forget(player);
            USE_STARTED_AT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            abandon(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            abandon(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
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
            recorder.remove(ModDataComponents.PROGRESS.get());
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
        RecordingProgress progress = stack.get(ModDataComponents.PROGRESS.get());
        return progress != null && progress.sessionId().equals(session.sessionId());
    }
}
