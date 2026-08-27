//? if <1.20.5 {
/*package com.skilles.chronoclones.compat;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;

// The slice of 1.20.5's RegistryFriendlyByteBuf that the mod uses: a buffer knowing its registries.
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {

    private final RegistryAccess registryAccess;

    public RegistryFriendlyByteBuf(ByteBuf source, RegistryAccess registryAccess) {
        super(source);
        this.registryAccess = registryAccess;
    }

    public RegistryAccess registryAccess() {
        return registryAccess;
    }
}
*///?}
