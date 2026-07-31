package com.skilles.chronoclones.mixin;

import com.skilles.chronoclones.recording.InteractionWatch;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
/**
 * Reports what right-clicking a block actually did. The interaction events fire before the
 * work, and NeoForge exposes nothing for the result.
 */
public abstract class UseItemOnMixin {

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void chronoclones$settleUseOnBlock(ServerPlayer player, Level level, ItemStack stack,
                                               InteractionHand hand, BlockHitResult hit,
                                               CallbackInfoReturnable<InteractionResult> callback) {
        InteractionWatch.settle(player, hand, callback.getReturnValue());
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void chronoclones$settleUseItem(ServerPlayer player, Level level, ItemStack stack,
                                            InteractionHand hand,
                                            CallbackInfoReturnable<InteractionResult> callback) {
        InteractionWatch.settle(player, hand, callback.getReturnValue());
    }
}
