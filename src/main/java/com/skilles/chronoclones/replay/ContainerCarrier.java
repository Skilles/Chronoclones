package com.skilles.chronoclones.replay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Lends the anchor's inventory to the fake player for the length of one container session.
 *
 * <p>A container menu is built around a player's inventory — that is what the bottom half of every
 * chest screen is, and what shift-click moves things into. Replaying a session through the real menu
 * therefore needs the anchor's contents to actually <em>be</em> the player's contents while the
 * clicks run, and to come back afterwards.
 *
 * <p>Same idea as {@link HeldItemLoan}, scaled from one stack to the whole inventory, and for the
 * same reason: the only way to get a mod's behaviour right is to give it the real thing and take
 * back whatever it hands you.
 *
 * <p>The fake player is shared per owner per level, so both halves are mandatory and
 * {@link #drain} belongs in a {@code finally}. Leaving items in it would strand them somewhere no
 * player can reach, and hand them to whichever of that owner's anchors acts next.
 */
public final class ContainerCarrier {

    private ContainerCarrier() {}

    /**
     * Copies the anchor's inventory into the fake player, emptying the anchor.
     *
     * <p>Emptying is deliberate: if the anchor kept a copy, a session that consumed items would
     * duplicate them on the way back.
     */
    public static void load(ItemStacksResourceHandler inventory, FakePlayer player) {
        Inventory target = player.getInventory();
        target.clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemResource resource = inventory.getResource(slot);
            int amount = inventory.getAmountAsInt(slot);
            if (resource.isEmpty() || amount <= 0) {
                continue;
            }
            if (slot < target.getContainerSize()) {
                target.setItem(slot, resource.toStack(amount));
            }
            inventory.set(slot, ItemResource.EMPTY, 0);
        }
    }

    /**
     * Moves everything the fake player is holding back into the anchor, dropping what will not fit.
     *
     * <p>Overflow should be unreachable — the anchor was emptied to fill the player — but a session
     * can end holding more than it started with, and voiding a player's items to keep an invariant
     * tidy is not a trade worth making.
     */
    public static void drain(ServerLevel level, BlockPos anchorPos,
                             ItemStacksResourceHandler inventory, FakePlayer player) {
        Inventory source = player.getInventory();

        for (int slot = 0; slot < source.getContainerSize(); slot++) {
            ItemStack stack = source.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            source.setItem(slot, ItemStack.EMPTY);
            give(level, anchorPos, inventory, stack);
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            give(level, anchorPos, inventory, carried);
        }
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
