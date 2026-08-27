//? if neoforge {
package com.skilles.chronoclones.platform.neoforge;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.network.ChronoclonesNetwork;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Walks the mod's payload table and registers it with NeoForge. */
@EventBusSubscriber(modid = Chronoclones.MODID)
public final class NeoForgeNetwork {

    private NeoForgeNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        ChronoclonesNetwork.toServer().forEach(entry -> registerToServer(registrar, entry));
        ChronoclonesNetwork.toClient().forEach(entry -> registerToClient(registrar, entry));
    }

    private static <T extends CustomPacketPayload> void registerToServer(
            PayloadRegistrar registrar, ChronoclonesNetwork.ToServer<T> entry) {
        registrar.playToServer(entry.type(), entry.codec(), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                entry.handler().accept(payload, player);
            }
        });
    }

    private static <T extends CustomPacketPayload> void registerToClient(
            PayloadRegistrar registrar, ChronoclonesNetwork.ToClient<T> entry) {
        registrar.playToClient(entry.type(), entry.codec(),
                (payload, context) -> entry.handler().accept(payload));
    }
}
//?}
