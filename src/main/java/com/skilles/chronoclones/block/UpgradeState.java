package com.skilles.chronoclones.block;

import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The anchor's capabilities, derived from whatever is sitting in its upgrade slots.
 *
 * <p>Upgrades are items rather than block tiers specifically to avoid a combinatorial crafting
 * tree: five independent axes as five block variants would be unshippable, whereas five item types
 * that stack in slots is one recipe each.
 *
 * <p>Derived on read rather than stored, so pulling an upgrade out takes effect immediately and
 * there is no cached state to fall out of sync with the slots.
 */
public record UpgradeState(int cloneCount, int ticksPerStep, int fidelityTier) {

    /** A bare anchor: one clone, one tick per step, mining only. */
    public static final UpgradeState BASE = new UpgradeState(1, 1, 0);

    public static final int MAX_CLONES = 4;
    public static final int MAX_RATE = 3;
    public static final int MAX_FIDELITY = 3;

    /** The three independent upgrade axes. */
    public enum Axis {
        CLONES,
        RATE,
        FIDELITY
    }

    /**
     * Reads the upgrade slots and totals each axis.
     *
     * <p>A thin adapter over {@link #of}: it only turns slot contents into per-axis counts. The
     * clamping logic lives in {@code of} so it can be unit tested — constructing an
     * {@code ItemResource} from an {@code Item} builds an {@code ItemStack} internally, which needs
     * datapack-bound components and therefore cannot happen outside a running game.
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

        return of(counts[Axis.CLONES.ordinal()], counts[Axis.RATE.ordinal()],
                counts[Axis.FIDELITY.ordinal()]);
    }

    /**
     * Builds a state from raw per-axis upgrade counts, clamped to their caps.
     *
     * <p>Multiple copies of the same upgrade stack, which is what makes three slots a real choice:
     * three splitters buys four clones but no combat and no speed.
     */
    public static UpgradeState of(int clones, int rate, int fidelity) {
        return new UpgradeState(
                Math.clamp(1L + Math.max(0, clones), 1, MAX_CLONES),
                Math.clamp(1L + Math.max(0, rate), 1, MAX_RATE),
                Math.clamp(Math.max(0, fidelity), 0, MAX_FIDELITY));
    }

    /** Which axis an item feeds, or null if it is not an upgrade. */
    public static @Nullable Axis axisOf(Item item) {
        if (item == ModItems.CHRONO_SPLITTER.get()) {
            return Axis.CLONES;
        }
        if (item == ModItems.CHRONO_ACCELERATOR.get()) {
            return Axis.RATE;
        }
        if (item == ModItems.CHRONO_FOCUS.get()) {
            return Axis.FIDELITY;
        }
        return null;
    }

    /** True if the given item contributes to any axis, for slot filtering. */
    public static boolean isUpgrade(Item item) {
        return axisOf(item) != null;
    }
}
