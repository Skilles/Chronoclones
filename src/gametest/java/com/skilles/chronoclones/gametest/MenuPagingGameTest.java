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
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The anchor menu showing one clone's inventory at a time.
 */
final class MenuPagingGameTest {

    private MenuPagingGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("shift_click_stays_on_the_visible_page",
                MenuPagingGameTest::shiftClickStaysOnThePage);
        ChronoclonesGameTests.add("menu_refuses_a_page_with_no_clone",
                MenuPagingGameTest::refusesAPageWithNoClone);
        ChronoclonesGameTests.add("menu_selection_needs_only_synced_data",
                MenuPagingGameTest::selectionNeedsOnlySyncedData);
    }

    /**
     * The clone count a page is checked against has to be the synced one.
     *
     * <p>UpgradeState is recomputed on the server tick and nowhere else, so a client asking its own
     * block entity is always told there is one clone, and every tab but the first stops working.
     */
    private static void selectionNeedsOnlySyncedData(GameTestHelper helper) {
        // An anchor that has never ticked, which is the state a client's copy is always in.
        ChronoAnchorBlockEntity untickedAnchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        FakePlayer player = AnchorTestFixture.owner(helper.getLevel());

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

    /** Menu order: every clone's squares, then fuel, then the modules, then the player. */
    private static final int PLAYER_SLOTS_START =
            ChronoAnchorBlockEntity.CLONE_INVENTORY_SLOTS * ChronoAnchorBlockEntity.CLONE_INVENTORIES
                    + 1 + ChronoAnchorBlockEntity.UPGRADE_SLOTS;

    /**
     * {@code moveItemStackTo} does not check {@code isActive}, so this is the one place the tabs
     * could leak: a shift-click that lands in a page nobody can see.
     */
    private static void shiftClickStaysOnThePage(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = anchorWithClones(helper, 4);
        FakePlayer player = AnchorTestFixture.owner(helper.getLevel());
        player.getInventory().clearContent();

        ChronoAnchorMenu menu = new ChronoAnchorMenu(1, player.getInventory(), anchor,
                anchor.getContainerData());
        if (!menu.clickMenuButton(player, 2)) {
            helper.fail("the menu refused to show clone 3 of four");
            return;
        }

        // Inventory slot 9 is the first square the menu lists after the anchor's own.
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

    /** A page with no clone behind it would take items nothing ever draws again. */
    private static void refusesAPageWithNoClone(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = anchorWithClones(helper, 2);
        FakePlayer player = AnchorTestFixture.owner(helper.getLevel());

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
        anchor.getUpgradeHandler().set(0, ItemResource.of(ModItems.CHRONO_SPLITTER.get()), clones - 1);
        // The clone count is read on tick; the menu asks for it straight away.
        anchor.serverTick();
        return anchor;
    }
}
