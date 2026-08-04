package com.skilles.chronoclones.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if neoforge {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?}

/** Client-to-server sends, kept apart so a dedicated server never loads client classes. */
public final class PlatformClientNetwork {

    private PlatformClientNetwork() {}

    //? if neoforge {
    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
    //?} else {
    /*public static void sendToServer(CustomPacketPayload payload) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
    }
    *///?}
}
