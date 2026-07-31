package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.TransferRule;

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

/** Lends a clone's items into the fake player for a container session, and drains them back. */
public final class ContainerCarrier {

    private ContainerCarrier() {}

    public static void load(ItemStacksResourceHandler inventory, FakePlayer player,
                            AbstractContainerMenu menu, ActionSettings settings) {
        Inventory target = player.getInventory();
        target.clearContent();
        menu.setCarried(ItemStack.EMPTY);

        TransferRule rule = settings.transfer();
        int budget = rule.quantity().budget();

        for (int slot = 0; slot < Math.min(inventory.size(), Inventory.INVENTORY_SIZE); slot++) {
            if (budget <= 0) {
                return;
            }
            ItemResource resource = inventory.getResource(slot);
            int amount = inventory.getAmountAsInt(slot);
            if (resource.isEmpty() || amount <= 0 || !rule.allows(resource.getItem())) {
                continue;
            }

            int lent = Math.min(amount, budget);
            budget -= lent;

            target.setItem(slot, resource.toStack(lent));
            if (lent == amount) {
                inventory.set(slot, ItemResource.EMPTY, 0);
            } else {
                inventory.set(slot, resource, amount - lent);
            }
        }
    }

    public static void drain(ServerLevel level, BlockPos anchorPos,
                             ItemStacksResourceHandler inventory, FakePlayer player,
                             @Nullable AbstractContainerMenu menu) {
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
