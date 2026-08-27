package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.inventory.StackInventory;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.TransferRule;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import com.skilles.chronoclones.platform.ClonePlayer;
import org.jspecify.annotations.Nullable;

/** Lends a clone's items into the fake player for a container session, and drains them back. */
public final class ContainerCarrier {

    private ContainerCarrier() {}

    public static void load(StackInventory inventory, ClonePlayer player,
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
            ItemStack held = inventory.getItem(slot);
            if (held.isEmpty() || !rule.allows(held.getItem())) {
                continue;
            }
            int amount = held.getCount();

            int lent = Math.min(amount, budget);
            budget -= lent;

            target.setItem(slot, held.copyWithCount(lent));
            if (lent == amount) {
                inventory.setItem(slot, ItemStack.EMPTY);
            } else {
                inventory.setItem(slot, held.copyWithCount(amount - lent));
            }
        }
    }

    public static void drain(ServerLevel level, BlockPos anchorPos,
                             StackInventory inventory, ClonePlayer player,
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
                                StackInventory inventory, int slot, ItemStack stack) {
        if (slot < inventory.size() && inventory.getItem(slot).isEmpty()) {
            inventory.setItem(slot, stack);
            return;
        }
        give(level, anchorPos, inventory, stack);
    }

    private static void give(ServerLevel level, BlockPos anchorPos,
                             StackInventory inventory, ItemStack stack) {
        int stored = inventory.insert(stack, stack.getCount());

        int lost = stack.getCount() - stored;
        if (lost > 0) {
            Containers.dropItemStack(level, anchorPos.getX() + 0.5, anchorPos.getY() + 1.0,
                    anchorPos.getZ() + 0.5, stack.copyWithCount(lost));
        }
    }
}
