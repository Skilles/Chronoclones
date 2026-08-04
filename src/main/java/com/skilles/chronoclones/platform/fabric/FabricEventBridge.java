//? if fabric {
/*package com.skilles.chronoclones.platform.fabric;

import com.skilles.chronoclones.recording.RecordingCapture;
import com.skilles.chronoclones.replay.LevelActionBudget;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

// Fabric's gameplay events, translated into the loader-neutral capture calls. Where NeoForge
// offered a LOWEST-priority look at a cancellable event, the pre-interaction callbacks here run
// as plain observers returning PASS; the three lifecycle gaps Fabric has no callback for at all
// (block place, use-item lifecycle, container open and close) are covered by the mod's own
// mixins in chronoclones.fabric.mixins.json.
public final class FabricEventBridge {

    private FabricEventBridge() {}

    public static void register() {
        ServerTickEvents.START_LEVEL_TICK.register(LevelActionBudget::resetBudget);

        // NeoForge reports each player after its own tick; ticking them all after the world tick
        // lands within the same tick and keeps ordering stable.
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            for (ServerPlayer player : level.players()) {
                RecordingCapture.tickPlayer(player);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                RecordingCapture.blockBroken(serverPlayer, pos, state);
            }
        });

        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                RecordingCapture.entityAttacked(serverPlayer, target);
            }
            return InteractionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
                RecordingCapture.entityDied(entity));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                RecordingCapture.rightClickBlock(serverPlayer, hand,
                        serverPlayer.getItemInHand(hand), hit);
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                RecordingCapture.rightClickItem(serverPlayer, hand,
                        serverPlayer.getItemInHand(hand));
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                RecordingCapture.entityInteracted(serverPlayer, hand,
                        serverPlayer.getItemInHand(hand), target);
            }
            return InteractionResult.PASS;
        });

        ServerPlayerEvents.LEAVE.register(RecordingCapture::loggedOut);

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
                (player, origin, destination) -> RecordingCapture.changedDimension(player));

        ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> RecordingCapture.respawned(newPlayer));

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RecordingCapture.serverStopped());
    }
}
*///?}
