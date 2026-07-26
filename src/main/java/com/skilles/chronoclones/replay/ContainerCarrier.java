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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

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
     * Sets the stage: empties the anchor into the fake player, with the recorded items in the slots
     * the session's clicks expect.
     *
     * <p>Placement is the whole job. A click on a player slot names a square whose contents are an
     * accident of where that player kept things, so dumping the anchor's supply in from index zero
     * leaves every recorded deposit clicking somewhere empty. The recorded layout says which square
     * held what, and this puts it back there.
     *
     * <p>Anything the layout did not ask for still comes along, in whatever slots are left, so a
     * session that shift-clicks its whole inventory into a chest still has one.
     *
     * <p>Emptying the anchor is deliberate: if it kept a copy, a session that consumed items would
     * duplicate them on the way back.
     *
     * @return false if the layout named an item the anchor does not have at all
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

        for (ChronoAction.UseContainer.CarrierSlot wanted : layout) {
            if (wanted.menuSlot() >= menu.slots.size()) {
                continue;
            }
            Slot slot = menu.slots.get(wanted.menuSlot());
            if (slot.container != target) {
                continue;
            }

            ItemStack staged = draw(pool, wanted.item().value(), wanted.count());
            if (staged.isEmpty()) {
                // The routine needs something the anchor is not stocked with. Report it rather than
                // running a session whose clicks will land on empty squares and quietly do nothing.
                return false;
            }
            target.setItem(slot.getContainerSlot(), staged);
        }

        // Everything the layout did not claim, wherever it fits.
        for (ItemStack leftover : pool) {
            if (!leftover.isEmpty()) {
                target.add(leftover);
            }
        }
        return true;
    }

    /** Takes up to {@code count} of {@code item} out of the pool. */
    private static ItemStack draw(List<ItemStack> pool, Item item, int count) {
        ItemStack drawn = ItemStack.EMPTY;
        for (int i = 0; i < pool.size() && drawn.getCount() < count; i++) {
            ItemStack candidate = pool.get(i);
            if (candidate.isEmpty() || !candidate.is(item)) {
                continue;
            }
            int take = Math.min(candidate.getCount(), count - drawn.getCount());
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
     *
     * <p>Overflow should be unreachable — the anchor was emptied to fill the player — but a session
     * can end holding more than it started with, and voiding a player's items to keep an invariant
     * tidy is not a trade worth making.
     */
    public static void drain(ServerLevel level, BlockPos anchorPos,
                             ItemStacksResourceHandler inventory, FakePlayer player,
                             @Nullable AbstractContainerMenu menu) {
        // The cursor first. removed() normally hands it back to the inventory, but only vanilla's
        // implementation promises that, and a stack left on a cursor nobody can see is gone.
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
