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

/** Everything an anchor holds: clone storage, banked experience, fuel, upgrades and charge. */
public final class AnchorStorage {

    public static final int CLONE_INVENTORY_SLOTS = Inventory.INVENTORY_SIZE;

    public static final int CLONE_INVENTORIES = UpgradeState.MAX_CLONES;

    public static final int UPGRADE_SLOTS = 3;

    private static final int LEGACY_INVENTORY_SLOTS = 18;

    private final Runnable onChanged;

    private final List<ItemStacksResourceHandler> cloneInventories;

    private final List<ExperienceStore> cloneExperience =
            new ArrayList<>(Collections.nCopies(CLONE_INVENTORIES, ExperienceStore.EMPTY));

    private final ResourceHandler<ItemResource> combinedInventory;

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

    public boolean canAfford(int cost) {
        return charge.canAfford(cost);
    }

    public void spendCharge(int cost) {
        charge = charge.spend(cost);
        onChanged.run();
    }

    public void consumeFuel(Level level) {
        if (charge.headroom() <= 0) {
            return;
        }
        ItemResource resource = fuelSlot.getResource(0);
        if (resource.isEmpty() || fuelSlot.getAmountAsInt(0) <= 0) {
            return;
        }

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

    /** @return true if the clone count changed, so the caller must rebuild its runtimes */
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

    public void spillClones(Level level, BlockPos pos) {
        cloneInventories.forEach(clone -> spill(level, pos, clone));
        awardBanked(level, pos, true);
    }

    public void spillEverything(Level level, BlockPos pos) {
        cloneInventories.forEach(clone -> spill(level, pos, clone));
        awardBanked(level, pos, false);
        spill(level, pos, fuelSlot);
        spill(level, pos, upgradeSlots);
    }

    /** @param clear false when the anchor itself is going away, so there is nothing to zero */
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

    private void adoptLegacyInventory(ValueInput saved) {
        // Through a handler of the old size: deserialize adopts the saved list wholesale.
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
