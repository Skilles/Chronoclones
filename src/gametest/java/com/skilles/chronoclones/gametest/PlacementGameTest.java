package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingSession;
import com.skilles.chronoclones.recording.RecordingSessions;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Placements that depend on how they were clicked.
 *
 * <p>A placement used to keep where the block landed and nothing else, and replay clicked the centre
 * of that square with a fake player facing north and looking level. Every question vanilla asks
 * while deciding a block state got the same wrong answer, so a routine that built a staircase built
 * a wall of identical north-facing stairs instead.
 */
final class PlacementGameTest {

    private PlacementGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("placement_keeps_the_click_that_made_it",
                PlacementGameTest::keepsTheClickThatMadeIt);
        ChronoclonesGameTests.add("placement_replays_the_state_it_recorded",
                PlacementGameTest::replaysTheStateItRecorded);
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    /** Clicking the underside of a block gives top-half stairs; the recording has to know that. */
    private static void keepsTheClickThatMadeIt(GameTestHelper helper) {
        Recorded recorded = recordAStair(helper);
        if (recorded == null) {
            return;
        }

        if (recorded.action().context().isEmpty()) {
            helper.fail("a placement was recorded without the click that caused it");
            return;
        }
        ChronoAction.PlaceContext context = recorded.action().context().get();
        if (context.hand() != InteractionHand.MAIN_HAND) {
            helper.fail("the recorded placement forgot which hand placed it");
            return;
        }
        // Upper-half stairs are the whole point: nothing but the hit position produces them.
        if (recorded.action().expectedResult().getValue(BlockStateProperties.HALF) != Half.TOP) {
            helper.fail("this test meant to record top-half stairs and recorded "
                    + recorded.action().expectedResult());
        }
        helper.succeed();
    }

    /**
     * The end of it: what the routine builds is what the player built.
     */
    private static void replaysTheStateItRecorded(GameTestHelper helper) {
        Recorded recorded = recordAStair(helper);
        if (recorded == null) {
            return;
        }
        BlockState wanted = recorded.action().expectedResult();

        // Take the stairs away again, leaving the support they were placed against.
        helper.setBlock(recorded.placedAt(), Blocks.AIR);

        ChronoAnchorBlockEntity anchor =
                AnchorTestFixture.placeAndImprint(helper, ANCHOR, recorded.recording());
        anchor.getCloneInventory(0).set(0, ItemResource.of(new ItemStack(Items.OAK_STAIRS)), 8);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    BlockState built = helper.getBlockState(recorded.placedAt());
                    if (!built.is(Blocks.OAK_STAIRS)) {
                        helper.fail("the routine did not rebuild its stair at "
                                + recorded.placedAt() + ", found " + built);
                        return;
                    }
                    if (!built.equals(wanted)) {
                        helper.fail("the routine rebuilt the stair facing the wrong way:\n"
                                + "  recorded " + wanted + "\n"
                                + "  replayed " + built);
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------- fixtures

    /** A captured placement, and where in the plot it landed. */
    private record Recorded(Recording recording, ChronoAction.PlaceBlock action, BlockPos placedAt) {}

    /**
     * Places a stair the awkward way -- against the underside of an overhang, from the south --
     * and hands back what the recorder made of it.
     */
    private static @org.jspecify.annotations.Nullable Recorded recordAStair(GameTestHelper helper) {
        // A ceiling to click the underside of, and the empty square beneath it for the stair.
        BlockPos support = ANCHOR.above(3);
        BlockPos placedAt = support.below();
        helper.setBlock(support, Blocks.STONE);
        helper.setBlock(placedAt, Blocks.AIR);

        // Standing on the anchor's own square, so the routine's origin is the anchor's, and the
        // local coordinates it records land back where they started when it is replayed there.
        BlockPos absoluteAnchor = helper.absolutePos(ANCHOR);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(absoluteAnchor.getX() + 0.5, absoluteAnchor.getY(), absoluteAnchor.getZ() + 0.5);
        // Facing north, because the fixture's anchor faces north: a routine recorded facing one
        // way and replayed on an anchor facing another is rotated to suit, quite correctly, and
        // this test is about the click rather than about rotation.
        player.setYRot(180.0f);
        player.setXRot(0.0f);

        RecordingSession session = RecordingSessions.start(player);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_STAIRS));

            BlockPos absoluteSupport = helper.absolutePos(support);
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(
                            Vec3.atCenterOf(absoluteSupport).add(0.0, -0.5, 0.0),
                            Direction.DOWN, absoluteSupport, false));

            if (!helper.getBlockState(placedAt).is(Blocks.OAK_STAIRS)) {
                helper.fail("the stair never got placed, so this test is about nothing");
                return null;
            }

            Recording recording = session.finish();
            List<TimedAction> actions = recording.actions();
            if (actions.size() != 1
                    || !(actions.getFirst().action() instanceof ChronoAction.PlaceBlock placed)) {
                helper.fail("placing one stair recorded " + actions.size() + " actions: "
                        + actions.stream().map(a -> a.action().type().toString()).toList());
                return null;
            }
            return new Recorded(recording, placed, placedAt);
        } finally {
            RecordingSessions.discard(player);
        }
    }
}
