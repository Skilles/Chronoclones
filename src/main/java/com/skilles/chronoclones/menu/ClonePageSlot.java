package com.skilles.chronoclones.menu;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import net.neoforged.neoforge.transfer.IndexModifier;
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
}
