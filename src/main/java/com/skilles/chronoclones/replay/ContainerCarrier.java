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
     * <p>How closely the staged items have to match what was recorded is
     * {@link TransferPrecision} — see {@link #choose} and {@link #draw} for the two axes it governs
     * here.
     *
     * @return false if a layout entry found nothing at all to stage
     */
    public static boolean load(ItemStacksResourceHandler inventory, FakePlayer player,
                               AbstractContainerMenu menu,
                               List<ChronoAction.UseContainer.CarrierSlot> layout,
                               TransferPrecision precision) {
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

        // Which squares staging has spoken for, so the leftovers below cannot land in one. Vanilla's
        // Inventory.add merges into the first partial stack it finds, which for a routine staging
        // five diamonds into a slot and holding twenty-seven more meant the slot quietly became
        // thirty-two — undoing the count that was just decided and shift-clicking the lot away.
        boolean[] claimed = new boolean[Inventory.INVENTORY_SIZE];

        // Two passes, and the order is the whole point. The first gives every square the amount the
        // recording used, so each one gets its share; only then does the second hand out what is
        // left over. Doing it in one pass let the first square take the entire stock — "quantity is
        // incidental" means take as much as there is — and every square after it found an empty
        // pool and reported the routine unstocked. A session with two carried squares could not run
        // at all, which is most of them.
        List<Staged> staged = new ArrayList<>(layout.size());
        for (ChronoAction.UseContainer.CarrierSlot wanted : layout) {
            if (wanted.menuSlot() >= menu.slots.size()) {
                continue;
            }
            Slot slot = menu.slots.get(wanted.menuSlot());
            if (slot.container != target) {
                continue;
            }

            ItemStack share = draw(pool, wanted.stack(), precision, wanted.stack().getCount());
            if (share.isEmpty()) {
                // The routine needs something the anchor is not stocked with. Report it rather than
                // running a session whose clicks will land on empty squares and quietly do nothing.
                //
                // This is the only failure the precision flags can produce, and it survives all eight
                // of them: an empty square is not a less specific transfer, it is no transfer.
                //
                // The anchor has already been emptied into the pool by this point, so bailing out
                // without this line destroyed everything it was holding — a routine that was merely
                // missing one ingredient would eat the other seventeen stacks. Spilling into the
                // player hands them to the drain in the caller's finally, which puts them back.
                spill(pool, target, claimed);
                return false;
            }
            staged.add(new Staged(slot.getContainerSlot(), share));
        }

        for (Staged entry : staged) {
            ItemStack stack = entry.stack();
            if (!precision.quantity()) {
                // Every square has had its share by now, so the surplus can go out without starving
                // one. A single square is still the ceiling.
                stack = gather(pool, stack, stack, stack.getMaxStackSize());
            }
            target.setItem(entry.inventorySlot(), stack);
            if (entry.inventorySlot() < claimed.length) {
                claimed[entry.inventorySlot()] = true;
            }
        }

        // Everything the layout did not claim, wherever it fits.
        spill(pool, target, claimed);
        return true;
    }

    /**
     * Moves whatever is left of the pool into the player, where {@link #drain} can find it.
     *
     * <p>Into squares staging is not using, and without merging. Both of those matter: a leftover
     * that topped up a staged slot would change the amount the session goes on to move, which is the
     * one thing staging had just finished deciding.
     */
    private static void spill(List<ItemStack> pool, Inventory target, boolean[] claimed) {
        for (ItemStack leftover : pool) {
            if (leftover.isEmpty()) {
                continue;
            }
            int free = firstFree(target, claimed);
            if (free < 0) {
                // Every free square is spoken for. Topping up a staged slot is wrong; losing the
                // stack is worse, and drain hands it all back to the anchor either way.
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
     * Takes one slot's worth out of the pool: which item, and how much of it.
     *
     * <p>The two questions are the item and quantity axes of {@link TransferPrecision}, and they are
     * answered in that order — what to take, then how much — because a slot holds one kind of thing
     * and mixing two would satisfy neither.
     *
     * <p>Gathering runs across the whole pool once a kind has been settled on, since the anchor may
     * be holding the same item spread over several of its own slots.
     */
    private static ItemStack draw(List<ItemStack> pool, ItemStack wanted, TransferPrecision precision,
                                  int limit) {
        ItemStack kind = choose(pool, wanted, precision);
        if (kind.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return gather(pool, kind, ItemStack.EMPTY, Math.min(limit, kind.getMaxStackSize()));
    }

    /**
     * Moves up to {@code limit} of {@code kind} out of the pool, adding it to {@code sofar}.
     *
     * <p>{@code kind} is passed separately rather than read off {@code sofar} because an empty stack
     * reports its item as air however it was built, so a running total cannot describe what it is a
     * total of until it has something in it.
     */
    private static ItemStack gather(List<ItemStack> pool, ItemStack kind, ItemStack sofar, int limit) {
        ItemStack drawn = sofar;
        for (int i = 0; i < pool.size() && drawn.getCount() < limit; i++) {
            ItemStack candidate = pool.get(i);
            if (candidate.isEmpty() || !ItemStack.isSameItemSameComponents(candidate, kind)) {
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

    /** One square's worth, decided but not yet written into the player. */
    private record Staged(int inventorySlot, ItemStack stack) {}

    /**
     * Which of the anchor's items this slot gets, as a ladder from most to least specific.
     *
     * <p>The recorded stack, then the same item ignoring components, then anything at all — with an
     * anchor that is specific about items stopping after the first rung.
     *
     * <p>The ladder matters more than the leniency does. "Take anything" implemented as "take
     * whatever is first in the pool" would let a correctly stocked anchor stage the wrong item purely
     * because of the order its own slots happened to be in, turning a working routine into a broken
     * one for no reason a player could see. Leniency is a fallback, not a coin toss.
     */
    private static ItemStack choose(List<ItemStack> pool, ItemStack wanted, TransferPrecision precision) {
        for (ItemStack candidate : pool) {
            if (!candidate.isEmpty() && ItemStack.isSameItemSameComponents(candidate, wanted)) {
                return candidate;
            }
        }
        if (precision.item()) {
            return ItemStack.EMPTY;
        }
        for (ItemStack candidate : pool) {
            if (!candidate.isEmpty() && candidate.is(wanted.getItem())) {
                return candidate;
            }
        }
        for (ItemStack candidate : pool) {
            if (!candidate.isEmpty()) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
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
