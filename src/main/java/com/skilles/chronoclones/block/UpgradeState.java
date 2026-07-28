package com.skilles.chronoclones.block;

import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The anchor's capabilities, derived from whatever is sitting in its upgrade slots.
 */
public record UpgradeState(int cloneCount, int ticksPerStep) {

    /** A bare anchor: one clone, one tick per step. */
    public static final UpgradeState BASE = new UpgradeState(1, 1);

    public static final int MAX_CLONES = 4;
    public static final int MAX_RATE = 3;

    /** The independent upgrade axes. */
    public enum Axis {
        CLONES,
        RATE
    }

    /**
     * Reads the upgrade slots and totals each axis.
     */
    public static UpgradeState from(ResourceHandler<ItemResource> upgrades) {
        int[] counts = new int[Axis.values().length];

        for (int slot = 0; slot < upgrades.size(); slot++) {
            ItemResource resource = upgrades.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            Axis axis = axisOf(resource.getItem());
            if (axis != null) {
                counts[axis.ordinal()] += Math.max(1, upgrades.getAmountAsInt(slot));
            }
        }

        return of(counts[Axis.CLONES.ordinal()], counts[Axis.RATE.ordinal()]);
    }

    /**
     * Builds a state from raw per-axis upgrade counts, clamped to their caps.
     */
    public static UpgradeState of(int clones, int rate) {
        return new UpgradeState(
                Math.clamp(1L + Math.max(0, clones), 1, MAX_CLONES),
                Math.clamp(1L + Math.max(0, rate), 1, MAX_RATE));
    }

    /** Which axis an item feeds, or null if it is not an upgrade. */
    public static @Nullable Axis axisOf(Item item) {
        if (item == ModItems.CHRONO_SPLITTER.get()) {
            return Axis.CLONES;
        }
        if (item == ModItems.CHRONO_ACCELERATOR.get()) {
            return Axis.RATE;
        }
        return null;
    }

    /** True if the given item contributes to any axis, for slot filtering. */
    public static boolean isUpgrade(Item item) {
        return axisOf(item) != null;
    }
}
