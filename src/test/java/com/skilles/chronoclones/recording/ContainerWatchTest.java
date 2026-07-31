package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Set;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerWatchTest {

    private static ChronoAction.UseContainer.CarrierSlot slot(int menuSlot) {
        return new ChronoAction.UseContainer.CarrierSlot(menuSlot, ItemStack.EMPTY);
    }

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
        assertEquals(List.of(), ContainerWatch.carried(fullInventory(), Set.of(0, 3, 8)));
    }

    @Test
    @DisplayName("a click outside the window carries nothing extra")
    void outsideClickIsHarmless() {
        assertEquals(List.of(), ContainerWatch.carried(fullInventory(), Set.of(-1)));
    }

    @Test
    @DisplayName("several touched slots are all kept, in menu order")
    void multipleTouchedSlots() {
        List<ChronoAction.UseContainer.CarrierSlot> carried =
                ContainerWatch.carried(fullInventory(), Set.of(31, 60));

        assertEquals(2, carried.size(), "carried: " + carried);
        assertEquals(60, carried.get(0).menuSlot());
        assertEquals(31, carried.get(1).menuSlot());
    }
}
