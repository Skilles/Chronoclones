package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.DiagnosticState;
import com.skilles.chronoclones.block.ExperienceStore;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.LocalSpace;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;

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

/**
 * The menus a session reaches that are not a chest: a villager's offers, an anvil's name field.
 */
final class MenuStepGameTest {

    private MenuStepGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("trade_survives_its_offers_being_reordered",
                MenuStepGameTest::tradeSurvivesReordering);
        ChronoclonesGameTests.add("trade_refuses_an_offer_that_is_gone",
                MenuStepGameTest::tradeRefusesAMissingOffer);
        ChronoclonesGameTests.add("session_finds_a_villager_that_wandered",
                MenuStepGameTest::sessionFindsAVillagerThatMoved);
        ChronoclonesGameTests.add("anvil_names_what_it_is_given",
                MenuStepGameTest::anvilNamesWhatItIsGiven);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /** Vanilla menu order for a merchant: two payment slots, the result, then the player's own. */
    private static final int MERCHANT_MENU_SIZE = 3 + 36;
    private static final int MERCHANT_RESULT = 2;

    private static final int ANVIL_MENU_SIZE = 3 + 36;
    private static final int ANVIL_INPUT = 0;
    private static final int ANVIL_RESULT = 2;
    /** Where the carrier puts the clone's first square, in a menu with three of its own. */
    private static final int ANVIL_HOTBAR_0 = 3 + 27;

    /**
     * A villager's trades reorder as it levels, so the fifth trade of that day is not the same
     * promise as the fifth trade today. The offer is matched, never the index.
     */
    private static void tradeSurvivesReordering(GameTestHelper helper) {
        Villager villager = merchant(helper, ANCHOR.north(),
                offer(Items.EMERALD, 1, Items.BREAD, 6),
                offer(Items.EMERALD, 1, Items.APPLE, 4));

        ChronoAnchorBlockEntity anchor = tradingAnchor(helper, villager, Items.APPLE, 4);

        // Recorded second, offered first: an index would buy bread.
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

    /** With the offer gone there is nothing to buy, and nothing worth guessing at. */
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
                    if (anchor.getLastFailure().reason() != DiagnosticState.FailureReason.NO_TARGET) {
                        helper.fail("expected the missing offer to be reported, got "
                                + anchor.getLastFailure().reason());
                    }
                    // And the payment is still in the anchor rather than in a villager's pocket.
                    if (AnchorTestFixture.countIn(anchor.getInventory(), Items.EMERALD) != 1) {
                        helper.fail("the emerald was spent on a trade that never happened");
                    }
                })
                .thenSucceed();
    }

    /** An entity target is a point to look around, not a square to reach into. */
    private static void sessionFindsAVillagerThatMoved(GameTestHelper helper) {
        Villager villager = merchant(helper, ANCHOR.north(),
                offer(Items.EMERALD, 1, Items.APPLE, 4));
        ChronoAnchorBlockEntity anchor = tradingAnchor(helper, villager, Items.APPLE, 4);

        // Two blocks off where it was recorded, well inside the default radius.
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

    /**
     * A rename travels by its own packet, so it is a step of its own rather than a click.
     */
    private static void anvilNamesWhatItIsGiven(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.ANVIL);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new MenuTarget.Block(new BlockPos(0, 0, -1)), ANVIL_MENU_SIZE, List.of(),
                        List.of(
                                move(ANVIL_HOTBAR_0, ANVIL_INPUT, Items.IRON_SWORD),
                                new SessionStep.Rename("Tunneler"),
                                new SessionStep.Move(ANVIL_RESULT, SessionStep.Move.ELSEWHERE,
                                        BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_SWORD),
                                        SessionStep.Amount.ALL)))));

        anchor.getCloneInventory(0).set(0, ItemResource.of(Items.IRON_SWORD), 1);
        // An anvil charges a level for the work; commit by commit, the clone banks its own.
        anchor.setCloneExperience(0, new ExperienceStore(ExperienceStore.pointsForLevels(5)));

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
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------- helpers

    /**
     * An anchor whose routine buys one thing from {@code villager}, by what it offers.
     */
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

    /**
     * A villager with exactly the offers the test is about, so no profession or levelling is
     * involved in deciding what it sells.
     */
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

    /**
     * Puts exactly these offers in the villager's book.
     *
     * <p>Through the live list rather than {@code overrideOffers}, which does nothing on the server:
     * it exists for the client to be told what a merchant sells.
     */
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
