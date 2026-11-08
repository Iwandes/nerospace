package za.co.neroland.nerospace.rocket;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

/**
 * The Docking Ring: a 3×3 landing plate whose centre registers itself as a
 * {@link PadKind#DOCK} node in {@link PadRegistry}. Station arrivals prefer a dock within the station's claim
 * box; on a planet a ring is just a fancy pad. The centre registers in {@code onPlace} and — because structure
 * placement never calls {@code onPlace} — again on a random tick when its node is missing. Breaking any cell
 * breaks the ring; only the centre drops the item.
 */
public class DockingRingBlock extends Block {

    public static final MapCodec<DockingRingBlock> CODEC = BlockCodecs.simple(DockingRingBlock::new);

    public static final EnumProperty<DockingRingPart> PART = EnumProperty.create("part", DockingRingPart.class);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** Touchdown flash length. */
    public static final int FLASH_TICKS = 40;

    /** The same 3-px plate as the launch pad, so a docked rocket stands at {@link LandingResolver#SURFACE_HEIGHT}. */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);

    private static boolean breaking;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public DockingRingBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(PART, DockingRingPart.CENTER)
                .setValue(FACING, Direction.EAST).setValue(ACTIVE, false));
    }

    protected MapCodec<DockingRingBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // --- template ---------------------------------------------------------------------------------

    /** The nine placements of a ring centred on {@code centre} (for the station module builder). */
    public static List<Placement> ringTemplate(BlockState base, BlockPos centre, Direction facing) {
        List<Placement> out = new ArrayList<>(9);
        for (DockingRingPart part : DockingRingPart.values()) {
            out.add(new Placement(part.at(centre),
                    base.setValue(PART, part).setValue(FACING, facing).setValue(ACTIVE, false)));
        }
        return out;
    }

    public record Placement(BlockPos pos, BlockState state) {
    }

    /**
     * Places a ring (cells that are air/replaceable without a block entity only; never over a player block)
     * and registers its {@code DOCK} node named {@code nodeName}. Idempotent.
     *
     * @return whether the centre stands afterwards
     */
    public static boolean placeRing(ServerLevel level, BlockPos centre, Direction facing, @Nullable String nodeName) {
        Block self = za.co.neroland.nerospace.rocket.DockingContent.DOCKING_RING.get();
        for (Placement p : ringTemplate(self.defaultBlockState(), centre, facing)) {
            BlockState current = level.getBlockState(p.pos());
            if (current.is(self)) {
                continue;
            }
            if ((current.isAir() || current.canBeReplaced()) && level.getBlockEntity(p.pos()) == null) {
                level.setBlock(p.pos(), p.state(), Block.UPDATE_ALL);
            }
        }
        BlockState c = level.getBlockState(centre);
        if (!c.is(self) || c.getValue(PART) != DockingRingPart.CENTER) {
            return false;
        }
        register(level, centre, c.getValue(FACING), nodeName);
        return true;
    }

    private static void register(ServerLevel level, BlockPos centre, Direction facing, @Nullable String nodeName) {
        PadRegistry registry = PadRegistry.get(level.getServer());
        PadRegistry.PadNode existing = registry.at(level.dimension(), centre);
        if (existing != null) {
            if (existing.kind() != PadKind.DOCK) {
                registry.rekind(level.dimension(), centre, PadKind.DOCK);
            }
            registry.fill(existing.id(), centre, facing);
            if (nodeName != null && !nodeName.isBlank() && !nodeName.equals(existing.name())) {
                registry.register(nodeName, level.dimension(), centre);
            }
            return;
        }
        registry.register(nodeName, level.dimension(), centre, PadKind.DOCK, centre, facing);
    }

    // --- placement --------------------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos centre = context.getClickedPos();
        for (DockingRingPart part : DockingRingPart.values()) {
            if (part == DockingRingPart.CENTER) {
                continue;
            }
            BlockPos at = part.at(centre);
            BlockState s = level.getBlockState(at);
            if (!(s.isAir() || s.canBeReplaced()) || level.getBlockEntity(at) != null) {
                return null; // not enough room for the 3×3
            }
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && state.getValue(PART) == DockingRingPart.CENTER) {
            // An anvil-renamed ring names its node (a free dock's list label); otherwise the auto "Dock N".
            placeRing(server, pos, state.getValue(FACING), customName(stack));
            if (placer instanceof Player player) {
                PadRegistry.PadNode node = PadRegistry.get(server.getServer()).at(level.dimension(), pos);
                if (node != null) {
                    player.sendSystemMessage(Component.translatable("block.nerospace.docking_ring.registered", node.name()));
                } else {
                    player.sendSystemMessage(Component.translatable("block.nerospace.rocket_launch_pad.pads_full"));
                }
            }
        }
    }

    /** The ring item's anvil name (trimmed, at most 32 chars), or {@code null}. No placer is recorded. */
    @Nullable
    static String customName(ItemStack stack) {
        net.minecraft.network.chat.Component name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name == null) {
            return null;
        }
        String text = name.getString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.length() > 32 ? text.substring(0, 32) : text;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this) && state.getValue(PART) == DockingRingPart.CENTER) {
            register(server, pos, state.getValue(FACING), null);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(PART) == DockingRingPart.CENTER;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != DockingRingPart.CENTER) {
            return;
        }
        boolean registered = PadRegistry.get(level.getServer()).at(level.dimension(), pos) != null;
        if (DockRules.shouldRegister(registered)) {
            register(level, pos, state.getValue(FACING), null);
        }
    }

    /** Lights the hazard stripes for {@link #FLASH_TICKS} (touchdown). Server-side. */
    public static void flash(ServerLevel level, BlockPos centre) {
        BlockState s = level.getBlockState(centre);
        if (s.getBlock() instanceof DockingRingBlock ring && !s.getValue(ACTIVE)) {
            level.setBlock(centre, s.setValue(ACTIVE, true), Block.UPDATE_ALL);
            level.scheduleTick(centre, ring, FLASH_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_ALL);
        }
    }

    // --- breaking ---------------------------------------------------------------------------------

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        breakRing(level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static void breakRing(Level level, BlockPos pos, BlockState state, @Nullable Player player) {
        if (breaking || level.isClientSide()) {
            return;
        }
        BlockPos centre = state.getValue(PART).centreFrom(pos);
        breaking = true;
        try {
            boolean creative = player != null && player.getAbilities().instabuild;
            if (!creative && !pos.equals(centre)) {
                Block.popResource(level, centre, new ItemStack(state.getBlock()));
            }
            for (DockingRingPart part : DockingRingPart.values()) {
                BlockPos cell = part.at(centre);
                if (cell.equals(pos)) {
                    continue; // vanilla clears the broken cell (and drops the centre's loot)
                }
                BlockState s = level.getBlockState(cell);
                if (s.getBlock() instanceof DockingRingBlock && s.getValue(PART) == part) {
                    level.removeBlock(cell, false);
                }
            }
        } finally {
            breaking = false;
        }
    }

    //? if >=1.21.5 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    //?} else {
    /*@Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level world, BlockPos pos, BlockState newState,
            boolean movedByPiston) {
        super.onRemove(state, world, pos, newState, movedByPiston);
        if (!(world instanceof ServerLevel level) || state.is(newState.getBlock())) {
            return;
        }
    *///?}
        if (state.getValue(PART) == DockingRingPart.CENTER && !(level.getBlockState(pos).getBlock() instanceof DockingRingBlock)) {
            PadRegistry.get(level.getServer()).unregisterAt(level.dimension(), pos);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
