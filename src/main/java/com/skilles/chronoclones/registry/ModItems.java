package com.skilles.chronoclones.registry;

import com.skilles.chronoclones.Chronoclones;
import com.skilles.chronoclones.item.CreativeChargeCellItem;
import com.skilles.chronoclones.item.ChronoGogglesItem;
import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.item.ChronoShardItem;
import com.skilles.chronoclones.item.UpgradeItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.Equippable;
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

    /** Transfer medium: blank until inscribed, then imprintable onto any number of anchors. */
    public static final DeferredItem<ChronoShardItem> CHRONO_SHARD = ITEMS.registerItem(
            "chrono_shard",
            ChronoShardItem::new,
            (Item.Properties props) -> props.stacksTo(16).rarity(Rarity.UNCOMMON));

    // Upgrades. Items in slots rather than block tiers, so five independent axes do not
    // become a combinatorial crafting tree.

    /** +1 clone, distributed along the timeline by phase offset. The visual showpiece. */
    public static final DeferredItem<UpgradeItem> CHRONO_SPLITTER = upgrade("chrono_splitter");

    /** Faster replay: raises ticksPerStep. */
    public static final DeferredItem<UpgradeItem> CHRONO_ACCELERATOR = upgrade("chrono_accelerator");

    /** Unlocks action types: break -> +place -> +attack -> +use. */
    public static final DeferredItem<UpgradeItem> CHRONO_FOCUS = upgrade("chrono_focus");

    /**
     * Tightens block matching: a lenient anchor breaks only the block it recorded.
     *
     * <p>Blocks only. How specific an anchor is about item transfers is three checkboxes in its own
     * screen, not an upgrade — see {@code TransferPrecision}.
     */
    public static final DeferredItem<UpgradeItem> CHRONO_LENS = upgrade("chrono_lens");

    /**
     * Worn on the head: shows every anchor's routine in range, not just the one you are staring at.
     *
     * <p>Not an upgrade — it goes on the player rather than into a slot, because what it changes is
     * what <em>you</em> can see rather than what an anchor can do.
     */
    public static final DeferredItem<ChronoGogglesItem> CHRONO_GOGGLES = ITEMS.registerItem(
            "chrono_goggles",
            ChronoGogglesItem::new,
            (Item.Properties props) -> props.stacksTo(1).rarity(Rarity.RARE)
                    .component(DataComponents.EQUIPPABLE,
                            Equippable.builder(EquipmentSlot.HEAD)
                                    .setEquipSound(SoundEvents.ARMOR_EQUIP_GENERIC)
                                    // No asset: nothing renders on the head. A model would be an art
                                    // task for a debug tool, and an unset asset is simply invisible.
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
