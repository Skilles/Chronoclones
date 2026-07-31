package com.skilles.chronoclones.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;

/**
 * Everything a Chrono Anchor holds: the clones' squares and banked experience, its fuel, its
 * upgrades, and the charge it has burned them into.
 *
 * <p>Split out of the block entity because none of it is about replaying a routine. The block entity
 * is still the only thing that owns one of these, and still decides when any of it is spent.
 *
 * @see ChronoAnchorBlockEntity
 */
public final class AnchorStorage {

    /** Shaped like a player's, so a recorded slot means the same thing on both sides. */
    public static final int CLONE_INVENTORY_SLOTS = Inventory.INVENTORY_SIZE;

    /** One per possible clone, allocated up front so a splitter coming and going resizes nothing. */
    public static final int CLONE_INVENTORIES = UpgradeState.MAX_CLONES;

    public static final int UPGRADE_SLOTS = 3;

    /** Anchors saved before clones had their own inventories held eighteen shared squares. */
    private static final int LEGACY_INVENTORY_SLOTS = 18;

    private final Runnable onChanged;

    private final List<ItemStacksResourceHandler> cloneInventories;

    /** What each clone has banked: what it mines and smelts, spent on what it enchants. */
    private final List<ExperienceStore> cloneExperience =
            new ArrayList<>(Collections.nCopies(CLONE_INVENTORIES, ExperienceStore.EMPTY));

    /** Every clone's inventory as one handler, for hoppers and pipes. */
    private final ResourceHandler<ItemResource> combinedInventory;

    /** One fuel item at a time; charge is drawn from it as it burns. */
    private final ItemStacksResourceHandler fuelSlot;

    private final ItemStacksResourceHandler upgradeSlots;

    private ChargeBuffer charge = ChargeBuffer.EMPTY;
    private UpgradeState upgrades = UpgradeState.BASE;

    public AnchorStorage(Runnable onChanged) {
        this.onChanged = onChanged;
        this.cloneInventories = new ArrayList<>(CLONE_INVENTORIES);
        for (int i = 0; i < CLONE_INVENTORIES; i++) {
            cloneInventories.add(watched(CLONE_INVENTORY_SLOTS));
        }
        this.combinedInventory = new CombinedResourceHandler<>(cloneInventories);
        this.fuelSlot = watched(1);
        this.upgradeSlots = watched(UPGRADE_SLOTS);
    }

    private ItemStacksResourceHandler watched(int slots) {
        return new ItemStacksResourceHandler(slots) {
            @Override
            protected void onContentsChanged(int index, @NonNull ItemStack previousContents) {
                onChanged.run();
            }
        };
    }

    // ------------------------------------------------------------------ access

    public ResourceHandler<ItemResource> combined() {
        return combinedInventory;
    }

    public ItemStacksResourceHandler cloneInventory(int clone) {
        return cloneInventories.get(clone);
    }

    public ExperienceStore cloneExperience(int clone) {
        return cloneExperience.get(clone);
    }

    public void setCloneExperience(int clone, ExperienceStore store) {
        cloneExperience.set(clone, store);
        onChanged.run();
    }

    public ItemStacksResourceHandler fuel() {
        return fuelSlot;
    }

    public ItemStacksResourceHandler upgradeSlots() {
        return upgradeSlots;
    }

    public ChargeBuffer charge() {
        return charge;
    }

    public UpgradeState upgrades() {
        return upgrades;
    }

    // ------------------------------------------------------------------ charge

    public boolean canAfford(int cost) {
        return charge.canAfford(cost);
    }

    public void spendCharge(int cost) {
        charge = charge.spend(cost);
        onChanged.run();
    }

    /**
     * Burns one fuel item if there is room for the charge it would produce.
     */
    public void consumeFuel(Level level) {
        if (charge.headroom() <= 0) {
            return;
        }
        ItemResource resource = fuelSlot.getResource(0);
        if (resource.isEmpty() || fuelSlot.getAmountAsInt(0) <= 0) {
            return;
        }

        // Creative cell: top up, never consume.
        if (resource.getItem() == ModItems.CREATIVE_CHARGE_CELL.get()) {
            charge = charge.refill(charge.headroom());
            onChanged.run();
            return;
        }

        ItemStack probe = resource.toStack(1);
        int burnTicks = probe.getBurnTime(null, level.fuelValues());
        if (burnTicks <= 0) {
            return;
        }

        int gained = burnTicks * ChargeBuffer.CHARGE_PER_BURN_TICK;
        if (gained > charge.headroom()) {
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            if (fuelSlot.extract(0, resource, 1, tx) != 1) {
                return;
            }
            tx.commit();
        }
        charge = charge.refill(gained);
        onChanged.run();
    }

    // ------------------------------------------------------------------ upgrades

    /**
     * Re-reads the upgrade slots, dropping the inventory of any clone that just went away.
     *
     * @return true if the number of clones changed, so the caller must rebuild its runtimes
     */
    public boolean reconcileUpgrades(ServerLevel level, BlockPos pos) {
        UpgradeState current = UpgradeState.from(upgradeSlots);
        int had = upgrades.cloneCount();
        upgrades = current;
        if (current.cloneCount() == had) {
            return false;
        }

        for (int clone = current.cloneCount(); clone < had; clone++) {
            spill(level, pos, cloneInventories.get(clone));
        }
        return true;
    }

    /** True if any active clone could still store something, used to clear an INVENTORY_FULL halt. */
    public boolean hasRoom() {
        for (int clone = 0; clone < upgrades.cloneCount(); clone++) {
            ItemStacksResourceHandler inventory = cloneInventories.get(clone);
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemResource resource = inventory.getResource(slot);
                if (resource.isEmpty()
                        || inventory.getAmountAsInt(slot) < inventory.getCapacityAsInt(slot, resource)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ spilling

    /**
     * Every clone's squares and banked experience, onto the ground above the anchor.
     *
     * <p>The storage belongs to the routine that filled it: leaving a heap of ore and a bank of
     * experience inside an anchor that no longer does anything is a way to lose both.
     */
    public void spillClones(Level level, BlockPos pos) {
        cloneInventories.forEach(clone -> spill(level, pos, clone));
        awardBanked(level, pos, true);
    }

    /** The same, plus the fuel and upgrades, for an anchor that is being broken. */
    public void spillEverything(Level level, BlockPos pos) {
        cloneInventories.forEach(clone -> spill(level, pos, clone));
        awardBanked(level, pos, false);
        spill(level, pos, fuelSlot);
        spill(level, pos, upgradeSlots);
    }

    /**
     * @param clear false when the anchor itself is going away, so there is nothing left to zero
     */
    private void awardBanked(Level level, BlockPos pos, boolean clear) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (int clone = 0; clone < CLONE_INVENTORIES; clone++) {
            ExperienceStore banked = cloneExperience.get(clone);
            if (banked.isEmpty()) {
                continue;
            }
            ExperienceOrb.award(serverLevel, Vec3.atCenterOf(pos), banked.points());
            if (clear) {
                cloneExperience.set(clone, ExperienceStore.EMPTY);
            }
        }
    }

    private static void spill(Level level, BlockPos pos, ItemStacksResourceHandler handler) {
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            int amount = handler.getAmountAsInt(slot);
            if (resource.isEmpty() || amount <= 0) {
                continue;
            }
            Containers.dropItemStack(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, resource.toStack(amount));
            handler.set(slot, ItemResource.EMPTY, 0);
        }
    }

    // ------------------------------------------------------------------ persistence

    private static String inventoryKey(int clone) {
        return "inventory_" + clone;
    }

    private static String experienceKey(int clone) {
        return "experience_" + clone;
    }

    public void save(ValueOutput output) {
        for (int clone = 0; clone < CLONE_INVENTORIES; clone++) {
            cloneInventories.get(clone).serialize(output.child(inventoryKey(clone)));
            output.store(experienceKey(clone), ExperienceStore.CODEC, cloneExperience.get(clone));
        }
        fuelSlot.serialize(output.child("fuel"));
        upgradeSlots.serialize(output.child("upgrades"));
        output.store("charge", ChargeBuffer.CODEC, charge);
    }

    public void load(ValueInput input) {
        for (int clone = 0; clone < CLONE_INVENTORIES; clone++) {
            int index = clone;
            input.child(inventoryKey(clone))
                    .ifPresent(child -> cloneInventories.get(index).deserialize(child));
        }
        for (int clone = 0; clone < CLONE_INVENTORIES; clone++) {
            cloneExperience.set(clone, input.read(experienceKey(clone), ExperienceStore.CODEC)
                    .orElse(ExperienceStore.EMPTY));
        }
        input.child("inventory").ifPresent(this::adoptLegacyInventory);
        input.child("fuel").ifPresent(fuelSlot::deserialize);
        input.child("upgrades").ifPresent(upgradeSlots::deserialize);
        charge = input.read("charge", ChargeBuffer.CODEC).orElse(ChargeBuffer.EMPTY);
    }

    /**
     * Moves an anchor saved before clones had their own inventories into the first one.
     */
    private void adoptLegacyInventory(ValueInput saved) {
        // Through a handler of the old size: deserialize adopts the saved list wholesale and would
        // otherwise shrink a clone's inventory to the 18 slots anchors used to have.
        ItemStacksResourceHandler legacy = new ItemStacksResourceHandler(LEGACY_INVENTORY_SLOTS);
        legacy.deserialize(saved);

        ItemStacksResourceHandler first = cloneInventories.getFirst();
        for (int slot = 0; slot < Math.min(legacy.size(), first.size()); slot++) {
            ItemResource resource = legacy.getResource(slot);
            if (!resource.isEmpty()) {
                first.set(slot, resource, legacy.getAmountAsInt(slot));
            }
        }
    }
}
