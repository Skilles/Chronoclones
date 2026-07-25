package com.skilles.chronoclones.registry;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.item.ChronoRecorderItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Chronoclones.MODID);

    public static final DeferredItem<BlockItem> CHRONO_ANCHOR = ITEMS.registerSimpleBlockItem(ModBlocks.CHRONO_ANCHOR);

    public static final DeferredItem<ChronoRecorderItem> CHRONO_RECORDER = ITEMS.registerItem(
            "chrono_recorder",
            ChronoRecorderItem::new,
            (Item.Properties props) -> props.stacksTo(1).rarity(Rarity.UNCOMMON));

    private ModItems() {}
}
