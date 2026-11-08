package za.co.neroland.nerospace.rocket;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerospace.menu.MenuOpener;
import za.co.neroland.nerospace.registry.ModBlocks;

/**
 * A solid filler block of the {@link LaunchControllerBlock} multiblock (3 wide × 2 tall). It is a full
 * cube — the player can't walk through the console — but carries no block entity; interaction and
 * breaking are forwarded to the core block via {@link LaunchControllerBlock}. Not obtainable as an item.
 */
public class LaunchControllerPartBlock extends Block {

    public LaunchControllerPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos core = LaunchControllerBlock.findCore(level, pos);
        if (core != null && level.getBlockEntity(core) instanceof LaunchControllerBlockEntity controller
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            controller.prepareOpen(serverPlayer);
            return MenuOpener.openOrConsume(serverPlayer, controller);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        LaunchControllerBlock.breakStructure(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ModBlocks.LAUNCH_CONTROLLER.get());
    }
}
