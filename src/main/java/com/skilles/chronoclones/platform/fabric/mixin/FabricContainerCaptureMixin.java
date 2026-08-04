//? if fabric {
/*package com.skilles.chronoclones.platform.fabric.mixin;

import com.skilles.chronoclones.recording.RecordingCapture;

import java.util.OptionalInt;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Fabric has no container open and close events; NeoForge's fire around these two methods.
@Mixin(ServerPlayer.class)
abstract class FabricContainerCaptureMixin {

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void chronoclones$menuOpened(MenuProvider provider,
                                         CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent()) {
            RecordingCapture.containerOpened((ServerPlayer) (Object) this);
        }
    }

    @Inject(method = "doCloseContainer", at = @At("HEAD"))
    private void chronoclones$menuClosed(CallbackInfo ci) {
        RecordingCapture.containerClosed((ServerPlayer) (Object) this);
    }
}
*///?}
