package com.skilles.chronoclones.item;

import java.util.List;
import java.util.Optional;

import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionIconsTest {

    @Test
    @DisplayName("a block action shows the block it is about")
    void blockActionsShowTheirBlock() {
        assertEquals(Optional.of(Items.STONE), item(new ChronoAction.BreakBlock(
                BlockPos.ZERO, BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE), ItemStack.EMPTY)));

        assertEquals(Optional.of(Items.OAK_PLANKS), item(new ChronoAction.PlaceBlock(
                BlockPos.ZERO, Direction.UP, BuiltInRegistries.ITEM.wrapAsHolder(Items.OAK_PLANKS),
                Blocks.OAK_PLANKS.defaultBlockState())));
    }

    @Test
    @DisplayName("a session shows the block it was opened on")
    void sessionsShowWhatTheyOpened() {
        assertEquals(Optional.of(Items.CHEST), item(new ChronoAction.UseContainer(
                new MenuTarget.Block(BlockPos.ZERO,
                        Optional.of(BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.CHEST))),
                63, List.of(), List.of())));
    }

    @Test
    @DisplayName("a session recorded before the block was kept has nothing to show")
    void oldSessionsShowNothing() {
        assertTrue(ActionIcons.of(new ChronoAction.UseContainer(
                new MenuTarget.Block(BlockPos.ZERO), 63, List.of(), List.of())).isEmpty());
    }

    @Test
    @DisplayName("using an empty hand has nothing to show")
    void anEmptyHandShowsNothing() {
        assertTrue(ActionIcons.of(new ChronoAction.UseItem(
                InteractionHand.MAIN_HAND, BuiltInRegistries.ITEM.wrapAsHolder(Items.AIR)))
                .isEmpty(), "air is not a picture of anything");
    }

    private static Optional<net.minecraft.world.item.Item> item(ChronoAction action) {
        return ActionIcons.of(action).map(net.minecraft.core.Holder::value);
    }
}
