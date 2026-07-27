package com.skilles.chronoclones.recording;

import java.util.ArrayList;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * All serialization for the recording model, in one place.
 */
public final class RecordingCodecs {

    private RecordingCodecs() {}

    // ------------------------------------------------------------------ motion

    public static final Codec<MotionSample> MOTION_SAMPLE = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("tick").forGetter(MotionSample::tick),
            Vec3.CODEC.fieldOf("pos").forGetter(MotionSample::localPos),
            Codec.FLOAT.fieldOf("yaw").forGetter(MotionSample::localYaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(MotionSample::pitch)
    ).apply(i, MotionSample::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MotionSample> MOTION_SAMPLE_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MotionSample::tick,
                    Vec3.STREAM_CODEC.cast(), MotionSample::localPos,
                    ByteBufCodecs.FLOAT, MotionSample::localYaw,
                    ByteBufCodecs.FLOAT, MotionSample::pitch,
                    MotionSample::new);

    // ------------------------------------------------------------------ actions

    /** InteractionHand ships a StreamCodec but no Codec, so build one by name. */
    static final Codec<InteractionHand> HAND = Codec.STRING.flatXmap(
            s -> switch (s) {
                case "main_hand" -> DataResult.success(InteractionHand.MAIN_HAND);
                case "off_hand" -> DataResult.success(InteractionHand.OFF_HAND);
                default -> DataResult.error(() -> "unknown hand: " + s);
            },
            h -> DataResult.success(h == InteractionHand.MAIN_HAND ? "main_hand" : "off_hand"));

    static final MapCodec<ChronoAction.BreakBlock> BREAK_BLOCK = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(ChronoAction.BreakBlock::localPos),
            BuiltInRegistries.BLOCK.holderByNameCodec().fieldOf("expected").forGetter(ChronoAction.BreakBlock::expectedBlock),
            ItemStack.OPTIONAL_CODEC.fieldOf("tool").forGetter(ChronoAction.BreakBlock::toolTemplate)
    ).apply(i, ChronoAction.BreakBlock::new));

    static final MapCodec<ChronoAction.PlaceBlock> PLACE_BLOCK = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(ChronoAction.PlaceBlock::localPos),
            Direction.CODEC.fieldOf("face").forGetter(ChronoAction.PlaceBlock::localFace),
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(ChronoAction.PlaceBlock::item),
            BlockState.CODEC.fieldOf("result").forGetter(ChronoAction.PlaceBlock::expectedResult)
    ).apply(i, ChronoAction.PlaceBlock::new));

    static final MapCodec<ChronoAction.AttackEntity> ATTACK_ENTITY = RecordCodecBuilder.mapCodec(i -> i.group(
            Vec3.CODEC.fieldOf("pos").forGetter(ChronoAction.AttackEntity::localPos),
            BuiltInRegistries.ENTITY_TYPE.holderByNameCodec().fieldOf("expected").forGetter(ChronoAction.AttackEntity::expectedType),
            ItemStack.OPTIONAL_CODEC.fieldOf("weapon").forGetter(ChronoAction.AttackEntity::weaponTemplate)
    ).apply(i, ChronoAction.AttackEntity::new));

    static final MapCodec<ChronoAction.UseOnBlock> USE_ON_BLOCK = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(ChronoAction.UseOnBlock::localPos),
            Direction.CODEC.fieldOf("face").forGetter(ChronoAction.UseOnBlock::localFace),
            Vec3.CODEC.fieldOf("hit").forGetter(ChronoAction.UseOnBlock::localHitOffset),
            Codec.BOOL.fieldOf("inside").forGetter(ChronoAction.UseOnBlock::inside),
            HAND.fieldOf("hand").forGetter(ChronoAction.UseOnBlock::hand),
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(ChronoAction.UseOnBlock::item)
    ).apply(i, ChronoAction.UseOnBlock::new));

    static final MapCodec<ChronoAction.UseItem> USE_ITEM = RecordCodecBuilder.mapCodec(i -> i.group(
            HAND.fieldOf("hand").forGetter(ChronoAction.UseItem::hand),
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(ChronoAction.UseItem::item)
    ).apply(i, ChronoAction.UseItem::new));

    static final MapCodec<ChronoAction.InteractEntity> INTERACT_ENTITY = RecordCodecBuilder.mapCodec(i -> i.group(
            Vec3.CODEC.fieldOf("pos").forGetter(ChronoAction.InteractEntity::localPos),
            BuiltInRegistries.ENTITY_TYPE.holderByNameCodec().fieldOf("expected")
                    .forGetter(ChronoAction.InteractEntity::expectedType),
            HAND.fieldOf("hand").forGetter(ChronoAction.InteractEntity::hand),
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(ChronoAction.InteractEntity::item)
    ).apply(i, ChronoAction.InteractEntity::new));

    /** ContainerInput ships a StreamCodec but no Codec, so build one by name. */
    static final Codec<ContainerInput> CONTAINER_INPUT =
            Codec.STRING.flatXmap(
                    name -> {
                        for (ContainerInput input : ContainerInput.values()) {
                            if (input.name().equalsIgnoreCase(name)) {
                                return DataResult.success(input);
                            }
                        }
                        return DataResult.error(() -> "unknown container input: " + name);
                    },
                    input -> DataResult.success(input.name().toLowerCase(java.util.Locale.ROOT)));

    static final Codec<ChronoAction.UseContainer.Click> CLICK = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(ChronoAction.UseContainer.Click::slot),
            Codec.INT.fieldOf("button").forGetter(ChronoAction.UseContainer.Click::button),
            CONTAINER_INPUT.fieldOf("input").forGetter(ChronoAction.UseContainer.Click::input)
    ).apply(i, ChronoAction.UseContainer.Click::new));

    static final Codec<ChronoAction.UseContainer.CarrierSlot> CARRIER_SLOT = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(ChronoAction.UseContainer.CarrierSlot::menuSlot),
            // The whole stack: an item id cannot answer isSameItemSameComponents.
            ItemStack.CODEC.fieldOf("stack").forGetter(ChronoAction.UseContainer.CarrierSlot::stack)
    ).apply(i, ChronoAction.UseContainer.CarrierSlot::new));

    static final MapCodec<ChronoAction.UseContainer> USE_CONTAINER_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(ChronoAction.UseContainer::localPos),
            Codec.INT.fieldOf("menu_size").forGetter(ChronoAction.UseContainer::menuSize),
            CARRIER_SLOT.listOf().fieldOf("carrier").forGetter(ChronoAction.UseContainer::carrier),
            CLICK.listOf().fieldOf("clicks").forGetter(ChronoAction.UseContainer::clicks)
    ).apply(i, ChronoAction.UseContainer::new));

    private static MapCodec<? extends ChronoAction> mapCodecFor(ChronoActionType type) {
        return switch (type) {
            case BREAK_BLOCK -> BREAK_BLOCK;
            case PLACE_BLOCK -> PLACE_BLOCK;
            case USE_CONTAINER -> USE_CONTAINER_CODEC;
            case ATTACK_ENTITY -> ATTACK_ENTITY;
            case USE_ON_BLOCK -> USE_ON_BLOCK;
            case USE_ITEM -> USE_ITEM;
            case INTERACT_ENTITY -> INTERACT_ENTITY;
        };
    }

    public static final Codec<ChronoAction> ACTION =
            ChronoActionType.CODEC.dispatch("type", ChronoAction::type, RecordingCodecs::mapCodecFor);

    // ------------------------------------------------------------------ action streams

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.BreakBlock> BREAK_BLOCK_STREAM =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), ChronoAction.BreakBlock::localPos,
                    ByteBufCodecs.holderRegistry(Registries.BLOCK), ChronoAction.BreakBlock::expectedBlock,
                    ItemStack.OPTIONAL_STREAM_CODEC, ChronoAction.BreakBlock::toolTemplate,
                    ChronoAction.BreakBlock::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.PlaceBlock> PLACE_BLOCK_STREAM =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), ChronoAction.PlaceBlock::localPos,
                    Direction.STREAM_CODEC.cast(), ChronoAction.PlaceBlock::localFace,
                    ByteBufCodecs.holderRegistry(Registries.ITEM), ChronoAction.PlaceBlock::item,
                    ByteBufCodecs.<BlockState>idMapper(
                            Block.BLOCK_STATE_REGISTRY::byId, Block.BLOCK_STATE_REGISTRY::getId).cast(),
                    ChronoAction.PlaceBlock::expectedResult,
                    ChronoAction.PlaceBlock::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.AttackEntity> ATTACK_ENTITY_STREAM =
            StreamCodec.composite(
                    Vec3.STREAM_CODEC.cast(), ChronoAction.AttackEntity::localPos,
                    ByteBufCodecs.holderRegistry(Registries.ENTITY_TYPE), ChronoAction.AttackEntity::expectedType,
                    ItemStack.OPTIONAL_STREAM_CODEC, ChronoAction.AttackEntity::weaponTemplate,
                    ChronoAction.AttackEntity::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.UseOnBlock> USE_ON_BLOCK_STREAM =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), ChronoAction.UseOnBlock::localPos,
                    Direction.STREAM_CODEC.cast(), ChronoAction.UseOnBlock::localFace,
                    Vec3.STREAM_CODEC.cast(), ChronoAction.UseOnBlock::localHitOffset,
                    ByteBufCodecs.BOOL.cast(), ChronoAction.UseOnBlock::inside,
                    InteractionHand.STREAM_CODEC.cast(), ChronoAction.UseOnBlock::hand,
                    ByteBufCodecs.holderRegistry(Registries.ITEM), ChronoAction.UseOnBlock::item,
                    ChronoAction.UseOnBlock::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.UseItem> USE_ITEM_STREAM =
            StreamCodec.composite(
                    InteractionHand.STREAM_CODEC.cast(), ChronoAction.UseItem::hand,
                    ByteBufCodecs.holderRegistry(Registries.ITEM), ChronoAction.UseItem::item,
                    ChronoAction.UseItem::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.InteractEntity> INTERACT_ENTITY_STREAM =
            StreamCodec.composite(
                    Vec3.STREAM_CODEC.cast(), ChronoAction.InteractEntity::localPos,
                    ByteBufCodecs.holderRegistry(Registries.ENTITY_TYPE), ChronoAction.InteractEntity::expectedType,
                    InteractionHand.STREAM_CODEC.cast(), ChronoAction.InteractEntity::hand,
                    ByteBufCodecs.holderRegistry(Registries.ITEM), ChronoAction.InteractEntity::item,
                    ChronoAction.InteractEntity::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.UseContainer.Click> CLICK_STREAM =
            StreamCodec.composite(
                    // Not VAR_INT: clicking outside a menu is slot -999, which unsigned varints
                    // encode in five bytes.
                    ByteBufCodecs.INT, ChronoAction.UseContainer.Click::slot,
                    ByteBufCodecs.INT, ChronoAction.UseContainer.Click::button,
                    ContainerInput.STREAM_CODEC.cast(), ChronoAction.UseContainer.Click::input,
                    ChronoAction.UseContainer.Click::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.UseContainer.CarrierSlot> CARRIER_SLOT_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ChronoAction.UseContainer.CarrierSlot::menuSlot,
                    ItemStack.STREAM_CODEC, ChronoAction.UseContainer.CarrierSlot::stack,
                    ChronoAction.UseContainer.CarrierSlot::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.UseContainer> USE_CONTAINER_STREAM =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), ChronoAction.UseContainer::localPos,
                    ByteBufCodecs.VAR_INT, ChronoAction.UseContainer::menuSize,
                    CARRIER_SLOT_STREAM.apply(ByteBufCodecs.collection(ArrayList::new)),
                    ChronoAction.UseContainer::carrier,
                    CLICK_STREAM.apply(ByteBufCodecs.collection(ArrayList::new)),
                    ChronoAction.UseContainer::clicks,
                    ChronoAction.UseContainer::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoActionType> ACTION_TYPE_STREAM =
            ByteBufCodecs.<ChronoActionType>idMapper(
                    i -> ChronoActionType.values()[i], ChronoActionType::ordinal).cast();

    @SuppressWarnings("unchecked")
    private static StreamCodec<RegistryFriendlyByteBuf, ChronoAction> streamCodecFor(ChronoActionType type) {
        StreamCodec<RegistryFriendlyByteBuf, ? extends ChronoAction> codec = switch (type) {
            case BREAK_BLOCK -> BREAK_BLOCK_STREAM;
            case PLACE_BLOCK -> PLACE_BLOCK_STREAM;
            case USE_CONTAINER -> USE_CONTAINER_STREAM;
            case ATTACK_ENTITY -> ATTACK_ENTITY_STREAM;
            case USE_ON_BLOCK -> USE_ON_BLOCK_STREAM;
            case USE_ITEM -> USE_ITEM_STREAM;
            case INTERACT_ENTITY -> INTERACT_ENTITY_STREAM;
        };
        // Safe: dispatch only ever hands us the codec matching the value's own type().
        return (StreamCodec<RegistryFriendlyByteBuf, ChronoAction>) codec;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction> ACTION_STREAM =
            ACTION_TYPE_STREAM.dispatch(ChronoAction::type, RecordingCodecs::streamCodecFor);

    // ------------------------------------------------------------------ timed action

    public static final Codec<TimedAction> TIMED_ACTION = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("tick").forGetter(TimedAction::tick),
            ACTION.fieldOf("action").forGetter(TimedAction::action)
    ).apply(i, TimedAction::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TimedAction> TIMED_ACTION_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TimedAction::tick,
                    ACTION_STREAM, TimedAction::action,
                    TimedAction::new);

    // ------------------------------------------------------------------ recording

    public static final Codec<Recording> RECORDING = RecordCodecBuilder.create(i -> i.group(
            MOTION_SAMPLE.listOf().fieldOf("motion").forGetter(Recording::motion),
            TIMED_ACTION.listOf().fieldOf("actions").forGetter(Recording::actions),
            Codec.INT.fieldOf("length").forGetter(Recording::lengthTicks),
            Codec.STRING.fieldOf("author_name").forGetter(Recording::authorName),
            UUIDUtil.CODEC.fieldOf("author_id").forGetter(Recording::authorId),
            Codec.BOOL.optionalFieldOf("creative", false).forGetter(Recording::creative)
    ).apply(i, Recording::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Recording> RECORDING_STREAM =
            StreamCodec.composite(
                    MOTION_SAMPLE_STREAM.apply(ByteBufCodecs.collection(ArrayList::new)), Recording::motion,
                    TIMED_ACTION_STREAM.apply(ByteBufCodecs.collection(ArrayList::new)), Recording::actions,
                    ByteBufCodecs.VAR_INT, Recording::lengthTicks,
                    ByteBufCodecs.STRING_UTF8, Recording::authorName,
                    UUIDUtil.STREAM_CODEC.cast(), Recording::authorId,
                    ByteBufCodecs.BOOL, Recording::creative,
                    Recording::new);
}
