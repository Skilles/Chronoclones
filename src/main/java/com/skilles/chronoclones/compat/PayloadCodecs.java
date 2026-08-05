//? if <1.20.5 {
/*package com.skilles.chronoclones.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// Pre-1.20.5 transports move (id, bytes); these tables recover each payload's codec by id.
public final class PayloadCodecs {

    private PayloadCodecs() {}

    public static final Map<ResourceLocation, StreamCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>>
            TO_CLIENT = new ConcurrentHashMap<>();

    public static final Map<ResourceLocation, StreamCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>>
            TO_SERVER = new ConcurrentHashMap<>();

    public static FriendlyByteBuf encodeToClient(CustomPacketPayload payload, RegistryAccess access) {
        return encode(TO_CLIENT, payload, access);
    }

    public static FriendlyByteBuf encodeToServer(CustomPacketPayload payload, RegistryAccess access) {
        return encode(TO_SERVER, payload, access);
    }

    @SuppressWarnings("unchecked")
    private static FriendlyByteBuf encode(
            Map<ResourceLocation, StreamCodec<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> table,
            CustomPacketPayload payload, RegistryAccess access) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> codec =
                (StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload>) table.get(payload.type().id());
        if (codec == null) {
            throw new IllegalArgumentException("no codec registered for " + payload.type().id());
        }
        codec.encode(buffer, payload);
        return buffer;
    }
}
*///?}
