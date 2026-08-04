//? if fabric {
/*package com.skilles.chronoclones.platform.fabric;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.ChronoclonesConfig;
import com.skilles.chronoclones.network.ChronoclonesNetwork;
import com.skilles.chronoclones.platform.PlatformNetwork;
import com.skilles.chronoclones.registry.ModBlockEntities;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class ChronoclonesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ChronoclonesConfig.loadOrCreate(
                FabricLoader.getInstance().getConfigDir().resolve(Chronoclones.MODID + ".json"));

        Chronoclones.init();

        registerNetwork();
        FabricEventBridge.register();

        // What hoppers and pipes reach; the gate lives in the container itself.
        ItemStorage.SIDED.registerForBlockEntity(
                (anchor, side) -> ContainerStorage.of(anchor.getExternalInventory(), side),
                ModBlockEntities.CHRONO_ANCHOR.get());

        ServerLifecycleEvents.SERVER_STARTING.register(server -> PlatformNetwork.currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PlatformNetwork.currentServer = null);
    }

    private static void registerNetwork() {
        ChronoclonesNetwork.toServer().forEach(ChronoclonesFabric::registerToServer);
        ChronoclonesNetwork.toClient().forEach(ChronoclonesFabric::registerToClient);
    }

    private static <T extends CustomPacketPayload> void registerToServer(
            ChronoclonesNetwork.ToServer<T> entry) {
        PayloadTypeRegistry.serverboundPlay().register(entry.type(), entry.codec());
        ServerPlayNetworking.registerGlobalReceiver(entry.type(),
                (payload, context) -> entry.handler().accept(payload, context.player()));
    }

    private static <T extends CustomPacketPayload> void registerToClient(
            ChronoclonesNetwork.ToClient<T> entry) {
        PayloadTypeRegistry.clientboundPlay().register(entry.type(), entry.codec());
    }
}
*///?}
