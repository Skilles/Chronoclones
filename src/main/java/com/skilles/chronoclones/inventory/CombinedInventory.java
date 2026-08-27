package com.skilles.chronoclones.inventory;

import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/** Several containers presented as one continuous run of slots. */
public final class CombinedInventory implements Container {

    private final List<? extends Container> parts;
    private final Runnable onChanged;
    private final int size;

    public CombinedInventory(List<? extends Container> parts, Runnable onChanged) {
        this.parts = parts;
        this.onChanged = onChanged;
        this.size = parts.stream().mapToInt(Container::getContainerSize).sum();
    }

    private Container partOf(int slot) {
        for (Container part : parts) {
            if (slot < part.getContainerSize()) {
                return part;
            }
            slot -= part.getContainerSize();
        }
        throw new IndexOutOfBoundsException("slot " + slot);
    }

    private int localSlot(int slot) {
        for (Container part : parts) {
            if (slot < part.getContainerSize()) {
                return slot;
            }
            slot -= part.getContainerSize();
        }
        throw new IndexOutOfBoundsException("slot " + slot);
    }

    @Override
    public int getContainerSize() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return parts.stream().allMatch(Container::isEmpty);
    }

    @Override
    @NonNull
    public ItemStack getItem(int slot) {
        return partOf(slot).getItem(localSlot(slot));
    }

    @Override
    @NonNull
    public ItemStack removeItem(int slot, int count) {
        return partOf(slot).removeItem(localSlot(slot), count);
    }

    @Override
    @NonNull
    public ItemStack removeItemNoUpdate(int slot) {
        return partOf(slot).removeItemNoUpdate(localSlot(slot));
    }

    @Override
    public void setItem(int slot, @NonNull ItemStack stack) {
        partOf(slot).setItem(localSlot(slot), stack);
    }

    @Override
    public void setChanged() {
        // A caller may have edited a live stack rather than going through setItem, so the parts
        // never saw it: the owner is told directly.
        onChanged.run();
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        parts.forEach(Container::clearContent);
    }
}
