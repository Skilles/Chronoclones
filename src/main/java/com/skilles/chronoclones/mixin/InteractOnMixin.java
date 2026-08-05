package com.skilles.chronoclones.mixin;

import com.skilles.chronoclones.recording.InteractionWatch;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
/** Reports what right-clicking an entity actually did. */
public abstract class InteractOnMixin {

    //? if >=26 {
    @Inject(method = "interactOn", at = @At("RETURN"))
    private void chronoclones$settleInteract(Entity entity, InteractionHand hand, Vec3 location,
                                             CallbackInfoReturnable<InteractionResult> callback) {
        if ((Object) this instanceof ServerPlayer player && !com.skilles.chronoclones.platform.ClonePlayer.isFake(player)) {
            InteractionWatch.settle(player, hand, callback.getReturnValue());
        }
    }
    //?} else {
    /*@Inject(method = "interactOn", at = @At("RETURN"))
    private void chronoclones$settleInteract(Entity entity, InteractionHand hand,
                                             CallbackInfoReturnable<InteractionResult> callback) {
        if ((Object) this instanceof ServerPlayer player && !com.skilles.chronoclones.platform.ClonePlayer.isFake(player)) {
            InteractionWatch.settle(player, hand, callback.getReturnValue());
        }
    }
    *///?}
}
