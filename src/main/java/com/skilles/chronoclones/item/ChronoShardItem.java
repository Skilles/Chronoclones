package com.skilles.chronoclones.item;

import java.util.function.Consumer;

import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class ChronoShardItem extends Item {

    public ChronoShardItem(Properties properties) {
        super(properties);
    }

    public static boolean isInscribed(ItemStack stack) {
        return stack.has(ModDataComponents.RECORDING.get());
    }

    public static @Nullable Recording recordingOf(ItemStack stack) {
        return stack.get(ModDataComponents.RECORDING.get());
    }

    public static ItemStack inscribe(ItemStack blank, Recording recording) {
        ItemStack inscribed = blank.copyWithCount(1);
        inscribed.set(ModDataComponents.RECORDING.get(), recording);
        // Stack-size-1 rides on the stack itself, which every loader honours; an Item-level
        // override for it only exists on NeoForge.
        inscribed.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 1);
        return inscribed;
    }

    @Override
    public boolean isFoil(@NonNull ItemStack stack) {
        return isInscribed(stack);
    }

    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display,
                                @NonNull Consumer<Component> adder, @NonNull TooltipFlag flag) {
        Recording recording = recordingOf(stack);
        if (recording == null) {
            adder.accept(Component.translatable("tooltip.chronoclones.shard.blank")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        RecordingTooltips.describe(recording).forEach(adder);
    }
}
