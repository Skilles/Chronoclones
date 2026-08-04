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
        //?}
    }
}
