package com.skilles.chronoclones.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A Recording must survive both persistence (Codec -> NBT, for the block entity) and sync
 * (StreamCodec -> buffer, for item components) with equality.
 */
class RecordingCodecTest {

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void captureRegistries() {
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    /**
     * Exercises every action variant, including the optional field on UseItem in both states.
     */
    private static Recording sample() {
        return new Recording(
                List.of(
                        new MotionSample(0, new Vec3(0.5, 0.0, 0.5), 0.0f, 0.0f),
                        new MotionSample(2, new Vec3(1.25, 0.5, -3.75), 90.0f, -12.5f),
                        new MotionSample(4, new Vec3(-7.0, 2.0, 4.0), -179.0f, 45.0f)),
                List.of(
                        new TimedAction(1, new ChronoAction.BreakBlock(
                                new BlockPos(3, -1, 2),
                                BuiltInRegistries.BLOCK.wrapAsHolder(Blocks.STONE),
                                ItemStack.EMPTY)),
                        new TimedAction(3, new ChronoAction.PlaceBlock(
                                new BlockPos(-2, 0, 5),
                                Direction.UP,
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.OAK_PLANKS),
                                Blocks.OAK_PLANKS.defaultBlockState())),
                        new TimedAction(5, new ChronoAction.AttackEntity(
                                new Vec3(1.5, 0.0, 1.5),
                                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.ZOMBIE),
                                ItemStack.EMPTY)),
                        new TimedAction(7, new ChronoAction.UseOnBlock(
                                new BlockPos(0, 0, 1),
                                Direction.UP,
                                new Vec3(0.25, 0.5, -0.125),
                                false,
                                InteractionHand.MAIN_HAND,
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.BONE_MEAL))),
                        new TimedAction(9, new ChronoAction.UseItem(
                                InteractionHand.OFF_HAND,
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.ENDER_PEARL))),
                        new TimedAction(11, new ChronoAction.InteractEntity(
                                new Vec3(-1.5, 0.0, 2.5),
                                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.COW),
                                InteractionHand.MAIN_HAND,
                                BuiltInRegistries.ITEM.wrapAsHolder(Items.BUCKET))),
                        new TimedAction(13, new ChronoAction.UseContainer(
                                new MenuTarget.Block(new BlockPos(2, 0, -3)), 63,
                                // No carrier entries, for the same reason the templates above are
                                // empty: one holds a whole ItemStack, and a carrier slot may not be
                                // empty. Its codec is the strict ItemStack.CODEC so that
                                // "this session needs nothing here" cannot be encoded. A populated
                                // carrier therefore round trips in PrecisionGameTest, with a server.
                                List.of(),
                                List.of(
                                        new SessionStep.Move(4, 54,
                                                BuiltInRegistries.ITEM.wrapAsHolder(Items.COAL),
                                                SessionStep.Amount.HALF),
                                        new SessionStep.Move(9, SessionStep.Move.ELSEWHERE,
                                                BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_INGOT),
                                                SessionStep.Amount.ALL),
                                        new SessionStep.RawClick(4, 1, ContainerInput.PICKUP),
                                        new SessionStep.RawClick(54, 0, ContainerInput.PICKUP),
                                        new SessionStep.RawClick(-999, 0, ContainerInput.THROW)))),
                        // A session on an entity, with the three steps that arrive as packets. The
                        // trade's stacks are empty for the same reason the carrier is.
                        new TimedAction(15, new ChronoAction.UseContainer(
                                new MenuTarget.Entity(new Vec3(-1.5, 0.0, 2.5),
                                        BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.VILLAGER)),
                                39,
                                List.of(),
                                List.of(
                                        new SessionStep.Button(2),
                                        new SessionStep.Trade(ItemStack.EMPTY, ItemStack.EMPTY,
                                                ItemStack.EMPTY),
                                        new SessionStep.Rename("Tunneler"))))),
                200,
                "Skilles",
                UUID.fromString("11111111-2222-3333-4444-555555555555"));
    }

    private static <T> T unwrap(DataResult<T> result) {
        return result.getOrThrow(msg -> new AssertionError("codec failed: " + msg));
    }

    @Test
    @DisplayName("Recording round trips through its Codec via NBT")
    void roundTripsThroughNbt() {
        Recording original = sample();

        Tag encoded = unwrap(RecordingCodecs.RECORDING.encodeStart(
                registries.createSerializationContext(NbtOps.INSTANCE), original));
        Recording decoded = unwrap(RecordingCodecs.RECORDING.parse(
                registries.createSerializationContext(NbtOps.INSTANCE), encoded));

        assertRecordingsEqual(original, decoded);
    }

    @Test
    @DisplayName("Recording round trips through its Codec via JSON")
    void roundTripsThroughJson() {
        Recording original = sample();

        var encoded = unwrap(RecordingCodecs.RECORDING.encodeStart(
                registries.createSerializationContext(JsonOps.INSTANCE), original));
        Recording decoded = unwrap(RecordingCodecs.RECORDING.parse(
                registries.createSerializationContext(JsonOps.INSTANCE), encoded));

        assertRecordingsEqual(original, decoded);
    }

    @Test
    @DisplayName("Recording round trips through its StreamCodec via a buffer")
    void roundTripsThroughStreamCodec() {
        Recording original = sample();

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        RecordingCodecs.RECORDING_STREAM.encode(buf, original);
        Recording decoded = RecordingCodecs.RECORDING_STREAM.decode(buf);

        assertEquals(0, buf.readableBytes(), "stream codec left bytes unread");
        assertRecordingsEqual(original, decoded);
    }

    @Test
    @DisplayName("action type is serialised by name, so reordering the enum cannot reinterpret data")
    void actionTypeIsSerialisedByName() {
        for (ChronoActionType type : ChronoActionType.values()) {
            String name = unwrap(ChronoActionType.CODEC.encodeStart(JsonOps.INSTANCE, type)).getAsString();
            assertEquals(type.getSerializedName(), name);
            assertEquals(type, unwrap(ChronoActionType.CODEC.parse(
                    JsonOps.INSTANCE, new com.google.gson.JsonPrimitive(name))));
        }
    }

    @Test
    @DisplayName("each action variant round trips individually")
    void eachVariantRoundTrips() {
        for (TimedAction timed : sample().actions()) {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            RecordingCodecs.ACTION_STREAM.encode(buf, timed.action());
            ChronoAction decoded = RecordingCodecs.ACTION_STREAM.decode(buf);
            assertEquals(timed.action(), decoded, "variant " + timed.action().type());
            assertEquals(0, buf.readableBytes(), "variant " + timed.action().type() + " left bytes");
        }
    }

    @Test
    @DisplayName("a session saved before steps existed reads its clicks as raw steps")
    void legacyClicksBecomeRawSteps() {
        // What an anchor imprinted before this release holds on disk: clicks, and no steps.
        com.google.gson.JsonObject click = new com.google.gson.JsonObject();
        click.addProperty("slot", 30);
        click.addProperty("button", 1);
        click.addProperty("input", "pickup");

        com.google.gson.JsonArray clicks = new com.google.gson.JsonArray();
        clicks.add(click);

        com.google.gson.JsonObject session = new com.google.gson.JsonObject();
        session.addProperty("type", "use_container");
        session.add("pos", unwrap(BlockPos.CODEC.encodeStart(JsonOps.INSTANCE, new BlockPos(1, 2, 3))));
        session.addProperty("menu_size", 63);
        session.add("carrier", new com.google.gson.JsonArray());
        session.add("clicks", clicks);

        ChronoAction decoded = unwrap(RecordingCodecs.ACTION.parse(
                registries.createSerializationContext(JsonOps.INSTANCE), session));

        ChronoAction.UseContainer use = (ChronoAction.UseContainer) decoded;
        assertEquals(List.of(new SessionStep.RawClick(30, 1, ContainerInput.PICKUP)), use.steps());
        assertEquals(new MenuTarget.Block(new BlockPos(1, 2, 3)), use.target());
    }

    @Test
    @DisplayName("an entity target and a block target both round trip, and neither writes the other")
    void bothTargetKindsRoundTrip() {
        List<MenuTarget> targets = sample().actions().stream()
                .map(TimedAction::action)
                .filter(a -> a instanceof ChronoAction.UseContainer)
                .map(a -> ((ChronoAction.UseContainer) a).target())
                .toList();

        assertEquals(List.of(MenuTarget.Kind.BLOCK, MenuTarget.Kind.ENTITY),
                targets.stream().map(MenuTarget::kind).toList(),
                "the sample no longer covers both kinds of target");

        for (MenuTarget target : targets) {
            var encoded = unwrap(RecordingCodecs.MENU_TARGET.encodeStart(
                    registries.createSerializationContext(JsonOps.INSTANCE), target));
            assertEquals(target, unwrap(RecordingCodecs.MENU_TARGET.parse(
                    registries.createSerializationContext(JsonOps.INSTANCE), encoded)));
        }
    }

    @Test
    @DisplayName("steps are written, and the clicks they replaced are not")
    void stepsReplaceClicksOnTheWayOut() {
        ChronoAction.UseContainer session = (ChronoAction.UseContainer) sample().actions().stream()
                .map(TimedAction::action)
                .filter(a -> a instanceof ChronoAction.UseContainer)
                .findFirst()
                .orElseThrow();

        var encoded = unwrap(RecordingCodecs.ACTION.encodeStart(
                registries.createSerializationContext(JsonOps.INSTANCE), session)).getAsJsonObject();

        assertTrue(encoded.has("steps"), "steps: " + encoded);
        assertTrue(!encoded.has("clicks"), "the legacy field was written back: " + encoded);
    }

    @Test
    @DisplayName("step kinds are serialised by name, so reordering the enum cannot reinterpret data")
    void stepKindsAreSerialisedByName() {
        for (SessionStep.Kind kind : SessionStep.Kind.values()) {
            String name = unwrap(SessionStep.Kind.CODEC.encodeStart(JsonOps.INSTANCE, kind)).getAsString();
            assertEquals(kind.getSerializedName(), name);
        }
        for (SessionStep.Amount amount : SessionStep.Amount.values()) {
            String name = unwrap(SessionStep.Amount.CODEC.encodeStart(JsonOps.INSTANCE, amount)).getAsString();
            assertEquals(amount.getSerializedName(), name);
        }
    }

    @Test
    @DisplayName("tooltip data is computed correctly for shard inspection")
    void tooltipDataIsCorrect() {
        Recording r = sample();

        assertEquals(10, r.lengthSeconds());
        assertEquals(1, r.actionCounts().get(ChronoActionType.BREAK_BLOCK));
        assertEquals(1, r.actionCounts().get(ChronoActionType.PLACE_BLOCK));
        assertEquals(1, r.actionCounts().get(ChronoActionType.ATTACK_ENTITY));
        assertEquals(1, r.actionCounts().get(ChronoActionType.USE_ON_BLOCK));
        assertEquals(1, r.actionCounts().get(ChronoActionType.USE_ITEM));
        assertEquals(1, r.actionCounts().get(ChronoActionType.INTERACT_ENTITY));
        assertEquals(2, r.actionCounts().get(ChronoActionType.USE_CONTAINER));

        // Furthest horizontal point is the motion sample at (-7, _, 4) -> sqrt(49+16) ~= 8.06
        assertEquals(Math.sqrt(65.0), r.reach(), 1.0e-9);
    }

    @Test
    @DisplayName("Recording is defensively copied, so the source lists cannot mutate it later")
    void listsAreDefensivelyCopied() {
        List<MotionSample> mutableMotion = new java.util.ArrayList<>();
        mutableMotion.add(new MotionSample(0, Vec3.ZERO, 0f, 0f));

        Recording r = new Recording(mutableMotion, List.of(), 20, "Skilles", UUID.randomUUID());
        mutableMotion.add(new MotionSample(2, new Vec3(9, 9, 9), 0f, 0f));

        assertEquals(1, r.motion().size());
    }

    @Test
    @DisplayName("charge costs: break 10, place 5, attack 20")
    void chargeCostsMatchSpec() {
        assertEquals(10, ChronoActionType.BREAK_BLOCK.chargeCost());
        assertEquals(5, ChronoActionType.PLACE_BLOCK.chargeCost());
        assertEquals(20, ChronoActionType.ATTACK_ENTITY.chargeCost());
    }

    private static void assertRecordingsEqual(Recording expected, Recording actual) {
        assertEquals(expected.lengthTicks(), actual.lengthTicks());
        assertEquals(expected.authorName(), actual.authorName());
        assertEquals(expected.authorId(), actual.authorId());
        assertEquals(expected.motion(), actual.motion());
        assertEquals(expected.actions().size(), actual.actions().size());

        for (int i = 0; i < expected.actions().size(); i++) {
            TimedAction e = expected.actions().get(i);
            TimedAction a = actual.actions().get(i);
            assertEquals(e.tick(), a.tick(), "tick at index " + i);
            assertChronoActionsEqual(e.action(), a.action(), i);
        }
    }

    /** ItemStack does not implement equals, so stacks are compared field-wise. */
    private static void assertChronoActionsEqual(ChronoAction expected, ChronoAction actual, int index) {
        assertEquals(expected.type(), actual.type(), "type at index " + index);

        switch (expected) {
            case ChronoAction.BreakBlock e -> {
                ChronoAction.BreakBlock a = (ChronoAction.BreakBlock) actual;
                assertEquals(e.localPos(), a.localPos());
                assertEquals(e.expectedBlock().value(), a.expectedBlock().value());
                assertTrue(ItemStack.matches(e.toolTemplate(), a.toolTemplate()), "tool at " + index);
            }
            case ChronoAction.PlaceBlock e -> {
                ChronoAction.PlaceBlock a = (ChronoAction.PlaceBlock) actual;
                assertEquals(e.localPos(), a.localPos());
                assertEquals(e.localFace(), a.localFace());
                assertEquals(e.item().value(), a.item().value());
                assertEquals(e.expectedResult(), a.expectedResult());
            }
            case ChronoAction.AttackEntity e -> {
                ChronoAction.AttackEntity a = (ChronoAction.AttackEntity) actual;
                assertEquals(e.localPos(), a.localPos());
                assertEquals(e.expectedType().value(), a.expectedType().value());
                assertTrue(ItemStack.matches(e.weaponTemplate(), a.weaponTemplate()), "weapon at " + index);
            }
            case ChronoAction.UseOnBlock e -> {
                ChronoAction.UseOnBlock a = (ChronoAction.UseOnBlock) actual;
                assertEquals(e.localPos(), a.localPos());
                assertEquals(e.localFace(), a.localFace());
                assertEquals(e.localHitOffset(), a.localHitOffset());
                assertEquals(e.inside(), a.inside());
                assertEquals(e.hand(), a.hand());
                assertEquals(e.item().value(), a.item().value());
            }
            case ChronoAction.UseItem e -> {
                ChronoAction.UseItem a = (ChronoAction.UseItem) actual;
                assertEquals(e.hand(), a.hand());
                assertEquals(e.item().value(), a.item().value());
            }
            case ChronoAction.InteractEntity e -> {
                ChronoAction.InteractEntity a = (ChronoAction.InteractEntity) actual;
                assertEquals(e.localPos(), a.localPos());
                assertEquals(e.expectedType().value(), a.expectedType().value());
                assertEquals(e.hand(), a.hand());
                assertEquals(e.item().value(), a.item().value());
            }
            case ChronoAction.UseContainer e -> {
                ChronoAction.UseContainer a = (ChronoAction.UseContainer) actual;
                assertEquals(e.target(), a.target());
                assertEquals(e.menuSize(), a.menuSize());
                assertEquals(e.steps(), a.steps());
                assertEquals(e.carrier().size(), a.carrier().size());
                for (int i = 0; i < e.carrier().size(); i++) {
                    assertEquals(e.carrier().get(i).menuSlot(), a.carrier().get(i).menuSlot());
                    assertTrue(ItemStack.matches(e.carrier().get(i).stack(), a.carrier().get(i).stack()),
                            "carrier stack at " + index + "/" + i);
                }
            }
        }
    }
}
