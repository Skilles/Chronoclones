package com.skilles.chronoclones.gametest;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.skilles.chronoclones.block.ChronoAnchorBlockEntity;
import com.skilles.chronoclones.item.ChronoShardItem;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.registry.ModItems;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Transferring a routine, and the attribution property that matters most about it.
 */
final class ShardGameTest {

    private ShardGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("shard_preserves_author", ShardGameTest::shardPreservesAuthor);
        ChronoclonesGameTests.add("shard_imprint_uses_new_owner", ShardGameTest::shardImprintUsesNewOwner);
        ChronoclonesGameTests.add("shard_is_not_consumed_by_imprint", ShardGameTest::shardIsNotConsumed);
    }

    private static final BlockPos ANCHOR = new BlockPos(2, 1, 2);

    /** A second player's UUID, distinct from both the author and the original owner. */
    private static final UUID SECOND_OWNER_ID = UUID.fromString("c0000000-0000-0000-0000-00000000000c");
    private static final String SECOND_OWNER_NAME = "SecondOwner";

    /** Inscribing copies the routine without relabelling who wrote it. */
    private static void shardPreservesAuthor(GameTestHelper helper) {
        Recording original = AnchorTestFixture.breakOneBlock(Blocks.STONE);
        ItemStack shard = ChronoShardItem.inscribe(new ItemStack(ModItems.CHRONO_SHARD.get()), original);

        Recording carried = ChronoShardItem.recordingOf(shard);
        if (carried == null) {
            helper.fail("inscribing produced a shard with no recording");
            return;
        }
        if (!AnchorTestFixture.AUTHOR_ID.equals(carried.authorId())) {
            helper.fail("shard rewrote the author to " + carried.authorId());
        }
        if (!ChronoShardItem.isInscribed(shard)) {
            helper.fail("inscribed shard does not report itself as inscribed");
        }
        if (shard.getMaxStackSize() != 1) {
            helper.fail("inscribed shards must not stack: each carries distinct data");
        }
        helper.succeed();
    }

    /** A routine authored by one player and imprinted by another acts as the imprinter. */
    private static void shardImprintUsesNewOwner(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ServerLevel level = helper.getLevel();
        Recording authored = AnchorTestFixture.breakOneBlock(Blocks.STONE);

        // Scoped to this test's target: game tests share a world and run concurrently.
        BlockPos absoluteTarget = helper.absolutePos(target);
        AtomicReference<UUID> observed = new AtomicReference<>();
        Object listener = new Object() {
            @net.neoforged.bus.api.SubscribeEvent(priority = EventPriority.HIGHEST)
            public void onBreak(BreakBlockEvent event) {
                if (event.getPos().equals(absoluteTarget)) {
                    observed.compareAndSet(null, event.getPlayer().getUUID());
                }
            }
        };
        NeoForge.EVENT_BUS.register(listener);

        // Imprint as a completely different player from both the author and the usual fixture owner.
        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR, authored);
        anchor.imprint(authored, FakePlayerFactory.get(level,
                new GameProfile(SECOND_OWNER_ID, SECOND_OWNER_NAME)));
        AnchorTestFixture.giveInfiniteCharge(anchor);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    NeoForge.EVENT_BUS.unregister(listener);

                    UUID actor = observed.get();
                    if (actor == null) {
                        helper.fail("the transferred routine never executed");
                    }
                    if (AnchorTestFixture.AUTHOR_ID.equals(actor)) {
                        helper.fail("a transferred routine acted as its author - this is exactly the "
                                + "griefing vector this guards");
                    }
                    if (!SECOND_OWNER_ID.equals(actor)) {
                        helper.fail("expected the imprinting player " + SECOND_OWNER_ID
                                + " to be the actor, got " + actor);
                    }
                })
                .thenSucceed();
    }

    /** One shard can seed many anchors, so imprinting must not consume it. */
    private static void shardIsNotConsumed(GameTestHelper helper) {
        Recording recording = AnchorTestFixture.breakOneBlock(Blocks.STONE);
        ItemStack shard = ChronoShardItem.inscribe(new ItemStack(ModItems.CHRONO_SHARD.get()), recording);

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR, recording);

        if (anchor.getRecording() == null) {
            helper.fail("anchor did not take the routine");
        }
        if (shard.isEmpty() || !ChronoShardItem.isInscribed(shard)) {
            helper.fail("the shard lost its recording: it must survive imprinting");
        }
        helper.succeed();
    }
}
