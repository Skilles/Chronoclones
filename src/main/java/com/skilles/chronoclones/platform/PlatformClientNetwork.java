package com.skilles.chronoclones.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if neoforge {
//? if >=26 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}
//?}

/** Client-to-server sends, kept apart so a dedicated server never loads client classes. */
public final class PlatformClientNetwork {

    private PlatformClientNetwork() {}

    //? if neoforge {
    public static void sendToServer(CustomPacketPayload payload) {
        //? if >=26 {
        ClientPacketDistributor.sendToServer(payload);
        //?} else {
        /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        *///?}
    }
    //?} else {
    /*public static void sendToServer(CustomPacketPayload payload) {
*///?}
    //? if fabric {
    //? if >=1.20.5 {
    /*        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
    *///?} else {
    /*        net.minecraft.client.multiplayer.ClientLevel level =
                net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                payload.type().id(),
                com.skilles.chronoclones.compat.PayloadCodecs.encodeToServer(
                        payload, level.registryAccess()));
    *///?}
    //?}
    //? if fabric {
    /*    }
    *///?}
}
