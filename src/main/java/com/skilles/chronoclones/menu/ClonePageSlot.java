package com.skilles.chronoclones.menu;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.IndexModifier;
import org.jspecify.annotations.NonNull;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/** Every clone's squares share coordinates; only the selected clone's are active. */
public class ClonePageSlot extends ResourceHandlerSlot {

    private final int clone;
    private final IntSupplier selected;
    private final BooleanSupplier usable;

    public ClonePageSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier,
                         int slot, int x, int y, int clone, IntSupplier selected,
                         BooleanSupplier usable) {
        super(handler, modifier, slot, x, y);
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
