package com.skilles.chronoclones.item;

import java.util.Optional;

import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

public final class ActionIcons {

    private ActionIcons() {}

    public static Optional<Holder<Item>> of(ChronoAction action) {
        return switch (action) {
            case ChronoAction.BreakBlock a -> ofBlock(a.expectedBlock().value());
            case ChronoAction.PlaceBlock a -> ofItem(a.item().value());
            case ChronoAction.UseOnBlock a -> ofItem(a.item().value());
            case ChronoAction.UseItem a -> ofItem(a.item().value());
            case ChronoAction.AttackEntity a -> creature(a.expectedType());
            case ChronoAction.InteractEntity a -> creature(a.expectedType());
            case ChronoAction.UseContainer a -> switch (a.target()) {
                case MenuTarget.Entity entity -> creature(entity.expectedType());
                case MenuTarget.Block block -> block.expectedBlock()
                        .flatMap(held -> ofBlock(held.value()));
            };
        };
    }

    private static Optional<Holder<Item>> creature(Holder<EntityType<?>> type) {
        //? if >=26 {
        return SpawnEggItem.byId(type.value());
        //?} else {
        /*SpawnEggItem egg = SpawnEggItem.byId(type.value());
        return egg == null ? Optional.empty() : ofItem(egg);
        *///?}
    }

    private static Optional<Holder<Item>> ofBlock(Block block) {
        return ofItem(block.asItem());
    }

    private static Optional<Holder<Item>> ofItem(Item item) {
        return item == Items.AIR
                ? Optional.empty()
                : Optional.of(BuiltInRegistries.ITEM.wrapAsHolder(item));
    }
}
