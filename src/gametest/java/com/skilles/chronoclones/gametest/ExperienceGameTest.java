package com.skilles.chronoclones.gametest;

import java.util.List;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.ExperienceStore;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.AABB;

final class ExperienceGameTest {

    private ExperienceGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("mined_ore_banks_its_experience",
                ExperienceGameTest::minedOreBanksItsExperience);
        ChronoclonesGameTests.add("mined_stone_banks_nothing",
                ExperienceGameTest::minedStoneBanksNothing);
        ChronoclonesGameTests.add("broken_anchor_gives_back_banked_experience",
                ExperienceGameTest::brokenAnchorGivesBackExperience);
        ChronoclonesGameTests.add("smelted_result_banks_the_furnace_experience",
                ExperienceGameTest::smeltedResultBanksItsExperience);
    }

    private static void smeltedResultBanksItsExperience(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.FURNACE);

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(target);
        if (!(level.getBlockEntity(absolute) instanceof FurnaceBlockEntity furnace)) {
            helper.fail("no furnace block entity to seed");
            return;
        }

        RecipeHolder<?> smelting = level.recipeAccess().getRecipes().stream()
                .filter(holder -> holder.value() instanceof SmeltingRecipe recipe
                        && recipe.experience() > 0.0f)
                .findFirst()
                .orElse(null);
        if (smelting == null) {
            helper.fail("no smelting recipe worth any experience to seed the furnace with");
            return;
        }
        for (int use = 0; use < 20; use++) {
            furnace.setRecipeUsed(smelting);
        }
        furnace.setItem(FURNACE_RESULT, new ItemStack(Items.IRON_INGOT, 8));

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR,
                AnchorTestFixture.routine(new ChronoAction.UseContainer(
                        new MenuTarget.Block(new BlockPos(0, 0, -1)), FURNACE_MENU_SIZE, List.of(),
                        List.of(new SessionStep.Move(FURNACE_RESULT, SessionStep.Move.ELSEWHERE,
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_INGOT),
                                SessionStep.Amount.ALL)))));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    if (AnchorTestFixture.countIn(anchor.getCloneInventory(0), Items.IRON_INGOT) == 0) {
                        helper.fail("the result was never taken, so this proves nothing about its XP");
                        return;
                    }
                    if (anchor.getCloneExperience(0).isEmpty()) {
                        helper.fail("a furnace result was taken and the clone banked no experience");
                    }
                })
                .thenSucceed();
    }

    private static final int FURNACE_MENU_SIZE = 3 + 36;
    private static final int FURNACE_RESULT = 2;

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

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
                    if (total != 40) {
                        helper.fail("expected 40 points back on the ground, found " + total);
                    }
                })
                .thenSucceed();
    }
}
