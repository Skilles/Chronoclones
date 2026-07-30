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

/**
 * What an action is about, as something you can look at.
 *
 * <p>A coloured diamond says an action happens here; a chest says which chest. The item an action
 * already names is the picture of it, so nothing new has to be drawn or kept in step with it.
 *
 * <p>An item rather than a stack: this is asked on the server, where a stack would be built only to
 * be taken apart again, and it is empty where an action has nothing to show rather than a stack of
 * air that every caller would have to check for anyway.
 */
public final class ActionIcons {

    private ActionIcons() {}

    public static Optional<Holder<Item>> of(ChronoAction action) {
        return switch (action) {
            case ChronoAction.BreakBlock a -> ofBlock(a.expectedBlock().value());
            case ChronoAction.PlaceBlock a -> ofItem(a.item().value());
            case ChronoAction.UseOnBlock a -> ofItem(a.item().value());
            case ChronoAction.UseItem a -> ofItem(a.item().value());
            // The creature, not what was held: a bucket says nothing about which cow.
            case ChronoAction.AttackEntity a -> creature(a.expectedType());
            case ChronoAction.InteractEntity a -> creature(a.expectedType());
            case ChronoAction.UseContainer a -> switch (a.target()) {
                case MenuTarget.Entity entity -> creature(entity.expectedType());
                // Empty for sessions recorded before the block was kept, which fall back to a mark.
                case MenuTarget.Block block -> block.expectedBlock()
                        .flatMap(held -> ofBlock(held.value()));
            };
        };
    }

    /** A spawn egg is the one thing in the game that is a picture of a kind of creature. */
    private static Optional<Holder<Item>> creature(Holder<EntityType<?>> type) {
        return SpawnEggItem.byId(type.value());
    }

    /** Blocks with nothing to pick up -- fire, a piston head -- have no picture to offer. */
    private static Optional<Holder<Item>> ofBlock(Block block) {
        return ofItem(block.asItem());
    }

    private static Optional<Holder<Item>> ofItem(Item item) {
        return item == Items.AIR
                ? Optional.empty()
                : Optional.of(BuiltInRegistries.ITEM.wrapAsHolder(item));
    }
}
