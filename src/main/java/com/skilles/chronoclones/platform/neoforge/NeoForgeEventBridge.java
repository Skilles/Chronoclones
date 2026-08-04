package com.skilles.chronoclones.platform.neoforge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.recording.RecordingCapture;
import com.skilles.chronoclones.replay.LevelActionBudget;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * NeoForge's gameplay events, translated into the loader-neutral capture calls. Cancellable
 * interactions subscribe at LOWEST priority and skip cancelled events, so the recorder only
 * hears about actions that actually happened.
 */
@EventBusSubscriber(modid = Chronoclones.MODID)
public final class NeoForgeEventBridge {

    private NeoForgeEventBridge() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (!event.getLevel().isClientSide()) {
            LevelActionBudget.resetBudget(event.getLevel());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player) {
            RecordingCapture.blockBroken(player, event.getPos(), event.getState());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.blockPlaced(player, event.getPos(), event.getPlacedBlock());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.entityAttacked(player, event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        RecordingCapture.entityDied(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.rightClickBlock(player, event.getHand(), event.getItemStack(),
                    event.getHitVec());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.rightClickItem(player, event.getHand(), event.getItemStack());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.entityInteracted(player, event.getHand(), event.getItemStack(),
                    event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.useItemStarted(player, event.getDuration());
        }
    }

    @SubscribeEvent
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.useItemEnded(player, event.getDuration());
        }
    }

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.useItemEnded(player, event.getDuration());
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.containerOpened(player);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.containerClosed(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.loggedOut(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.changedDimension(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RecordingCapture.respawned(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RecordingCapture.serverStopped();
    }
}
