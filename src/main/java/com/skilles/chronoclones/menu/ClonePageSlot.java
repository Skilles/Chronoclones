package com.skilles.chronoclones.menu;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/** Every clone's squares share coordinates; only the selected clone's are active. */
public class ClonePageSlot extends Slot {

    private final int clone;
    private final IntSupplier selected;
    private final BooleanSupplier usable;

    public ClonePageSlot(Container container, int slot, int x, int y, int clone,
                         IntSupplier selected, BooleanSupplier usable) {
        super(container, slot, x, y);
        this.clone = clone;
        this.selected = selected;
        this.usable = usable;
    }

    @Override
    public boolean isActive() {
        return usable.getAsBoolean() && clone == selected.getAsInt();
    }

    /** A hidden square still takes a shift-click, and what it swallows looks lost until an
     * imprint reveals the storage again. */
    @Override
    public boolean mayPlace(@NonNull ItemStack stack) {
        return isActive() && super.mayPlace(stack);
    }
}
