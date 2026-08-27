//? if forge {
/*package com.skilles.chronoclones.platform.forge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.recording.RecordingCapture;
import com.skilles.chronoclones.replay.LevelActionBudget;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Forge's gameplay events, translated into the loader-neutral capture calls; the LOWEST-priority
// listeners skip cancelled interactions so the recorder only hears about what actually happened.
@Mod.EventBusSubscriber(modid = Chronoclones.MODID)
public final class ForgeEventBridge {

    private ForgeEventBridge() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            RecordingCapture.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !event.level.isClientSide()) {
            LevelActionBudget.resetBudget(event.level);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
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
*///?}
