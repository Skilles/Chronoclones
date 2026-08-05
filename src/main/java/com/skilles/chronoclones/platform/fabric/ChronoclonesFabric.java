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
*///?}
//? if fabric {
//? if >=1.20.5 {
/*import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
*///?}
//?}
//? if fabric {
/*import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
*///?}
//? if fabric {
//? if >=1.20.5 {
/*        PayloadTypeRegistry.serverboundPlay().register(entry.type(), entry.codec());
        ServerPlayNetworking.registerGlobalReceiver(entry.type(),
                (payload, context) -> entry.handler().accept(payload, context.player()));
*///?} else {
/*        com.skilles.chronoclones.compat.PayloadCodecs.TO_SERVER.put(
                entry.type().id(), entry.codec().cast());
        ServerPlayNetworking.registerGlobalReceiver(entry.type().id(),
                (server, player, handler, buf, sender) -> {
                    T payload = entry.codec().decode(
                            new com.skilles.chronoclones.compat.RegistryFriendlyByteBuf(
                                    buf, server.registryAccess()));
                    server.execute(() -> entry.handler().accept(payload, player));
                });
*///?}
//?}
//? if fabric {
/*    }

    private static <T extends CustomPacketPayload> void registerToClient(
            ChronoclonesNetwork.ToClient<T> entry) {
*///?}
//? if fabric {
//? if >=1.20.5 {
/*        PayloadTypeRegistry.clientboundPlay().register(entry.type(), entry.codec());
*///?} else {
/*        com.skilles.chronoclones.compat.PayloadCodecs.TO_CLIENT.put(
                entry.type().id(), entry.codec().cast());
*///?}
//?}
//? if fabric {
/*    }
}
*///?}
