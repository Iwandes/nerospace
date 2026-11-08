package za.co.neroland.nerospace.item;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import za.co.neroland.nerospace.config.NerospaceConfig;
import za.co.neroland.nerospace.entity.RoverEntity;
import za.co.neroland.nerospace.entity.RoverRules;
import za.co.neroland.nerospace.registry.ModDataComponents;
import za.co.neroland.nerospace.registry.ModEntities;
import za.co.neroland.nerospace.registry.ModSounds;
import za.co.neroland.nerospace.telemetry.NerospaceTelemetry;

/**
 * Places a {@link RoverEntity} on the clicked face (2.1, D16–D19). The rover takes the charge stored on the item and
 * records the placer as its owner (D18). A player may have at most {@code rover.maxPerPlayer} loaded rovers placed; the
 * count is taken over loaded rovers at the moment of placing, so no per-player counter is stored anywhere.
 */
public class RoverItem extends Item {

    private static final int BAR_COLOR = 0x4DD0E1;

    public RoverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        // Into a replaceable block (grass, snow layer) where it stands, else against the clicked face.
        BlockPos at = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
        double x = at.getX() + 0.5D;
        double y = at.getY();
        double z = at.getZ() + 0.5D;

        if (player != null) {
            int cap = NerospaceConfig.roverMaxPerPlayer();
            int owned = RoverEntity.countOwned(serverLevel.getServer(), player.getUUID());
            if (!RoverRules.mayPlace(owned, cap)) {
                player.sendOverlayMessage(Component.translatable("item.nerospace.rover.cap", owned, cap));
                return InteractionResult.FAIL;
            }
        }

        RoverEntity rover = ModEntities.ROVER.get().create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (rover == null) {
            return InteractionResult.FAIL;
        }
        rover.snapTo(x, y, z, player != null ? Mth.wrapDegrees(player.getYRot()) : 0.0F, 0.0F);
        if (!level.noCollision(rover, rover.getBoundingBox())) {
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("item.nerospace.rover.no_room"));
            }
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        rover.setEnergy(stack.getOrDefault(ModDataComponents.ROVER_ENERGY.get(), 0));
        if (player != null) {
            rover.setOwner(player.getUUID());
        }
        level.addFreshEntity(rover);
        level.playSound(null, x, y, z, ModSounds.ROVER_PLACE.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        // Ints only (POPIA/GDPR): no player, no position.
        NerospaceTelemetry.breadcrumb("rover", "placed charge_pct=" + rover.energyPercent());
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stack.has(ModDataComponents.ROVER_ENERGY.get());
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int energy = stack.getOrDefault(ModDataComponents.ROVER_ENERGY.get(), 0);
        return Math.round(13.0F * RoverRules.percent(energy, RoverEntity.capacity()) / 100.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("item.nerospace.rover.charge",
                stack.getOrDefault(ModDataComponents.ROVER_ENERGY.get(), 0), RoverEntity.capacity()));
    }
}
