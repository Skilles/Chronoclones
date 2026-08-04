package com.skilles.chronoclones.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-to-server sends, kept apart so a dedicated server never loads client classes. */
public final class PlatformClientNetwork {

    private PlatformClientNetwork() {}

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
