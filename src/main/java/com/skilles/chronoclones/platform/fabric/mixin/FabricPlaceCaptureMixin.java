//? if fabric {
/*package com.skilles.chronoclones.platform.fabric.mixin;

import com.skilles.chronoclones.recording.RecordingCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Fabric has no block-place event; NeoForge's EntityPlaceEvent is matched by watching the one
// path the recorder cares about, a player's BlockItem placement committing.
@Mixin(BlockItem.class)
abstract class FabricPlaceCaptureMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void chronoclones$afterPlace(BlockPlaceContext context,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (!cir.getReturnValue().consumesAction()) {
            return;
        }
        if (context.getPlayer() instanceof ServerPlayer player) {
            BlockPos pos = context.getClickedPos();
            RecordingCapture.blockPlaced(player, pos, context.getLevel().getBlockState(pos));
        }
    }
}
*///?}
