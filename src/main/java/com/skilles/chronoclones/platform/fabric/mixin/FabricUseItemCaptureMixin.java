//? if fabric {
/*package com.skilles.chronoclones.platform.fabric.mixin;

import com.skilles.chronoclones.recording.RecordingCapture;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric has no use-item lifecycle events; NeoForge's Start, Stop and Finish map onto the vanilla
// methods that begin, release and complete a use.
@Mixin(LivingEntity.class)
abstract class FabricUseItemCaptureMixin {

    @Shadow
    public int useItemRemaining;

    @Inject(method = "startUsingItem", at = @At("TAIL"))
    private void chronoclones$useStarted(InteractionHand hand, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            RecordingCapture.useItemStarted(player, useItemRemaining);
        }
    }

    @Inject(method = "releaseUsingItem", at = @At("HEAD"))
    private void chronoclones$useReleased(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && player.isUsingItem()) {
            RecordingCapture.useItemEnded(player, useItemRemaining);
        }
    }

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void chronoclones$useCompleted(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && player.isUsingItem()) {
            RecordingCapture.useItemEnded(player, useItemRemaining);
        }
    }
}
*///?}
