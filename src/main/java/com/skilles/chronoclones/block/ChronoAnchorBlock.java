package com.skilles.chronoclones.block;

import com.skilles.chronoclones.item.ChronoRecorderItem;
import com.skilles.chronoclones.recording.Recording;
import com.skilles.chronoclones.registry.ModBlockEntities;
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
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    /**
     * The anchor's facing is captured at placement and is the basis the recording is rebased onto
     *. Rotating the anchor rotates the routine.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChronoAnchorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CHRONO_ANCHOR.get(),
                (lvl, pos, st, be) -> be.serverTick());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor) {
            player.openMenu(anchor, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Right-clicking with a recorder in HOLDING state imprints it.
     *
     * <p>Ownership transfers to the imprinting player, not the recording's author — see
     * {@link ChronoAnchorBlockEntity} for why that distinction is security-critical.
     *
     * <p>Anything that is not a recorder must return {@link InteractionResult#TRY_WITH_EMPTY_HAND},
     * NOT {@code PASS}. Only the former makes the game fall through to {@link #useWithoutItem}, so
     * returning {@code PASS} here silently stops the anchor's GUI from ever opening.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        // Only a recorder actually carrying a recording is an imprint attempt. An idle recorder in
        // hand should still open the GUI rather than refusing, so it defers as well. The component
        // is network-synchronised, so this decision matches on both sides.
        Recording recording = ChronoRecorderItem.recordingOf(stack);
        if (!stack.is(ModItems.CHRONO_RECORDER.get()) || recording == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof ChronoAnchorBlockEntity anchor)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        anchor.imprint(recording, serverPlayer);
        ChronoRecorderItem.clear(stack);

        serverPlayer.sendOverlayMessage(Component.translatable(
                "message.chronoclones.anchor.imprinted",
                Component.literal(recording.authorName()).withStyle(ChatFormatting.WHITE),
                recording.actions().size()).withStyle(ChatFormatting.AQUA));
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8f, 1.2f);

        return InteractionResult.SUCCESS;
    }
}
