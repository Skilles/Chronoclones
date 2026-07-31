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
            // create(), not a bare new ItemStack: a clone holding a tipped arrow or a charged
            // crossbow should look like the one that was recorded.
            case PlaceBlock a -> a.itemTemplate().create();
            case UseOnBlock a -> a.itemTemplate().create();
            case UseItem a -> a.itemTemplate().create();
            case InteractEntity a -> a.itemTemplate().create();
            // Working a chest is reaching into it, not brandishing something.
            case UseContainer ignored -> ItemStack.EMPTY;
        };
    }

    /**
     * Which hand it was in, so the clone appears to use the hand the routine actually uses.
     *
     * <p>Everything that does not name a hand is a main-hand action: a dig and a swing both are.
     */
    default InteractionHand heldHand() {
        return switch (this) {
            case UseOnBlock a -> a.hand();
            case UseItem a -> a.hand();
            case InteractEntity a -> a.hand();
            case PlaceBlock a -> a.context().map(PlaceContext::hand).orElse(InteractionHand.MAIN_HAND);
            default -> InteractionHand.MAIN_HAND;
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

    /**
     * Putting a block down.
     *
     * @param localPos       where the block ended up
     * @param localFace      the face that was clicked, or UP for a routine recorded before the
     *                       click that caused a placement was correlated with the placement
     * @param expectedResult the state it produced, kept for the editor rather than checked
     * @param context        how it was clicked, absent on recordings made before that was captured
     */
    record PlaceBlock(BlockPos localPos, Direction localFace, RecordedItem itemTemplate,
                      BlockState expectedResult, Optional<PlaceContext> context)
            implements ChronoAction {

        public PlaceBlock(BlockPos localPos, Direction localFace, Holder<Item> item,
                          BlockState expectedResult) {
            this(localPos, localFace, RecordedItem.of(item), expectedResult, Optional.empty());
        }

        public Holder<Item> item() {
            return itemTemplate.item();
        }

        @Override
        public ChronoActionType type() {
            return ChronoActionType.PLACE_BLOCK;
        }
    }

    /**
     * Everything about a placement except which block came out of it.
     *
     * <p>A placement used to keep only where the block landed and a hardcoded upward face, and
     * replay clicked the centre of the block's own square with a fake player facing nowhere. Every
     * question vanilla asks while working out a block state -- which face, whereabouts on it, which
     * half, which way was the player looking -- was answered with the same wrong answer, so stairs,
     * slabs, trapdoors, doors, beds and every directional block came out facing north.
     *
     * @param localClicked   the supporting block that was clicked, not the square the block filled
     * @param localHitOffset where on that block's face, relative to its centre
     * @param inside         whether the click was inside the block's own box
     */
    record PlaceContext(BlockPos localClicked, Vec3 localHitOffset, boolean inside,
                        InteractionHand hand, ActionPose pose) {}

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
                      InteractionHand hand, RecordedItem itemTemplate,
                      Optional<Holder<Block>> expectedBlock) implements ChronoAction {

        public UseOnBlock(BlockPos localPos, Direction localFace, Vec3 localHitOffset, boolean inside,
                          InteractionHand hand, Holder<Item> item) {
            this(localPos, localFace, localHitOffset, inside, hand, item, Optional.empty());
        }

        /** An item named by kind alone, for a routine that cares about nothing on it. */
        public UseOnBlock(BlockPos localPos, Direction localFace, Vec3 localHitOffset, boolean inside,
                          InteractionHand hand, Holder<Item> item,
                          Optional<Holder<Block>> expectedBlock) {
            this(localPos, localFace, localHitOffset, inside, hand, RecordedItem.of(item),
                    expectedBlock);
        }

        public Holder<Item> item() {
            return itemTemplate.item();
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
    record UseItem(InteractionHand hand, RecordedItem itemTemplate, int holdTicks,
                   Optional<ActionPose> pose) implements ChronoAction {

        public UseItem(InteractionHand hand, Holder<Item> item) {
            this(hand, RecordedItem.of(item), 0, Optional.empty());
        }

        public UseItem(InteractionHand hand, Holder<Item> item, int holdTicks) {
            this(hand, RecordedItem.of(item), holdTicks, Optional.empty());
        }

        public Holder<Item> item() {
            return itemTemplate.item();
        }

        /** True for the items that have to be held rather than clicked. */
        public boolean isHeld() {
            return holdTicks > 0;
        }

        public UseItem heldFor(int ticks) {
            return new UseItem(hand, itemTemplate, Math.max(0, ticks), pose);
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
                          InteractionHand hand, RecordedItem itemTemplate)
            implements ChronoAction {

        public InteractEntity(Vec3 localPos, Holder<EntityType<?>> expectedType,
                              InteractionHand hand, Holder<Item> item) {
            this(localPos, expectedType, hand, RecordedItem.of(item));
        }

        public Holder<Item> item() {
            return itemTemplate.item();
        }
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
