package com.skilles.chronoclones.block;

import java.util.function.BooleanSupplier;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.NonNull;

/**
 * The clone storage as hoppers and pipes see it. Insertion waits for an imprint: the screen
 * hides storage until then, so anything pushed in early would look lost. Extraction always
 * works, or a cleared anchor could strand what its clones had gathered.
 */
final class GatedInventory implements ResourceHandler<ItemResource> {

    private final ResourceHandler<ItemResource> inventory;
    private final BooleanSupplier open;

    GatedInventory(ResourceHandler<ItemResource> inventory, BooleanSupplier open) {
        this.inventory = inventory;
        this.open = open;
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    @NonNull
    public ItemResource getResource(int index) {
        return inventory.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return inventory.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, @NonNull ItemResource resource) {
        return inventory.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, @NonNull ItemResource resource) {
        return open.getAsBoolean() && inventory.isValid(index, resource);
    }

    @Override
    public int insert(int index, @NonNull ItemResource resource, int amount,
                      @NonNull TransactionContext transaction) {
        return open.getAsBoolean() ? inventory.insert(index, resource, amount, transaction) : 0;
    }

    @Override
    public int insert(@NonNull ItemResource resource, int amount,
                      @NonNull TransactionContext transaction) {
        return open.getAsBoolean() ? inventory.insert(resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, @NonNull ItemResource resource, int amount,
                       @NonNull TransactionContext transaction) {
        return inventory.extract(index, resource, amount, transaction);
    }

    @Override
    public int extract(@NonNull ItemResource resource, int amount,
                       @NonNull TransactionContext transaction) {
        return inventory.extract(resource, amount, transaction);
    }
}
