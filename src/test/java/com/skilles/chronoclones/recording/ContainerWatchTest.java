package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Set;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a container session carries.
 *
 * <p>The carried list exists so replay can put the anchor's supply in the squares the recorded clicks
 * name. That makes it tempting to record the whole player inventory and be safe — but it is not safe:
 * replay refuses a session whose carried items the anchor lacks, so an over-broad list turns "move a
 * stack of sandstone" into "and also be holding a pickaxe, a map, and six other things".
 *
 * <p>These assertions are about that narrowing, which is why they check absence as much as presence.
 */
class ContainerWatchTest {

    /**
     * One occupied square. The stack is empty because it cannot be anything else here — an item's
     * default components are bound during datapack load, so {@code new ItemStack(Items.X)} throws in
     * the FML JUnit bootstrap. It costs nothing: {@link ContainerWatch#carried} decides purely on slot
     * index, and which stack survives the narrowing is asserted in {@code PrecisionGameTest}.
     */
    private static ChronoAction.UseContainer.CarrierSlot slot(int menuSlot) {
        return new ChronoAction.UseContainer.CarrierSlot(menuSlot, ItemStack.EMPTY);
    }

    /** A full player inventory, of which any one session touches almost nothing. */
    private static List<ChronoAction.UseContainer.CarrierSlot> fullInventory() {
        return List.of(slot(54), slot(55), slot(60), slot(31), slot(42));
    }

    @Test
    @DisplayName("only the slots the clicks name are carried")
    void untouchedSlotsAreDropped() {
        List<ChronoAction.UseContainer.CarrierSlot> carried =
                ContainerWatch.carried(fullInventory(), Set.of(60));

        assertEquals(1, carried.size(), "carried: " + carried);
        assertEquals(60, carried.getFirst().menuSlot());
    }

    @Test
    @DisplayName("a session that only clicks container slots carries nothing")
    void withdrawalCarriesNothing() {
        // Taking things out of a chest names chest slots throughout. Demanding the player's inventory
        // as a precondition would make a pure withdrawal impossible to run on a fresh anchor.
        assertEquals(List.of(), ContainerWatch.carried(fullInventory(), Set.of(0, 3, 8)));
    }

    @Test
    @DisplayName("a click outside the window carries nothing extra")
    void outsideClickIsHarmless() {
        // Dropping by clicking the background arrives as slot -1, which matches no square.
        assertEquals(List.of(), ContainerWatch.carried(fullInventory(), Set.of(-1)));
    }

    @Test
    @DisplayName("several touched slots are all kept, in menu order")
    void multipleTouchedSlots() {
        List<ChronoAction.UseContainer.CarrierSlot> carried =
                ContainerWatch.carried(fullInventory(), Set.of(31, 60));

        assertEquals(2, carried.size(), "carried: " + carried);
        // Snapshot order, not click order: this is a description of a layout, not of a sequence.
        assertEquals(60, carried.get(0).menuSlot());
        assertEquals(31, carried.get(1).menuSlot());
    }
}
