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

/**
 * Server-side capture hooks.
 */
@EventBusSubscriber(modid = Chronoclones.MODID)
public final class RecordingCapture {

    private RecordingCapture() {}

    // ------------------------------------------------------------------ motion + caps

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
            // Only once the recorder has left the inventory: it need not stay in hand.
            RecordingSessions.discard(player);
            ContainerWatch.forget(player);
            return;
        }

        RecordingSession.StopReason stop = session.tickAndSample(player);

        recorder.set(ModDataComponents.PROGRESS.get(), new RecordingProgress(
                session.sessionId(), session.tick(), session.actionCount(), session.outOfRangeWarning()));
        session.clearOutOfRangeWarning();

        if (stop != null) {
            ChronoRecorderItem.stopRecording(player, recorder, stop);
        }
    }

    // ------------------------------------------------------------------ actions

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

        // The item that produced this block, matched against anchor inventory at replay.
        //
        // A BlockItem, and only a BlockItem. Anything else that changes a block does so as the
        // effect of using it -- a hoe tilling dirt, a bucket emptying, flint and steel -- and this
        // event fires for that too, which is how tilling a field recorded a "Use Hoe" and then a
        // "Place Hoe" that could only ever fail. Every one of those is already captured by
        // onRightClickBlock, which skips BlockItems for exactly this reason: between them the two
        // handlers cover every interaction once.
        if (!(held.getItem() instanceof BlockItem)) {
            return;
        }

        capture(player, session, new ChronoAction.PlaceBlock(
                        session.toLocal(pos),
                        // Facing is stored local so a rotated anchor places rotated blocks.
                        session.toLocal(Direction.UP),
                        BuiltInRegistries.ITEM.wrapAsHolder(held.getItem()),
                        placed),
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

    /**
     * A kill turns the run of swings that caused it into one action with a goal.
     */
    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        // Every session running anywhere: a second player may have been recording the same fight.
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

        // Armed above the item filters. Below them the recorder itself, still in hand when the
        // first chest opens, filtered out the one click most likely to open a container.
        int recordedIndex = -1;

        if (!isControlInput(stack) && !(stack.getItem() instanceof BlockItem)) {
            BlockHitResult hit = event.getHitVec();
            // Relative to the block centre so it rotates with the anchor.
            Vec3 offset = hit.getLocation().subtract(Vec3.atCenterOf(pos));

            recordedIndex = session.nextActionIndex();
            capture(player, session, new ChronoAction.UseOnBlock(
                            session.toLocal(pos),
                            session.toLocal(hit.getDirection()),
                            LocalSpace.rotateY(offset, -LocalSpace.stepsFromNorth(session.originFacing())),
                            hit.isInside(),
                            event.getHand(),
                            BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()),
                            // What it was used on, so the routine can be told to insist on it.
                            Optional.of(BuiltInRegistries.BLOCK.wrapAsHolder(
                                    player.level().getBlockState(pos).getBlock()))),
                    Vec3.atCenterOf(pos));
        }

        // Always armed. -1, or an index past the end, retracts nothing.
        ContainerWatch.noteInteraction(player, pos, recordedIndex, session);
    }

    /**
     * True for items whose use is us, not the routine.
     */
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
        if (stack.isEmpty() || isControlInput(stack) || stack.getItem() instanceof BlockItem) {
            return;
        }

        capture(player, session, new ChronoAction.UseItem(
                        event.getHand(),
                        BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem())),
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
        int recordedIndex = session.nextActionIndex();
        capture(player, session, new ChronoAction.InteractEntity(
                        session.toLocal(target),
                        BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(event.getTarget().getType()),
                        event.getHand(),
                        BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem())),
                target);

        // A villager's trades, a horse's saddlebags, a chest boat: if this opened a menu, the
        // session replaces the interaction that opened it.
        ContainerWatch.noteInteraction(player, event.getTarget(), recordedIndex, session);
    }

    // ------------------------------------------------------------- held-down items

    /**
     * How long each recording player has been holding an item down, keyed by player.
     *
     * <p>The duration a use starts with, kept so the time actually held can be worked out as the
     * difference when they let go: the events report what is left, not what has passed.
     */
    private static final java.util.Map<UUID, Integer> USE_STARTED_AT = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player
                && RecordingSessions.get(player) != null) {
            USE_STARTED_AT.put(player.getUUID(), event.getDuration());
        }
    }

    /** Let go early: a bow loosed at half draw, a shield lowered. */
    @SubscribeEvent
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        noteHeld(event);
    }

    /** Held to the end: food eaten, a potion drunk, a spyglass put away at full duration. */
    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        noteHeld(event);
    }

    /**
     * Writes the time held onto the use that was recorded when the click arrived.
     */
    private static void noteHeld(LivingEntityUseItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Integer startedAt = USE_STARTED_AT.remove(player.getUUID());
        RecordingSession session = RecordingSessions.get(player);
        if (startedAt == null || session == null) {
            return;
        }
        // What is left, subtracted from what there was: a bow drawn for twenty ticks reports
        // 72000 at the start and 71980 at release.
        int held = startedAt - event.getDuration();
        if (held > 0) {
            session.noteHeldFor(held);
        }
    }

    // ------------------------------------------------------------------ containers

    /** {@link ContainerWatch} turns the collected clicks into one action on close. */
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

        // Read before closing the watch, which clears it.
        BlockPos containerPos = ContainerWatch.openPosition(player);
        ChronoAction.UseContainer containerSession = ContainerWatch.onContainerClosed(player, session);
        if (containerPos != null && containerSession != null) {
            capture(player, session, containerSession, Vec3.atCenterOf(containerPos));
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Both maps, not just the session: a watch is dropped by the container-close event, and nothing
     * promises that event runs before this one. A player who logs out with a chest open would
     * otherwise leave their clicks in {@link ContainerWatch} until the server stopped.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingSessions.discard(player);
            ContainerWatch.forget(player);
            USE_STARTED_AT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        // The origin is meaningless in another dimension, so the session cannot survive the trip.
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

    /**
     * Wipes the capture maps when the server goes away.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RecordingSessions.clear();
        ContainerWatch.clear();
        USE_STARTED_AT.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static void capture(ServerPlayer player, RecordingSession session,
                                ChronoAction action, Vec3 worldPos) {
        capture(player, session, action, worldPos, null);
    }

    private static void capture(ServerPlayer player, RecordingSession session,
                                ChronoAction action, Vec3 worldPos, @Nullable UUID target) {
        // The slot, not just the item: a clone reaches into the square the player reached into.
        RecordingSession.StopReason stop = session.record(
                action, worldPos, player.getInventory().getSelectedSlot(), target);
        if (stop != null) {
            stop(player, session, stop);
        }
    }

    /**
     * Ends a session that has reached a cap, telling the player which one.
     *
     * <p>Package-private because {@link ContainerWatch} reaches its own cap without ever calling
     * {@link RecordingSession#record}: a whole container session is one action, however many times
     * it was clicked in, so the count that runs away is not one the session is counting.
     */
    static void stop(ServerPlayer player, RecordingSession session,
                     RecordingSession.StopReason reason) {
        ItemStack recorder = findSessionRecorder(player, session);
        if (recorder != null) {
            ChronoRecorderItem.stopRecording(player, recorder, reason);
        }
    }

    /** Ends a session and clears the stranded PROGRESS stamp from its recorder. */
    private static void abandon(ServerPlayer player) {
        RecordingSession session = RecordingSessions.get(player);
        if (session == null) {
            return;
        }
        RecordingSessions.discard(player);
        ContainerWatch.forget(player);

        // Clear the stamp, or the item reports RECORDING for a session that no longer exists.
        ItemStack recorder = findSessionRecorder(player, session);
        if (recorder != null) {
            recorder.remove(ModDataComponents.PROGRESS.get());
        }
    }

    /**
     * The specific recorder stack this session is bound to, or null if it is gone.
     */
    public static @Nullable ItemStack findSessionRecorder(Player player, RecordingSession session) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (belongsTo(stack, session)) {
                return stack;
            }
        }
        // Check hands explicitly so a recorder in the off-hand is still found.
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
