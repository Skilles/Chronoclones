package com.skilles.chronoclones.registry;

import java.util.function.Supplier;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.item.CreativeChargeCellItem;
import com.skilles.chronoclones.item.ChronoGogglesItem;
import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.item.ChronoShardItem;
import com.skilles.chronoclones.item.UpgradeItem;
import com.skilles.chronoclones.platform.Registrar;

import net.minecraft.world.item.BlockItem;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
//? if >=26 {
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
//?}
import net.minecraft.world.item.Rarity;

public final class ModItems {

    public static final Registrar<Item> ITEMS = Registrar.create(BuiltInRegistries.ITEM, Chronoclones.MODID);

    //? if >=26 {
    private static final ResourceKey<EquipmentAsset> GOGGLES_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, Chronoclones.id("chrono_goggles"));
    //?}

    /** The id-bound properties every item is built from, matching what the loaders' sugar did. */
    private static Item.Properties props(String name) {
        //? if >=26 {
        return new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Chronoclones.id(name)));
        //?} else {
        /*return new Item.Properties();
        *///?}
    }

    public static final Supplier<BlockItem> CHRONO_ANCHOR = ITEMS.register("chrono_anchor",
            () -> new BlockItem(ModBlocks.CHRONO_ANCHOR.get(),
                    //? if >=26 {
                    props("chrono_anchor").useBlockDescriptionPrefix()));
                    //?} else {
                    /*props("chrono_anchor")));
                    *///?}

    public static final Supplier<ChronoRecorderItem> CHRONO_RECORDER = ITEMS.register("chrono_recorder",
            () -> new ChronoRecorderItem(props("chrono_recorder")
                    .stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final Supplier<ChronoShardItem> CHRONO_SHARD = ITEMS.register("chrono_shard",
            () -> new ChronoShardItem(props("chrono_shard")
                    .stacksTo(16).rarity(Rarity.UNCOMMON)));

    public static final Supplier<UpgradeItem> CHRONO_SPLITTER = upgrade("chrono_splitter");

    public static final Supplier<UpgradeItem> CHRONO_ACCELERATOR = upgrade("chrono_accelerator");

    public static final Supplier<ChronoGogglesItem> CHRONO_GOGGLES = ITEMS.register("chrono_goggles",
            //? if >=26 {
            () -> new ChronoGogglesItem(props("chrono_goggles")
                    .stacksTo(1).rarity(Rarity.RARE)
                    .component(DataComponents.EQUIPPABLE,
                            Equippable.builder(EquipmentSlot.HEAD)
                                    .setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC)
                                    .setAsset(GOGGLES_ASSET)
                                    .setSwappable(true)
                                    .build())));
            //?} else {
            /*() -> new ChronoGogglesItem(props("chrono_goggles")
                    .stacksTo(1).rarity(Rarity.RARE)));
            *///?}

    public static final Supplier<CreativeChargeCellItem> CREATIVE_CHARGE_CELL = ITEMS.register(
            "creative_charge_cell",
            () -> new CreativeChargeCellItem(props("creative_charge_cell")
                    .stacksTo(1).rarity(Rarity.EPIC)));

    private static Supplier<UpgradeItem> upgrade(String name) {
        return ITEMS.register(name,
                () -> new UpgradeItem(props(name).stacksTo(16).rarity(Rarity.UNCOMMON),
                        "tooltip.chronoclones." + name));
    }

    public static void init() {}

    private ModItems() {}
}
