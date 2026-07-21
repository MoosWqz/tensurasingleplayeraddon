package com.mooswqz.moostensuraaddon.block;

import com.mooswqz.moostensuraaddon.ritual
        .GreatCrystalAltarInteractionRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties
        .BlockStateProperties;
import net.minecraft.world.level.block.state.properties
        .DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GreatCrystalAltarBlock extends Block {

    public static final EnumProperty<DoubleBlockHalf> HALF =
            BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape LOWER_SHAPE =
            Shapes.or(
                    Block.box(
                            6.0D,
                            0.0D,
                            6.0D,
                            10.0D,
                            1.0D,
                            10.0D
                    ),
                    Block.box(
                            7.0D,
                            1.0D,
                            7.0D,
                            9.0D,
                            16.0D,
                            9.0D
                    )
            );

    private static final VoxelShape UPPER_SHAPE =
            Block.box(
                    0.0D,
                    0.0D,
                    0.0D,
                    16.0D,
                    16.0D,
                    16.0D
            );

    public GreatCrystalAltarBlock(
            Properties properties
    ) {
        super(properties);

        this.registerDefaultState(
                this.stateDefinition
                        .any()
                        .setValue(
                                HALF,
                                DoubleBlockHalf.LOWER
                        )
        );
    }

    @Override
    public BlockState getStateForPlacement(
            BlockPlaceContext context
    ) {
        BlockPos position =
                context.getClickedPos();

        Level level =
                context.getLevel();

        if (position.getY()
                >= level.getMaxBuildHeight() - 1) {
            return null;
        }

        if (!level.getBlockState(
                position.above()
        ).canBeReplaced(context)) {
            return null;
        }

        return this.defaultBlockState()
                .setValue(
                        HALF,
                        DoubleBlockHalf.LOWER
                );
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos position,
            BlockState state,
            LivingEntity placer,
            ItemStack stack
    ) {
        level.setBlock(
                position.above(),
                state.setValue(
                        HALF,
                        DoubleBlockHalf.UPPER
                ),
                Block.UPDATE_ALL
        );
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            BlockHitResult hitResult
    ) {
        return interactWithAltar(
                state,
                level,
                position,
                player
        );
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (player.isSecondaryUseActive()) {
            return ItemInteractionResult
                    .PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        InteractionResult result =
                interactWithAltar(
                        state,
                        level,
                        position,
                        player
                );

        if (result.consumesAction()) {
            return ItemInteractionResult.sidedSuccess(
                    level.isClientSide()
            );
        }

        return ItemInteractionResult
                .PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private InteractionResult interactWithAltar(
            BlockState state,
            Level level,
            BlockPos position,
            Player player
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockPos altarPosition =
                getLowerHalfPosition(
                        state,
                        position
                );

        BlockState lowerState =
                level.getBlockState(
                        altarPosition
                );

        if (!lowerState.is(this)
                || lowerState.getValue(HALF)
                != DoubleBlockHalf.LOWER) {

            return InteractionResult.CONSUME;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            GreatCrystalAltarInteractionRouter.interact(
                    serverPlayer,
                    altarPosition
            );
        }

        return InteractionResult.CONSUME;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos position,
            BlockPos neighborPosition
    ) {
        DoubleBlockHalf half =
                state.getValue(HALF);

        if (half == DoubleBlockHalf.LOWER
                && direction == Direction.UP) {

            if (!neighborState.is(this)
                    || neighborState.getValue(HALF)
                    != DoubleBlockHalf.UPPER) {

                return Blocks.AIR
                        .defaultBlockState();
            }
        }

        if (half == DoubleBlockHalf.UPPER
                && direction == Direction.DOWN) {

            if (!neighborState.is(this)
                    || neighborState.getValue(HALF)
                    != DoubleBlockHalf.LOWER) {

                return Blocks.AIR
                        .defaultBlockState();
            }
        }

        return super.updateShape(
                state,
                direction,
                neighborState,
                level,
                position,
                neighborPosition
        );
    }

    @Override
    protected boolean canSurvive(
            BlockState state,
            LevelReader level,
            BlockPos position
    ) {
        DoubleBlockHalf half =
                state.getValue(HALF);

        if (half == DoubleBlockHalf.UPPER) {
            BlockState belowState =
                    level.getBlockState(
                            position.below()
                    );

            return belowState.is(this)
                    && belowState.getValue(HALF)
                    == DoubleBlockHalf.LOWER;
        }

        return position.getY()
                < level.getMaxBuildHeight() - 1;
    }

    @Override
    public BlockState playerWillDestroy(
            Level level,
            BlockPos position,
            BlockState state,
            Player player
    ) {
        if (!level.isClientSide()) {
            DoubleBlockHalf half =
                    state.getValue(HALF);

            BlockPos otherPosition =
                    half == DoubleBlockHalf.LOWER
                            ? position.above()
                            : position.below();

            BlockState otherState =
                    level.getBlockState(
                            otherPosition
                    );

            if (otherState.is(this)
                    && otherState.getValue(HALF)
                    != half) {

                if (half == DoubleBlockHalf.UPPER
                        && !player.isCreative()) {

                    Block.dropResources(
                            otherState,
                            level,
                            otherPosition
                    );
                }

                level.setBlock(
                        otherPosition,
                        Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL
                                | Block.UPDATE_SUPPRESS_DROPS
                );
            }
        }

        return super.playerWillDestroy(
                level,
                position,
                state,
                player
        );
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos position,
            CollisionContext context
    ) {
        return state.getValue(HALF)
                == DoubleBlockHalf.LOWER
                ? LOWER_SHAPE
                : UPPER_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos position,
            CollisionContext context
    ) {
        return state.getValue(HALF)
                == DoubleBlockHalf.LOWER
                ? LOWER_SHAPE
                : UPPER_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(HALF);
    }

    private static BlockPos getLowerHalfPosition(
            BlockState state,
            BlockPos position
    ) {
        return state.getValue(HALF)
                == DoubleBlockHalf.UPPER
                ? position.below()
                : position;
    }
}