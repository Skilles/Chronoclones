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

/**
 * Tells the recorder what right-clicking a block actually did.
 *
 * <p>{@code RightClickBlock} is fired from inside this method, before any of the work: it is the
 * question, not the answer. NeoForge exposes nothing for the answer, and the difference matters --
 * a click that returned PASS did nothing, and a routine that replays it will do nothing forever.
 *
 * <p>The event and this return are the same invocation, so the hand they report is the same hand,
 * which is what lets a main hand that passed be told apart from the off hand that worked.
 */
@Mixin(ServerPlayerGameMode.class)
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
