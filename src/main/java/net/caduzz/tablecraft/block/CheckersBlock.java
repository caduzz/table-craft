package net.caduzz.tablecraft.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;

import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector3f;

import net.caduzz.tablecraft.block.entity.CheckersBlockEntity;
import net.caduzz.tablecraft.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class CheckersBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * Altura em voxels (16 = 1 bloco). 2 = 1/8 de bloco, mais baixo que laje (8).
     * Ajuste aqui se quiser ainda mais fino.
     */
    public static final int HEIGHT_VOXELS = 2;

    /** Y do topo da madeira / base do tabuleiro em espaço de bloco [0,1]. */
    public static final float BOARD_SURFACE_Y = HEIGHT_VOXELS / 16f;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, HEIGHT_VOXELS, 16);

    public static final MapCodec<CheckersBlock> CODEC = simpleCodec(CheckersBlock::new);

    public CheckersBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /**
     * Rotação Y (graus) igual à variante do blockstate — alinha modelo e BER.
     * Convenção vanilla: sul 0°, leste 90°, norte 180°, oeste 270°.
     */
    public static float facingModelYRotationDegrees(Direction facing) {
        return switch (facing) {
            case SOUTH -> 0f;
            case EAST -> 90f;
            case NORTH -> 180f;
            case WEST -> 270f;
            default -> 0f;
        };
    }

    public static void applyBoardRenderRotation(PoseStack poseStack, Direction facing) {
        float deg = facingModelYRotationDegrees(facing);
        poseStack.translate(0.5f, 0f, 0.5f);
        poseStack.mulPose(new Quaternionf().rotationY(deg * Mth.DEG_TO_RAD));
        poseStack.translate(-0.5f, 0f, -0.5f);
    }

    /**
     * Topo do bloco em coords alinhadas ao mundo [0,1]² → frações lógicas (col em x, row em z)
     * antes da rotação visual (inversa da rotação do render).
     */
    public static void axisAlignedHitToLogicalBoardFrac(double ax, double az, Direction facing, Vector2d outLogicalColRow) {
        float deg = facingModelYRotationDegrees(facing);
        Quaternionf q = new Quaternionf().rotationY(deg * Mth.DEG_TO_RAD);
        Vector3f p = new Vector3f((float) (ax - 0.5), 0f, (float) (az - 0.5));
        q.conjugate().transform(p);
        outLogicalColRow.set(p.x + 0.5, p.z + 0.5);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CheckersBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(type, ModBlockEntities.CHECKERS.get(), CheckersBlockEntity::tick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CheckersBlockEntity checkers) {
            if (player.isShiftKeyDown()) {
                checkers.cyclePlayerTimePreset(player);
                return ItemInteractionResult.CONSUME;
            }
            checkers.handlePlayerClick(player, hitResult);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        return interact(level, pos, player, hitResult);
    }

    private static InteractionResult interact(Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CheckersBlockEntity checkers) {
            if (player.isShiftKeyDown()) {
                checkers.cyclePlayerTimePreset(player);
                return InteractionResult.CONSUME;
            }
            checkers.handlePlayerClick(player, hitResult);
        }
        return InteractionResult.CONSUME;
    }
}
