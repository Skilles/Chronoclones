package com.skilles.chronoclones.platform;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * How long a stack burns, in ticks. 26.x has vanilla {@code FuelValues}; older versions answer
 * through the loader (NeoForge/Forge burn-time extension, Fabric's FuelRegistry).
 */
public final class Fuel {

    private Fuel() {}

    public static int burnTicks(Level level, ItemStack stack) {
        //? if >=26 {
        return level.fuelValues().burnDuration(stack);
        //?} else {
        //? if neoforge {
        /*int ticks = stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);
        return Math.max(ticks, 0);
        *///?}
        //? if fabric {
        /*Integer ticks = net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.get(stack.getItem());
        return ticks == null ? 0 : ticks;
        *///?}
        //? if forge {
        /*// The stack's own getBurnTime answers -1 for "ask vanilla"; the hook resolves that.
        int ticks = net.minecraftforge.common.ForgeHooks.getBurnTime(
                stack, net.minecraft.world.item.crafting.RecipeType.SMELTING);
        return Math.max(ticks, 0);
        *///?}
        //?}
    }
}
