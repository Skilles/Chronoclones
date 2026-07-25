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

    /** Lowest priority; gated behind the fidelity upgrade and first on the spec's cut list. */
    record UseItem(InteractionHand hand, Holder<Item> item, Optional<BlockPos> localPos)
            implements ChronoAction {
        @Override
        public ChronoActionType type() {
            return ChronoActionType.USE_ITEM;
        }
    }
}
