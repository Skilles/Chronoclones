package com.skilles.chronoclones.recording;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class RecordingCodecs {

    private RecordingCodecs() {}

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

    /** InteractionHand ships a StreamCodec but no Codec. */
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

    public static final Codec<ActionPose> ACTION_POSE = RecordCodecBuilder.create(i -> i.group(
            Vec3.CODEC.fieldOf("pos").forGetter(ActionPose::localPos),
            Codec.FLOAT.fieldOf("yaw").forGetter(ActionPose::localYaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(ActionPose::pitch)
    ).apply(i, ActionPose::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPose> ACTION_POSE_STREAM =
            StreamCodec.composite(
                    Vec3.STREAM_CODEC.cast(), ActionPose::localPos,
                    ByteBufCodecs.FLOAT, ActionPose::localYaw,
                    ByteBufCodecs.FLOAT, ActionPose::pitch,
                    ActionPose::new);

    static final Codec<ChronoAction.PlaceContext> PLACE_CONTEXT = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("clicked").forGetter(ChronoAction.PlaceContext::localClicked),
            Vec3.CODEC.fieldOf("hit").forGetter(ChronoAction.PlaceContext::localHitOffset),
            Codec.BOOL.fieldOf("inside").forGetter(ChronoAction.PlaceContext::inside),
            HAND.fieldOf("hand").forGetter(ChronoAction.PlaceContext::hand),
            ACTION_POSE.fieldOf("pose").forGetter(ChronoAction.PlaceContext::pose)
    ).apply(i, ChronoAction.PlaceContext::new));

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.PlaceContext> PLACE_CONTEXT_STREAM =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), ChronoAction.PlaceContext::localClicked,
                    Vec3.STREAM_CODEC.cast(), ChronoAction.PlaceContext::localHitOffset,
                    ByteBufCodecs.BOOL, ChronoAction.PlaceContext::inside,
                    InteractionHand.STREAM_CODEC.cast(), ChronoAction.PlaceContext::hand,
                    ACTION_POSE_STREAM, ChronoAction.PlaceContext::pose,
                    ChronoAction.PlaceContext::new);

    static final MapCodec<ChronoAction.PlaceBlock> PLACE_BLOCK = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(ChronoAction.PlaceBlock::localPos),
            Direction.CODEC.fieldOf("face").forGetter(ChronoAction.PlaceBlock::localFace),
            RecordedItem.CODEC.fieldOf("item").forGetter(ChronoAction.PlaceBlock::itemTemplate),
            BlockState.CODEC.fieldOf("result").forGetter(ChronoAction.PlaceBlock::expectedResult),
            PLACE_CONTEXT.optionalFieldOf("click").forGetter(ChronoAction.PlaceBlock::context)
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
            RecordedItem.CODEC.fieldOf("item").forGetter(ChronoAction.UseOnBlock::itemTemplate),
            BuiltInRegistries.BLOCK.holderByNameCodec().optionalFieldOf("expected")
                    .forGetter(ChronoAction.UseOnBlock::expectedBlock)
    ).apply(i, ChronoAction.UseOnBlock::new));

    static final MapCodec<ChronoAction.UseItem> USE_ITEM = RecordCodecBuilder.mapCodec(i -> i.group(
            HAND.fieldOf("hand").forGetter(ChronoAction.UseItem::hand),
            RecordedItem.CODEC.fieldOf("item").forGetter(ChronoAction.UseItem::itemTemplate),
            Codec.INT.optionalFieldOf("hold", 0).forGetter(ChronoAction.UseItem::holdTicks),
            ACTION_POSE.optionalFieldOf("pose").forGetter(ChronoAction.UseItem::pose)
    ).apply(i, ChronoAction.UseItem::new));

    static final MapCodec<ChronoAction.InteractEntity> INTERACT_ENTITY = RecordCodecBuilder.mapCodec(i -> i.group(
            Vec3.CODEC.fieldOf("pos").forGetter(ChronoAction.InteractEntity::localPos),
            BuiltInRegistries.ENTITY_TYPE.holderByNameCodec().fieldOf("expected")
                    .forGetter(ChronoAction.InteractEntity::expectedType),
            HAND.fieldOf("hand").forGetter(ChronoAction.InteractEntity::hand),
            RecordedItem.CODEC.fieldOf("item").forGetter(ChronoAction.InteractEntity::itemTemplate)
    ).apply(i, ChronoAction.InteractEntity::new));

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

    static final MapCodec<MenuTarget.Block> MENU_BLOCK = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(MenuTarget.Block::localPos),
            BuiltInRegistries.BLOCK.holderByNameCodec().optionalFieldOf("block")
                    .forGetter(MenuTarget.Block::expectedBlock)
    ).apply(i, MenuTarget.Block::new));

    static final MapCodec<MenuTarget.Entity> MENU_ENTITY = RecordCodecBuilder.mapCodec(i -> i.group(
            Vec3.CODEC.fieldOf("pos").forGetter(MenuTarget.Entity::localPos),
            BuiltInRegistries.ENTITY_TYPE.holderByNameCodec().fieldOf("expected")
                    .forGetter(MenuTarget.Entity::expectedType)
    ).apply(i, MenuTarget.Entity::new));

    private static MapCodec<? extends MenuTarget> menuTargetCodecFor(MenuTarget.Kind kind) {
        return switch (kind) {
            case BLOCK -> MENU_BLOCK;
            case ENTITY -> MENU_ENTITY;
        };
    }

    public static final Codec<MenuTarget> MENU_TARGET =
            MenuTarget.Kind.CODEC.dispatch("at", MenuTarget::kind, RecordingCodecs::menuTargetCodecFor);

    static final StreamCodec<RegistryFriendlyByteBuf, MenuTarget.Block> MENU_BLOCK_STREAM =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC.cast(), MenuTarget.Block::localPos,
                    ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(Registries.BLOCK)),
                    MenuTarget.Block::expectedBlock,
                    MenuTarget.Block::new);

    static final StreamCodec<RegistryFriendlyByteBuf, MenuTarget.Entity> MENU_ENTITY_STREAM =
            StreamCodec.composite(
                    Vec3.STREAM_CODEC.cast(), MenuTarget.Entity::localPos,
                    ByteBufCodecs.holderRegistry(Registries.ENTITY_TYPE), MenuTarget.Entity::expectedType,
                    MenuTarget.Entity::new);

    @SuppressWarnings("unchecked")
    private static StreamCodec<RegistryFriendlyByteBuf, MenuTarget> menuTargetStreamFor(MenuTarget.Kind kind) {
        StreamCodec<RegistryFriendlyByteBuf, ? extends MenuTarget> codec = switch (kind) {
            case BLOCK -> MENU_BLOCK_STREAM;
            case ENTITY -> MENU_ENTITY_STREAM;
        };
        return (StreamCodec<RegistryFriendlyByteBuf, MenuTarget>) codec;
    }

    static final StreamCodec<RegistryFriendlyByteBuf, MenuTarget> MENU_TARGET_STREAM =
            ByteBufCodecs.<MenuTarget.Kind>idMapper(
                            id -> MenuTarget.Kind.values()[id], Enum::ordinal).<RegistryFriendlyByteBuf>cast()
                    .dispatch(MenuTarget::kind, RecordingCodecs::menuTargetStreamFor);

    static final MapCodec<SessionStep.Move> MOVE = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("from").forGetter(SessionStep.Move::from),
            Codec.INT.fieldOf("to").forGetter(SessionStep.Move::to),
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("item").forGetter(SessionStep.Move::item),
            SessionStep.Amount.CODEC.optionalFieldOf("amount", SessionStep.Amount.ALL)
                    .forGetter(SessionStep.Move::observed)
    ).apply(i, SessionStep.Move::new));

    static final MapCodec<SessionStep.RawClick> RAW_CLICK = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(SessionStep.RawClick::slot),
            Codec.INT.fieldOf("button").forGetter(SessionStep.RawClick::button),
            CONTAINER_INPUT.fieldOf("input").forGetter(SessionStep.RawClick::input)
    ).apply(i, SessionStep.RawClick::new));

    static final MapCodec<SessionStep.Button> BUTTON = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(SessionStep.Button::id)
    ).apply(i, SessionStep.Button::new));

    static final MapCodec<SessionStep.Trade> TRADE = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemStack.OPTIONAL_CODEC.fieldOf("cost_a").forGetter(SessionStep.Trade::costA),
            ItemStack.OPTIONAL_CODEC.fieldOf("cost_b").forGetter(SessionStep.Trade::costB),
            ItemStack.OPTIONAL_CODEC.fieldOf("result").forGetter(SessionStep.Trade::result)
    ).apply(i, SessionStep.Trade::new));

    static final MapCodec<SessionStep.Rename> RENAME = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("text").forGetter(SessionStep.Rename::text)
    ).apply(i, SessionStep.Rename::new));

    private static MapCodec<? extends SessionStep> stepCodecFor(SessionStep.Kind kind) {
        return switch (kind) {
            case MOVE -> MOVE;
            case RAW_CLICK -> RAW_CLICK;
            case BUTTON -> BUTTON;
            case TRADE -> TRADE;
            case RENAME -> RENAME;
        };
    }

    public static final Codec<SessionStep> SESSION_STEP =
            SessionStep.Kind.CODEC.dispatch("step", SessionStep::kind, RecordingCodecs::stepCodecFor);

    static final Codec<ChronoAction.UseContainer.CarrierSlot> CARRIER_SLOT = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("slot").forGetter(ChronoAction.UseContainer.CarrierSlot::menuSlot),
            ItemStack.CODEC.fieldOf("stack").forGetter(ChronoAction.UseContainer.CarrierSlot::stack)
    ).apply(i, ChronoAction.UseContainer.CarrierSlot::new));

    static final MapCodec<ChronoAction.UseContainer> USE_CONTAINER_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MENU_TARGET.optionalFieldOf("target").forGetter(a -> Optional.of(a.target())),
            BlockPos.CODEC.optionalFieldOf("pos").forGetter(a -> Optional.<BlockPos>empty()),
            Codec.INT.fieldOf("menu_size").forGetter(ChronoAction.UseContainer::menuSize),
            CARRIER_SLOT.listOf().fieldOf("carrier").forGetter(ChronoAction.UseContainer::carrier),
            SESSION_STEP.listOf().optionalFieldOf("steps", List.of())
                    .forGetter(ChronoAction.UseContainer::steps),
            RAW_CLICK.codec().listOf().optionalFieldOf("clicks")
                    .forGetter(a -> Optional.<List<SessionStep.RawClick>>empty())
    ).apply(i, (target, legacyPos, size, carrier, steps, legacySteps) -> new ChronoAction.UseContainer(
            target.orElseGet(() -> new MenuTarget.Block(legacyPos.orElse(BlockPos.ZERO))),
            size, carrier,
            steps.isEmpty() ? List.copyOf(legacySteps.orElse(List.of())) : steps)));

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
                    RecordedItem.STREAM_CODEC, ChronoAction.PlaceBlock::itemTemplate,
                    ByteBufCodecs.<BlockState>idMapper(
                            Block.BLOCK_STATE_REGISTRY::byId, Block.BLOCK_STATE_REGISTRY::getId).cast(),
                    ChronoAction.PlaceBlock::expectedResult,
                    ByteBufCodecs.optional(PLACE_CONTEXT_STREAM), ChronoAction.PlaceBlock::context,
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
                    RecordedItem.STREAM_CODEC, ChronoAction.UseOnBlock::itemTemplate,
                    ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(Registries.BLOCK)),
                    ChronoAction.UseOnBlock::expectedBlock,
                    ChronoAction.UseOnBlock::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.UseItem> USE_ITEM_STREAM =
            StreamCodec.composite(
                    InteractionHand.STREAM_CODEC.cast(), ChronoAction.UseItem::hand,
                    RecordedItem.STREAM_CODEC, ChronoAction.UseItem::itemTemplate,
                    ByteBufCodecs.VAR_INT, ChronoAction.UseItem::holdTicks,
                    ByteBufCodecs.optional(ACTION_POSE_STREAM), ChronoAction.UseItem::pose,
                    ChronoAction.UseItem::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.InteractEntity> INTERACT_ENTITY_STREAM =
            StreamCodec.composite(
                    Vec3.STREAM_CODEC.cast(), ChronoAction.InteractEntity::localPos,
                    ByteBufCodecs.holderRegistry(Registries.ENTITY_TYPE), ChronoAction.InteractEntity::expectedType,
                    InteractionHand.STREAM_CODEC.cast(), ChronoAction.InteractEntity::hand,
                    RecordedItem.STREAM_CODEC, ChronoAction.InteractEntity::itemTemplate,
                    ChronoAction.InteractEntity::new);

    static final StreamCodec<RegistryFriendlyByteBuf, SessionStep.Move> MOVE_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SessionStep.Move::from,
                    ByteBufCodecs.INT, SessionStep.Move::to,
                    ByteBufCodecs.holderRegistry(Registries.ITEM), SessionStep.Move::item,
                    ByteBufCodecs.idMapper(id -> SessionStep.Amount.values()[id], Enum::ordinal),
                    SessionStep.Move::observed,
                    SessionStep.Move::new);

    static final StreamCodec<RegistryFriendlyByteBuf, SessionStep.RawClick> RAW_CLICK_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SessionStep.RawClick::slot,
                    ByteBufCodecs.INT, SessionStep.RawClick::button,
                    ContainerInput.STREAM_CODEC.cast(), SessionStep.RawClick::input,
                    SessionStep.RawClick::new);

    static final StreamCodec<RegistryFriendlyByteBuf, SessionStep.Button> BUTTON_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SessionStep.Button::id,
                    SessionStep.Button::new);

    static final StreamCodec<RegistryFriendlyByteBuf, SessionStep.Trade> TRADE_STREAM =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC, SessionStep.Trade::costA,
                    ItemStack.OPTIONAL_STREAM_CODEC, SessionStep.Trade::costB,
                    ItemStack.OPTIONAL_STREAM_CODEC, SessionStep.Trade::result,
                    SessionStep.Trade::new);

    static final StreamCodec<RegistryFriendlyByteBuf, SessionStep.Rename> RENAME_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SessionStep.Rename::text,
                    SessionStep.Rename::new);

    @SuppressWarnings("unchecked")
    private static StreamCodec<RegistryFriendlyByteBuf, SessionStep> stepStreamFor(SessionStep.Kind kind) {
        StreamCodec<RegistryFriendlyByteBuf, ? extends SessionStep> codec = switch (kind) {
            case MOVE -> MOVE_STREAM;
            case RAW_CLICK -> RAW_CLICK_STREAM;
            case BUTTON -> BUTTON_STREAM;
            case TRADE -> TRADE_STREAM;
            case RENAME -> RENAME_STREAM;
        };
        return (StreamCodec<RegistryFriendlyByteBuf, SessionStep>) codec;
    }

    static final StreamCodec<RegistryFriendlyByteBuf, SessionStep> SESSION_STEP_STREAM =
            ByteBufCodecs.<SessionStep.Kind>idMapper(
                            id -> SessionStep.Kind.values()[id], Enum::ordinal).<RegistryFriendlyByteBuf>cast()
                    .dispatch(SessionStep::kind, RecordingCodecs::stepStreamFor);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.UseContainer.CarrierSlot> CARRIER_SLOT_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ChronoAction.UseContainer.CarrierSlot::menuSlot,
                    ItemStack.STREAM_CODEC, ChronoAction.UseContainer.CarrierSlot::stack,
                    ChronoAction.UseContainer.CarrierSlot::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction.UseContainer> USE_CONTAINER_STREAM =
            StreamCodec.composite(
                    MENU_TARGET_STREAM, ChronoAction.UseContainer::target,
                    ByteBufCodecs.VAR_INT, ChronoAction.UseContainer::menuSize,
                    CARRIER_SLOT_STREAM.apply(ByteBufCodecs.collection(ArrayList::new)),
                    ChronoAction.UseContainer::carrier,
                    SESSION_STEP_STREAM.apply(ByteBufCodecs.collection(ArrayList::new)),
                    ChronoAction.UseContainer::steps,
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
        return (StreamCodec<RegistryFriendlyByteBuf, ChronoAction>) codec;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ChronoAction> ACTION_STREAM =
            ACTION_TYPE_STREAM.dispatch(ChronoAction::type, RecordingCodecs::streamCodecFor);

    static final Codec<ActionSettings.SlotRule> SLOT_RULE = RecordCodecBuilder.create(i -> i.group(
            StringRepresentable.fromEnum(ActionSettings.SlotRule.Mode::values)
                    .optionalFieldOf("mode", ActionSettings.SlotRule.Mode.PREFER)
                    .forGetter(ActionSettings.SlotRule::mode),
            Codec.INT.optionalFieldOf("slot", ActionSettings.SlotRule.NONE)
                    .forGetter(ActionSettings.SlotRule::slot)
    ).apply(i, ActionSettings.SlotRule::new));

    static final Codec<ActionSettings.TargetRule> TARGET_RULE = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ENTITY_TYPE.holderByNameCodec().listOf()
                    .optionalFieldOf("filter", List.of())
                    .forGetter(ActionSettings.TargetRule::filter),
            Codec.DOUBLE.optionalFieldOf("radius", ActionSettings.TargetRule.DEFAULT_RADIUS)
                    .forGetter(ActionSettings.TargetRule::radius),
            Codec.BOOL.optionalFieldOf("sticky", false).forGetter(ActionSettings.TargetRule::sticky),
            StringRepresentable.fromEnum(ActionSettings.TargetRule.Completion::values)
                    .optionalFieldOf("completion", ActionSettings.TargetRule.Completion.ONCE)
                    .forGetter(ActionSettings.TargetRule::completion)
    ).apply(i, ActionSettings.TargetRule::new));

    static final Codec<ActionSettings.QuantityRule> QUANTITY_RULE = RecordCodecBuilder.create(i -> i.group(
            StringRepresentable.fromEnum(ActionSettings.QuantityRule.Mode::values)
                    .optionalFieldOf("mode", ActionSettings.QuantityRule.Mode.ANY)
                    .forGetter(ActionSettings.QuantityRule::mode),
            Codec.INT.optionalFieldOf("count", 0).forGetter(ActionSettings.QuantityRule::count)
    ).apply(i, ActionSettings.QuantityRule::new));

    static final Codec<ActionSettings.TransferRule> TRANSFER_RULE = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().listOf()
                    .optionalFieldOf("items", List.of())
                    .forGetter(ActionSettings.TransferRule::items),
            QUANTITY_RULE.optionalFieldOf("quantity", ActionSettings.QuantityRule.DEFAULT)
                    .forGetter(ActionSettings.TransferRule::quantity)
    ).apply(i, ActionSettings.TransferRule::new));

    static final Codec<ActionSettings.StepSettings> STEP_SETTINGS = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(ActionSettings.StepSettings::name),
            SLOT_RULE.optionalFieldOf("slot", ActionSettings.SlotRule.DEFAULT)
                    .forGetter(ActionSettings.StepSettings::slot),
            BuiltInRegistries.ITEM.holderByNameCodec().listOf().optionalFieldOf("items", List.of())
                    .forGetter(ActionSettings.StepSettings::items),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(ActionSettings.StepSettings::enabled),
            SessionStep.Amount.CODEC.optionalFieldOf("amount")
                    .forGetter(ActionSettings.StepSettings::amount)
    ).apply(i, ActionSettings.StepSettings::new));

    static final Codec<ActionSettings.ItemRule> ITEM_RULE =
            StringRepresentable.fromEnum(ActionSettings.ItemRule::values);

    static final StreamCodec<RegistryFriendlyByteBuf, ActionSettings.ItemRule> ITEM_RULE_STREAM =
            ByteBufCodecs.<ActionSettings.ItemRule>idMapper(
                    id -> ActionSettings.ItemRule.values()[id], Enum::ordinal).cast();

    public static final Codec<ActionSettings> ACTION_SETTINGS = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(ActionSettings::name),
            SLOT_RULE.optionalFieldOf("slot", ActionSettings.SlotRule.DEFAULT)
                    .forGetter(ActionSettings::slot),
            StringRepresentable.fromEnum(ActionSettings.ToolRule::values)
                    .optionalFieldOf("tool", ActionSettings.ToolRule.EXACT)
                    .forGetter(ActionSettings::tool),
            Codec.BOOL.optionalFieldOf("recorded_subject", true)
                    .forGetter(ActionSettings::recordedSubject),
            TARGET_RULE.optionalFieldOf("target", ActionSettings.TargetRule.DEFAULT)
                    .forGetter(ActionSettings::target),
            TRANSFER_RULE.optionalFieldOf("transfer", ActionSettings.TransferRule.DEFAULT)
                    .forGetter(ActionSettings::transfer),
            STEP_SETTINGS.listOf().optionalFieldOf("steps", List.of())
                    .forGetter(ActionSettings::steps),
            ITEM_RULE.optionalFieldOf("item_rule", ActionSettings.ItemRule.SAME_ITEM)
                    .forGetter(ActionSettings::item)
    ).apply(i, ActionSettings::new));

    static final StreamCodec<RegistryFriendlyByteBuf, ActionSettings.SlotRule> SLOT_RULE_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.idMapper(
                            id -> ActionSettings.SlotRule.Mode.values()[id], Enum::ordinal),
                    ActionSettings.SlotRule::mode,
                    ByteBufCodecs.INT, ActionSettings.SlotRule::slot,
                    ActionSettings.SlotRule::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ActionSettings.TargetRule> TARGET_RULE_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.holderRegistry(Registries.ENTITY_TYPE)
                            .apply(ByteBufCodecs.collection(ArrayList::new)),
                    ActionSettings.TargetRule::filter,
                    ByteBufCodecs.DOUBLE, ActionSettings.TargetRule::radius,
                    ByteBufCodecs.BOOL, ActionSettings.TargetRule::sticky,
                    ByteBufCodecs.idMapper(
                            id -> ActionSettings.TargetRule.Completion.values()[id], Enum::ordinal),
                    ActionSettings.TargetRule::completion,
                    ActionSettings.TargetRule::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ActionSettings.TransferRule> TRANSFER_RULE_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.holderRegistry(Registries.ITEM)
                            .apply(ByteBufCodecs.collection(ArrayList::new)),
                    ActionSettings.TransferRule::items,
                    StreamCodec.composite(
                            ByteBufCodecs.idMapper(
                                    id -> ActionSettings.QuantityRule.Mode.values()[id], Enum::ordinal),
                            ActionSettings.QuantityRule::mode,
                            ByteBufCodecs.VAR_INT, ActionSettings.QuantityRule::count,
                            ActionSettings.QuantityRule::new),
                    ActionSettings.TransferRule::quantity,
                    ActionSettings.TransferRule::new);

    static final StreamCodec<RegistryFriendlyByteBuf, ActionSettings.StepSettings> STEP_SETTINGS_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ActionSettings.StepSettings::name,
                    SLOT_RULE_STREAM, ActionSettings.StepSettings::slot,
                    ByteBufCodecs.holderRegistry(Registries.ITEM)
                            .apply(ByteBufCodecs.collection(ArrayList::new)),
                    ActionSettings.StepSettings::items,
                    ByteBufCodecs.BOOL, ActionSettings.StepSettings::enabled,
                    ByteBufCodecs.optional(ByteBufCodecs.idMapper(
                            id -> SessionStep.Amount.values()[id], Enum::ordinal)),
                    ActionSettings.StepSettings::amount,
                    ActionSettings.StepSettings::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ActionSettings> ACTION_SETTINGS_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ActionSettings::name,
                    SLOT_RULE_STREAM, ActionSettings::slot,
                    ByteBufCodecs.idMapper(
                            id -> ActionSettings.ToolRule.values()[id], Enum::ordinal),
                    ActionSettings::tool,
                    ByteBufCodecs.BOOL, ActionSettings::recordedSubject,
                    TARGET_RULE_STREAM, ActionSettings::target,
                    TRANSFER_RULE_STREAM, ActionSettings::transfer,
                    STEP_SETTINGS_STREAM.apply(ByteBufCodecs.collection(ArrayList::new)),
                    ActionSettings::steps,
                    ITEM_RULE_STREAM, ActionSettings::item,
                    ActionSettings::new);

    public static final Codec<TimedAction> TIMED_ACTION = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("tick").forGetter(TimedAction::tick),
            ACTION.fieldOf("action").forGetter(TimedAction::action),
            ACTION_SETTINGS.optionalFieldOf("settings").forGetter(t -> Optional.of(t.settings())),
            Codec.INT.optionalFieldOf("held_slot").forGetter(t -> Optional.<Integer>empty())
    ).apply(i, (tick, action, settings, legacySlot) -> new TimedAction(tick, action,
            settings.orElseGet(() -> ActionSettings.DEFAULT.withSlot(ActionSettings.SlotRule.prefer(
                    legacySlot.orElse(ActionSettings.SlotRule.NONE)))))));

    public static final StreamCodec<RegistryFriendlyByteBuf, TimedAction> TIMED_ACTION_STREAM =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TimedAction::tick,
                    ACTION_STREAM, TimedAction::action,
                    ACTION_SETTINGS_STREAM, TimedAction::settings,
                    TimedAction::new);

    public static final Codec<Recording> RECORDING = RecordCodecBuilder.<Recording>create(i -> i.group(
            MOTION_SAMPLE.listOf().fieldOf("motion").forGetter(Recording::motion),
            TIMED_ACTION.listOf().fieldOf("actions").forGetter(Recording::actions),
            Codec.INT.fieldOf("length").forGetter(Recording::lengthTicks),
            Codec.STRING.fieldOf("author_name").forGetter(Recording::authorName),
            UUIDUtil.CODEC.fieldOf("author_id").forGetter(Recording::authorId),
            Codec.BOOL.optionalFieldOf("creative", false).forGetter(Recording::creative)
    ).apply(i, Recording::new)).validate(RecordingCodecs::withinStructuralLimits);

    private static DataResult<Recording> withinStructuralLimits(Recording recording) {
        RecordingLimits.Refusal refusal = RecordingLimits.refuse(recording, null);
        return refusal == null
                ? DataResult.success(recording)
                : DataResult.error(() -> "recording refused: " + refusal.name());
    }

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
