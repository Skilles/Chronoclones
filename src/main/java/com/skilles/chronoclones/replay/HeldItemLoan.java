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
     * Takes the whole of one matching slot out of the anchor, as {@code rule} allows.
     *
     * @return the loan, or null if the anchor has none of {@code item} where it may look
     */
    public static Loan take(ResourceHandler<ItemResource> inventory, Item item, SlotRule rule) {
        if (item == net.minecraft.world.item.Items.AIR) {
            return EMPTY_HANDED;
        }

        Loan preferred = takeFrom(inventory, item, rule.preferred());
        if (preferred != null || rule.strict()) {
            return preferred;
        }

        // Stock rarely lands where the recording left it, so the slot is a preference by default.
        for (int slot = 0; slot < inventory.size(); slot++) {
            Loan loan = takeFrom(inventory, item, slot);
            if (loan != null) {
                return loan;
            }
        }
        return null;
    }

    /**
     * The stack {@code rule} would lend, without lending it.
     *
     * <p>For an action that only ever reads what it is holding -- swinging a pickaxe damages
     * nothing and consumes nothing -- where taking the tool out and putting it back every tick of a
     * ten-second dig would be churn for its own sake.
     *
     * @return the stack, {@link ItemStack#EMPTY} for an action recorded empty-handed, or null when
     *         the anchor has none of {@code item} anywhere the rule may look
     */
    public static @org.jspecify.annotations.Nullable ItemStack peek(
            ResourceHandler<ItemResource> inventory, Item item, SlotRule rule) {
        if (item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack preferred = peekAt(inventory, item, rule.preferred());
        if (preferred != null || rule.strict()) {
            return preferred;
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack found = peekAt(inventory, item, slot);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @org.jspecify.annotations.Nullable ItemStack peekAt(
            ResourceHandler<ItemResource> inventory, Item item, int slot) {
        if (slot < 0 || slot >= inventory.size()) {
            return null;
        }
        ItemResource resource = inventory.getResource(slot);
        int amount = inventory.getAmountAsInt(slot);
        if (resource.isEmpty() || resource.getItem() != item || amount <= 0) {
            return null;
        }
        return resource.toStack(amount);
    }

    private static @org.jspecify.annotations.Nullable Loan takeFrom(
            ResourceHandler<ItemResource> inventory, Item item, int slot) {
        if (slot < 0 || slot >= inventory.size()) {
            return null;
        }
        ItemResource resource = inventory.getResource(slot);
        if (resource.isEmpty() || resource.getItem() != item) {
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
