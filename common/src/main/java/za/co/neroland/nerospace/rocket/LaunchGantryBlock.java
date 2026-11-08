package za.co.neroland.nerospace.rocket;

import java.util.Set;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;
import za.co.neroland.nerospace.menu.MenuOpener;

/**
 * The Launch Gantry module: placed on a 5x5 pad's border ring it forms the Heavy Launch Complex
 * (Tier 4 launch infrastructure). Right-click opens the flight console of the rocket on the adjacent
 * pad — the service-tower QoL, no more pixel-hunting the entity (the player boards only on Launch).
 *
 * <p>The block itself renders nothing ({@link RenderShape#INVISIBLE}); its tall service tower is drawn by
 * {@code LaunchGantryRenderer} via the {@link LaunchGantryBlockEntity}, so the tower can lean back to
 * release a launching rocket and swing upright once the rocket clears it.</p>
 */
public class LaunchGantryBlock extends BaseEntityBlock {

    public static final MapCodec<LaunchGantryBlock> CODEC = BlockCodecs.simple(LaunchGantryBlock::new);

    public LaunchGantryBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<LaunchGantryBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // the tower is drawn by the block-entity renderer
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchGantryBlockEntity(pos, state);
    }

    /** Announce the Heavy Launch Complex when this gantry completes it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        LaunchPadFeedback.onPartPlaced(level, pos, placer);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pad = LaunchPadMultiblock.adjacentPad(level, pos);
        Set<BlockPos> pads = pad == null ? Set.of() : LaunchPadMultiblock.connectedPads(level, pad);
        RocketEntity rocket = LaunchPadMultiblock.rocketAbove(level, pads);
        if (rocket == null) {
            player.sendSystemMessage(Component.translatable("block.nerospace.launch_gantry.no_rocket"));
            return InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            rocket.prepareConsoleFor(serverPlayer);
            return MenuOpener.openOrConsume(serverPlayer, rocket);
        }
        return InteractionResult.SUCCESS;
    }
}
