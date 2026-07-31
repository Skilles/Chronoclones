package com.skilles.chronoclones.block;

import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.menu.ChronoAnchorMenu;
import com.skilles.chronoclones.item.ChronoShardItem;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.recording.RecordingLimits;
import com.skilles.chronoclones.registry.ModBlockEntities;
import com.skilles.chronoclones.network.AnchorAuthority;
import com.skilles.chronoclones.registry.ModItems;
import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class ChronoAnchorBlock extends BaseEntityBlock {

    public static final MapCodec<ChronoAnchorBlock> CODEC = simpleCodec(ChronoAnchorBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ChronoAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    @NonNull
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state, @Nullable LivingEntity placer,
                            @NonNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor) {
            anchor.adopt(player);
        }
    }

    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new ChronoAnchorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NonNull BlockState state, @NonNull BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CHRONO_ANCHOR.get(),
                (lvl, pos, st, be) -> be.serverTick());
    }

    @Override
    protected @NonNull InteractionResult useWithoutItem(@NonNull BlockState state, Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor) {
            player.openMenu(anchor, buffer -> {
                buffer.writeBlockPos(pos);
                ChronoAnchorMenu.writeTimeline(buffer, anchor.getRecording());
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected @NonNull InteractionResult useItemOn(ItemStack stack, @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                                   @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hit) {
        boolean isRecorder = stack.is(ModItems.CHRONO_RECORDER.get());
        boolean isShard = stack.is(ModItems.CHRONO_SHARD.get());

        if (!isRecorder && !isShard) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        Recording carried = isRecorder ? ChronoRecorderItem.recordingOf(stack) : ChronoShardItem.recordingOf(stack);
        boolean blankShard = isShard && carried == null;

        if (carried == null && !blankShard) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        if (blankShard) {
            return inscribeShard(anchor, stack, serverPlayer, level, pos);
        }

        RecordingLimits.Refusal refusal = RecordingLimits.refuse(carried, level.registryAccess());
        if (refusal != null) {
            serverPlayer.sendOverlayMessage(Component.translatable(refusal.translationKey())
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        anchor.imprint(carried, serverPlayer);
        if (isRecorder) {
            ChronoRecorderItem.clear(stack);
        }

        serverPlayer.sendOverlayMessage(Component.translatable(
                "message.chronoclones.anchor.imprinted",
                Component.literal(carried.authorName()).withStyle(ChatFormatting.WHITE),
                carried.actions().size()).withStyle(ChatFormatting.AQUA));
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8f, 1.2f);

        return InteractionResult.SUCCESS;
    }

    public static InteractionResult extractRecording(ChronoAnchorBlockEntity anchor, ItemStack recorders,
                                                     ServerPlayer player, Level level, BlockPos pos) {
        if (anchor.getRecording() == null) {
            player.sendOverlayMessage(Component
                    .translatable("message.chronoclones.anchor.nothing_to_extract")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        if (!AnchorAuthority.mayRetune(anchor.getOwnerId(), player.getUUID())) {
            player.sendOverlayMessage(Component
                    .translatable("message.chronoclones.anchor.not_yours")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        Recording taken = anchor.extractRecording();
        ItemStack holding = ChronoRecorderItem.holding(recorders.copyWithCount(1), taken);
        recorders.shrink(1);

        if (!player.getInventory().add(holding)) {
            player.drop(holding, false);
        }

        player.sendOverlayMessage(Component.translatable(
                "message.chronoclones.anchor.extracted",
                Component.literal(taken.authorName()).withStyle(ChatFormatting.WHITE),
                taken.actions().size()).withStyle(ChatFormatting.AQUA));
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);

        return InteractionResult.SUCCESS;
    }

    private static InteractionResult inscribeShard(ChronoAnchorBlockEntity anchor, ItemStack blanks,
                                                   ServerPlayer player, Level level, BlockPos pos) {
        Recording recording = anchor.getRecording();
        if (recording == null) {
            player.sendOverlayMessage(Component
                    .translatable("message.chronoclones.shard.nothing_to_copy")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        RecordingLimits.Refusal refusal = RecordingLimits.refuse(recording, level.registryAccess());
        if (refusal != null) {
            player.sendOverlayMessage(Component.translatable(refusal.translationKey())
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        ItemStack inscribed = ChronoShardItem.inscribe(blanks, recording);
        blanks.shrink(1);

        if (!player.getInventory().add(inscribed)) {
            player.drop(inscribed, false);
        }

        player.sendOverlayMessage(Component.translatable(
                "message.chronoclones.shard.inscribed",
                Component.literal(recording.authorName()).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.AQUA));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8f, 1.4f);

        return InteractionResult.SUCCESS;
    }
}
