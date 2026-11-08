package za.co.neroland.nerospace.rocket;

import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * The craftable launch mount: a short pad a rocket is assembled onto. A {@link RocketItem} may only
 * place a {@link RocketEntity} directly above one of these blocks, so the pad doubles as the
 * "assembly point" gate for a launch. Empty-hand right-click prints a formation report (cluster size,
 * pad tier, what to build next). It also doubles as a travel node: apply a Name Tag to commission the
 * pad as a named landing spot for pad-to-pad travel (see {@link PadRegistry}); sneak + empty-hand
 * decommissions it.
 */
public class RocketLaunchPadBlock extends Block {

    /** The plate's top surface, in blocks — rockets stand at pad Y + this. */
    public static final double SURFACE_HEIGHT = 3.0D / 16.0D;

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);

    public RocketLaunchPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Announce the pad tier this block completes, so a hand-built pad says when it is ready. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        LaunchPadFeedback.onPartPlaced(level, pos, placer);
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
    //?} else {
    /*// Before 1.21.2 useItemOn returns an ItemInteractionResult; the 26.x body below runs as a helper.
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return za.co.neroland.nerospace.compat.mc.UseCompat.toItem(
                useItemOnResult(stack, state, level, pos, player, hand, hit));
    }

    private InteractionResult useItemOnResult(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
    *///?}
        // A Name Tag commissions this pad as a named travel node (the tag's name labels it, or "Pad N").
        if (stack.is(Items.NAME_TAG)) {
            if (level instanceof ServerLevel serverLevel) {
                Component custom = stack.get(DataComponents.CUSTOM_NAME);
                Set<BlockPos> cluster = LaunchPadMultiblock.connectedPads(level, pos);
                // No renaming a pad out from under someone else's crewed rocket (2.1: anyone else aboard, any seat).
                RocketEntity parked = LaunchPadMultiblock.rocketAbove(level, cluster);
                if (parked != null && Crew.seated(parked).stream().anyMatch(rider -> rider != player)) {
                    player.sendSystemMessage(Component.translatable("block.nerospace.rocket_launch_pad.in_use"));
                    return InteractionResult.SUCCESS;
                }
                // A 2.0 commission records the pad's centre and front, so landings are centred.
                BlockPos centre = LandingPadGeometry.centreOf(cluster, pos);
                PadRegistry.PadNode node = PadRegistry.get(serverLevel.getServer())
                        .register(custom == null ? null : custom.getString(), level.dimension(), pos,
                                PadKind.LAUNCH_PAD, centre, LandingResolverLevelAdapter.apronFacing(level, cluster, centre));
                if (node == null) {
                    player.sendSystemMessage(Component.translatable("block.nerospace.rocket_launch_pad.pads_full"));
                } else {
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    player.sendSystemMessage(Component.translatable(
                            "block.nerospace.rocket_launch_pad.registered", node.name()));
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Only act on a truly empty hand: this path also runs while HOLDING items (the 26.1 interaction
        // order tries it before Item#useOn), and consuming the click here would swallow rocket-item deploy.
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // Sneak + empty hand decommissions a registered travel node here.
        if (player.isShiftKeyDown() && level instanceof ServerLevel serverLevel) {
            PadRegistry.PadNode removed = PadRegistry.get(serverLevel.getServer()).unregisterAt(level.dimension(), pos);
            if (removed != null) {
                player.sendSystemMessage(Component.translatable(
                        "block.nerospace.rocket_launch_pad.unregistered", removed.name()));
                return InteractionResult.SUCCESS;
            }
        }
        Set<BlockPos> pads = LaunchPadMultiblock.connectedPads(level, pos);
        int tier = LaunchPadMultiblock.padTier(level, pads);
        // Summary line: the tier (highlighted) and the cluster size.
        player.sendSystemMessage(Component.translatable("block.nerospace.rocket_launch_pad.report.summary",
                tierLabel(tier), pads.size()).withStyle(ChatFormatting.AQUA));
        // Say plainly that the pad is ready, so nobody goes looking for a missing step.
        if (LaunchPadMultiblock.rocketAbove(level, pads) == null) {
            player.sendSystemMessage(Component.translatable("block.nerospace.rocket_launch_pad.report.ready",
                    tierLabel(tier)).withStyle(ChatFormatting.GREEN));
        }
        // Next-tier build hint (or "max" once the Heavy Launch Complex is formed).
        String hint = switch (tier) {
            case 1 -> "block.nerospace.rocket_launch_pad.report.next_t2";
            case 2 -> "block.nerospace.rocket_launch_pad.report.next_t3";
            case 3 -> "block.nerospace.rocket_launch_pad.report.next_t4";
            default -> "block.nerospace.rocket_launch_pad.report.max";
        };
        player.sendSystemMessage(Component.translatable(hint)
                .withStyle(tier >= 4 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    /** "Tier N" in bold gold, the one part of a pad message the eye should land on. */
    public static Component tierLabel(int tier) {
        return Component.translatable("block.nerospace.rocket_launch_pad.tier", tier)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    }
}
