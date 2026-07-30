package com.skilles.chronoclones.mixin;

import com.skilles.chronoclones.recording.ContainerWatch;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures a menu's own controls: an enchantment tier, a loom pattern, a beacon's confirmation.
 *
 * <p>These travel by their own packet rather than as a click, so there is no slot to read them from
 * and no event to hear them on.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerButtonMixin {

    @Inject(method = "clickMenuButton", at = @At("RETURN"))
    private void chronoclones$captureButton(Player player, int id,
                                           CallbackInfoReturnable<Boolean> callback) {
        // Only a button the menu accepted: a refused one changed nothing to replay.
        if (callback.getReturnValue() && player instanceof ServerPlayer serverPlayer) {
            ContainerWatch.onButton(serverPlayer, id);
        }
    }
}
