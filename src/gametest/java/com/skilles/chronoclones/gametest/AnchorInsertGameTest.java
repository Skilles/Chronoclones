package com.skilles.chronoclones.gametest;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.registry.ModBlocks;
import com.skilles.chronoclones.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;

final class AnchorInsertGameTest {

    private AnchorInsertGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("a_hopper_cannot_feed_an_anchor_before_an_imprint",
                AnchorInsertGameTest::hopperWaitsForAnImprint);
        ChronoclonesGameTests.add("the_fuel_slot_takes_only_what_will_burn",
                AnchorInsertGameTest::fuelSlotTakesOnlyFuel);
        ChronoclonesGameTests.add("the_upgrade_slots_take_only_upgrades",
                AnchorInsertGameTest::upgradeSlotsTakeOnlyUpgrades);
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static void hopperWaitsForAnImprint(GameTestHelper helper) {
        helper.setBlock(ANCHOR, ModBlocks.CHRONO_ANCHOR.get().defaultBlockState());
        BlockPos absolute = helper.absolutePos(ANCHOR);
        if (!(helper.getLevel().getBlockEntity(absolute)
                instanceof ChronoAnchorBlockEntity anchor)) {
            helper.fail("anchor block entity missing");
            return;
        }

        if (!TestItemPipes.present(helper.getLevel(), absolute)) {
            helper.fail("the anchor exposes no item handler at all");
            return;
        }

        if (TestItemPipes.insert(helper.getLevel(), absolute, Items.DIAMOND, 4) != 0) {
            helper.fail("a hopper could feed an anchor whose storage the screen still hides,"
                    + " and the items would look lost until an imprint");
            return;
        }

        anchor.imprint(AnchorTestFixture.breakOneBlock(Blocks.STONE),
                AnchorTestFixture.owner(helper.getLevel()));

        if (TestItemPipes.insert(helper.getLevel(), absolute, Items.DIAMOND, 4) != 4) {
            helper.fail("an imprinted anchor refused the hopper that stocks it");
            return;
        }
        if (AnchorTestFixture.countIn(anchor.getInventory(), Items.DIAMOND) != 4) {
            helper.fail("the committed insertion never reached the clone storage");
            return;
        }
        helper.succeed();
    }

    private static ChronoAnchorMenu menuOn(GameTestHelper helper) {
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(
                helper, ANCHOR, AnchorTestFixture.breakOneBlock(Blocks.STONE));
        ServerPlayer player = AnchorTestFixture.owner(helper.getLevel());
        return new ChronoAnchorMenu(1, player.getInventory(), anchor, anchor.getContainerData());
    }

    private static void fuelSlotTakesOnlyFuel(GameTestHelper helper) {
        ChronoAnchorMenu menu = menuOn(helper);
        var fuel = menu.getSlot(ChronoAnchorMenu.FUEL_SLOT);

        if (fuel.mayPlace(new ItemStack(Items.DIAMOND))) {
            helper.fail("the fuel slot took a diamond it can never burn");
            return;
        }
        if (!fuel.mayPlace(new ItemStack(Items.COAL))) {
            helper.fail("the fuel slot refused coal");
            return;
        }
        if (!fuel.mayPlace(new ItemStack(ModItems.CREATIVE_CHARGE_CELL.get()))) {
            helper.fail("the fuel slot refused the creative charge cell");
            return;
        }
        helper.succeed();
    }

    private static void upgradeSlotsTakeOnlyUpgrades(GameTestHelper helper) {
        ChronoAnchorMenu menu = menuOn(helper);
        var upgrade = menu.getSlot(ChronoAnchorMenu.FUEL_SLOT + 1);

        if (upgrade.mayPlace(new ItemStack(Items.COAL))) {
            helper.fail("an upgrade slot took coal, which the upgrade tally silently ignores");
            return;
        }
        if (!upgrade.mayPlace(new ItemStack(ModItems.CHRONO_SPLITTER.get()))) {
            helper.fail("an upgrade slot refused the splitter");
            return;
        }
        helper.succeed();
    }
}
