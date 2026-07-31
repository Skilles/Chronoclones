package com.skilles.chronoclones.replay;

import com.skilles.chronoclones.block.DiagnosticState.FailureReason;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Lends an item out of a clone's storage for one action and takes back what returns. */
public final class HeldItemLoan {

    private HeldItemLoan() {}

    public record Loan(int slot, ItemStack stack) {}

    public static final Loan EMPTY_HANDED = new Loan(-1, ItemStack.EMPTY);

    public static Loan take(ResourceHandler<ItemResource> inventory, Item item, SlotRule rule) {
        return take(inventory, ItemMatch.sameItem(item), rule);
    }

    /** Takes the whole of one matching slot, so the action can put back what it does not use. */
    public static Loan take(ResourceHandler<ItemResource> inventory, ItemMatch match, SlotRule rule) {
        if (match.isEmptyHanded()) {
            return EMPTY_HANDED;
        }

        Loan preferred = takeFrom(inventory, match, rule.preferred());
        if (preferred != null || rule.strict()) {
            return preferred;
        }

        for (int slot = 0; slot < inventory.size(); slot++) {
            Loan loan = takeFrom(inventory, match, slot);
            if (loan != null) {
                return loan;
            }
        }
        return null;
    }

    public static @org.jspecify.annotations.Nullable ItemStack peek(
            ResourceHandler<ItemResource> inventory, Item item, SlotRule rule) {
        return peek(inventory, ItemMatch.sameItem(item), rule);
    }

    /**
     * What the rule would lend, without lending it.
     *
     * <p>For actions that only read what they hold. A swing neither consumes nor damages a tool,
     * and taking it out and putting it back on every tick of a long dig would be churn.
     */
    public static @org.jspecify.annotations.Nullable ItemStack peek(
            ResourceHandler<ItemResource> inventory, ItemMatch match, SlotRule rule) {
        if (match.isEmptyHanded()) {
            return ItemStack.EMPTY;
        }

        ItemStack preferred = peekAt(inventory, match, rule.preferred());
        if (preferred != null || rule.strict()) {
            return preferred;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack found = peekAt(inventory, match, slot);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @org.jspecify.annotations.Nullable ItemStack peekAt(
            ResourceHandler<ItemResource> inventory, ItemMatch match, int slot) {
        if (slot < 0 || slot >= inventory.size()) {
            return null;
        }
        ItemResource resource = inventory.getResource(slot);
        int amount = inventory.getAmountAsInt(slot);
        if (resource.isEmpty() || !match.accepts(resource) || amount <= 0) {
            return null;
        }
        return resource.toStack(amount);
    }

    private static @org.jspecify.annotations.Nullable Loan takeFrom(
            ResourceHandler<ItemResource> inventory, ItemMatch match, int slot) {
        if (slot < 0 || slot >= inventory.size()) {
            return null;
        }
        ItemResource resource = inventory.getResource(slot);
        if (resource.isEmpty() || !match.accepts(resource)) {
            return null;
        }
        int amount = inventory.getAmountAsInt(slot);
        if (amount <= 0) {
            return null;
        }

        try (Transaction tx = Transaction.openRoot()) {
            int taken = inventory.extract(slot, resource, amount, tx);
            if (taken <= 0) {
                return null;
            }
            tx.commit();
            return new Loan(slot, resource.toStack(taken));
        }
    }

    /** Back to the slot it came from where possible, and on the ground rather than lost. */
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

    public static FailureReason missingItemReason() {
        return FailureReason.NO_ITEM;
    }
}
