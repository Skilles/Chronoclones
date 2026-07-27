package com.skilles.chronoclones.replay;

import java.util.ArrayList;
import java.util.List;

import com.skilles.chronoclones.recording.ChronoAction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * Lends the anchor's inventory to the fake player for the length of one container session.
 */
public final class ContainerCarrier {

    private ContainerCarrier() {}

    /**
     * Sets the stage: empties the anchor into the fake player, with the recorded items in the slots
     * the session's clicks expect.
     *
     * @return false if a layout entry found nothing at all to stage
     */
    public static boolean load(ItemStacksResourceHandler inventory, FakePlayer player,
                               AbstractContainerMenu menu,
                               List<ChronoAction.UseContainer.CarrierSlot> layout) {
        Inventory target = player.getInventory();
        target.clearContent();
        menu.setCarried(ItemStack.EMPTY);

        // Drain the anchor first, so staging and leftovers draw from the same pool and nothing is
        // counted twice.
        List<ItemStack> pool = new ArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemResource resource = inventory.getResource(slot);
            int amount = inventory.getAmountAsInt(slot);
            if (!resource.isEmpty() && amount > 0) {
                pool.add(resource.toStack(amount));
            }
            inventory.set(slot, ItemResource.EMPTY, 0);
        }

        // Which squares staging has claimed, so leftovers cannot land in one. Inventory.add
        // merges into the first partial stack it finds, which would undo the staged count.
        boolean[] claimed = new boolean[Inventory.INVENTORY_SIZE];

        for (ChronoAction.UseContainer.CarrierSlot wanted : layout) {
            if (wanted.menuSlot() >= menu.slots.size()) {
                continue;
            }
            Slot slot = menu.slots.get(wanted.menuSlot());
            if (slot.container != target) {
                continue;
            }

            // Bounded by what the recording held, which is also what stops one square emptying the
            // anchor and leaving the next one to report the routine unstocked.
            ItemStack staged = draw(pool, wanted.stack());
            if (staged.isEmpty()) {
                // The anchor is already emptied into the pool, so bailing out without spilling
                // would destroy everything it was holding.
                spill(pool, target, claimed);
                return false;
            }
            target.setItem(slot.getContainerSlot(), staged);
            if (slot.getContainerSlot() < claimed.length) {
                claimed[slot.getContainerSlot()] = true;
            }
        }

        // Everything the layout did not claim, wherever it fits.
        spill(pool, target, claimed);
        return true;
    }

    /**
     * Moves whatever is left of the pool into the player, where {@link #drain} can find it.
     */
    private static void spill(List<ItemStack> pool, Inventory target, boolean[] claimed) {
        for (ItemStack leftover : pool) {
            if (leftover.isEmpty()) {
                continue;
            }
            int free = firstFree(target, claimed);
            if (free < 0) {
                // Topping up a staged slot is wrong; losing the stack is worse.
                target.add(leftover);
                continue;
            }
            target.setItem(free, leftover);
        }
    }

    private static int firstFree(Inventory target, boolean[] claimed) {
        for (int slot = 0; slot < claimed.length; slot++) {
            if (!claimed[slot] && target.getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Takes up to {@code wanted}'s count of {@code wanted}'s item out of the pool.
     */
    private static ItemStack draw(List<ItemStack> pool, ItemStack wanted) {
        int limit = Math.min(wanted.getCount(), wanted.getMaxStackSize());

        ItemStack drawn = ItemStack.EMPTY;
        for (int i = 0; i < pool.size() && drawn.getCount() < limit; i++) {
            ItemStack candidate = pool.get(i);
            if (candidate.isEmpty() || !candidate.is(wanted.getItem())) {
                continue;
            }
            int take = Math.min(candidate.getCount(), limit - drawn.getCount());
            if (drawn.isEmpty()) {
                drawn = candidate.copyWithCount(take);
            } else {
                drawn.grow(take);
            }
            candidate.shrink(take);
        }
        return drawn;
    }

    /**
     * Moves everything the fake player is holding back into the anchor, dropping what will not fit.
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
            give(level, anchorPos, inventory, stack);
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
