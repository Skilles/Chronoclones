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
 * <p><b>Why a mixin, in a mod that otherwise has none.</b> Every other kind of capture has an event:
 * breaking, placing, attacking, interacting, opening a container. Slot clicks have none, and the
 * thing that has to be captured cannot be recovered from the results — "the cursor gained 32 and the
 * slot lost 32" is equally consistent with a right-click taking half of 64 and a left-click taking
 * all of 32. Those two mean different things on replay, against a chest whose contents have moved on
 * since. Recording the button rather than the amount is what makes a routine say "split this stack"
 * instead of "move exactly 32", and inferring it from arithmetic would be a guess that quietly
 * breaks the moment a mod's slot does something unusual.
 *
 * <p>{@code clicked} is chosen over the packet handler deliberately: it is the same entry point
 * replay calls, so capture and execution are symmetric, and anything else that drives a menu is
 * captured too.
 *
 * <p>Injected at {@code RETURN} so only clicks that ran are recorded, and gated on the player having
 * an active session — which fake players never do, so a clone working a chest cannot record itself.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @Inject(method = "clicked", at = @At("RETURN"))
    private void chronoclones$captureClick(int slotIndex, int buttonNum, ContainerInput containerInput,
                                        Player player, CallbackInfo callback) {
        if (player instanceof ServerPlayer serverPlayer) {
            ContainerWatch.onClick(serverPlayer, slotIndex, buttonNum, containerInput);
        }
    }
}
