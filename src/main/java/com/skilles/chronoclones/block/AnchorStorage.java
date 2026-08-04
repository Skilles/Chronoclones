package com.skilles.chronoclones.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.skilles.chronoclones.inventory.CombinedInventory;
import com.skilles.chronoclones.inventory.StackInventory;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/** Everything an anchor holds: clone storage, banked experience, fuel, upgrades and charge. */
public final class AnchorStorage {

    public static final int CLONE_INVENTORY_SLOTS = Inventory.INVENTORY_SIZE;

    public static final int CLONE_INVENTORIES = UpgradeState.MAX_CLONES;

    public static final int UPGRADE_SLOTS = 3;

    private static final int LEGACY_INVENTORY_SLOTS = 18;

    private final Runnable onChanged;

    private final List<StackInventory> cloneInventories;

    private final List<ExperienceStore> cloneExperience =
            new ArrayList<>(Collections.nCopies(CLONE_INVENTORIES, ExperienceStore.EMPTY));

    private final Container combinedInventory;

    private final StackInventory fuelSlot;

    private final StackInventory upgradeSlots;

    private ChargeBuffer charge = ChargeBuffer.EMPTY;
    private UpgradeState upgrades = UpgradeState.BASE;

    public AnchorStorage(Runnable onChanged) {
        this.onChanged = onChanged;
        this.cloneInventories = new ArrayList<>(CLONE_INVENTORIES);
        for (int i = 0; i < CLONE_INVENTORIES; i++) {
            cloneInventories.add(watched(CLONE_INVENTORY_SLOTS));
        }
        this.combinedInventory = new CombinedInventory(cloneInventories, onChanged);
        this.fuelSlot = watched(1);
        this.upgradeSlots = watched(UPGRADE_SLOTS);
    }

    private StackInventory watched(int slots) {
        return new StackInventory(slots) {
            @Override
            protected void onContentsChanged(int index, @NonNull ItemStack previousContents) {
                onChanged.run();
            }
        };
    }

    public Container combined() {
        return combinedInventory;
    }

    public StackInventory cloneInventory(int clone) {
        return cloneInventories.get(clone);
    }

    public ExperienceStore cloneExperience(int clone) {
        return cloneExperience.get(clone);
    }

    public void setCloneExperience(int clone, ExperienceStore store) {
        cloneExperience.set(clone, store);
        onChanged.run();
    }

    public StackInventory fuel() {
        return fuelSlot;
    }

    public StackInventory upgradeSlots() {
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
        ItemStack fuel = fuelSlot.getItem(0);
        if (fuel.isEmpty()) {
            return;
        }

        if (fuel.getItem() == ModItems.CREATIVE_CHARGE_CELL.get()) {
            charge = charge.refill(charge.headroom());
            onChanged.run();
            return;
        }

        ItemStack probe = fuel.copyWithCount(1);
        int burnTicks = probe.getBurnTime(null, level.fuelValues());
        if (burnTicks <= 0) {
            return;
        }

        int gained = burnTicks * ChargeBuffer.CHARGE_PER_BURN_TICK;
        if (gained > charge.headroom()) {
            return;
        }

        if (fuelSlot.extract(0, 1).isEmpty()) {
            return;
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
            StackInventory inventory = cloneInventories.get(clone);
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemStack held = inventory.getItem(slot);
                if (held.isEmpty() || held.getCount() < inventory.capacity(slot, held)) {
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

    private static void spill(Level level, BlockPos pos, StackInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack held = inventory.getItem(slot);
            if (held.isEmpty()) {
                continue;
            }
            Containers.dropItemStack(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held.copy());
            inventory.setItem(slot, ItemStack.EMPTY);
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
        // Through an inventory of the old size: deserialize adopts the saved list wholesale.
        StackInventory legacy = new StackInventory(LEGACY_INVENTORY_SLOTS);
        legacy.deserialize(saved);

        StackInventory first = cloneInventories.getFirst();
        for (int slot = 0; slot < Math.min(legacy.size(), first.size()); slot++) {
            ItemStack held = legacy.getItem(slot);
            if (!held.isEmpty()) {
                first.setItem(slot, held);
            }
        }
    }
}
