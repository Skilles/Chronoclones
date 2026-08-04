package com.skilles.chronoclones.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The loader's automation view of a block inventory — what a hopper or pipe would see — so the
 * tests exercising the external insertion path stay loader-neutral.
 */
final class TestItemPipes {

    private TestItemPipes() {}

    //? if neoforge {
    private static net.neoforged.neoforge.transfer.@org.jspecify.annotations.Nullable ResourceHandler<
            net.neoforged.neoforge.transfer.item.ItemResource> handler(
            ServerLevel level, BlockPos absolutePos) {
        return level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, absolutePos, null);
    }

    static boolean present(ServerLevel level, BlockPos absolutePos) {
        return handler(level, absolutePos) != null;
    }

    /** Inserts wherever fits, first slot to last; @return how much went in. */
    static int insert(ServerLevel level, BlockPos absolutePos, Item item, int amount) {
        var found = handler(level, absolutePos);
        if (found == null) {
            return 0;
        }
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int inserted = found.insert(
                    net.neoforged.neoforge.transfer.item.ItemResource.of(item), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    static void insertIntoSlot(ServerLevel level, BlockPos absolutePos, int slot,
                               Item item, int amount) {
        var found = handler(level, absolutePos);
        if (found == null) {
            return;
        }
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            found.insert(slot, net.neoforged.neoforge.transfer.item.ItemResource.of(item), amount, tx);
            tx.commit();
        }
    }

    /** A copy of what the automation view shows in one slot, or EMPTY. */
    static ItemStack slot(ServerLevel level, BlockPos absolutePos, int slot) {
        var found = handler(level, absolutePos);
        if (found == null || slot >= found.size()) {
            return ItemStack.EMPTY;
        }
        var resource = found.getResource(slot);
        return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(found.getAmountAsInt(slot));
    }

    static int count(ServerLevel level, BlockPos absolutePos, Item item) {
        var found = handler(level, absolutePos);
        if (found == null) {
            return 0;
        }
        int total = 0;
        for (int index = 0; index < found.size(); index++) {
            var resource = found.getResource(index);
            if (!resource.isEmpty() && resource.getItem() == item) {
                total += found.getAmountAsInt(index);
            }
        }
        return total;
    }
    //?} else {
    /*private static net.fabricmc.fabric.api.transfer.v1.storage.@org.jspecify.annotations.Nullable Storage<
            net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> handler(
            ServerLevel level, BlockPos absolutePos) {
        return net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, absolutePos, null);
    }

    static boolean present(ServerLevel level, BlockPos absolutePos) {
        return handler(level, absolutePos) != null;
    }

    // Inserts wherever fits, first slot to last; returns how much went in.
    static int insert(ServerLevel level, BlockPos absolutePos, Item item, int amount) {
        var found = handler(level, absolutePos);
        if (found == null) {
            return 0;
        }
        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long inserted = found.insert(
                    net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(item), amount, tx);
            tx.commit();
            return (int) inserted;
        }
    }

    static void insertIntoSlot(ServerLevel level, BlockPos absolutePos, int slot,
                               Item item, int amount) {
        var found = handler(level, absolutePos);
        if (!(found instanceof net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage<
                net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> slotted)
                || slot >= slotted.getSlotCount()) {
            return;
        }
        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            slotted.getSlot(slot).insert(
                    net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(item), amount, tx);
            tx.commit();
        }
    }

    // A copy of what the automation view shows in one slot, or EMPTY.
    static ItemStack slot(ServerLevel level, BlockPos absolutePos, int slot) {
        var found = handler(level, absolutePos);
        if (!(found instanceof net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage<
                net.fabricmc.fabric.api.transfer.v1.item.ItemVariant> slotted)
                || slot >= slotted.getSlotCount()) {
            return ItemStack.EMPTY;
        }
        var view = slotted.getSlot(slot);
        return view.isResourceBlank()
                ? ItemStack.EMPTY
                : view.getResource().toStack((int) view.getAmount());
    }

    static int count(ServerLevel level, BlockPos absolutePos, Item item) {
        var found = handler(level, absolutePos);
        if (found == null) {
            return 0;
        }
        int total = 0;
        for (var view : found) {
            if (!view.isResourceBlank() && view.getResource().getItem() == item) {
                total += (int) view.getAmount();
            }
        }
        return total;
    }
    *///?}
}
