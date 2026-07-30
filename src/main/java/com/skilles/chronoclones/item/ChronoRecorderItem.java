package com.skilles.chronoclones.item;

import java.util.List;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.recording.ContainerWatch;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingSession;
import com.skilles.chronoclones.recording.RecordingSessions;
import com.skilles.chronoclones.network.RoutinePayloads;
import com.skilles.chronoclones.registry.ModDataComponents;
import com.skilles.chronoclones.registry.RecordingProgress;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The Chrono Recorder.
 */
public class ChronoRecorderItem extends Item {

    public enum State {
        IDLE,
        RECORDING,
        HOLDING
    }

    public ChronoRecorderItem(Properties properties) {
        super(properties);
    }

    public static State stateOf(ItemStack stack) {
        if (stack.has(ModDataComponents.PROGRESS.get())) {
            return State.RECORDING;
        }
        if (stack.has(ModDataComponents.RECORDING.get())) {
            return State.HOLDING;
        }
        return State.IDLE;
    }

    public static @Nullable Recording recordingOf(ItemStack stack) {
        return stack.get(ModDataComponents.RECORDING.get());
    }

    /** The same recorder, now holding a recording and ready to imprint it. */
    public static ItemStack holding(ItemStack stack, Recording recording) {
        stack.set(ModDataComponents.RECORDING.get(), recording);
        stack.remove(ModDataComponents.PROGRESS.get());
        return stack;
    }

    public static void clear(ItemStack stack) {
        stack.remove(ModDataComponents.RECORDING.get());
        stack.remove(ModDataComponents.PROGRESS.get());
    }

    @Override
    public @NonNull InteractionResult use(Level level, Player player, @NonNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        boolean discarding = player.isShiftKeyDown();
        State state = stateOf(stack);

        if (discarding) {
            if (state == State.IDLE) {
                return InteractionResult.PASS;
            }
            // A finished routine is the one thing worth opening rather than throwing away.
            if (state == State.HOLDING) {
                PacketDistributor.sendToPlayer(serverPlayer, new RoutinePayloads.Open(
                        RoutinePayloads.Source.ofHand(hand),
                        stack.get(ModDataComponents.RECORDING.get())));
                return InteractionResult.SUCCESS;
            }
            // Only if THIS recorder owns the session; another may be capturing in the same
            // inventory.
            RecordingProgress stamp = stack.get(ModDataComponents.PROGRESS.get());
            RecordingSession active = RecordingSessions.get(serverPlayer);
            if (stamp != null && active != null && stamp.sessionId().equals(active.sessionId())) {
                RecordingSessions.discard(serverPlayer);
            }
            clear(stack);
            feedback(serverPlayer, "message.chronoclones.recorder.discarded", ChatFormatting.GRAY);
            playSound(serverPlayer, SoundEvents.ITEM_BREAK.value(), 0.7f);
            return InteractionResult.SUCCESS;
        }

        return switch (state) {
            case IDLE -> beginRecording(serverPlayer, stack);
            case RECORDING -> stopRecording(serverPlayer, stack, RecordingSession.StopReason.MANUAL);
            // Imprinting is handled by the block; clicking air must not discard the recording.
            case HOLDING -> {
                feedback(serverPlayer, "message.chronoclones.recorder.holding", ChatFormatting.AQUA);
                yield InteractionResult.SUCCESS;
            }
        };
    }

    private InteractionResult beginRecording(ServerPlayer player, ItemStack stack) {
        // Sessions are keyed by player, so a second capture would strand the first recorder.
        if (RecordingSessions.isRecording(player)) {
            feedback(player, "message.chronoclones.recorder.already_recording", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }

        RecordingSession session = RecordingSessions.start(player);
        stack.set(ModDataComponents.PROGRESS.get(),
                new RecordingProgress(session.sessionId(), 0, 0, false));

        player.sendOverlayMessage(Component.translatable(
                "message.chronoclones.recorder.started",
                Component.literal(session.originFacing().getName()).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.AQUA));
        playSound(player, SoundEvents.BEACON_ACTIVATE, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Also called by the tick handler when a cap is reached, hence the explicit reason. */
    public static InteractionResult stopRecording(ServerPlayer player, ItemStack stack,
                                                  RecordingSession.StopReason reason) {
        // Only the recorder that started the session may end it; a stale stamp would otherwise
        // write somebody else's recording onto this item.
        RecordingProgress stamp = stack.get(ModDataComponents.PROGRESS.get());
        RecordingSession active = RecordingSessions.get(player);
        if (stamp != null && active != null && !stamp.sessionId().equals(active.sessionId())) {
            stack.remove(ModDataComponents.PROGRESS.get());
            Chronoclones.LOGGER.warn("Cleared a stale recording stamp from {}'s recorder; "
                    + "it did not belong to the running session.", player.getGameProfile().name());
            feedback(player, "message.chronoclones.recorder.lost", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }

        RecordingSession session = RecordingSessions.end(player);
        // A recording can stop with a chest still open, and Close will find no session.
        ContainerWatch.forget(player);
        stack.remove(ModDataComponents.PROGRESS.get());

        // "Session vanished" and "session captured nothing" are different failures.
        if (session == null) {
            ChronoRecorderItem.clear(stack);
            Chronoclones.LOGGER.warn("Recorder stopped for {} but no capture session existed: "
                    + "it was discarded while the item still read RECORDING.",
                    player.getGameProfile().name());
            feedback(player, "message.chronoclones.recorder.lost", ChatFormatting.RED);
            playSound(player, SoundEvents.ITEM_BREAK.value(), 0.7f);
            return InteractionResult.SUCCESS;
        }

        if (session.isEmpty()) {
            ChronoRecorderItem.clear(stack);
            Chronoclones.LOGGER.warn("Recorder stopped for {} with an empty session: {} ticks elapsed, "
                    + "{} actions. Capture events are not reaching the session.",
                    player.getGameProfile().name(), session.tick(), session.actionCount());
            feedback(player, "message.chronoclones.recorder.empty", ChatFormatting.RED);
            playSound(player, SoundEvents.ITEM_BREAK.value(), 0.7f);
            return InteractionResult.SUCCESS;
        }

        Recording recording = session.finish();
        stack.set(ModDataComponents.RECORDING.get(), recording);

        String key = switch (reason) {
            case MANUAL -> "message.chronoclones.recorder.stopped";
            case LENGTH_CAP -> "message.chronoclones.recorder.stopped_length";
            case ACTION_CAP -> "message.chronoclones.recorder.stopped_actions";
            case ABANDONED -> "message.chronoclones.recorder.discarded";
        };

        player.sendOverlayMessage(Component.translatable(key,
                recording.lengthSeconds(), recording.actions().size()).withStyle(ChatFormatting.AQUA));

        // A distinct sound for auto-stop, so hitting a cap is not mistaken for a manual stop.
        playSound(player, reason == RecordingSession.StopReason.MANUAL
                ? SoundEvents.BEACON_DEACTIVATE
                : SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.0f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(@NonNull ItemStack stack) {
        return stateOf(stack) != State.IDLE;
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display,
                                java.util.function.@NonNull Consumer<Component> adder, @NonNull TooltipFlag flag) {
        switch (stateOf(stack)) {
            case IDLE -> adder.accept(Component.translatable("tooltip.chronoclones.recorder.idle")
                    .withStyle(ChatFormatting.DARK_GRAY));
            case RECORDING -> {
                RecordingProgress progress = stack.getOrDefault(
                        ModDataComponents.PROGRESS.get(), RecordingProgress.EMPTY);
                adder.accept(Component.translatable("tooltip.chronoclones.recorder.recording",
                        progress.elapsedTicks() / 20, progress.actionCount())
                        .withStyle(ChatFormatting.RED));
            }
            case HOLDING -> {
                Recording recording = recordingOf(stack);
                if (recording != null) {
                    RecordingTooltips.describe(recording).forEach(adder);
                }
            }
        }
    }

    private static void feedback(ServerPlayer player, String key, ChatFormatting colour) {
        player.sendOverlayMessage(Component.translatable(key).withStyle(colour));
    }

    private static void playSound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.6f, pitch);
    }

    /** Exposed for the shard, which shows the same summary. */
    public static List<Component> describe(Recording recording) {
        return RecordingTooltips.describe(recording);
    }
}
