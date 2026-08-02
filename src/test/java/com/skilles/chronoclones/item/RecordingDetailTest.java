package com.skilles.chronoclones.item;

import java.util.List;

import com.skilles.chronoclones.recording.ChronoAction;
import com.skilles.chronoclones.recording.MenuTarget;
import com.skilles.chronoclones.recording.SessionStep;
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

class RecordingDetailTest {

    @Test
    @DisplayName("a row names itself after its options")
    void rowsNameThemselvesAfterTheirOptions() {
        // ItemStack.EMPTY, not a real tool: unit tests run before item components are bound,
        // and the title only reads the block anyway.
        TimedAction breaking = new TimedAction(1, new ChronoAction.BreakBlock(
                new BlockPos(0, 0, -1),
                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.COBBLESTONE),
                net.minecraft.world.item.ItemStack.EMPTY));
        assertEquals("gui.chronoclones.editor.name.break",
                keyOf(RecordingDetail.title(breaking)),
                "a break recorded on cobblestone was not named after it");

        TimedAction widened = breaking.withSettings(
                com.skilles.chronoclones.recording.ActionSettings.DEFAULT
                        .withRecordedSubject(false));
        assertEquals("gui.chronoclones.editor.name.break.any",
                keyOf(RecordingDetail.title(widened)),
                "a break widened to any block kept the name of one");

        TimedAction named = breaking.withSettings(
                com.skilles.chronoclones.recording.ActionSettings.DEFAULT
                        .withName("Cobble farm").withRecordedSubject(false));
        assertEquals("Cobble farm", RecordingDetail.title(named).getString(),
                "changing an option overwrote a name the player typed");
    }

    private static String keyOf(Component component) {
        return component.getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                ? translatable.getKey()
                : component.getString();
    }

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
        assertTrue(text.contains("3, -1, 2"), text);
        assertTrue(text.contains("-2, 0, 5"), text);
    }

    @Test
    @DisplayName("a container session lists which button each click uses")
    void containerSessionIsExpanded() {
        List<Component> lines = RecordingDetail.describe(List.of(
                new TimedAction(20, new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(0, 0, -1)), 63,
                        List.of(),
                        List.of(
                                new SessionStep.RawClick(0, 1, ContainerInput.PICKUP),
                                new SessionStep.RawClick(31, 0, ContainerInput.QUICK_MOVE))))));

        String text = keysOf(lines);
        assertTrue(text.contains("tooltip.chronoclones.detail.container"), text);
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
                new MenuTarget.Block(BlockPos.ZERO), 63, List.of(),
                List.of(new SessionStep.RawClick(0, button, ContainerInput.PICKUP))));
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
