package com.skilles.chronoclones.mixin;

import com.skilles.chronoclones.recording.ContainerWatch;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
/** Captures menu buttons, which have no event. */
public abstract class ContainerButtonMixin {

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void chronoclones$captureButton(Player player, int id,
                                           CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() && player instanceof ServerPlayer serverPlayer) {
            ContainerWatch.onButton(serverPlayer, id);
        }
    }
}
