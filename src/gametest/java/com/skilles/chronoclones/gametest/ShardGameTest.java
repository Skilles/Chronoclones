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

final class ShardGameTest {

    private ShardGameTest() {}

    static void register() {
        ChronoclonesGameTests.add("shard_preserves_author", ShardGameTest::shardPreservesAuthor);
        ChronoclonesGameTests.add("shard_imprint_uses_new_owner", ShardGameTest::shardImprintUsesNewOwner);
        ChronoclonesGameTests.add("shard_is_not_consumed_by_imprint", ShardGameTest::shardIsNotConsumed);
    }

    private static final BlockPos ANCHOR = new BlockPos(8, 1, 8);

    private static final UUID SECOND_OWNER_ID = UUID.fromString("c0000000-0000-0000-0000-00000000000c");
    private static final String SECOND_OWNER_NAME = "SecondOwner";

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

    private static void shardImprintUsesNewOwner(GameTestHelper helper) {
        BlockPos target = AnchorTestFixture.targetOf(ANCHOR);
        helper.setBlock(target, Blocks.STONE);

        ServerLevel level = helper.getLevel();
        Recording authored = AnchorTestFixture.breakOneBlock(Blocks.STONE);

        BlockPos absoluteTarget = helper.absolutePos(target);
        AtomicReference<UUID> observed = new AtomicReference<>();
        BreakWatch watch = BreakWatch.at(absoluteTarget,
                attempt -> observed.compareAndSet(null, attempt.playerId()));

        ChronoAnchorBlockEntity anchor = AnchorTestFixture.placeAndImprint(helper, ANCHOR, authored);
        anchor.imprint(authored, AnchorTestFixture.fakePlayer(level,
                new GameProfile(SECOND_OWNER_ID, SECOND_OWNER_NAME)));
        AnchorTestFixture.giveInfiniteCharge(anchor);

        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    watch.close();

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
