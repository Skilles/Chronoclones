package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.item.ActionIcons;
import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.item.RecordingDetail;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.registry.ModItems;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ActionSettings.SlotRule;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.SessionStep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
//? if >=26 {
import net.minecraft.world.entity.EntityTypes;
//?} else {
/*import net.minecraft.world.entity.EntityType;
*///?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class RoutineEditGameTest {

    private RoutineEditGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("edited_settings_reach_the_running_routine",
                RoutineEditGameTest::editedSettingsReachTheRoutine);
        ChronoclonesGameTests.add("reinterpreting_does_not_restart_the_clones",
                RoutineEditGameTest::reinterpretingDoesNotRestartClones);
        ChronoclonesGameTests.add("discarding_leaves_the_anchor_blank",
                RoutineEditGameTest::discardingLeavesTheAnchorBlank);
        ChronoclonesGameTests.add("a_skipped_step_moves_nothing_and_its_neighbours_still_run",
                RoutineEditGameTest::skippedStepMovesNothing);
        ChronoclonesGameTests.add("a_step_carries_only_what_it_is_told_to",
                RoutineEditGameTest::stepCarriesOnlyItsItem);
        ChronoclonesGameTests.add("a_step_finds_its_item_in_another_square",
                RoutineEditGameTest::stepFindsItsItemElsewhere);
        ChronoclonesGameTests.add("a_step_told_exactly_where_looks_nowhere_else",
                RoutineEditGameTest::exactStepLooksNowhereElse);
        ChronoclonesGameTests.add("a_step_told_to_move_one_moves_one",
                RoutineEditGameTest::cappedStepMovesPartOfIt);
        ChronoclonesGameTests.add("an_action_about_a_creature_is_pictured_as_that_creature",
                RoutineEditGameTest::creatureActionsArePicturedAsCreatures);
        ChronoclonesGameTests.add("discarding_hands_back_what_the_clones_were_holding",
                RoutineEditGameTest::discardingSpillsTheStorage);
        ChronoclonesGameTests.add("a_blank_anchor_has_no_storage_to_reach",
                RoutineEditGameTest::blankAnchorHasNoStorage);
        ChronoclonesGameTests.add("a_blank_recorder_takes_a_recording_back_out",
                RoutineEditGameTest::blankRecorderTakesTheRecordingBack);
        ChronoclonesGameTests.add("every_change_to_the_routine_bumps_its_revision",
                RoutineEditGameTest::everyChangeBumpsTheRevision);
    }

    /** The revision is how a stale editor is told apart from a current one, so every path
     * that changes what an edit's indices mean has to move it. */
    private static void everyChangeBumpsTheRevision(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        int afterImprint = anchor.getRevision();
        anchor.reinterpret(anchor.getRecording().withSettings(0,
                ActionSettings.DEFAULT.withName("renamed")));
        if (anchor.getRevision() == afterImprint) {
            helper.fail("reinterpreting left the revision alone");
            return;
        }

        int afterEdit = anchor.getRevision();
        anchor.nudgeOrigin(new BlockPos(1, 0, 0));
        if (anchor.getRevision() == afterEdit) {
            helper.fail("nudging the origin left the revision alone");
            return;
        }

        int afterNudge = anchor.getRevision();
        anchor.clearRecording();
        if (anchor.getRevision() == afterNudge) {
            helper.fail("discarding the routine left the revision alone");
            return;
        }
        helper.succeed();
    }

    private static void blankRecorderTakesTheRecordingBack(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.DIAMOND, 3));

        int actions = anchor.getRecording().actions().size();
        ServerPlayer player = AnchorTestFixture.owner(helper.getLevel());
        crouchOnto(helper, player, new ItemStack(ModItems.CHRONO_RECORDER.get()), ANCHOR);

        try {
            if (anchor.getRecording() != null) {
                helper.fail("crouching with a blank recorder left the recording on the anchor");
                return;
            }
            Recording taken = recordingCarriedBy(player);
            if (taken == null || taken.actions().size() != actions) {
                helper.fail("the recording did not come back out whole");
                return;
            }
            if (AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND) != 0) {
                helper.fail("the storage stayed shut inside an anchor with nothing to run");
                return;
            }

            crouchOnto(helper, player, new ItemStack(ModItems.CHRONO_RECORDER.get()), ANCHOR);
            if (anchor.extractRecording() != null) {
                helper.fail("a blank anchor handed over a second recording");
                return;
            }
            helper.succeed();
        } finally {
            player.getInventory().clearContent();
        }
    }

    private static void crouchOnto(GameTestHelper helper, ServerPlayer player, ItemStack held,
                                   BlockPos relativePos) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relativePos);

        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.setShiftKeyDown(true);
        try {
            player.gameMode.useItemOn(player, level, player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        } finally {
            player.setShiftKeyDown(false);
        }
    }

    private static @org.jspecify.annotations.Nullable Recording recordingCarriedBy(ServerPlayer player) {
        //? if >=26 {
        for (ItemStack stack : player.getInventory()) {
        //?} else {
        /*for (ItemStack stack : player.getInventory().items) {
        *///?}
            Recording recording = ChronoRecorderItem.recordingOf(stack);
            if (recording != null) {
                return recording;
            }
        }
        return null;
    }

    private static void discardingSpillsTheStorage(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.getCloneInventory(0).setItem(0, new ItemStack(Items.DIAMOND, 7));
        anchor.setCloneExperience(1, new com.skilles.chronoclones.block.ExperienceStore(40));

        anchor.clearRecording();

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(ANCHOR);

        helper.startSequence()
                .thenExecuteAfter(6, () -> {
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND) != 0) {
                        helper.fail("a discarded recording left its diamonds shut inside the anchor");
                    }
                    if (!anchor.getCloneExperience(1).isEmpty()) {
                        helper.fail("a discarded recording kept its banked experience");
                    }

                    int diamonds = level.getEntitiesOfClass(ItemEntity.class,
                                    new AABB(absolute).inflate(3.0)).stream()
                            .filter(item -> item.getItem().is(Items.DIAMOND))
                            .mapToInt(item -> item.getItem().getCount())
                            .sum();
                    if (diamonds != 7) {
                        helper.fail("expected seven diamonds handed back, found " + diamonds);
                    }
                    int points = level.getEntitiesOfClass(ExperienceOrb.class,
                                    new AABB(absolute).inflate(3.0)).stream()
                            .mapToInt(ExperienceOrb::getValue).sum();
                    if (points != 40) {
                        helper.fail("expected the banked experience back, found " + points);
                    }
                })
                .thenSucceed();
    }

    private static void blankAnchorHasNoStorage(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        ChronoAnchorMenu menu = new ChronoAnchorMenu(1,
                AnchorTestFixture.mockServerPlayer(helper).getInventory(), anchor,
                anchor.getContainerData());
        if (!menu.hasStorage()) {
            helper.fail("an imprinted anchor refused its own storage");
        }

        anchor.clearRecording();
        if (menu.hasStorage()) {
            helper.fail("a blank anchor still offers squares nothing can come out of");
        }
        helper.succeed();
    }

    /** Component lookups, so it cannot be a unit test: spawn eggs are found by scanning
     * item components, which are only bound once the game is fully up. */
    private static void creatureActionsArePicturedAsCreatures(GameTestHelper helper) {
        assertIcon(helper, new ChronoAction.InteractEntity(
                        //? if >=26 {
                        Vec3.ZERO, BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.COW),
                        //?} else {
                        /*Vec3.ZERO, BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.COW),
                        *///?}
                        InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(Items.BUCKET)),
                Items.COW_SPAWN_EGG);

        assertIcon(helper, new ChronoAction.UseContainer(
                        new MenuTarget.Entity(Vec3.ZERO,
                                //? if >=26 {
                                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.VILLAGER)),
                                //?} else {
                                /*BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.VILLAGER)),
                                *///?}
                        39, List.of(), List.of()),
                Items.VILLAGER_SPAWN_EGG);

        helper.succeed();
    }

    private static void assertIcon(GameTestHelper helper, ChronoAction action,
                                   net.minecraft.world.item.Item expected) {
        net.minecraft.world.item.Item shown = ActionIcons.of(action)
                .map(net.minecraft.core.Holder::value)
                .orElse(null);
        if (shown != expected) {
            helper.fail("expected " + expected + " to stand for " + action.type() + ", got " + shown);
        }
    }

    private static void stepCarriesOnlyItsItem(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = sendingAnchor(helper, 0, Items.DIAMOND,
                ActionSettings.StepSettings.DEFAULT.withItems(
                        List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.EMERALD))));
        stock(helper.getLevel(), helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR)),
                0, Items.DIAMOND, 5);

        helper.startSequence()
                .thenExecuteAfter(20, () -> assertHolds(helper, anchor, Items.DIAMOND, 0,
                        "a filter for emeralds let diamonds through"))
                .thenSucceed();
    }

    private static void stepFindsItsItemElsewhere(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = sendingAnchor(helper, 3, Items.DIAMOND,
                ActionSettings.StepSettings.DEFAULT);
        stock(helper.getLevel(), helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR)),
                7, Items.DIAMOND, 5);

        helper.startSequence()
                .thenExecuteAfter(20, () -> assertHolds(helper, anchor, Items.DIAMOND, 5,
                        "the diamonds moved one square along and the step gave up"))
                .thenSucceed();
    }

    private static void exactStepLooksNowhereElse(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = sendingAnchor(helper, 3, Items.DIAMOND,
                ActionSettings.StepSettings.DEFAULT.withSlot(
                        new SlotRule(SlotRule.Mode.EXACT, SlotRule.NONE)));
        stock(helper.getLevel(), helper.absolutePos(AnchorTestFixture.targetOf(ANCHOR)),
                7, Items.DIAMOND, 5);

        helper.startSequence()
                .thenExecuteAfter(20, () -> assertHolds(helper, anchor, Items.DIAMOND, 0,
                        "a step told to use one square only went looking anyway"))
                .thenSucceed();
    }

    private static void cappedStepMovesPartOfIt(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        BlockPos absolute = helper.absolutePos(target);
        stock(helper.getLevel(), absolute, 0, Items.DIAMOND, 12);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), 27 + 36, List.of(),
                                List.of(new SessionStep.Move(0, 9,
                                        BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND),
                                        SessionStep.Amount.ALL))),
                        ActionSettings.DEFAULT.withStep(0,
                                ActionSettings.StepSettings.DEFAULT.withAmount(
                                        java.util.Optional.of(SessionStep.Amount.ONE)))));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    ServerLevel level = helper.getLevel();
                    if (!TestItemPipes.present(level, absolute)) {
                        helper.fail("the barrel exposes no item handler");
                        return;
                    }
                    if (TestItemPipes.slot(level, absolute, 9).getCount() != 1) {
                        helper.fail("expected one moved of a stack of twelve, slot 9 holds "
                                + TestItemPipes.slot(level, absolute, 9).getCount());
                    }
                    if (TestItemPipes.slot(level, absolute, 0).getCount() != 11) {
                        helper.fail("the rest was not put back, slot 0 holds "
                                + TestItemPipes.slot(level, absolute, 0).getCount());
                    }
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND) != 0) {
                        helper.fail("the remainder came home with the clone");
                    }
                })
                .thenSucceed();
    }

    private static ChronoAnchorBlockEntity sendingAnchor(GameTestHelper helper, int from,
                                                         net.minecraft.world.item.Item item,
                                                         ActionSettings.StepSettings rule) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.BARREL);

        return AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), 27 + 36, List.of(),
                                List.of(send(from, item))),
                        ActionSettings.DEFAULT.withStep(0, rule)));
    }

    private static void assertHolds(GameTestHelper helper, ChronoAnchorBlockEntity anchor,
                                    net.minecraft.world.item.Item item, int expected, String what) {
        int held = AnchorTestFixture.countIn(anchor.getInventory(), item);
        if (held != expected) {
            helper.fail(what + ": expected " + expected + ", the anchor holds " + held);
        }
    }

    private static void skippedStepMovesNothing(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.BARREL);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(target);
        stock(level, absolute, 0, Items.DIAMOND, 5);
        stock(level, absolute, 1, Items.EMERALD, 5);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), 27 + 36, List.of(),
                                List.of(send(0, Items.DIAMOND), send(1, Items.EMERALD))),
                        ActionSettings.DEFAULT.withStep(0,
                                ActionSettings.StepSettings.DEFAULT.withEnabled(false))));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND) != 0) {
                        helper.fail("the skipped step ran anyway: the anchor holds "
                                + AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND)
                                + " diamonds");
                    }
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.EMERALD) != 5) {
                        helper.fail("the step after the skipped one did not run: the anchor holds "
                                + AnchorTestFixture.countIn(anchor.getInventory(), Items.EMERALD)
                                + " emeralds");
                    }
                })
                .thenSucceed();
    }

    private static SessionStep send(int from, net.minecraft.world.item.Item item) {
        return new SessionStep.Move(from, SessionStep.Move.ELSEWHERE,
                BuiltInRegistries.ITEM.wrapAsHolder(item), SessionStep.Amount.ALL);
    }

    private static void stock(ServerLevel level, BlockPos absolutePos, int slot,
                              net.minecraft.world.item.Item item, int amount) {
        TestItemPipes.insertIntoSlot(level, absolutePos, slot, item, amount);
    }

    private static void discardingLeavesTheAnchorBlank(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.clearRecording();

        if (anchor.getRecording() != null) {
            helper.fail("the anchor kept its routine after being told to discard it");
        }
        if (helper.getBlockState(ANCHOR).getValue(
                com.skilles.chronoclones.block.ChronoAnchorBlock.ACTIVE)) {
            helper.fail("a blank anchor is still lit as though it were running");
        }
        helper.succeed();
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static void editedSettingsReachTheRoutine(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        Recording edited = anchor.getRecording().withSettings(0, ActionSettings.DEFAULT
                .withName("Quarry the north face")
                .withSlot(new SlotRule(SlotRule.Mode.EXACT, 3)));
        anchor.reinterpret(edited);

        ActionSettings settings = anchor.getRecording().actions().getFirst().settings();
        if (!"Quarry the north face".equals(settings.name())) {
            helper.fail("the name did not survive the edit: " + settings.name());
        }
        if (settings.slot().mode() != SlotRule.Mode.EXACT || settings.slot().slot() != 3) {
            helper.fail("the slot rule did not survive the edit: " + settings.slot());
        }
        helper.succeed();
    }

    private static void reinterpretingDoesNotRestartClones(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    int before = anchor.getContainerData().get(
                            com.skilles.chronoclones.menu.AnchorData.playhead(0));
                    anchor.reinterpret(anchor.getRecording().withSettings(0,
                            ActionSettings.DEFAULT.withName("Renamed mid-stride")));

                    int after = anchor.getContainerData().get(
                            com.skilles.chronoclones.menu.AnchorData.playhead(0));
                    if (after != before) {
                        helper.fail("an edit moved the playhead from " + before + " to " + after);
                    }
                })
                .thenSucceed();
    }

}
