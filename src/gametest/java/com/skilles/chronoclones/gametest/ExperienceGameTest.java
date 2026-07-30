package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.ExperienceStore;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Experience a clone earns, keeps, and hands back when its anchor is broken.
 */
final class ExperienceGameTest {

    private ExperienceGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("mined_ore_banks_its_experience",
                ExperienceGameTest::minedOreBanksItsExperience);
        ChronoclonesGameTests.add("mined_stone_banks_nothing",
                ExperienceGameTest::minedStoneBanksNothing);
        ChronoclonesGameTests.add("broken_anchor_gives_back_banked_experience",
                ExperienceGameTest::brokenAnchorGivesBackExperience);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /**
     * destroyBlock never runs playerDestroy, so an ore mined by a clone used to pay nothing at all.
     *
     * <p>Diamond rather than coal: coal owes {@code UniformInt.of(0, 2)}, so a clone banking nothing
     * from one is a legal roll of the dice and this test would fail about one run in three. Diamond
     * owes three at the least.
     */
    private static void minedOreBanksItsExperience(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.DIAMOND_ORE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.DIAMOND_ORE));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.DIAMOND) == 0) {
                        helper.fail("the ore was never broken, so this proves nothing about its XP");
                        return;
                    }
                    if (anchor.getCloneExperience(0).points() < 3) {
                        helper.fail("a diamond ore was mined and the clone banked "
                                + anchor.getCloneExperience(0).points() + " points");
                    }
                })
                .thenSucceed();
    }

    /** The counterpart: a block that owes nothing must not somehow pay. */
    private static void minedStoneBanksNothing(GameTestHelper helper) {
        helper.setBlock(AnchorTestFixture.targetOf(ANCHOR), Blocks.STONE);
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.COBBLESTONE) == 0) {
                        helper.fail("the stone was never broken");
                        return;
                    }
                    if (!anchor.getCloneExperience(0).isEmpty()) {
                        helper.fail("plain stone paid " + anchor.getCloneExperience(0).points()
                                + " points");
                    }
                })
                .thenSucceed();
    }

    /** Banked experience belongs to whoever breaks the anchor, like everything else inside it. */
    private static void brokenAnchorGivesBackExperience(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.setCloneExperience(2, new ExperienceStore(40));

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(ANCHOR);
        level.destroyBlock(absolute, true);

        helper.startSequence()
                .thenExecuteAfter(8, () -> {
                    List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class,
                            new AABB(absolute).inflate(5.0));
                    int total = orbs.stream().mapToInt(ExperienceOrb::getValue).sum();
                    // At least, not exactly: the box has to be wide enough for a spilled orb's random
                    // motion, and every test shares one world, so a villager trading in the next test
                    // along drops orbs of its own within reach of it.
                    if (total < 40) {
                        helper.fail("expected 40 points back on the ground, found " + total);
                    }
                })
                .thenSucceed();
    }
}
