package com.skilles.chronoclones.registry;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.item.CreativeChargeCellItem;
import com.skilles.chronoclones.item.ChronoGogglesItem;
import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.item.ChronoShardItem;
import com.skilles.chronoclones.item.UpgradeItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Chronoclones.MODID);

    /**
     * Names {@code assets/chronoclones/equipment/chrono_goggles.json}, and through it the layer texture.
     */
    private static final ResourceKey<EquipmentAsset> GOGGLES_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, Chronoclones.id("chrono_goggles"));

    public static final DeferredItem<BlockItem> CHRONO_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.CHRONO_ANCHOR);

    public static final DeferredItem<ChronoRecorderItem> CHRONO_RECORDER = ITEMS.registerItem(
            "chrono_recorder",
            ChronoRecorderItem::new,
            (Item.Properties props) -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    /** Transfer medium: blank until inscribed, then imprintable onto any number of anchors. */
    public static final DeferredItem<ChronoShardItem> CHRONO_SHARD = ITEMS.registerItem(
            "chrono_shard",
            ChronoShardItem::new,
            (Item.Properties props) -> props.stacksTo(16).rarity(Rarity.UNCOMMON));

    // Upgrades as items in slots rather than block tiers, to avoid a combinatorial recipe tree.

    /** +1 clone, distributed along the timeline by phase offset. The visual showpiece. */
    public static final DeferredItem<UpgradeItem> CHRONO_SPLITTER = upgrade("chrono_splitter");

    /** Faster replay: raises ticksPerStep. */
    public static final DeferredItem<UpgradeItem> CHRONO_ACCELERATOR = upgrade("chrono_accelerator");

    /** Unlocks action types: break -> +place -> +attack -> +use. */

    /**
     * Worn on the head: shows every anchor's routine in range, not only the one under the crosshair.
     */
    public static final DeferredItem<ChronoGogglesItem> CHRONO_GOGGLES = ITEMS.registerItem(
            "chrono_goggles",
            ChronoGogglesItem::new,
            (Item.Properties props) -> props.stacksTo(1).rarity(Rarity.RARE)
                    .component(DataComponents.EQUIPPABLE,
                            Equippable.builder(EquipmentSlot.HEAD)
                                    .setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC)
                                    // Without an asset, HumanoidArmorLayer.shouldRender is false
                                    // and CustomHeadLayer draws the inventory icon as a slab
                                    // floating above the skull.
                                    .setAsset(GOGGLES_ASSET)
                                    .setSwappable(true)
                                    .build()));


    /** Creative-only: keeps an anchor charged so the rest of the system can be observed. */
    public static final DeferredItem<CreativeChargeCellItem> CREATIVE_CHARGE_CELL = ITEMS.registerItem(
            "creative_charge_cell",
            CreativeChargeCellItem::new,
            (Item.Properties props) -> props.stacksTo(1).rarity(Rarity.EPIC));

    private static DeferredItem<UpgradeItem> upgrade(String name) {
        return ITEMS.registerItem(name,
                (Item.Properties props) -> new UpgradeItem(props, "tooltip.chronoclones." + name),
                (Item.Properties props) -> props.stacksTo(16).rarity(Rarity.UNCOMMON));
    }

    private ModItems() {}
}
