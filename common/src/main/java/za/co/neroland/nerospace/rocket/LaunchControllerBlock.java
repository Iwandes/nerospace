package za.co.neroland.nerospace.rocket;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerospace.menu.MenuOpener;
import za.co.neroland.nerospace.registry.ModBlocks;

/**
 * The Launch Controller: a console that builds and tiers-up a rocket launch pad in front of it. It is a
 * solid multiblock — 3 wide × 2 tall — with this (the core) at the bottom-centre holding the block entity
 * + GUI, and five {@link LaunchControllerPartBlock} filler cubes forming the rest. The sleek console face
 * (animated screen, projector arms, glow) is drawn on top by {@code LaunchControllerRenderer}.
 */
public class LaunchControllerBlock extends BaseEntityBlock {

    public static final MapCodec<LaunchControllerBlock> CODEC = BlockCodecs.simple(LaunchControllerBlock::new);
    /** The horizontal direction the pad is built toward; also the structure's width axis (clockwise). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Re-entrancy guard so breaking one cell doesn't recurse while it tears the rest down. */
    private static boolean breaking = false;

    public LaunchControllerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    protected MapCodec<LaunchControllerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // --- multiblock geometry -------------------------------------------------------------------

    /** All six cells of the structure (core + 5 parts), given the core position and facing. */
    public static List<BlockPos> allCells(BlockPos core, Direction facing) {
        Direction right = facing.getClockWise();
        List<BlockPos> cells = new ArrayList<>(6);
        for (int w = -1; w <= 1; w++) {
            BlockPos base = core.relative(right, w);
            cells.add(base);
            cells.add(base.above());
        }
        return cells;
    }

    /** The five filler-part cells (everything except the core). */
    private static List<BlockPos> partCells(BlockPos core, Direction facing) {
        List<BlockPos> parts = new ArrayList<>(allCells(core, facing));
        parts.remove(core);
        return parts;
    }

    /** True when all five part cells are free to build into. */
    public static boolean canAssemble(Level level, BlockPos core, Direction facing) {
        for (BlockPos p : partCells(core, facing)) {
            if (!level.getBlockState(p).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** Places the five filler parts around an already-placed core. */
    public static void assemble(Level level, BlockPos core, Direction facing) {
        BlockState part = ModBlocks.LAUNCH_CONTROLLER_PART.get().defaultBlockState();
        for (BlockPos p : partCells(core, facing)) {
            if (level.getBlockState(p).canBeReplaced()) {
                level.setBlock(p, part, Block.UPDATE_ALL);
            }
        }
    }

    /** Finds the core block from any cell of the structure (the clicked/broken position). */
    @Nullable
    public static BlockPos findCore(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(ModBlocks.LAUNCH_CONTROLLER.get())) {
            return pos;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    if (level.getBlockState(p).is(ModBlocks.LAUNCH_CONTROLLER.get())) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /** Breaks the whole structure when any one cell is destroyed, dropping a single controller. */
    public static void breakStructure(Level level, BlockPos anyPos, @Nullable Player player) {
        if (breaking || level.isClientSide()) {
            return;
        }
        BlockPos core = findCore(level, anyPos);
        if (core == null) {
            return;
        }
        BlockState coreState = level.getBlockState(core);
        if (!coreState.is(ModBlocks.LAUNCH_CONTROLLER.get())) {
            return;
        }
        Direction facing = coreState.getValue(FACING);
        breaking = true;
        try {
            boolean creative = player != null && player.getAbilities().instabuild;
            if (!creative) {
                Block.popResource(level, core, new ItemStack(ModBlocks.LAUNCH_CONTROLLER.get()));
            }
            for (BlockPos cell : allCells(core, facing)) {
                if (cell.equals(anyPos)) {
                    continue; // the broken cell is cleared by vanilla after playerWillDestroy
                }
                BlockState s = level.getBlockState(cell);
                if (s.is(ModBlocks.LAUNCH_CONTROLLER.get()) || s.is(ModBlocks.LAUNCH_CONTROLLER_PART.get())) {
                    level.removeBlock(cell, false);
                }
            }
        } finally {
            breaking = false;
        }
    }

    // --- placement / breaking ------------------------------------------------------------------

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        if (!canAssemble(context.getLevel(), context.getClickedPos(), facing)) {
            return null; // not enough room for the 3×2 structure
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            assemble(level, pos, state.getValue(FACING));
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        breakStructure(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    // --- block-entity plumbing -----------------------------------------------------------------

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, za.co.neroland.nerospace.registry.ModBlockEntities.LAUNCH_CONTROLLER.get(),
                (lvl, pos, st, be) -> be.tick(lvl, pos, st));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof LaunchControllerBlockEntity controller) {
            controller.prepareOpen(serverPlayer);
            return MenuOpener.openOrConsume(serverPlayer, controller);
        }
        return InteractionResult.SUCCESS;
    }

    //? if <1.21.5 {
    /*// Before 1.21.5 there is no BlockEntity.preRemoveSideEffects hook: run it from the block when it is replaced.
    @Override
    protected void onRemove(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LaunchControllerBlockEntity be) {
            be.preRemoveSideEffects(pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    *///?}
}
