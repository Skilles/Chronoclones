package com.skilles.chronoclones.inventory;

import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * The clone storage as hoppers and pipes see it. Insertion waits for an imprint: the screen
 * hides storage until then, so anything pushed in early would look lost. Extraction always
 * works, or a cleared anchor could strand what its clones had gathered.
 */
public final class GatedContainer implements WorldlyContainer {

    private final Container inventory;
    private final BooleanSupplier open;
    private int[] allSlots = new int[0];

    public GatedContainer(Container inventory, BooleanSupplier open) {
        this.inventory = inventory;
        this.open = open;
    }

    @Override
    public int[] getSlotsForFace(@NonNull Direction side) {
        if (allSlots.length != inventory.getContainerSize()) {
            allSlots = IntStream.range(0, inventory.getContainerSize()).toArray();
        }
        return allSlots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, @NonNull ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, @NonNull ItemStack stack, @NonNull Direction side) {
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, @NonNull ItemStack stack) {
        return open.getAsBoolean() && inventory.canPlaceItem(slot, stack);
    }

    @Override
    public int getContainerSize() {
        return inventory.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return inventory.isEmpty();
    }

    @Override
    @NonNull
    public ItemStack getItem(int slot) {
        return inventory.getItem(slot);
    }

    @Override
    @NonNull
    public ItemStack removeItem(int slot, int count) {
        return inventory.removeItem(slot, count);
    }

    @Override
    @NonNull
    public ItemStack removeItemNoUpdate(int slot) {
        return inventory.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, @NonNull ItemStack stack) {
        inventory.setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        inventory.setChanged();
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return inventory.stillValid(player);
    }

    @Override
    public void clearContent() {
        inventory.clearContent();
    }
}
