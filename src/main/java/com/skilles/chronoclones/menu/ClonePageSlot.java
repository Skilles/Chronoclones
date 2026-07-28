package com.skilles.chronoclones.menu;

import java.util.function.IntSupplier;

import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * One square of one clone's inventory. Every clone's squares share coordinates; only the selected
 * clone's are active, which is what vanilla draws and hit-tests against.
 */
public class ClonePageSlot extends ResourceHandlerSlot {

    private final int clone;
    private final IntSupplier selected;

    public ClonePageSlot(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier,
                         int slot, int x, int y, int clone, IntSupplier selected) {
        super(handler, modifier, slot, x, y);
        this.clone = clone;
        this.selected = selected;
    }

    @Override
    public boolean isActive() {
        return clone == selected.getAsInt();
    }
}
