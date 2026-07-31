package com.skilles.chronoclones.recording;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A semantic action captured during recording.
 */
public sealed interface ChronoAction {

    ChronoActionType type();

    default int chargeCost() {
        return type().chargeCost();
    }

    /**
     * What the player was visibly holding when they did this, for the clone to hold too.
     */
    default ItemStack heldTemplate() {
        return switch (this) {
            case BreakBlock a -> a.toolTemplate();
            case AttackEntity a -> a.weaponTemplate();
            case PlaceBlock a -> new ItemStack(a.item());
            case UseOnBlock a -> new ItemStack(a.item());
            case UseItem a -> new ItemStack(a.item());
            case InteractEntity a -> new ItemStack(a.item());
            // Working a chest is reaching into it, not brandishing something.
            case UseContainer ignored -> ItemStack.EMPTY;
        };
    }

    // ------------------------------------------------------------------------------

    record BreakBlock(BlockPos localPos, Holder<Block> expectedBlock, ItemStack toolTemplate)
            implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.BREAK_BLOCK;
        }
    }

    record PlaceBlock(BlockPos localPos, Direction localFace, Holder<Item> item, BlockState expectedResult)
            implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.PLACE_BLOCK;
        }
    }

    /** {@code expectedType} is a hint used to pick the best target, not a hard requirement. */
    record AttackEntity(Vec3 localPos, Holder<EntityType<?>> expectedType, ItemStack weaponTemplate)
            implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.ATTACK_ENTITY;
        }
    }

    /**
     * Right-clicking a block, replayed through the server's own interaction entry point.
     *
     * @param expectedBlock what was standing there, so a routine can insist on it: a hoe told to
     *                      till dirt should say so rather than striking whatever grew back. Empty
     *                      for a session recorded before there was anything to record it with.
     */
    record UseOnBlock(BlockPos localPos, Direction localFace, Vec3 localHitOffset, boolean inside,
                      InteractionHand hand, Holder<Item> item,
                      Optional<Holder<Block>> expectedBlock) implements ChronoAction {

        public UseOnBlock(BlockPos localPos, Direction localFace, Vec3 localHitOffset, boolean inside,
                          InteractionHand hand, Holder<Item> item) {
            this(localPos, localFace, localHitOffset, inside, hand, item, Optional.empty());
        }

        @Override
        public ChronoActionType type() {
            return ChronoActionType.USE_ON_BLOCK;
        }
    }

    /** Right-clicking with nothing targeted: throwing, eating, firing a bow. */
    /**
     * Right-clicking with nothing targeted.
     *
     * @param holdTicks how long the player held it down, or 0 for a use that finished the instant
     *                  it started. A bow, a crossbow, a trident, food and a shield all have a
     *                  duration, and replaying one as an instant click could never fire, eat or
     *                  block anything -- so the time is part of what was recorded.
     */
    record UseItem(InteractionHand hand, Holder<Item> item, int holdTicks) implements ChronoAction {

        public UseItem(InteractionHand hand, Holder<Item> item) {
            this(hand, item, 0);
        }

        /** True for the items that have to be held rather than clicked. */
        public boolean isHeld() {
            return holdTicks > 0;
        }

        public UseItem heldFor(int ticks) {
            return new UseItem(hand, item, Math.max(0, ticks));
        }

        @Override
        public ChronoActionType type() {
            return ChronoActionType.USE_ITEM;
        }
    }

    /**
     * Right-clicking an entity: shearing, milking, feeding, trading, a mod's interactable mob.
     */
    record InteractEntity(Vec3 localPos, Holder<EntityType<?>> expectedType,
                          InteractionHand hand, Holder<Item> item) implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.INTERACT_ENTITY;
        }
    }

    /**
     * A container session, as the steps the player worked through rather than net movement: "take
     * 32" would bake in what the chest held that day, where "take half" is a thing to do to a chest.
     */
    record UseContainer(MenuTarget target, int menuSize, List<CarrierSlot> carrier,
                        List<SessionStep> steps) implements ChronoAction {

        /** The count is a target for staging; the steps operate on whatever is there. */
        public record CarrierSlot(int menuSlot, ItemStack stack) {
            public CarrierSlot {
                stack = stack.copy();
            }
        }

        public UseContainer {
            carrier = List.copyOf(carrier);
            steps = List.copyOf(steps);
        }

        @Override
        public ChronoActionType type() {
            return ChronoActionType.USE_CONTAINER;
        }
    }
}
