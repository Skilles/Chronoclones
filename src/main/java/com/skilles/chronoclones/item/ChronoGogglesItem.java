package com.skilles.chronoclones.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//? if >=26 {
import net.minecraft.world.item.component.TooltipDisplay;
//?}
import org.jspecify.annotations.NonNull;

public class ChronoGogglesItem extends Item
        //? if <26 {
        /*implements net.minecraft.world.item.Equipable
        *///?}
{

    public ChronoGogglesItem(Properties properties) {
        super(properties);
    }

    //? if <26 {
    /*// 26.x expresses this through the EQUIPPABLE component on the item's properties.
    @Override
    public net.minecraft.world.entity.EquipmentSlot getEquipmentSlot() {
        return net.minecraft.world.entity.EquipmentSlot.HEAD;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }
    *///?}

    //? if >=26 {
    @Override
    public void appendHoverText(@NonNull ItemStack stack, @NonNull TooltipContext context, @NonNull TooltipDisplay display,
                                Consumer<Component> adder, @NonNull TooltipFlag flag) {
        appendSharedHoverText(stack, adder, flag);
    }
    //?} else {
    //? if >=1.20.5 {
    /*@Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> lines, TooltipFlag flag) {
        appendSharedHoverText(stack, lines::add, flag);
    }
    *///?} else {
    /*@Override
    public void appendHoverText(ItemStack stack,
                                net.minecraft.world.level.@org.jspecify.annotations.Nullable Level level,
                                java.util.List<Component> lines, TooltipFlag flag) {
        appendSharedHoverText(stack, lines::add, flag);
    }
    *///?}
    //?}

    private void appendSharedHoverText(ItemStack stack,
                                       Consumer<Component> adder, TooltipFlag flag) {
        adder.accept(Component.translatable("tooltip.chronoclones.chrono_goggles")
                .withStyle(ChatFormatting.GRAY));
    }
}
