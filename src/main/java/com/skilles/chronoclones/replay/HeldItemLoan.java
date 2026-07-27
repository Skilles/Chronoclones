package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Lends an item out of the anchor's inventory for the duration of one interaction, and takes back
 * whatever comes home.
 */
public final class HeldItemLoan {

    private HeldItemLoan() {}

    /**
     * A stack taken out of the anchor, plus where it came from.
     *
     * @param slot  the slot it was extracted from, so the remainder goes back where it belongs
     * @param stack the stack now in the fake player's hand; never empty
     */
    public record Loan(int slot, ItemStack stack) {}

    /** Marker for an action recorded with an empty hand, which needs no loan and always succeeds. */
    public static final Loan EMPTY_HANDED = new Loan(-1, ItemStack.EMPTY);

    /**
     * Takes the whole of one matching slot out of the anchor.
     *
     * @return the loan, or null if the anchor has none of {@code item}
     */
    public static Loan take(ResourceHandler<ItemResource> inventory, Item item) {
        if (item == net.minecraft.world.item.Items.AIR) {
            return EMPTY_HANDED;
        }

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemResource resource = inventory.getResource(slot);
            if (resource.isEmpty() || resource.getItem() != item) {
                continue;
            }
            int amount = inventory.getAmountAsInt(slot);
            if (amount <= 0) {
                continue;
            }

            try (Transaction tx = Transaction.openRoot()) {
                int taken = inventory.extract(slot, resource, amount, tx);
                if (taken <= 0) {
                    continue;
                }
                tx.commit();
                return new Loan(slot, resource.toStack(taken));
            }
        }
        return null;
    }

    /**
     * Puts {@code remainder} back, preferring the slot it came from.
     */
    public static void giveBack(ServerLevel level, BlockPos anchorPos,
                                ResourceHandler<ItemResource> inventory, Loan loan, ItemStack remainder) {
        if (remainder.isEmpty()) {
            return;
        }

        ItemResource resource = ItemResource.of(remainder);
        try (Transaction tx = Transaction.openRoot()) {
            int stored = loan.slot() >= 0
                    ? inventory.insert(loan.slot(), resource, remainder.getCount(), tx)
                    : 0;
            if (stored < remainder.getCount()) {
                stored += inventory.insert(resource, remainder.getCount() - stored, tx);
            }
            tx.commit();

            int lost = remainder.getCount() - stored;
            if (lost > 0) {
                Containers.dropItemStack(level, anchorPos.getX() + 0.5, anchorPos.getY() + 1.0,
                        anchorPos.getZ() + 0.5, resource.toStack(lost));
            }
        }
    }

    /** The failure to report when the anchor is not stocked with what the routine needs. */
    public static FailureReason missingItemReason() {
        return FailureReason.NO_ITEM;
    }
}
