package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.menu.AnchorData;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;

final class MenuPagingGameTest {

    private MenuPagingGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("shift_click_stays_on_the_visible_page",
                MenuPagingGameTest::shiftClickStaysOnThePage);
        ChronoclonesGameTests.add("menu_refuses_a_page_with_no_clone",
                MenuPagingGameTest::refusesAPageWithNoClone);
        ChronoclonesGameTests.add("menu_selection_needs_only_synced_data",
                MenuPagingGameTest::selectionNeedsOnlySyncedData);
        ChronoclonesGameTests.add("an_unimprinted_anchor_refuses_a_shift_click",
                MenuPagingGameTest::unimprintedAnchorRefusesAShiftClick);
    }

    private static void unimprintedAnchorRefusesAShiftClick(GameTestHelper helper) {
        helper.setBlock(ANCHOR, com.skilles.chronoclones.registry.ModBlocks.CHRONO_ANCHOR.get()
                .defaultBlockState());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(ANCHOR))
                instanceof ChronoAnchorBlockEntity anchor)) {
            helper.fail("anchor block entity missing");
            return;
        }

        ServerPlayer player = AnchorTestFixture.owner(helper.getLevel());
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 12));

        ChronoAnchorMenu menu = new ChronoAnchorMenu(1, player.getInventory(), anchor,
                anchor.getContainerData());
        menu.quickMoveStack(player, PLAYER_SLOTS_START);

        if (player.getInventory().getItem(9).getCount() != 12) {
            helper.fail("a shift-click into a blank anchor took the stack: its storage is hidden,"
                    + " so whatever it swallows looks lost until an imprint reveals it");
            return;
        }
        for (int clone = 0; clone < ChronoAnchorBlockEntity.CLONE_INVENTORIES; clone++) {
            if (AnchorTestFixture.countIn(anchor.getCloneInventory(clone), Items.DIAMOND) != 0) {
                helper.fail("clone " + clone + " swallowed the diamonds before any imprint");
                return;
            }
        }
        helper.succeed();
    }

    private static void selectionNeedsOnlySyncedData(GameTestHelper helper) {
        ChronoAnchorBlockEntity untickedAnchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        ServerPlayer player = AnchorTestFixture.owner(helper.getLevel());

        SimpleContainerData synced = new SimpleContainerData(ChronoAnchorMenu.DATA_COUNT);
        synced.set(AnchorData.ACTIVE_CLONES, 2);

        ChronoAnchorMenu menu = new ChronoAnchorMenu(1, player.getInventory(), untickedAnchor, synced);
        if (!menu.clickMenuButton(player, 1)) {
            helper.fail("the second tab was refused a menu that had been told there are two clones");
            return;
        }
        if (menu.getSelectedClone() != 1) {
            helper.fail("the selection did not move to clone 2");
        }
        helper.succeed();
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static final int PLAYER_SLOTS_START =
            ChronoAnchorBlockEntity.CLONE_INVENTORY_SLOTS * ChronoAnchorBlockEntity.CLONE_INVENTORIES
                    + 1 + ChronoAnchorBlockEntity.UPGRADE_SLOTS;

    private static void shiftClickStaysOnThePage(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = anchorWithClones(helper, 4);
        ServerPlayer player = AnchorTestFixture.owner(helper.getLevel());
        player.getInventory().clearContent();

        ChronoAnchorMenu menu = new ChronoAnchorMenu(1, player.getInventory(), anchor,
                anchor.getContainerData());
        if (!menu.clickMenuButton(player, 2)) {
            helper.fail("the menu refused to show clone 3 of four");
            return;
        }

        player.getInventory().setItem(9, new ItemStack(Items.DIAMOND, 12));
        menu.quickMoveStack(player, PLAYER_SLOTS_START);

        for (int clone = 0; clone < ChronoAnchorBlockEntity.CLONE_INVENTORIES; clone++) {
            int held = AnchorTestFixture.countIn(anchor.getCloneInventory(clone), Items.DIAMOND);
            int wanted = clone == 2 ? 12 : 0;
            if (held != wanted) {
                helper.fail("clone " + clone + " holds " + held + " diamonds, expected " + wanted);
                return;
            }
        }
        helper.succeed();
    }

    private static void refusesAPageWithNoClone(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = anchorWithClones(helper, 2);
        ServerPlayer player = AnchorTestFixture.owner(helper.getLevel());

        ChronoAnchorMenu menu = new ChronoAnchorMenu(1, player.getInventory(), anchor,
                anchor.getContainerData());
        if (menu.clickMenuButton(player, 3)) {
            helper.fail("the menu opened a page for a clone that does not exist");
            return;
        }
        if (menu.getSelectedClone() != 0) {
            helper.fail("a refused page still moved the selection to " + menu.getSelectedClone());
        }
        helper.succeed();
    }

    private static ChronoAnchorBlockEntity anchorWithClones(GameTestHelper helper, int clones) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        anchor.getUpgradeHandler().setItem(0, new ItemStack(ModItems.CHRONO_SPLITTER.get(), clones - 1));
        anchor.serverTick();
        return anchor;
    }
}
