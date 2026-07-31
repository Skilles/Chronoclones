package com.skilles.chronoclones.gametest;

import java.util.UUID;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.RunState;
import com.skilles.chronoclones.entity.ChronoCloneEntity;
import com.skilles.chronoclones.menu.AnchorData;
import com.skilles.chronoclones.network.AnchorAuthority;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

final class RunStateGameTest {

    private RunStateGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("pausing_holds_the_playhead_where_it_was",
                RunStateGameTest::pausingHoldsThePlayhead);
        ChronoclonesGameTests.add("playing_after_a_pause_carries_on",
                RunStateGameTest::playingAfterAPauseCarriesOn);
        ChronoclonesGameTests.add("stopping_takes_the_clones_away_and_starts_over",
                RunStateGameTest::stoppingStartsOver);
        ChronoclonesGameTests.add("a_stopped_anchor_still_shows_its_storage_tabs",
                RunStateGameTest::stoppedAnchorKeepsItsTabs);
        ChronoclonesGameTests.add("only_the_owner_may_work_the_transport",
                RunStateGameTest::onlyTheOwnerMayWorkTheTransport);
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static ChronoAnchorBlockEntity running(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        return AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.breakOneBlock(Blocks.STONE));
    }

    private static int playhead(ChronoAnchorBlockEntity anchor) {
        return anchor.getContainerData().get(AnchorData.playhead(0));
    }

    private static void pausingHoldsThePlayhead(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = running(helper);

        helper.startSequence()
                .thenExecuteAfter(6, () -> anchor.setRunState(RunState.PAUSED))
                .thenExecuteAfter(1, () -> {
                    int held = playhead(anchor);
                    if (held <= 0) {
                        helper.fail("the anchor was paused before it had run at all, which proves "
                                + "nothing about pausing");
                    }
                    helper.startSequence()
                            .thenExecuteAfter(10, () -> {
                                if (playhead(anchor) != held) {
                                    helper.fail("a paused anchor moved its playhead from " + held
                                            + " to " + playhead(anchor));
                                }
                            })
                            .thenSucceed();
                })
                .thenExecuteAfter(30, () -> { });
    }

    private static void playingAfterAPauseCarriesOn(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = running(helper);

        helper.startSequence()
                .thenExecuteAfter(6, () -> anchor.setRunState(RunState.PAUSED))
                .thenExecuteAfter(2, () -> {
                    int held = playhead(anchor);
                    anchor.setRunState(RunState.RUNNING);

                    helper.startSequence()
                            .thenExecuteAfter(3, () -> {
                                if (playhead(anchor) < held) {
                                    helper.fail("resuming started the recording again: the playhead "
                                            + "went from " + held + " back to " + playhead(anchor));
                                }
                            })
                            .thenSucceed();
                })
                .thenExecuteAfter(30, () -> { });
    }

    private static void stoppingStartsOver(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = running(helper);

        helper.startSequence()
                .thenExecuteAfter(8, () -> {
                    if (clones(helper).isEmpty()) {
                        helper.fail("no clone was ever standing, so its going away proves nothing");
                    }
                    anchor.setRunState(RunState.STOPPED);
                })
                .thenExecuteAfter(2, () -> {
                    if (playhead(anchor) != 0) {
                        helper.fail("a stopped anchor kept a playhead at " + playhead(anchor));
                    }
                    if (!clones(helper).isEmpty()) {
                        helper.fail("a stopped anchor left " + clones(helper).size()
                                + " clones standing");
                    }
                    if (helper.getBlockState(ANCHOR).getValue(
                            com.skilles.chronoclones.block.ChronoAnchorBlock.ACTIVE)) {
                        helper.fail("a stopped anchor is still lit as though it were working");
                    }
                })
                .thenSucceed();
    }

    private static void stoppedAnchorKeepsItsTabs(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = running(helper);
        anchor.setRunState(RunState.STOPPED);

        helper.startSequence()
                .thenExecuteAfter(4, () -> {
                    int clones = anchor.getContainerData().get(AnchorData.ACTIVE_CLONES);
                    if (clones < 1) {
                        helper.fail("a stopped anchor reported " + clones + " clone pages, so its "
                                + "storage cannot be reached");
                    }
                    if (anchor.getContainerData().get(AnchorData.RUN_STATE)
                            != RunState.STOPPED.ordinal()) {
                        helper.fail("the run state is not synced to the screen that sets it");
                    }
                })
                .thenSucceed();
    }

    private static void onlyTheOwnerMayWorkTheTransport(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = running(helper);

        UUID stranger = UUID.fromString("c0000000-0000-0000-0000-00000000000c");
        if (AnchorAuthority.mayRetune(anchor.getOwnerId(), stranger)) {
            helper.fail("a stranger was allowed to stop somebody else's anchor");
        }
        if (!AnchorAuthority.mayRetune(anchor.getOwnerId(), AnchorTestFixture.OWNER_ID)) {
            helper.fail("the owner was refused the controls of their own anchor");
        }
        helper.succeed();
    }

    private static java.util.List<ChronoCloneEntity> clones(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ChronoCloneEntity.class,
                new AABB(helper.absolutePos(ANCHOR)).inflate(8.0));
    }
}
