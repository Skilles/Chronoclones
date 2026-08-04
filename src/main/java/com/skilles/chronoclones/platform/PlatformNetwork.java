package com.skilles.chronoclones.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

/** The payload send calls each loader spells differently. Server-safe half. */
public final class PlatformNetwork {

    private PlatformNetwork() {}

    //? if neoforge {
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        PacketDistributor.sendToAllPlayers(payload);
    }
    //?} else {
    /*// Held for broadcast sends; the Fabric entrypoint keeps it current across server lifecycles.
    public static volatile net.minecraft.server.@org.jspecify.annotations.Nullable MinecraftServer currentServer;

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        net.minecraft.server.MinecraftServer server = currentServer;
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }
    *///?}
}
