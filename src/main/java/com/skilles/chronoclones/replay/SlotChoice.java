package com.skilles.chronoclones.replay;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Which slot a recorded click should actually land on.
 *
 * <p>A recording names a slot index, and most of the time that index is exactly right. But a furnace
 * whose input slot already has something in it, or a chest whose third square got filled since, are
 * ordinary states rather than errors — a player standing there would put the item in the next
 * sensible square and carry on. An anchor that instead reported "slot occupied" and stopped is
 * technically correct and useless.
 *
 * <p>So an anchor may substitute a slot unless it has been told the square itself is the point — a
 * sorting system, where landing one row over is the whole failure. That is the slot axis of
 * {@link TransferPrecision}, set per anchor rather than bought, because pinning a square only ever
 * narrows what a routine will touch.
 *
 * <h2>What "the same kind of slot" means</h2>
 *
 * <p>Two conditions, and both are necessary.
 *
 * <p>The candidate must accept the item, which is {@link Slot#mayPlace} — the menu's own rule,
 * so a mod's restrictions apply without this class knowing they exist.
 *
 * <p>And it must be the <em>same class</em> of slot as the one recorded. That sounds like a
 * heuristic and is in fact the sharpest tool available: a furnace's fuel slot is a
 * {@code FurnaceFuelSlot} and its input is a plain {@code Slot}, so class identity is precisely the
 * distinction between "another square that means the same thing" and "a different part of the
 * machine". Without it, coal that will not fit in the fuel slot would cheerfully go into the smelting
 * slot — which {@code mayPlace} permits, because a furnace will happily try to smelt coal. Chest
 * slots are all plain {@code Slot}s and so remain interchangeable, which is the right answer there
 * too.
 */
public final class SlotChoice {

    private SlotChoice() {}

    /**
     * The slot a click on {@code recordedIndex} should use.
     *
     * @param stack what is about to go in, for the menu's own {@code mayPlace} check
     * @return the index to click, which is {@code recordedIndex} unless that one is unusable and the
     *         anchor allows looking elsewhere
     */
    public static int resolve(AbstractContainerMenu menu, int recordedIndex, ItemStack stack,
                              TransferPrecision precision) {
        if (recordedIndex < 0 || recordedIndex >= menu.slots.size()) {
            return recordedIndex;
        }
        Slot recorded = menu.slots.get(recordedIndex);
        if (precision.slot() || fits(recorded, stack)) {
            return recordedIndex;
        }

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot candidate = menu.slots.get(index);
            if (index != recordedIndex && sameKind(recorded, candidate) && fits(candidate, stack)) {
                return index;
            }
        }
        // Nothing better exists. Returning the recorded index means the click still happens and
        // still fails, which keeps the diagnostic pointing at the slot the routine actually wanted.
        return recordedIndex;
    }

    /** Whether this slot would take the stack: right kind of slot, and room for it. */
    private static boolean fits(Slot slot, ItemStack stack) {
        if (!slot.mayPlace(stack)) {
            return false;
        }
        ItemStack existing = slot.getItem();
        if (existing.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(existing, stack)
                && existing.getCount() < Math.min(slot.getMaxStackSize(existing), existing.getMaxStackSize());
    }

    /**
     * Whether two slots are the same part of the machine.
     *
     * <p>Same container as well as same class: a chest menu's player rows and its chest rows are both
     * plain {@code Slot}s, and spilling one into the other would turn "put this in the chest" into
     * "keep it".
     */
    private static boolean sameKind(Slot recorded, Slot candidate) {
        return recorded.getClass() == candidate.getClass()
                && recorded.container == candidate.container;
    }
}
