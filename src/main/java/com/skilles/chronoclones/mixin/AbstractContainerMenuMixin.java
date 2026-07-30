package com.skilles.chronoclones.mixin;

import com.skilles.chronoclones.recording.ContainerWatch;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures container clicks while a recording is running.
 *
 * <p>Slot clicks have no event, and the button cannot be recovered from the result. "Cursor gained
 * 32, slot lost 32" is equally a right-click taking half of 64 and a left-click taking all of 32,
 * which replay differently.
 *
 * <p>Both ends of the call, because naming a click needs the menu before it and after it.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("HEAD"))
    private void chronoclones$beforeClick(int slotIndex, int buttonNum, ContainerInput containerInput,
                                         Player player, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer) {
            ContainerWatch.beforeClick(serverPlayer, slotIndex);
        }
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void chronoclones$captureClick(int slotIndex, int buttonNum, ContainerInput containerInput,
                                        Player player, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer) {
            ContainerWatch.onClick(serverPlayer, slotIndex, buttonNum, containerInput);
        }
    }
}
