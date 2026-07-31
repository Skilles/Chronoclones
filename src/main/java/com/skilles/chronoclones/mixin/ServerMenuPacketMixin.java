package com.skilles.chronoclones.mixin;

import com.skilles.chronoclones.recording.ContainerWatch;

import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
/** Captures anvil renames, which have no event. */
public abstract class ServerMenuPacketMixin {

    @Inject(method = "handleSelectTrade", at = @At("HEAD"))
    private void chronoclones$captureTrade(ServerboundSelectTradePacket packet, CallbackInfo callback) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (player.containerMenu instanceof MerchantMenu) {
            ContainerWatch.onTrade(player, packet.getItem());
        }
    }

    @Inject(method = "handleRenameItem", at = @At("HEAD"))
    private void chronoclones$captureRename(ServerboundRenameItemPacket packet, CallbackInfo callback) {
        ServerPlayer player = ((ServerGamePacketListenerImpl) (Object) this).player;
        if (player.containerMenu instanceof AnvilMenu) {
            ContainerWatch.onRename(player, packet.getName());
        }
    }
}
