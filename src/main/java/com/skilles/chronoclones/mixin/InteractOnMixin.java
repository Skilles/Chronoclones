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

/**
 * The same, for right-clicking a creature.
 *
 * <p>{@code EntityInteract} fires from inside this method and says only that somebody clicked. A
 * cow that is not holding still for shears, a villager with nothing to say, a mount somebody else
 * is riding: all of them fire the event and none of them do anything.
 *
 * <p>Fake players are skipped, because a clone replaying an interaction calls this too, and a
 * recording that recorded its own clones would grow every time it ran.
 */
@Mixin(Player.class)
public abstract class InteractOnMixin {

    @Inject(method = "interactOn", at = @At("RETURN"))
    private void chronoclones$settleInteract(Entity entity, InteractionHand hand, Vec3 location,
                                             CallbackInfoReturnable<InteractionResult> callback) {
        if ((Object) this instanceof ServerPlayer player && !player.isFakePlayer()) {
            InteractionWatch.settle(player, hand, callback.getReturnValue());
        }
    }
}
