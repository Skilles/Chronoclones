package com.skilles.chronoclones.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.skilles.chronoclones.registry.ModItems;
import com.skilles.chronoclones.replay.CloneRuntime;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Capabilities are derived from slot contents on every read. These tests cover that derivation
 * and the caps that stop a stack of splitters spawning unbounded clones.
 */
class UpgradeStateTest {

    /**
     * Counts upgrades by axis the way {@code UpgradeState.from} does, without building an
     * {@code ItemResource}, which constructs an {@code ItemStack} internally, which needs
     * datapack-bound components and so cannot happen in a unit test.
     */
    private static UpgradeState install(net.minecraft.world.item.Item... items) {
        int[] counts = new int[UpgradeState.Axis.values().length];
        for (net.minecraft.world.item.Item item : items) {
            UpgradeState.Axis axis = UpgradeState.axisOf(item);
            if (axis != null) {
                counts[axis.ordinal()]++;
            }
        }
        return UpgradeState.of(counts[0], counts[1]);
    }

    @Test
    @DisplayName("an anchor with empty slots is the base state: one clone, one tick per step")
    void emptySlotsGiveBaseState() {
        UpgradeState state = install();

        assertEquals(1, state.cloneCount());
        assertEquals(1, state.ticksPerStep());
    }

    @Test
    @DisplayName("each splitter adds a clone")
    void splittersAddClones() {
        assertEquals(2, install(ModItems.CHRONO_SPLITTER.get()).cloneCount());
        assertEquals(3, install(ModItems.CHRONO_SPLITTER.get(), ModItems.CHRONO_SPLITTER.get()).cloneCount());
    }

    @Test
    @DisplayName("axes are independent, so the slots are a genuine choice")
    void axesAreIndependent() {
        UpgradeState state = install(
                ModItems.CHRONO_SPLITTER.get(),
                ModItems.CHRONO_ACCELERATOR.get());

        assertEquals(2, state.cloneCount());
        assertEquals(2, state.ticksPerStep());
    }

    @Test
    @DisplayName("filling every slot with splitters trades away all other capability")
    void allInOneAxisCostsTheOthers() {
        UpgradeState state = install(
                ModItems.CHRONO_SPLITTER.get(),
                ModItems.CHRONO_SPLITTER.get(),
                ModItems.CHRONO_SPLITTER.get());

        assertEquals(4, state.cloneCount());
        assertEquals(1, state.ticksPerStep(), "no speed");
    }

    @Test
    @DisplayName("every axis is capped, so a stacked slot cannot run away")
    void axesAreCapped() {
        UpgradeState state = UpgradeState.of(64, 64);

        assertEquals(UpgradeState.MAX_CLONES, state.cloneCount());
        assertEquals(UpgradeState.MAX_RATE, state.ticksPerStep());
    }

    @Test
    @DisplayName("non-upgrade items in the slots contribute nothing")
    void junkItemsDoNothing() {
        assertEquals(UpgradeState.BASE, install(Items.DIRT, Items.DIAMOND));
    }

    @Test
    @DisplayName("isUpgrade recognises exactly the upgrade items")
    void isUpgradeIsExact() {
        assertTrue(UpgradeState.isUpgrade(ModItems.CHRONO_SPLITTER.get()));
        assertTrue(UpgradeState.isUpgrade(ModItems.CHRONO_ACCELERATOR.get()));

        assertFalse(UpgradeState.isUpgrade(Items.DIAMOND));
        assertFalse(UpgradeState.isUpgrade(ModItems.CHRONO_GOGGLES.get()),
                "the goggles are worn rather than slotted, so they are not an upgrade");
        assertFalse(UpgradeState.isUpgrade(ModItems.CHRONO_RECORDER.get()));
    }

    @Test
    @DisplayName("the maximum clone count still distributes evenly along the timeline")
    void maxClonesStayEvenlySpaced() {
        int length = 200;
        int count = UpgradeState.MAX_CLONES;

        int previous = -1;
        for (int i = 0; i < count; i++) {
            int offset = CloneRuntime.phaseOffsetFor(i, count, length);
            assertTrue(offset > previous, "offsets must strictly increase");
            assertTrue(offset < length);
            previous = offset;
        }
    }
}
