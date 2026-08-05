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
        //? if <26 {
        /*// 21.1's distributor reads a channel attribute that mock gametest players lack.
        if (player.connection == null || !player.connection.isAcceptingMessages()) {
            return;
        }
        *///?}
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        //? if >=26 {
        PacketDistributor.sendToAllPlayers(payload);
        //?} else {
        /*// Routed one by one so channel-less mock players are skipped, as in sendToPlayer.
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, payload);
        }
        *///?}
    }
    //?} else {
    /*// Held for broadcast sends; the Fabric entrypoint keeps it current across server lifecycles.
    public static volatile net.minecraft.server.@org.jspecify.annotations.Nullable MinecraftServer currentServer;

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
*///?}
    //? if fabric {
    //? if >=1.20.5 {
    /*        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    *///?} else {
    /*        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                payload.type().id(),
                com.skilles.chronoclones.compat.PayloadCodecs.encodeToClient(
                        payload, player.server.registryAccess()));
    *///?}
    //?}
    //? if fabric {
    /*    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        net.minecraft.server.MinecraftServer server = currentServer;
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToPlayer(player, payload);
        }
    }
    *///?}
}
