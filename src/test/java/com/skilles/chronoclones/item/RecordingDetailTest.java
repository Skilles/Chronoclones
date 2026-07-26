package com.skilles.chronoclones.item;

import java.util.List;

import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.TimedAction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The detailed listing behind shift.
 *
 * <p>Its job is to let someone decide whether to trust a stranger's shard, so the assertions here are
 * about what a reader can actually learn: which block, which place, and — for a container — which
 * button, since take-half and take-all are the difference between splitting a stack and emptying it.
 */
class RecordingDetailTest {

    /** Translation keys rather than rendered text: the test should not depend on the lang file. */
    private static String keysOf(List<Component> lines) {
        StringBuilder all = new StringBuilder();
        for (Component line : lines) {
            all.append(line.toString()).append('\n');
        }
        return all.toString();
    }

    @Test
    @DisplayName("every action produces a line naming what it touches and where")
    void everyActionIsListed() {
        List<Component> lines = RecordingDetail.describe(List.of(
                new TimedAction(20, new ChronoAction.BreakBlock(
                        new BlockPos(3, -1, 2),
                        BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE),
                        net.minecraft.world.item.ItemStack.EMPTY)),
                new TimedAction(40, new ChronoAction.PlaceBlock(
                        new BlockPos(-2, 0, 5),
                        net.minecraft.core.Direction.UP,
                        BuiltInRegistries.ITEM.wrapAsHolder(Items.OAK_PLANKS),
                        Blocks.OAK_PLANKS.defaultBlockState()))));

        assertEquals(2, lines.size());
        String text = keysOf(lines);
        assertTrue(text.contains("tooltip.chronoclones.detail.break"), text);
        assertTrue(text.contains("tooltip.chronoclones.detail.place"), text);
        // The position is the point — "14 breaks" without one tells a reader nothing.
        assertTrue(text.contains("3, -1, 2"), text);
        assertTrue(text.contains("-2, 0, 5"), text);
    }

    @Test
    @DisplayName("a container session lists which button each click uses")
    void containerSessionIsExpanded() {
        // No carrier entries: naming one means naming its stack, and a carrier line renders through
        // getHoverName, which reads an item's default components — unbound without a loaded datapack.
        // That the "needs" line appears, and names the right thing, is asserted in PrecisionGameTest.
        List<Component> lines = RecordingDetail.describe(List.of(
                new TimedAction(20, new ChronoAction.UseContainer(
                        new BlockPos(0, 0, -1), 63,
                        List.of(),
                        List.of(
                                new ChronoAction.UseContainer.Click(0, 1, ContainerInput.PICKUP),
                                new ChronoAction.UseContainer.Click(31, 0, ContainerInput.QUICK_MOVE))))));

        String text = keysOf(lines);
        assertTrue(text.contains("tooltip.chronoclones.detail.container"), text);
        // Take-half versus take-all is the distinction the whole click model exists to preserve, so
        // it had better be the distinction a reader can see.
        assertTrue(text.contains("tooltip.chronoclones.detail.click.pickup_half"), text);
        assertTrue(text.contains("tooltip.chronoclones.detail.click.quick_move"), text);
    }

    @Test
    @DisplayName("a right-click and a left-click on the same slot read differently")
    void buttonsAreDistinguished() {
        String half = keysOf(RecordingDetail.describe(List.of(session(1))));
        String all = keysOf(RecordingDetail.describe(List.of(session(0))));

        assertTrue(half.contains("pickup_half"), half);
        assertTrue(all.contains("pickup_all"), all);
    }

    private static TimedAction session(int button) {
        return new TimedAction(20, new ChronoAction.UseContainer(
                BlockPos.ZERO, 63, List.of(),
                List.of(new ChronoAction.UseContainer.Click(0, button, ContainerInput.PICKUP))));
    }

    @Test
    @DisplayName("a long routine is truncated rather than filling the screen")
    void longRoutineIsTruncated() {
        List<TimedAction> many = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            many.add(new TimedAction(i, new ChronoAction.BreakBlock(
                    new BlockPos(i, 0, 0),
                    BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE),
                    net.minecraft.world.item.ItemStack.EMPTY)));
        }

        List<Component> lines = RecordingDetail.describe(many);
        assertTrue(lines.size() < 40, "tooltip ran to " + lines.size() + " lines");
        assertTrue(keysOf(lines).contains("tooltip.chronoclones.detail.more"),
                "truncation must say how much was hidden, or the reader thinks they saw it all");
    }
}
