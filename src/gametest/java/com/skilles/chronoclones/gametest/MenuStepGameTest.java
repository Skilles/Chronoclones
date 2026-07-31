package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ExperienceStore;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;
import com.skilles.chronoclones.recording.SessionSteps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

final class MenuStepGameTest {

    private MenuStepGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("trade_survives_its_offers_being_reordered",
                MenuStepGameTest::tradeSurvivesReordering);
        ChronoclonesGameTests.add("trade_refuses_an_offer_that_is_gone",
                MenuStepGameTest::tradeRefusesAMissingOffer);
        ChronoclonesGameTests.add("trade_says_so_when_the_merchant_is_sold_out",
                MenuStepGameTest::tradeReportsBeingSoldOut);
        ChronoclonesGameTests.add("session_finds_a_villager_that_wandered",
                MenuStepGameTest::sessionFindsAVillagerThatMoved);
        ChronoclonesGameTests.add("anvil_names_what_it_is_given",
                MenuStepGameTest::anvilNamesWhatItIsGiven);
        ChronoclonesGameTests.add("anvil_without_banked_experience_says_so",
                MenuStepGameTest::anvilWithoutExperienceSaysSo);
        ChronoclonesGameTests.add("anvil_drinks_a_bottle_to_afford_the_work",
                MenuStepGameTest::anvilDrinksABottle);
        ChronoclonesGameTests.add("choosing_one_offer_twice_is_one_step",
                MenuStepGameTest::repeatedTradesAreOneStep);
    }

    private static void repeatedTradesAreOneStep(GameTestHelper helper) {
        SessionStep.Trade first = new SessionStep.Trade(new ItemStack(Items.EMERALD, 1),
                ItemStack.EMPTY, new ItemStack(Items.BRICK, 4));
        SessionStep.Trade again = new SessionStep.Trade(new ItemStack(Items.EMERALD, 1),
                ItemStack.EMPTY, new ItemStack(Items.BRICK, 4));

        List<SessionStep> steps = SessionSteps.interpret(List.of(
                new SessionSteps.Event.Did(first), new SessionSteps.Event.Did(again)));

        if (steps.size() != 1) {
            helper.fail("clicking one offer twice made " + steps.size() + " steps: " + steps);
        }

        SessionStep.Trade other = new SessionStep.Trade(new ItemStack(Items.EMERALD, 1),
                ItemStack.EMPTY, new ItemStack(Items.BREAD, 6));
        if (SessionSteps.interpret(List.of(new SessionSteps.Event.Did(first),
                new SessionSteps.Event.Did(other))).size() != 2) {
            helper.fail("two different offers were collapsed into one");
        }
        helper.succeed();
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static final int MERCHANT_MENU_SIZE = 3 + 36;
    private static final int MERCHANT_RESULT = 2;

    private static final int ANVIL_MENU_SIZE = 3 + 36;
    private static final int ANVIL_INPUT = 0;
    private static final int ANVIL_RESULT = 2;
    private static final int ANVIL_HOTBAR_0 = 3 + 27;

    private static void tradeSurvivesReordering(GameTestHelper helper) {
        Villager villager = merchant(helper, ANCHOR.north(),
                offer(Items.EMERALD, 1, Items.BREAD, 6),
                offer(Items.EMERALD, 1, Items.APPLE, 4));

        ChronoAnchorBlockEntity anchor = tradingAnchor(helper, villager, Items.APPLE, 4);

        setOffers(villager,
                offer(Items.EMERALD, 1, Items.APPLE, 4),
                offer(Items.EMERALD, 1, Items.BREAD, 6));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.APPLE) != 4) {
                        helper.fail("expected 4 apples traded for, the anchor holds "
                                + AnchorTestFixture.countIn(anchor.getInventory(), Items.APPLE)
                                + " apples and "
                                + AnchorTestFixture.countIn(anchor.getInventory(), Items.BREAD)
                                + " bread, reporting " + anchor.getLastFailure().reason());
                    }
                })
                .thenSucceed();
    }

    private static void tradeRefusesAMissingOffer(GameTestHelper helper) {
        Villager villager = merchant(helper, ANCHOR.north(),
                offer(Items.EMERALD, 1, Items.APPLE, 4));

        ChronoAnchorBlockEntity anchor = tradingAnchor(helper, villager, Items.APPLE, 4);
        setOffers(villager, offer(Items.EMERALD, 1, Items.BREAD, 6));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.BREAD) != 0) {
                        helper.fail("the clone bought bread it was never told to buy");
                    }
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_OFFER) {
                        helper.fail("expected the missing offer to be reported, got "
                                + anchor.getLastFailure().reason());
                    }
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.EMERALD) != 1) {
                        helper.fail("the emerald was spent on a trade that never happened");
                    }
                })
                .thenSucceed();
    }

    private static void tradeReportsBeingSoldOut(GameTestHelper helper) {
        Villager villager = merchant(helper, ANCHOR.north(),
                offer(Items.EMERALD, 1, Items.APPLE, 4));
        ChronoAnchorBlockEntity anchor = tradingAnchor(helper, villager, Items.APPLE, 4);

        villager.getOffers().getFirst().setToOutOfStock();

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (anchor.getLastFailure().reason()
                            != DiagnosticState.FailureReason.OUT_OF_STOCK) {
                        helper.fail("expected being sold out to be reported, got "
                                + anchor.getLastFailure().reason());
                    }
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.APPLE) != 0) {
                        helper.fail("a sold-out offer was traded anyway");
                    }
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.EMERALD) != 1) {
                        helper.fail("the payment was left in a sold-out merchant's slots");
                    }
                })
                .thenSucceed();
    }

    private static void sessionFindsAVillagerThatMoved(GameTestHelper helper) {
        Villager villager = merchant(helper, ANCHOR.north(),
                offer(Items.EMERALD, 1, Items.APPLE, 4));
        ChronoAnchorBlockEntity anchor = tradingAnchor(helper, villager, Items.APPLE, 4);

        Vec3 moved = villager.position().add(2.0, 0.0, 0.0);
        villager.snapTo(moved.x, moved.y, moved.z);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.APPLE) != 4) {
                        helper.fail("the villager moved two blocks and the session lost it");
                    }
                })
                .thenSucceed();
    }

    private static void anvilNamesWhatItIsGiven(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = renamingAnchor(helper);
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.IRON_SWORD), 1);
        int banked = ExperienceStore.pointsForLevels(5);
        anchor.setCloneExperience(0, new ExperienceStore(banked));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    ItemStack sword = AnchorTestFixture.findStack(anchor.getInventory(),
                            Items.IRON_SWORD);
                    if (sword == null) {
                        helper.fail("the sword never came back from the anvil");
                        return;
                    }
                    if (!sword.getHoverName().getString().equals("Tunneler")) {
                        helper.fail("the anvil returned a sword called "
                                + sword.getHoverName().getString());
                    }
                    if (anchor.getCloneExperience(0).points() >= banked) {
                        helper.fail("the anvil did its work for free: the clone still holds "
                                + anchor.getCloneExperience(0).points() + " of " + banked);
                    }
                })
                .thenSucceed();
    }

    private static void anvilWithoutExperienceSaysSo(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = renamingAnchor(helper);
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.IRON_SWORD), 1);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (anchor.getLastFailure().reason()
                            != DiagnosticState.FailureReason.NO_EXPERIENCE) {
                        helper.fail("expected the shortfall to be reported, got "
                                + anchor.getLastFailure().reason());
                    }
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.IRON_SWORD) != 1) {
                        helper.fail("the sword did not come back from the anvil it could not pay for");
                    }
                })
                .thenSucceed();
    }

    private static void anvilDrinksABottle(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = renamingAnchor(helper);
        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.IRON_SWORD), 1);
        anchor.getCloneInventory(0).set(1, ItemResource.of(Items.EXPERIENCE_BOTTLE), 4);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    ItemStack sword = AnchorTestFixture.findStack(anchor.getInventory(),
                            Items.IRON_SWORD);
                    if (sword == null || !sword.getHoverName().getString().equals("Tunneler")) {
                        helper.fail("the anvil returned "
                                + (sword == null ? "nothing" : sword.getHoverName().getString())
                                + ", reporting " + anchor.getLastFailure().reason());
                        return;
                    }
                    int bottles = AnchorTestFixture.countIn(anchor.getInventory(),
                            Items.EXPERIENCE_BOTTLE);
                    if (bottles == 4) {
                        helper.fail("the work was done without drinking anything");
                    }
                    if (bottles < 1) {
                        helper.fail("it drank all four bottles for one level of work");
                    }
                })
                .thenSucceed();
    }

    private static ChronoAnchorBlockEntity renamingAnchor(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.ANVIL);

        return AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new MenuTarget.Block(new BlockPos(0, 0, -1)), ANVIL_MENU_SIZE, List.of(),
                        List.of(
                                move(ANVIL_HOTBAR_0, ANVIL_INPUT, Items.IRON_SWORD),
                                new SessionStep.Rename("Tunneler"),
                                new SessionStep.Move(ANVIL_RESULT, SessionStep.Move.ELSEWHERE,
                                        BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_SWORD),
                                        SessionStep.Amount.ALL)))));
    }

    private static ChronoAnchorBlockEntity tradingAnchor(GameTestHelper helper, Villager villager,
                                                         net.minecraft.world.item.Item bought,
                                                         int count) {
        Vec3 local = LocalSpace.toLocal(villager.position(),
                helper.absolutePos(ANCHOR), Direction.NORTH);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new MenuTarget.Entity(local,
                                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.VILLAGER)),
                        MERCHANT_MENU_SIZE, List.of(),
                        List.of(
                                new SessionStep.Trade(new ItemStack(Items.EMERALD, 1),
                                        ItemStack.EMPTY, new ItemStack(bought, count)),
                                new SessionStep.Move(MERCHANT_RESULT, SessionStep.Move.ELSEWHERE,
                                        BuiltInRegistries.ITEM.wrapAsHolder(bought),
                                        SessionStep.Amount.ALL)))));

        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.EMERALD), 1);
        return anchor;
    }

    private static SessionStep.Move move(int from, int to, net.minecraft.world.item.Item item) {
        return new SessionStep.Move(from, to, BuiltInRegistries.ITEM.wrapAsHolder(item),
                SessionStep.Amount.ALL);
    }

    private static Villager merchant(GameTestHelper helper, BlockPos relative,
                                     MerchantOffer... offers) {
        Villager villager = EntityTypes.VILLAGER.spawn(helper.getLevel(),
                helper.absolutePos(relative), EntitySpawnReason.TRIGGERED);
        if (villager == null) {
            helper.fail("could not spawn the villager this test is about");
            throw new IllegalStateException("unreachable");
        }
        villager.setNoAi(true);
        setOffers(villager, offers);
        return villager;
    }

    private static void setOffers(Villager villager, MerchantOffer... offers) {
        MerchantOffers live = villager.getOffers();
        live.clear();
        live.addAll(List.of(offers));
    }

    private static MerchantOffer offer(net.minecraft.world.item.Item cost, int costCount,
                                       net.minecraft.world.item.Item result, int resultCount) {
        return new MerchantOffer(new ItemCost(cost, costCount),
                new ItemStack(result, resultCount), 16, 0, 0.0f);
    }
}
