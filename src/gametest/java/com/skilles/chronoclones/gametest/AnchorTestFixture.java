package com.skilles.chronoclones.gametest;

import java.util.List;
import java.util.UUID;

import com.skilles.chronoclones.block.ChronoAnchorBlock;
import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.block.UpgradeState;
import com.skilles.chronoclones.recording.ActionSettings;
import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MotionSample;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.TimedAction;
import com.skilles.chronoclones.registry.ModBlocks;
import com.skilles.chronoclones.registry.ModItems;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.skilles.chronoclones.inventory.StackInventory;

import net.minecraft.world.Container;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class AnchorTestFixture {

    static final UUID AUTHOR_ID = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    static final String AUTHOR_NAME = "RoutineAuthor";

    static final UUID OWNER_ID = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
    static final String OWNER_NAME = "AnchorOwner";

    private AnchorTestFixture() {}

    static Recording breakOneBlock(Block expected) {
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, new ChronoAction.BreakBlock(
                        new BlockPos(0, 0, -1),
                        BuiltInRegistries.BLOCK.wrapAsHolder(expected),
                        new ItemStack(Items.NETHERITE_PICKAXE)))),
                20, AUTHOR_NAME, AUTHOR_ID);
    }

    static Recording routine(ChronoAction action) {
        return routine(List.of(action));
    }

    static Recording routine(ChronoAction action, int heldSlot) {
        return routine(action, ActionSettings.DEFAULT.withSlot(ActionSettings.SlotRule.prefer(heldSlot)));
    }

    static Recording routine(ChronoAction action, ActionSettings settings) {
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, action, settings)),
                20, AUTHOR_NAME, AUTHOR_ID);
    }

    static Recording routine(List<ChronoAction> actions) {
        List<TimedAction> timed = new java.util.ArrayList<>(actions.size());
        for (int i = 0; i < actions.size(); i++) {
            timed.add(new TimedAction(1 + i, actions.get(i)));
        }
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.copyOf(timed),
                20, AUTHOR_NAME, AUTHOR_ID);
    }

    static BlockPos targetOf(BlockPos anchorPos) {
        return anchorPos.north();
    }

    static ChronoAnchorBlockEntity placeAndImprint(GameTestHelper helper, BlockPos relativeAnchorPos,
                                                 Recording recording) {
        return placeAndImprint(helper, relativeAnchorPos, recording, owner(helper.getLevel()));
    }

    static ChronoAnchorBlockEntity placeAndImprint(GameTestHelper helper, BlockPos relativeAnchorPos,
                                                 Recording recording,
                                                 net.minecraft.server.level.ServerPlayer imprinter) {
        ChronoAnchorBlockEntity anchor =
                placeAndImprintUnfueled(helper, relativeAnchorPos, recording, imprinter);
        giveInfiniteCharge(anchor);
        return anchor;
    }

    static ChronoAnchorBlockEntity placeAndImprintUnfueled(GameTestHelper helper,
                                                           BlockPos relativeAnchorPos,
                                                           Recording recording) {
        return placeAndImprintUnfueled(helper, relativeAnchorPos, recording, owner(helper.getLevel()));
    }

    static ChronoAnchorBlockEntity placeAndImprintUnfueled(GameTestHelper helper,
                                                           BlockPos relativeAnchorPos,
                                                           Recording recording,
                                                           net.minecraft.server.level.ServerPlayer imprinter) {
        helper.setBlock(relativeAnchorPos, ModBlocks.CHRONO_ANCHOR.get()
                .defaultBlockState()
                .setValue(ChronoAnchorBlock.FACING, Direction.NORTH));

        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relativeAnchorPos);

        if (!(level.getBlockEntity(absolute) instanceof ChronoAnchorBlockEntity anchor)) {
            helper.fail("anchor block entity missing at " + relativeAnchorPos);
            throw new IllegalStateException("unreachable");
        }

        requireRoom(helper);
        anchor.imprint(recording, imprinter);
        giveRecordedTools(anchor, recording);
        return anchor;
    }

    static void giveRecordedTools(ChronoAnchorBlockEntity anchor, Recording recording) {
        for (TimedAction timed : recording.actions()) {
            ItemStack swung = switch (timed.action()) {
                case ChronoAction.BreakBlock breaking -> breaking.toolTemplate();
                case ChronoAction.AttackEntity attacking -> attacking.weaponTemplate();
                default -> ItemStack.EMPTY;
            };
            if (swung.isEmpty()) {
                continue;
            }
            for (int clone = 0; clone < ChronoAnchorBlockEntity.CLONE_INVENTORIES; clone++) {
                StackInventory inventory = anchor.getCloneInventory(clone);
                if (countIn(inventory, swung.getItem()) == 0) {
                    inventory.setItem(inventory.size() - 1, swung.copyWithCount(1));
                }
            }
        }
    }

    private static final int PLOT_SIZE = 17;

    /** A structure that fails to load leaves a one-block plot and fails in baffling ways. */
    private static void requireRoom(GameTestHelper helper) {
        AABB plot = helper.getBounds();
        if (plot.getXsize() < PLOT_SIZE || plot.getZsize() < PLOT_SIZE) {
            helper.fail("this plot is " + (int) plot.getXsize() + "x" + (int) plot.getZsize()
                    + " and the tests need " + PLOT_SIZE + "x" + PLOT_SIZE
                    + ": the chronoclones:test_plot structure did not load, so the framework is"
                    + " loading, clearing and ticking one block of it");
        }
    }

    static FakePlayer owner(ServerLevel level) {
        return FakePlayerFactory.get(level, new GameProfile(OWNER_ID, OWNER_NAME));
    }

    static void giveInfiniteCharge(ChronoAnchorBlockEntity anchor) {
        anchor.getFuelHandler().setItem(0, new ItemStack(ModItems.CREATIVE_CHARGE_CELL.get()));
    }

    static BlockState stateAt(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockState(relative);
    }

    static void fillSlot(GameTestHelper helper, BlockPos relative, int slot,
                         net.minecraft.world.item.ItemStack stack) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(relative))
                instanceof net.minecraft.world.Container container) {
            container.setItem(slot, stack);
        } else {
            helper.fail("no container at " + relative);
        }
    }

    static net.minecraft.world.item.ItemStack findStack(
            Container container, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack held = container.getItem(slot);
            if (!held.isEmpty() && held.getItem() == item) {
                return held.copyWithCount(Math.max(1, held.getCount()));
            }
        }
        return null;
    }

    static int countIn(Container container, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack held = container.getItem(slot);
            if (!held.isEmpty() && held.getItem() == item) {
                total += held.getCount();
            }
        }
        return total;
    }
}
