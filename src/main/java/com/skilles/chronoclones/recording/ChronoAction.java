package com.skilles.chronoclones.recording;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A semantic action captured during recording.
 *
 * <p><b>Semantic intent, never raw input.</b> Replaying raw clicks is non-deterministic and
 * unrecoverable once the world diverges from what was recorded; replaying "break the block at
 * local (3, -1, 2), which was stone" degrades gracefully and can be reported when it fails.
 *
 * <p>All positions are anchor-local — see {@link LocalSpace}. Codecs live in
 * {@link RecordingCodecs} so this file stays free of registry access at class-init time.
 */
public sealed interface ChronoAction {

    ChronoActionType type();

    /** Charge cost of executing this action. */
    default int chargeCost() {
        return type().chargeCost();
    }

    /**
     * What the player was visibly holding when they did this, for the ghost to hold too.
     *
     * <p>Cosmetic. The item that actually gets consumed or swung comes from the anchor's inventory
     * at execute time — this is only what the clone appears to be carrying.
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
     * <p>The geometry is captured in full — face, and the exact point on the block face — because
     * that is what the interaction pipeline consumes. A lever, a jukebox, a mod machine's side panel
     * and a cake all read different parts of the same {@code BlockHitResult}, so recording only
     * "which block" would work for some and quietly misbehave for the rest.
     *
     * <p>{@code localHitOffset} is measured from the block centre so that it rotates with the
     * anchor exactly as the block position does.
     */
    record UseOnBlock(BlockPos localPos, Direction localFace, Vec3 localHitOffset, boolean inside,
                      InteractionHand hand, Holder<Item> item) implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.USE_ON_BLOCK;
        }
    }

    /** Right-clicking with nothing targeted — throwing, eating, firing a bow. */
    record UseItem(InteractionHand hand, Holder<Item> item) implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.USE_ITEM;
        }
    }

    /**
     * Right-clicking an entity: shearing, milking, feeding, trading, a mod's interactable mob.
     *
     * <p>{@code expectedType} is a hint, as it is for {@link AttackEntity} — the nearest match wins,
     * falling back to the nearest entity of any type, because a routine that mills around a pen must
     * not stop working because one particular sheep wandered off.
     */
    record InteractEntity(Vec3 localPos, Holder<EntityType<?>> expectedType,
                          InteractionHand hand, Holder<Item> item) implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.INTERACT_ENTITY;
        }
    }

    /**
     * A whole session at a container, recorded as the clicks the player made.
     *
     * <p>This replaced a version that recorded net movements — "32 cobblestone from slot 4 to the
     * player" — and the difference is intent versus arithmetic. Right-clicking a stack means
     * <em>take half of whatever is there</em>; recording it as "take 32" bakes in the contents of
     * the chest on the day it was taught, and a routine that splits a stack stops splitting the
     * moment the stack is a different size. Buttons survive that. Amounts do not.
     *
     * <p>Replayed by opening the block's real menu and calling {@code clicked} on it, the same
     * method the server calls for a player, so a mod's slot restrictions, shift-click behaviour and
     * crafting-output rules all apply without this mod knowing they exist. Which also means the
     * whole grammar comes free: shift-click, drag-distribute, swap-to-hotbar, throw.
     *
     * <p>{@code menuSize} is recorded so a session can refuse to run against a menu of a different
     * shape. Slot indices only mean anything relative to the menu that produced them.
     *
     * <p>{@code carrier} is what the player's own half of the menu held when they opened it, and it
     * is what makes depositing work at all. A click on a container slot names a place that exists on
     * both sides; a click on a player slot names a place whose contents depend entirely on where
     * that player happened to be keeping things. Without this, replay stocked the anchor's items
     * from index zero, the recorded click pointed at some other slot, and every deposit silently
     * clicked an empty square.
     */
    record UseContainer(BlockPos localPos, int menuSize, List<CarrierSlot> carrier, List<Click> clicks)
            implements ChronoAction {

        /** One click: which slot, which button, and what kind of click it was. */
        public record Click(int slot, int button, ContainerInput input) {}

        /**
         * One slot of the player's own inventory as the session found it.
         *
         * <p>The whole stack, not just its item and count, because an anchor set to be specific about
         * items has to be able to tell an enchanted pickaxe from a plain one — and a stack's identity
         * lives in its components. See {@link com.skilles.chronoclones.replay.TransferPrecision}.
         *
         * <p>The count is a target for staging, not a promise. Clicks still operate on whatever is
         * actually in the slot — that is the whole point of recording buttons — so a session that
         * splits a stack splits whatever the anchor could supply.
         *
         * <p>Copied on the way in. {@code ItemStack} is mutable and this record outlives every
         * container it was taken from, so sharing the instance would let a later mutation rewrite
         * history.
         */
        public record CarrierSlot(int menuSlot, ItemStack stack) {
            public CarrierSlot {
                stack = stack.copy();
            }
        }

        public UseContainer {
            carrier = List.copyOf(carrier);
            clicks = List.copyOf(clicks);
        }

        @Override
        public ChronoActionType type() {
            return ChronoActionType.USE_CONTAINER;
        }
    }
}
