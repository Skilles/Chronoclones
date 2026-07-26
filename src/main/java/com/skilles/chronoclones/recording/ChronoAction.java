package com.skilles.chronoclones.recording;

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
            // A transfer is reaching into a chest, not brandishing something.
            case TransferItems ignored -> ItemStack.EMPTY;
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
     * Moving items from one slot to another, recorded as net intent.
     *
     * <p>Deliberately not a replay of slot clicks. Clicks are raw input — the thing this model
     * exists to avoid — and simulating them would mean driving a real {@code AbstractContainerMenu},
     * whose behaviour every mod is free to override. Capturing where items ended up and replaying it
     * through the block's item-handler capability instead works for anything that exposes one,
     * which is every vanilla container and every mod machine that wanted to be automatable.
     *
     * <p><b>Slots, not totals.</b> "The furnace gained two logs" is not enough to reproduce what the
     * player did: one goes in the input and one in the fuel slot, and an insert that just looks for
     * room puts both in the first slot that accepts them and smelts nothing. A slot in a machine is
     * a meaning, and the routine has to carry it.
     *
     * <p>Both endpoints use the same encoding, so the three interesting cases are one shape:
     * container slot → carrier is a withdrawal, carrier → container slot is a deposit, and slot →
     * slot is a move within the container.
     *
     * @param fromSlot source slot in the container at {@code localPos}, or {@link #CARRIER}
     * @param toSlot   destination slot in that container, or {@link #CARRIER}
     */
    record TransferItems(BlockPos localPos, Holder<Item> item, int amount, int fromSlot, int toSlot)
            implements ChronoAction {

        /**
         * The player's own inventory at record time; the anchor's at replay.
         *
         * <p>Unindexed on purpose. A player has thirty-six slots and an anchor eighteen, so a
         * remembered index would mean nothing on the other side — and unlike a machine's slots, they
         * carry no meaning worth preserving.
         */
        public static final int CARRIER = -1;

        public boolean isWithdrawal() {
            return toSlot == CARRIER && fromSlot != CARRIER;
        }

        public boolean isDeposit() {
            return fromSlot == CARRIER && toSlot != CARRIER;
        }

        @Override
        public ChronoActionType type() {
            return ChronoActionType.TRANSFER_ITEMS;
        }
    }
}
