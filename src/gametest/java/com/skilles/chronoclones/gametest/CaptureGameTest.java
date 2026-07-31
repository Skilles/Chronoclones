package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.ContainerWatch;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.RecordingSession;
import com.skilles.chronoclones.recording.RecordingSessions;
import com.skilles.chronoclones.recording.TimedAction;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

final class CaptureGameTest {

    private CaptureGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("recording_ignores_own_clones", CaptureGameTest::recordingIgnoresOwnClones);
        ChronoclonesGameTests.add("a_recorded_session_remembers_what_it_opened",
                CaptureGameTest::recordedSessionRemembersWhatItOpened);
        ChronoclonesGameTests.add("tilling_records_one_action_and_the_block_it_worked",
                CaptureGameTest::tillingRecordsOneAction);
        ChronoclonesGameTests.add("a_refused_interaction_records_nothing",
                CaptureGameTest::refusedInteractionRecordsNothing);
        ChronoclonesGameTests.add("a_refused_item_use_records_nothing",
                CaptureGameTest::refusedItemUseRecordsNothing);
        ChronoclonesGameTests.add("a_passing_main_hand_does_not_shadow_the_off_hand",
                CaptureGameTest::passingMainHandDoesNotShadowTheOffHand);
    }

    private static void refusedInteractionRecordsNothing(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        BlockPos absolute = helper.absolutePos(target);
        ServerPlayer player = recordingPlayerAt(helper, absolute);
        RecordingSession session = RecordingSessions.start(player);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));

            List<TimedAction> recorded = session.finish().actions();
            if (!recorded.isEmpty()) {
                helper.fail("poking stone with a stick recorded " + recorded.size()
                        + " action(s): " + recorded.stream()
                                .map(a -> a.action().type().toString()).toList());
                return;
            }
            helper.succeed();
        } finally {
            RecordingSessions.discard(player);
        }
    }

    private static void refusedItemUseRecordsNothing(GameTestHelper helper) {
        BlockPos absolute = helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR));
        ServerPlayer player = recordingPlayerAt(helper, absolute);
        RecordingSession session = RecordingSessions.start(player);
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.getFoodData().setFoodLevel(20);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
            player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND);

            List<TimedAction> recorded = session.finish().actions();
            if (!recorded.isEmpty()) {
                helper.fail("eating on a full stomach recorded " + recorded.size() + " action(s)");
                return;
            }
            helper.succeed();
        } finally {
            RecordingSessions.discard(player);
        }
    }

    private static void passingMainHandDoesNotShadowTheOffHand(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);
        helper.setBlock(target.above(), Blocks.AIR);

        BlockPos absolute = helper.absolutePos(target);
        ServerPlayer player = recordingPlayerAt(helper, absolute);
        RecordingSession session = RecordingSessions.start(player);
        try {
            BlockHitResult hit =
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND_HOE));

            player.gameMode.useItemOn(player, helper.getLevel(),
                    player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
            player.gameMode.useItemOn(player, helper.getLevel(),
                    player.getItemInHand(InteractionHand.OFF_HAND), InteractionHand.OFF_HAND, hit);

            helper.assertBlockPresent(Blocks.FARMLAND, target);

            List<TimedAction> recorded = session.finish().actions();
            if (recorded.size() != 1) {
                helper.fail("a passing main hand and a working off hand recorded "
                        + recorded.size() + " actions, expected only the off hand's");
                return;
            }
            if (!(recorded.getFirst().action() instanceof ChronoAction.UseOnBlock use)
                    || use.hand() != InteractionHand.OFF_HAND) {
                helper.fail("the recorded action was not the off hand's: " + recorded.getFirst().action());
                return;
            }
            helper.succeed();
        } finally {
            RecordingSessions.discard(player);
        }
    }

    private static ServerPlayer recordingPlayerAt(GameTestHelper helper, BlockPos absolute) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(absolute.getX() + 0.5, absolute.getY() + 1.0, absolute.getZ() + 0.5);
        return player;
    }

    private static void tillingRecordsOneAction(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.DIRT);
        helper.setBlock(target.above(), Blocks.AIR);

        BlockPos absolute = helper.absolutePos(target);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.snapTo(absolute.getX() + 0.5, absolute.getY() + 1.0, absolute.getZ() + 0.5);

        RecordingSession session = RecordingSessions.start(player);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_HOE));
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));

            helper.assertBlockPresent(Blocks.FARMLAND, target);

            List<TimedAction> recorded = session.finish().actions();
            if (recorded.size() != 1) {
                helper.fail("one swing of a hoe recorded " + recorded.size() + " actions: "
                        + recorded.stream().map(a -> a.action().type().toString()).toList());
                return;
            }
            if (!(recorded.getFirst().action() instanceof ChronoAction.UseOnBlock use)) {
                helper.fail("tilling was recorded as " + recorded.getFirst().action().type()
                        + " rather than a use on a block");
                return;
            }
            if (use.expectedBlock().map(block -> block.value() != Blocks.DIRT).orElse(true)) {
                helper.fail("the hoe did not remember tilling dirt: " + use.expectedBlock());
                return;
            }
            helper.succeed();
        } finally {
            RecordingSessions.discard(player);
        }
    }

    private static void recordedSessionRemembersWhatItOpened(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.CHEST);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RecordingSession session = RecordingSessions.start(player);
        try {
            BlockPos absolute = helper.absolutePos(target);
            ContainerWatch.noteInteraction(player, absolute, session);

            helper.useBlock(target, player);
            if (!(player.containerMenu instanceof ChestMenu)) {
                helper.fail("the chest never opened, so nothing was recorded to check");
                return;
            }
            ContainerWatch.onContainerOpened(player, session);
            ContainerWatch.onClick(player, 0, 0, ContainerInput.PICKUP);

            ChronoAction.UseContainer recorded = ContainerWatch.onContainerClosed(player, session);
            if (recorded == null) {
                helper.fail("nothing was recorded for a session that was clicked in");
                return;
            }
            if (!(recorded.target() instanceof MenuTarget.Block block)) {
                helper.fail("a chest was recorded as " + recorded.target());
                return;
            }
            if (block.expectedBlock().map(Holder::value).orElse(null) != Blocks.CHEST) {
                helper.fail("the session did not remember the chest it was opened on, got "
                        + block.expectedBlock().map(Holder::value).orElse(null));
            }
        } finally {
            RecordingSessions.discard(player);
            ContainerWatch.forget(player);
        }
        helper.succeed();
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static void recordingIgnoresOwnClones(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ServerLevel level = helper.getLevel();
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        FakePlayer owner = AnchorTestFixture.owner(level);
        BlockPos anchorAbsolute = helper.absolutePos(ANCHOR);
        owner.setPos(anchorAbsolute.getX() + 0.5, anchorAbsolute.getY(), anchorAbsolute.getZ() + 0.5);

        RecordingSession session = RecordingSessions.start(owner);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    try {
                        helper.assertBlockNotPresent(Blocks.STONE, target);

                        if (session.actionCount() != 0) {
                            helper.fail("the anchor's own replay was captured into the recording ("
                                    + session.actionCount() + " action(s)) - a routine must never "
                                    + "record its own clones");
                        }
                        if (anchor.getRecording() == null) {
                            helper.fail("the anchor lost its routine mid-test");
                        }
                    } finally {
                        RecordingSessions.clear();
                    }
                })
                .thenSucceed();
    }
}
