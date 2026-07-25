package com.skilles.chronoclones.gametest;

import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.block.ChronoAnchorBlock;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.UpgradeState;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.registry.ModBlocks;
import com.skilles.chronoclones.registry.ModItems;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Shared setup for anchor game tests.
 *
 * <p>The author and owner identities are fixed constants here so every test asserts against the
 * same two, and so the distinction stays visible at the call site — the whole point of the 
 * tests is that these are never the same person.
 */
final class AnchorTestFixture {

    /** Whoever recorded the routine. Cosmetic only: decides whose skin a ghost wears. */
    static final UUID AUTHOR_ID = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    static final String AUTHOR_NAME = "RoutineAuthor";

    /** Whoever imprinted the anchor. Every event and permission check must see this identity. */
    static final UUID OWNER_ID = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
    static final String OWNER_NAME = "AnchorOwner";

    private AnchorTestFixture() {}

    /**
     * A recording that breaks a single block one step north of the anchor.
     *
     * <p>Authored by {@link #AUTHOR_ID}, deliberately never by the owner.
     */
    static Recording breakOneBlock(Block expected) {
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, new ChronoAction.BreakBlock(
                        new BlockPos(0, 0, -1),
                        BuiltInRegistries.BLOCK.wrapAsHolder(expected),
                        new ItemStack(Items.NETHERITE_PICKAXE)))),
                20, AUTHOR_NAME, AUTHOR_ID);
    }

    /** A one-action routine, for exercising a single executor path. */
    static Recording routine(ChronoAction action) {
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, action)),
                20, AUTHOR_NAME, AUTHOR_ID);
    }

    /** The world position the above routine targets, for an anchor at {@code anchorPos}. */
    static BlockPos targetOf(BlockPos anchorPos) {
        return anchorPos.north();
    }

    /**
     * Places a north-facing anchor and imprints {@code recording} as {@link #OWNER_ID}.
     *
     * <p>Imprinting through a {@link FakePlayer} is what makes the owner assertable: it extends
     * {@code ServerPlayer}, so it satisfies the same imprint path a real player takes, with an
     * identity the test controls.
     */
    static ChronoAnchorBlockEntity placeAndImprint(GameTestHelper helper, BlockPos relativeAnchorPos,
                                                 Recording recording) {
        helper.setBlock(relativeAnchorPos, ModBlocks.CHRONO_ANCHOR.get()
                .defaultBlockState()
                .setValue(ChronoAnchorBlock.FACING, Direction.NORTH));

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relativeAnchorPos);

        if (!(level.getBlockEntity(absolute) instanceof ChronoAnchorBlockEntity anchor)) {
            helper.fail("anchor block entity missing at " + relativeAnchorPos);
            throw new IllegalStateException("unreachable");
        }

        anchor.imprint(recording, owner(level));
        giveInfiniteCharge(anchor);
        return anchor;
    }

    /** The fake player standing in for the anchor's owner. */
    static FakePlayer owner(ServerLevel level) {
        return FakePlayerFactory.get(level, new GameProfile(OWNER_ID, OWNER_NAME));
    }

    /**
     * Installs the creative charge cell so charge never confounds a test.
     *
     * <p>These tests are about attribution and execution, not economy; leaving charge in play would
     * make every one of them depend on the fuel system as well.
     */
    static void giveInfiniteCharge(ChronoAnchorBlockEntity anchor) {
        anchor.getFuelHandler().set(0,
                net.neoforged.neoforge.transfer.item.ItemResource.of(
                        ModItems.CREATIVE_CHARGE_CELL.get()), 1);
    }

    /**
     * Unlocks every action type, for tests that are about an executor rather than the fidelity gate.
     *
     * <p>Without this, anything above break-tier is refused with {@code NOT_PERMITTED} and an
     * interaction test fails for a reason that has nothing to do with interactions.
     */
    static void unlockAllActions(ChronoAnchorBlockEntity anchor) {
        anchor.getUpgradeHandler().set(0,
                net.neoforged.neoforge.transfer.item.ItemResource.of(ModItems.CHRONO_FOCUS.get()),
                UpgradeState.MAX_FIDELITY);
    }

    static BlockState stateAt(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockState(relative);
    }
}
