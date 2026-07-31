package com.skilles.chronoclones.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.skilles.chronoclones.item.RecordingTooltips;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecordingTooltipsTest {

    private static Recording routine() {
        return new Recording(
                List.of(new MotionSample(0, new Vec3(0, 0, 0), 0f, 0f),
                        new MotionSample(2, new Vec3(3.0, 0, 4.0), 0f, 0f)),
                List.of(
                        new TimedAction(1, breakAt(1, 0, 0)),
                        new TimedAction(2, breakAt(2, 0, 0)),
                        new TimedAction(3, breakAt(3, 0, 0)),
                        new TimedAction(4, new ChronoAction.PlaceBlock(
                                new BlockPos(0, 1, 0), Direction.UP,
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.OAK_PLANKS),
                                Blocks.OAK_PLANKS.defaultBlockState())),
                        new TimedAction(5, new ChronoAction.UseItem(
                                InteractionHand.MAIN_HAND,
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.BONE_MEAL)))),
                240, "Skilles", UUID.randomUUID());
    }

    private static ChronoAction breakAt(int x, int y, int z) {
        return new ChronoAction.BreakBlock(
                new BlockPos(x, y, z),
                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE),
                ItemStack.EMPTY);
    }

    private static List<String> keys(List<Component> lines) {
        List<String> found = new ArrayList<>();
        for (Component line : lines) {
            collectKeys(line, found);
        }
        return found;
    }

    private static void collectKeys(Component component, List<String> into) {
        if (component.getContents() instanceof TranslatableContents t) {
            into.add(t.getKey());
        }
        component.getSiblings().forEach(s -> collectKeys(s, into));
    }

    private static Object argOf(List<Component> lines, String key, int index) {
        for (Component line : lines) {
            Object found = argOf(line, key, index);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Object argOf(Component component, String key, int index) {
        if (component.getContents() instanceof TranslatableContents t && t.getKey().equals(key)) {
            return t.getArgs().length > index ? t.getArgs()[index] : null;
        }
        for (Component sibling : component.getSiblings()) {
            Object found = argOf(sibling, key, index);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    @DisplayName("tooltip names the author, so a shared routine is never anonymous")
    void showsAuthor() {
        List<Component> lines = RecordingTooltips.describe(routine());

        assertTrue(keys(lines).contains("tooltip.chronoclones.recording.author"));

        Object author = argOf(lines, "tooltip.chronoclones.recording.author", 0);
        assertEquals("Skilles", ((Component) author).getString());
    }

    @Test
    @DisplayName("action counts are broken down by type rather than given as a bare total")
    void countsAreBrokenDownByType() {
        Map<ChronoActionType, Integer> counts = routine().actionCounts();

        assertEquals(3, counts.get(ChronoActionType.BREAK_BLOCK));
        assertEquals(1, counts.get(ChronoActionType.PLACE_BLOCK));
        assertEquals(1, counts.get(ChronoActionType.USE_ITEM));
        assertNull(counts.get(ChronoActionType.ATTACK_ENTITY));
    }

    @Test
    @DisplayName("every action type present gets its own line, and absent types are omitted")
    void everyPresentTypeHasItsOwnLine() {
        List<String> found = keys(RecordingTooltips.describe(routine()));

        assertEquals(1, found.stream()
                .filter(k -> k.equals("tooltip.chronoclones.recording.action.break_block")).count());
        assertEquals(1, found.stream()
                .filter(k -> k.equals("tooltip.chronoclones.recording.action.place_block")).count());
        assertEquals(1, found.stream()
                .filter(k -> k.equals("tooltip.chronoclones.recording.action.use_item")).count());

        assertTrue(found.stream()
                .noneMatch(k -> k.equals("tooltip.chronoclones.recording.action.attack_entity")));
    }

    @Test
    @DisplayName("the break count shown is the real count, not a placeholder")
    void breakCountIsAccurate() {
        List<Component> lines = RecordingTooltips.describe(routine());
        assertEquals(3, argOf(lines, "tooltip.chronoclones.recording.action.break_block", 0));
    }

    @Test
    @DisplayName("tooltip reports reach, so a routine that digs far from the anchor cannot hide it")
    void showsReach() {
        assertEquals(5.0, routine().reach(), 1.0e-9);

        List<Component> lines = RecordingTooltips.describe(routine());
        assertTrue(keys(lines).contains("tooltip.chronoclones.recording.reach"));
        assertEquals("5.0", argOf(lines, "tooltip.chronoclones.recording.reach", 0));
    }

    @Test
    @DisplayName("a movement-only recording says so instead of showing an empty breakdown")
    void movementOnlyIsLabelled() {
        Recording r = new Recording(
                List.of(new MotionSample(0, Vec3.ZERO, 0f, 0f)),
                List.of(), 40, "Skilles", UUID.randomUUID());

        assertTrue(keys(RecordingTooltips.describe(r))
                .contains("tooltip.chronoclones.recording.no_actions"));
    }

    @Test
    @DisplayName("length is reported in seconds alongside the action total")
    void showsLength() {
        List<Component> lines = RecordingTooltips.describe(routine());
        assertEquals(12, argOf(lines, "tooltip.chronoclones.recording.length", 0));
        assertEquals(5, argOf(lines, "tooltip.chronoclones.recording.length", 1));
    }
}
