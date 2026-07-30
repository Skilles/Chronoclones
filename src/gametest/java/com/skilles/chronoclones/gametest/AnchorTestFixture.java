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
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Shared setup for anchor game tests.
 */
final class AnchorTestFixture {

    /** Whoever recorded the routine. Cosmetic only: decides whose skin a clone wears. */
    static final UUID AUTHOR_ID = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    static final String AUTHOR_NAME = "RoutineAuthor";

    /** Whoever imprinted the anchor. Every event and permission check must see this identity. */
    static final UUID OWNER_ID = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
    static final String OWNER_NAME = "AnchorOwner";

    private AnchorTestFixture() {}

    /**
     * A recording that breaks a single block one step north of the anchor.
     */
    static Recording breakOneBlock(Block expected) {
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, new ChronoAction.BreakBlock(
                        new BlockPos(0, 0, -1),
                        BuiltInRegistries.BLOCK.wrapAsHolder(expected),
                        new ItemStack(Items.NETHERITE_PICKAXE)))),
                20, AUTHOR_NAME, AUTHOR_ID);
    }

    /** A one-action routine, for exercising a single executor path. */
    static Recording routine(ChronoAction action) {
        return routine(List.of(action));
    }

    /** The same, recorded as if the player held the item in {@code heldSlot}. */
    static Recording routine(ChronoAction action, int heldSlot) {
        return routine(action, ActionSettings.DEFAULT.withSlot(ActionSettings.SlotRule.prefer(heldSlot)));
    }

    /** The same, with the interpretation the editor would have written. */
    static Recording routine(ChronoAction action, ActionSettings settings) {
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, -1), 0f, 0f)),
                List.of(new TimedAction(1, action, settings)),
                20, AUTHOR_NAME, AUTHOR_ID);
    }

    /** Several actions on consecutive ticks, for paths whose point is that they happen in sequence. */
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

    /** The world position the above routine targets, for an anchor at {@code anchorPos}. */
    static BlockPos targetOf(BlockPos anchorPos) {
        return anchorPos.north();
    }

    /**
     * Places a north-facing anchor and imprints {@code recording} as {@link #OWNER_ID}.
     */
    static ChronoAnchorBlockEntity placeAndImprint(GameTestHelper helper, BlockPos relativeAnchorPos,
                                                 Recording recording) {
        return placeAndImprint(helper, relativeAnchorPos, recording, owner(helper.getLevel()));
    }

    /** The same, imprinted by somebody specific: for tests about who may do what afterwards. */
    static ChronoAnchorBlockEntity placeAndImprint(GameTestHelper helper, BlockPos relativeAnchorPos,
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

        keepTicking(helper, relativeAnchorPos);
        anchor.imprint(recording, imprinter);
        giveInfiniteCharge(anchor);
        giveRecordedTools(anchor, recording);
        return anchor;
    }

    /**
     * Stocks every clone with the tool each break in {@code recording} was recorded swinging.
     *
     * <p>A clone digs with a tool it owns, the same as it places with a block it owns, so a test
     * about anything else has to be given one or it is really a test about an empty inventory.
     * Alongside the creative charge cell, and for the same reason.
     *
     * <p>Only break tools: what a placement holds is the thing it consumes, and handing that out
     * would feed the tests whose whole point is an anchor with nothing to build from.
     */
    static void giveRecordedTools(ChronoAnchorBlockEntity anchor, Recording recording) {
        for (TimedAction timed : recording.actions()) {
            if (!(timed.action() instanceof ChronoAction.BreakBlock breaking)
                    || breaking.toolTemplate().isEmpty()) {
                continue;
            }
            ItemResource tool = ItemResource.of(breaking.toolTemplate());
            for (int clone = 0; clone < ChronoAnchorBlockEntity.CLONE_INVENTORIES; clone++) {
                ItemStacksResourceHandler inventory = anchor.getCloneInventory(clone);
                if (countIn(inventory, breaking.toolTemplate().getItem()) == 0) {
                    // The last square, so a test filling the inventory from the front has to
                    // reach the tool deliberately rather than by accident.
                    inventory.set(inventory.size() - 1, tool, 1);
                }
            }
        }
    }

    /** How far from the anchor a routine may reach, and so how far its chunks must tick. */
    private static final int WORKING_RADIUS = 8;

    /**
     * Forces the chunks a plot works in, so the entities standing in them are ticked.
     *
     * <p>The framework force-loads only the chunks its structure box covers, and these tests build
     * their scenery outside that box: an anchor a couple of blocks from the corner can sit in the
     * next chunk along, which loads far enough to tick block entities and not far enough to tick
     * mobs. An anchor is a block entity and a cow is not, so the anchor would go on swinging at a
     * mob whose invulnerability never wore off -- one swing landing and every later one refused.
     *
     * <p>Nothing here has to undo this: the framework drops every forced chunk in the level when
     * the batch ends.
     */
    private static void keepTicking(GameTestHelper helper, BlockPos relativeAnchorPos) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(relativeAnchorPos);

        int lowX = SectionPos.blockToSectionCoord(anchor.getX() - WORKING_RADIUS);
        int highX = SectionPos.blockToSectionCoord(anchor.getX() + WORKING_RADIUS);
        int lowZ = SectionPos.blockToSectionCoord(anchor.getZ() - WORKING_RADIUS);
        int highZ = SectionPos.blockToSectionCoord(anchor.getZ() + WORKING_RADIUS);

        for (int x = lowX; x <= highX; x++) {
            for (int z = lowZ; z <= highZ; z++) {
                level.setChunkForced(x, z, true);
            }
        }
    }

    static FakePlayer owner(ServerLevel level) {
        return FakePlayerFactory.get(level, new GameProfile(OWNER_ID, OWNER_NAME));
    }

    /**
     * Installs the creative charge cell so charge never confounds a test.
     */
    static void giveInfiniteCharge(ChronoAnchorBlockEntity anchor) {
        anchor.getFuelHandler().set(0,
                net.neoforged.neoforge.transfer.item.ItemResource.of(
                        ModItems.CREATIVE_CHARGE_CELL.get()), 1);
    }

    static BlockState stateAt(GameTestHelper helper, BlockPos relative) {
        return helper.getBlockState(relative);
    }

    /**
     * Puts a stack straight into a container slot, past any face restrictions.
     */
    static void fillSlot(GameTestHelper helper, BlockPos relative, int slot,
                         net.minecraft.world.item.ItemStack stack) {
        // Through the level rather than helper.getBlockEntity, which wants an exact class and this
        // wants any container.
        if (helper.getLevel().getBlockEntity(helper.absolutePos(relative))
                instanceof net.minecraft.world.Container container) {
            container.setItem(slot, stack);
        } else {
            helper.fail("no container at " + relative);
        }
    }

    /** The first stack of one item, components and all, or null. */
    static net.minecraft.world.item.ItemStack findStack(
            net.neoforged.neoforge.transfer.ResourceHandler<
                    net.neoforged.neoforge.transfer.item.ItemResource> handler,
            net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < handler.size(); slot++) {
            net.neoforged.neoforge.transfer.item.ItemResource resource = handler.getResource(slot);
            if (!resource.isEmpty() && resource.getItem() == item) {
                return resource.toStack(Math.max(1, handler.getAmountAsInt(slot)));
            }
        }
        return null;
    }

    /** Total of one item across a handler, for asserting what ended up where. */
    static int countIn(net.neoforged.neoforge.transfer.ResourceHandler<
            net.neoforged.neoforge.transfer.item.ItemResource> handler, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            net.neoforged.neoforge.transfer.item.ItemResource resource = handler.getResource(slot);
            if (!resource.isEmpty() && resource.getItem() == item) {
                total += handler.getAmountAsInt(slot);
            }
        }
        return total;
    }
}
