package com.skilles.chronoclones.inventory;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.skilles.chronoclones.io.DataIn;
import com.skilles.chronoclones.io.DataOut;
import org.jspecify.annotations.NonNull;

/**
 * A fixed-size list of stacks behind a vanilla {@link Container} face.
 *
 * <p>Persists under the same {@code "stacks"} key and stack codec the NeoForge handler this
 * class replaced used, so worlds saved before the change load unchanged.
 */
public class StackInventory implements Container {

    private static final String VALUE_IO_KEY = "stacks";

    private NonNullList<ItemStack> stacks;

    public StackInventory(int size) {
        this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    /** Called after a slot's contents change, with what the slot held before. */
    protected void onContentsChanged(int index, ItemStack previousContents) {}

    @Override
    public int getContainerSize() {
        return stacks.size();
    }

    /** Alias so call sites can stay close to the old handler's vocabulary. */
    public int size() {
        return stacks.size();
    }

    @Override
    public boolean isEmpty() {
        return stacks.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    @NonNull
    public ItemStack getItem(int slot) {
        return stacks.get(slot);
    }

    @Override
    @NonNull
    public ItemStack removeItem(int slot, int count) {
        ItemStack current = stacks.get(slot);
        if (current.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack previous = current.copy();
        ItemStack taken = current.split(count);
        if (current.isEmpty()) {
            stacks.set(slot, ItemStack.EMPTY);
        }
        if (!taken.isEmpty()) {
            onContentsChanged(slot, previous);
        }
        return taken;
    }

    @Override
    @NonNull
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = stacks.get(slot);
        stacks.set(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int slot, @NonNull ItemStack stack) {
        ItemStack previous = stacks.set(slot, stack);
        onContentsChanged(slot, previous);
    }

    @Override
    public void setChanged() {
        // The live-stack mutation path: a caller edited getItem's return in place. The slot's
        // prior contents are unknowable here, so the change is reported against what is there now.
        onContentsChanged(0, stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst());
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < stacks.size(); slot++) {
            if (!stacks.get(slot).isEmpty()) {
                setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    /** Matches the old handler's capacity: the item's own limit, never above the absolute cap. */
    public int capacity(int slot, ItemStack stack) {
        return stack.isEmpty()
                ? Item.ABSOLUTE_MAX_STACK_SIZE
                : Math.min(stack.getMaxStackSize(), Item.ABSOLUTE_MAX_STACK_SIZE);
    }

    /**
     * Inserts up to {@code amount} of {@code stack} into one slot.
     *
     * @return how much went in, {@code 0} if the slot holds something else or is full
     */
    public int insert(int slot, ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemStack current = stacks.get(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) {
            return 0;
        }

        int inserted = Math.min(amount, capacity(slot, stack) - current.getCount());
        if (inserted <= 0) {
            return 0;
        }

        ItemStack previous = current.copy();
        stacks.set(slot, stack.copyWithCount(current.getCount() + inserted));
        onContentsChanged(slot, previous);
        return inserted;
    }

    /** First slot to last, like the old handler's index-order insert. */
    public int insert(ItemStack stack, int amount) {
        int inserted = 0;
        for (int slot = 0; slot < stacks.size() && inserted < amount; slot++) {
            inserted += insert(slot, stack, amount - inserted);
        }
        return inserted;
    }

    public int insert(ItemStack stack) {
        return insert(stack, stack.getCount());
    }

    /** Takes up to {@code amount} out of one slot. */
    @NonNull
    public ItemStack extract(int slot, int amount) {
        return removeItem(slot, amount);
    }

    /**
     * Stores every stack whole, or none of them: the all-or-nothing insert the old code got
     * from transaction rollback.
     */
    public boolean insertAllOrNothing(List<ItemStack> toStore) {
        NonNullList<ItemStack> snapshot = copyToList();
        for (ItemStack stack : toStore) {
            if (stack.isEmpty()) {
                continue;
            }
            if (insert(stack, stack.getCount()) < stack.getCount()) {
                restore(snapshot);
                return false;
            }
        }
        return true;
    }

    private void restore(NonNullList<ItemStack> snapshot) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            stacks.set(slot, snapshot.get(slot));
        }
        // One notification for the batch; per-slot attribution is gone with the rollback.
        if (!stacks.isEmpty()) {
            onContentsChanged(0, stacks.getFirst());
        }
    }

    @NonNull
    public NonNullList<ItemStack> copyToList() {
        NonNullList<ItemStack> copy = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < stacks.size(); slot++) {
            copy.set(slot, stacks.get(slot).copy());
        }
        return copy;
    }

    public void serialize(DataOut output) {
        output.store(VALUE_IO_KEY, ItemStack.OPTIONAL_CODEC.listOf(), stacks);
    }

    /** Adopts the saved list wholesale, size included, exactly as the old handler did. */
    public void deserialize(DataIn input) {
        input.read(VALUE_IO_KEY, ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(saved -> {
            NonNullList<ItemStack> adopted = NonNullList.withSize(saved.size(), ItemStack.EMPTY);
            for (int slot = 0; slot < saved.size(); slot++) {
                adopted.set(slot, saved.get(slot));
            }
            stacks = adopted;
        });
    }
}
