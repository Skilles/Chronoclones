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

public sealed interface ChronoAction {

    ChronoActionType type();

    default int chargeCost() {
        return type().chargeCost();
    }

    default ItemStack heldTemplate() {
        return switch (this) {
            case BreakBlock a -> a.toolTemplate();
            case AttackEntity a -> a.weaponTemplate();
            case PlaceBlock a -> a.itemTemplate().create();
            case UseOnBlock a -> a.itemTemplate().create();
            case UseItem a -> a.itemTemplate().create();
            case InteractEntity a -> a.itemTemplate().create();
            case UseContainer ignored -> ItemStack.EMPTY;
        };
    }

    default InteractionHand heldHand() {
        return switch (this) {
            case UseOnBlock a -> a.hand();
            case UseItem a -> a.hand();
            case InteractEntity a -> a.hand();
            case PlaceBlock a -> a.context().map(PlaceContext::hand).orElse(InteractionHand.MAIN_HAND);
            default -> InteractionHand.MAIN_HAND;
        };
    }

    record BreakBlock(BlockPos localPos, Holder<Block> expectedBlock, ItemStack toolTemplate)
            implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.BREAK_BLOCK;
        }
    }

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

    record PlaceContext(BlockPos localClicked, Vec3 localHitOffset, boolean inside,
                        InteractionHand hand, ActionPose pose) {}

    record AttackEntity(Vec3 localPos, Holder<EntityType<?>> expectedType, ItemStack weaponTemplate)
            implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.ATTACK_ENTITY;
        }
    }

    record UseOnBlock(BlockPos localPos, Direction localFace, Vec3 localHitOffset, boolean inside,
                      InteractionHand hand, RecordedItem itemTemplate,
                      Optional<Holder<Block>> expectedBlock) implements ChronoAction {
        public UseOnBlock(BlockPos localPos, Direction localFace, Vec3 localHitOffset, boolean inside,
                          InteractionHand hand, Holder<Item> item) {
            this(localPos, localFace, localHitOffset, inside, hand, item, Optional.empty());
        }

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

    record UseContainer(MenuTarget target, int menuSize, List<CarrierSlot> carrier,
                        List<SessionStep> steps) implements ChronoAction {
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
