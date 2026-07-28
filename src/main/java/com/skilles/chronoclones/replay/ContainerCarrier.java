package com.skilles.chronoclones.replay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Lends a clone's inventory to the fake player for the length of one container session.
 *
 * <p>A clone's inventory is shaped like a player's, so the recorded clicks land on the squares the
 * player clicked without anything having to be staged into place.
 */
public final class ContainerCarrier {

    private ContainerCarrier() {}

    /** Moves the clone's inventory into the fake player, square for square. */
    public static void load(ItemStacksResourceHandler inventory, FakePlayer player,
                            AbstractContainerMenu menu) {
        Inventory target = player.getInventory();
        target.clearContent();
        menu.setCarried(ItemStack.EMPTY);

        for (int slot = 0; slot < Math.min(inventory.size(), Inventory.INVENTORY_SIZE); slot++) {
            ItemResource resource = inventory.getResource(slot);
            int amount = inventory.getAmountAsInt(slot);
            if (resource.isEmpty() || amount <= 0) {
                continue;
            }
            target.setItem(slot, resource.toStack(amount));
            inventory.set(slot, ItemResource.EMPTY, 0);
        }
    }

    /**
     * Moves everything the fake player is holding back, dropping what will not fit.
     */
    public static void drain(ServerLevel level, BlockPos anchorPos,
                             ItemStacksResourceHandler inventory, FakePlayer player,
                             @Nullable AbstractContainerMenu menu) {
        // The cursor first: only vanilla's removed() promises to hand it back.
        if (menu != null) {
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty()) {
                menu.setCarried(ItemStack.EMPTY);
                give(level, anchorPos, inventory, carried);
            }
        }

        Inventory source = player.getInventory();
        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            source.setItem(slot, ItemStack.EMPTY);
            restore(level, anchorPos, inventory, slot, stack);
        }
    }

    /**
     * Puts a stack back in the square it was lent from, or anywhere at all if that has been taken.
     *
     * <p>Slots past the inventory proper are armour and the offhand, which a session can reach with
     * a swap and which have nowhere of their own to come home to.
     */
    private static void restore(ServerLevel level, BlockPos anchorPos,
                                ItemStacksResourceHandler inventory, int slot, ItemStack stack) {
        if (slot < inventory.size() && inventory.getResource(slot).isEmpty()) {
            inventory.set(slot, ItemResource.of(stack), stack.getCount());
            return;
        }
        give(level, anchorPos, inventory, stack);
    }

    private static void give(ServerLevel level, BlockPos anchorPos,
                             ItemStacksResourceHandler inventory, ItemStack stack) {
        ItemResource resource = ItemResource.of(stack);
        int stored;
        try (Transaction tx = Transaction.openRoot()) {
            stored = inventory.insert(resource, stack.getCount(), tx);
            tx.commit();
        }

        int lost = stack.getCount() - stored;
        if (lost > 0) {
            Containers.dropItemStack(level, anchorPos.getX() + 0.5, anchorPos.getY() + 1.0,
                    anchorPos.getZ() + 0.5, resource.toStack(lost));
        }
    }
}
