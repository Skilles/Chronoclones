//? if <1.20.5 {
/*package com.skilles.chronoclones.compat;

import net.minecraft.resources.ResourceLocation;

// The slice of 1.20.5's CustomPacketPayload that the mod uses; pre-1.20.5 transports carry
// (id, bytes) pairs, so only the type id matters here.
public interface CustomPacketPayload {

    Type<? extends CustomPacketPayload> type();

    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
}
*///?}
