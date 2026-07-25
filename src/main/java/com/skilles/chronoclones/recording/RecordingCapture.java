package com.skilles.chronoclones.recording;

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
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Server-side capture hooks.
 *
 * <p>Records <em>semantic intent</em>, never raw input, and only actions that actually succeeded —
 * every handler runs at {@link EventPriority#LOWEST} and bails if another listener cancelled the
 * event, so a routine can never contain a step that was blocked by a protection mod at record time.
 *
 * <p>All handlers no-op unless the player has an active session, so the cost on a server with no
 * recordings in progress is a single map lookup. That same lookup is what keeps a routine from
 * recording its own clones — see {@link RecordingSessions} for why an anchor's actions arrive here
 * wearing the recording player's identity.
 *
 * <p>A session is tied to the <em>player</em>, not to what they are holding. Any routine worth
 * recording means holding something other than the recorder — a pickaxe, a stack of blocks — so the
 * recorder only has to remain somewhere in the inventory.
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
            // Only abandon if the recorder has left the inventory entirely (dropped, died). It does
            // NOT need to stay in hand: recording a mining routine requires holding a pickaxe, so
            // treating "not in hand" as abandonment would kill the session the instant the player
            // switched hotbar slots — which is to say, always.
            RecordingSessions.discard(player);
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

        // The item that produced this block — matched against anchor inventory at replay.
        if (held.isEmpty()) {
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
                target);
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

        ItemStack stack = event.getItemStack();
        // Using the recorder itself is control input, not part of the routine.
        if (stack.is(ModItems.CHRONO_RECORDER.get())) {
            return;
        }

        // Placing a block fires this event AND EntityPlaceEvent. Recording both would capture every
        // placement twice — once as a place, once as a spurious use — so block items are left
        // entirely to the place handler.
        if (stack.getItem() instanceof BlockItem) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockHitResult hit = event.getHitVec();
        // Stored relative to the block centre so it rotates with the anchor, like every other
        // position in a recording.
        Vec3 offset = hit.getLocation().subtract(Vec3.atCenterOf(pos));

        // Noted before capturing, so that if this click turns out to open a container the index
        // points at the action we are about to add and the open handler can retract it.
        ContainerWatch.noteInteraction(player, pos, session.nextActionIndex());

        capture(player, session, new ChronoAction.UseOnBlock(
                        session.toLocal(pos),
                        session.toLocal(hit.getDirection()),
                        LocalSpace.rotateY(offset, -LocalSpace.stepsFromNorth(session.originFacing())),
                        hit.isInside(),
                        event.getHand(),
                        BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem())),
                Vec3.atCenterOf(pos));
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
        if (stack.isEmpty() || stack.is(ModItems.CHRONO_RECORDER.get())
                || stack.getItem() instanceof BlockItem) {
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
        if (stack.is(ModItems.CHRONO_RECORDER.get()) || stack.is(ModItems.CHRONO_SHARD.get())) {
            return;
        }

        Vec3 target = event.getTarget().position();
        capture(player, session, new ChronoAction.InteractEntity(
                        session.toLocal(target),
                        BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(event.getTarget().getType()),
                        event.getHand(),
                        BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem())),
                target);
    }

    // ------------------------------------------------------------------ containers

    /**
     * Records what a player did inside a container as a net movement of items, on close.
     *
     * <p>Not slot clicks. Clicks are raw input, and replaying them would mean driving a real
     * container menu — whose behaviour every mod is free to override, and which needs a client to
     * drive it in the first place. The net difference is both simpler and more faithful to what the
     * routine is actually for: "this run takes 32 cobblestone out of that barrel" survives the
     * player having shuffled the stack around three times while deciding.
     */
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
        for (ChronoAction.TransferItems transfer : ContainerWatch.onContainerClosed(player, session)) {
            capture(player, session, transfer, Vec3.atCenterOf(containerPos));
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingSessions.discard(player);
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

    // ------------------------------------------------------------------ helpers

    private static void capture(ServerPlayer player, RecordingSession session,
                                ChronoAction action, Vec3 worldPos) {
        RecordingSession.StopReason stop = session.record(action, worldPos);
        if (stop != null) {
            ItemStack recorder = findSessionRecorder(player, session);
            if (recorder != null) {
                ChronoRecorderItem.stopRecording(player, recorder, stop);
            }
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

        // Clear the stamp, otherwise the item keeps reporting RECORDING for a session that no
        // longer exists and every later interaction with it reads as a failure.
        ItemStack recorder = findSessionRecorder(player, session);
        if (recorder != null) {
            recorder.remove(ModDataComponents.PROGRESS.get());
        }
    }

    /**
     * The specific recorder stack this session is bound to, or null if it is gone.
     *
     * <p>Matched by session id, not by "is a recorder". Scanning for any recorder would let a
     * running session latch onto a <em>different</em> recorder the player happens to be carrying —
     * stamping PROGRESS over its finished RECORDING and then overwriting or erasing it on stop.
     * That is a silent data-loss bug and it depends on inventory slot order, so it only shows up
     * sometimes.
     *
     * <p>Deliberately not restricted to the hands: any routine worth recording involves holding
     * something else, so requiring the recorder in hand would end the session on the first hotbar
     * switch.
     */
    public static @Nullable ItemStack findSessionRecorder(Player player, RecordingSession session) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (belongsTo(stack, session)) {
                return stack;
            }
        }
        // Off-hand and armour are not part of getNonEquipmentItems on every version; check hands
        // explicitly so a recorder held in the off-hand is still found.
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
