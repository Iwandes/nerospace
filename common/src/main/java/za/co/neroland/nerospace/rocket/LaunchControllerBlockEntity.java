package za.co.neroland.nerospace.rocket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.energy.EnergyBuffer;
import za.co.neroland.nerospace.energy.NerospaceEnergyStorage;
import za.co.neroland.nerospace.fluid.FluidTank;
import za.co.neroland.nerospace.fluid.ModFluids;
import za.co.neroland.nerospace.fluid.NerospaceFluidStorage;
import za.co.neroland.nerospace.gas.GasResource;
import za.co.neroland.nerospace.gas.GasTank;
import za.co.neroland.nerospace.gas.NerospaceGasStorage;
import za.co.neroland.nerospace.menu.LaunchControllerMenu;
import za.co.neroland.nerospace.registry.ModBlockEntities;
import za.co.neroland.nerospace.registry.ModBlocks;

/**
 * Brains of the {@link LaunchControllerBlock}: holds the three building-material slots (Launch Pads,
 * Station Wall, Launch Gantry), a target tier, and the logic to lay out the real pad formation in
 * front of the controller. Building is additive — picking a higher tier only places the blocks the
 * formation is still missing (displaced pad-system blocks are dropped back to the world, so a
 * Tier-3→4 upgrade returns the Station Wall ring it replaces).
 */
public class LaunchControllerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_PAD = 0;
    public static final int SLOT_WALL = 1;
    public static final int SLOT_GANTRY = 2;
    private static final int SLOTS = 3;
    /** How far ahead of the controller the pad centre sits (clears the wall ring off the controller). */
    private static final int PAD_OFFSET = 6;

    /** Tank capacities + per-tick transfer rates into the docked rocket. */
    private static final int FUEL_CAP = 16_000;
    private static final int OXY_CAP = 8_000;
    private static final int POWER_CAP = 20_000;
    private static final int FUEL_RATE = 80;
    private static final int OXY_RATE = 40;
    private static final int POWER_RATE = 200;

    private int targetTier = 1;
    /** The tier the formation in front of the controller actually forms right now (0 = none). */
    private int builtTier;

    /**
     * Synced data slots (0..23): [22] the docked rocket's {@code StationSlot}, [23] its pad index (append-only). The
     * menu sizes its client-side mirror from this — keep them in lockstep.
     */
    public static final int DATA_COUNT = 27;
    /** Data index of the docked rocket's docking slot ({@link DockSelection}). */
    public static final int DATA_STATION = 22;
    /** Data index of the docked rocket's landing-pad index ({@link PadSelection}). */
    public static final int DATA_PAD = 23;
    /** 2.1: data index of the players aboard the docked rocket. */
    public static final int DATA_CREW = 24;
    /** 2.1: data index of how many seats the docked rocket's oxygen covers for its flight ("O₂ for N"). */
    public static final int DATA_OXYGEN_FOR = 25;
    /** 2.1: data index of the server's seats per rocket ({@code rockets.maxCrew}). */
    public static final int DATA_MAX_CREW = 26;
    private boolean hologram;
    private int mode; // 0 = pad build, 1 = launch
    private final SimpleContainer inputs = new SimpleContainer(SLOTS);

    // Cached readout of the rocket docked on the pad (server-side, refreshed per tick for the launch UI).
    private boolean rocketPresent;
    private int rocketTier;
    private int rocketFuelPct;
    private int rocketO2Pct;
    private int rocketPowerPct;
    private int rocketDest;
    private int rocketStation = DockSelection.ORIGIN;
    private int rocketPad = PadSelection.AUTO;
    private int rocketMask;
    private boolean rocketLaunchable;
    private int rocketCrew;
    private int rocketOxygenFor;

    /** Resource hub: fuel + oxygen + power, fed by pipes/cables and pumped into the rocket on the pad. */
    private final FluidTank fuelTank = new FluidTank(FUEL_CAP, this::setChanged);
    private final GasTank oxygenTank = new GasTank(OXY_CAP, this::setChanged);
    private final EnergyBuffer energy = new EnergyBuffer(POWER_CAP, 1_000, 1_000, this::setChanged);

    private long cacheTick = -1L;
    private final int[] needed = new int[3]; // [pad, wall, gantry]
    private boolean canBuild;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            refresh();
            return switch (index) {
                case 0 -> targetTier;
                case 1 -> needed[0];
                case 2 -> needed[1];
                case 3 -> needed[2];
                case 4 -> canBuild ? 1 : 0;
                case 5 -> hologram ? 1 : 0;
                case 6 -> (int) fuelTank.getAmount();
                case 7 -> FUEL_CAP;
                case 8 -> (int) oxygenTank.getAmount();
                case 9 -> OXY_CAP;
                case 10 -> energy.getRaw();
                case 11 -> POWER_CAP;
                case 12 -> mode;
                case 13 -> rocketPresent ? 1 : 0;
                case 14 -> rocketTier;
                case 15 -> rocketFuelPct;
                case 16 -> rocketO2Pct;
                case 17 -> rocketPowerPct;
                case 18 -> rocketDest;
                case 19 -> rocketMask;
                case 20 -> rocketLaunchable ? 1 : 0;
                case 21 -> builtTier;
                case DATA_STATION -> rocketStation;
                case DATA_PAD -> rocketPad;
                case DATA_CREW -> rocketCrew;
                case DATA_OXYGEN_FOR -> rocketOxygenFor;
                case DATA_MAX_CREW -> Crew.maxCrew();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Read-only from the client.
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public LaunchControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAUNCH_CONTROLLER.get(), pos, state);
    }

    public Container getInputs() {
        return this.inputs;
    }

    public ContainerData getData() {
        return this.data;
    }

    public int getTargetTier() {
        return this.targetTier;
    }

    /** Set the target pad tier (1–4); server-side, from the menu. */
    public void setTargetTier(int tier) {
        this.targetTier = Math.max(1, Math.min(4, tier));
        this.cacheTick = -1L;
        markChangedAndSync();
    }

    public boolean isHologram() {
        return this.hologram;
    }

    /** Toggle the holographic pad preview (server-side, synced to the client renderer). */
    public void toggleHologram() {
        this.hologram = !this.hologram;
        markChangedAndSync();
    }

    private void markChangedAndSync() {
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Whether an item belongs in {@code slot} (pad / wall / gantry). */
    public boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_PAD -> stack.is(ModBlocks.ROCKET_LAUNCH_PAD.get().asItem());
            case SLOT_WALL -> stack.is(ModBlocks.STATION_WALL.get().asItem());
            case SLOT_GANTRY -> stack.is(ModBlocks.LAUNCH_GANTRY.get().asItem());
            default -> false;
        };
    }

    // --- Pad layout ----------------------------------------------------------

    /** The block at depth {@code d} (along the facing) and width {@code w} (clockwise), at the pad's Y. */
    private BlockPos cell(Direction facing, Direction right, int d, int w) {
        return getBlockPos().relative(facing, d).relative(right, w);
    }

    /** The target formation for {@code tier}: every position that should hold a given pad block. */
    private Map<BlockPos, Block> targets(int tier) {
        Map<BlockPos, Block> map = new LinkedHashMap<>();
        Direction facing = getBlockState().getValue(LaunchControllerBlock.FACING);
        Direction right = facing.getClockWise();
        Block pad = ModBlocks.ROCKET_LAUNCH_PAD.get();
        int n = tier <= 1 ? 1 : (tier <= 3 ? 3 : 5);
        int r = (n - 1) / 2;
        for (int dd = -r; dd <= r; dd++) {
            for (int ww = -r; ww <= r; ww++) {
                map.put(cell(facing, right, PAD_OFFSET + dd, ww), pad);
            }
        }
        if (tier == 3) { // Station Wall ring around the 3x3
            Block wall = ModBlocks.STATION_WALL.get();
            for (int dd = -2; dd <= 2; dd++) {
                for (int ww = -2; ww <= 2; ww++) {
                    if (Math.abs(dd) == 2 || Math.abs(ww) == 2) {
                        map.put(cell(facing, right, PAD_OFFSET + dd, ww), wall);
                    }
                }
            }
        }
        if (tier >= 4) { // Launch Gantry on the 5x5 border ring
            map.put(cell(facing, right, PAD_OFFSET + 3, 0), ModBlocks.LAUNCH_GANTRY.get());
        }
        return map;
    }

    /**
     * Ghost cells for the holographic preview: each still-missing target position, as an offset from the
     * controller and a block TYPE ({@code [dx, dy, dz, type]} — 0 = Launch Pad, 1 = Station Wall,
     * 2 = Launch Gantry) so the renderer can draw the real block. Empty when the hologram is off.
     * Client-safe (reads the synced block states around the controller).
     */
    public List<int[]> previewCells() {
        if (!this.hologram || this.level == null) {
            return List.of();
        }
        List<int[]> cells = new ArrayList<>();
        BlockPos base = getBlockPos();
        for (Map.Entry<BlockPos, Block> entry : targets(this.targetTier).entrySet()) {
            BlockState current = this.level.getBlockState(entry.getKey());
            if (current.is(entry.getValue())) {
                continue; // already placed — no ghost needed
            }
            Block block = entry.getValue();
            int type = block == ModBlocks.STATION_WALL.get() ? 1
                    : (block == ModBlocks.LAUNCH_GANTRY.get() ? 2 : 0);
            BlockPos rel = entry.getKey().subtract(base);
            cells.add(new int[] {rel.getX(), rel.getY(), rel.getZ(), type});
        }
        return cells;
    }

    private int slotFor(Block block) {
        if (block == ModBlocks.STATION_WALL.get()) {
            return SLOT_WALL;
        }
        if (block == ModBlocks.LAUNCH_GANTRY.get()) {
            return SLOT_GANTRY;
        }
        return SLOT_PAD;
    }

    private boolean isPadPart(BlockState state) {
        return state.is(ModBlocks.ROCKET_LAUNCH_PAD.get())
                || state.is(ModBlocks.STATION_WALL.get())
                || state.is(ModBlocks.LAUNCH_GANTRY.get());
    }

    private boolean canPlaceAt(BlockState current) {
        return current.isAir() || current.canBeReplaced() || isPadPart(current);
    }

    /** Recompute the per-type "needed" counts + buildability (memoised per game tick, server-side). */
    private void refresh() {
        if (this.level == null || this.level.isClientSide() || this.level.getGameTime() == this.cacheTick) {
            return;
        }
        this.cacheTick = this.level.getGameTime();
        syncBuiltTier();
        this.needed[0] = 0;
        this.needed[1] = 0;
        this.needed[2] = 0;
        for (Map.Entry<BlockPos, Block> entry : targets(this.targetTier).entrySet()) {
            BlockState current = this.level.getBlockState(entry.getKey());
            if (!current.is(entry.getValue()) && canPlaceAt(current)) {
                this.needed[slotFor(entry.getValue())]++;
            }
        }
        this.canBuild = this.needed[0] <= this.inputs.getItem(SLOT_PAD).getCount()
                && this.needed[1] <= this.inputs.getItem(SLOT_WALL).getCount()
                && this.needed[2] <= this.inputs.getItem(SLOT_GANTRY).getCount()
                && (this.needed[0] + this.needed[1] + this.needed[2]) > 0;

        // Cache the docked rocket's state for the launch-mode UI.
        RocketEntity rocket = dockedRocket();
        this.rocketPresent = rocket != null;
        if (rocket != null) {
            this.rocketTier = rocket.getTier().ordinal();
            this.rocketFuelPct = rocket.getFuelPercent();
            this.rocketO2Pct = rocket.getOxygenPercent();
            this.rocketPowerPct = rocket.getPowerPercent();
            this.rocketDest = rocket.getDestinationIndex();
            this.rocketMask = rocket.destinationMask();
            this.rocketLaunchable = rocket.isLaunchReady();
            this.rocketStation = rocket.getDockValue(); // station slot, or a free-dock value (2.1)
            this.rocketPad = rocket.getPadIndex();
            this.rocketCrew = Crew.count(rocket);
            this.rocketOxygenFor = rocket.oxygenForSeats();
        } else {
            this.rocketCrew = 0;
            this.rocketOxygenFor = 0;
            this.rocketLaunchable = false;
            this.rocketStation = DockSelection.ORIGIN;
            this.rocketPad = PadSelection.AUTO;
        }
    }

    /**
     * Reads the tier the formation in front of the controller actually forms, using the same geometry
     * check a rocket uses at launch, and raises the target tier to match. Without this a pad built by
     * hand left the controller stuck on whatever tier was last picked in its menu. Never lowers the
     * target: picking a tier above what is built is how a player plans an upgrade.
     *
     * @return whether the target tier changed
     */
    private boolean syncBuiltTier() {
        if (this.level == null || this.level.isClientSide()) {
            return false;
        }
        Direction facing = getBlockState().getValue(LaunchControllerBlock.FACING);
        BlockPos centre = getBlockPos().relative(facing, PAD_OFFSET);
        this.builtTier = LaunchPadMultiblock.padTierContaining(this.level,
                LaunchPadMultiblock.connectedPads(this.level, centre), centre);
        if (this.builtTier > this.targetTier) {
            this.targetTier = this.builtTier;
            markChangedAndSync();
            return true;
        }
        return false;
    }

    /** The non-launching rocket docked on the pad this controller builds, or {@code null}. */
    @Nullable
    private RocketEntity dockedRocket() {
        if (this.level == null) {
            return null;
        }
        Direction facing = getBlockState().getValue(LaunchControllerBlock.FACING);
        return LaunchPadMultiblock.dockedRocket(this.level, getBlockPos().relative(facing, PAD_OFFSET));
    }

    // --- Launch mode ---------------------------------------------------------

    public int getMode() {
        return this.mode;
    }

    /** Toggle between pad-build mode (0) and launch mode (1). */
    public void toggleMode() {
        this.mode = this.mode == 0 ? 1 : 0;
        this.cacheTick = -1L;
        setChanged();
    }

    /** Cycle the docked rocket's destination (launch mode). */
    public void cycleRocketDestination() {
        stepRocketDestination(1, null);
    }

    /**
     * Steps the docked rocket's destination forward ({@code dir} &gt; 0) or back, skipping worlds whose Core gate
     * is closed for {@code player}. Invalidates the menu cache so the same broadcast carries the new value.
     */
    public DestinationSelection.Outcome stepRocketDestination(int dir, @Nullable Player player) {
        RocketEntity rocket = dockedRocket();
        if (rocket == null) {
            return DestinationSelection.Outcome.NO_ROCKET;
        }
        DestinationSelection.Outcome outcome = rocket.stepDestination(dir, player);
        this.cacheTick = -1L;
        return outcome;
    }

    /** Selects the docked rocket's destination by {@link Destinations} index for {@code player} (launch mode). */
    public DestinationSelection.Outcome selectRocketDestination(int index, @Nullable Player player) {
        RocketEntity rocket = dockedRocket();
        if (rocket == null) {
            return DestinationSelection.Outcome.NO_ROCKET;
        }
        DestinationSelection.Outcome outcome = rocket.selectDestination(index, player);
        this.cacheTick = -1L;
        return outcome;
    }

    /**
     * Picks the docked rocket's docking target for {@code player} (launch mode). The rocket is the authority; the
     * menu cache is invalidated so the same broadcast carries the new slot (the label never lags a tick).
     */
    public DestinationSelection.Outcome selectRocketStation(int slot, @Nullable Player player) {
        RocketEntity rocket = dockedRocket();
        if (rocket == null) {
            return DestinationSelection.Outcome.NO_ROCKET;
        }
        DestinationSelection.Outcome outcome = rocket.selectStation(slot, player);
        this.cacheTick = -1L;
        return outcome;
    }

    /** {@link #selectRocketPad(int)} for {@code player}: only the docked rocket's pilot (or anyone while it is empty). */
    public DestinationSelection.Outcome selectRocketPad(int index, @Nullable Player player) {
        RocketEntity rocket = dockedRocket();
        if (rocket == null) {
            return DestinationSelection.Outcome.NO_ROCKET;
        }
        DestinationSelection.Outcome outcome = rocket.selectPad(index, player);
        this.cacheTick = -1L;
        return outcome;
    }

    /** Picks the docked rocket's landing pad (launch mode, API callers); invalidates the menu cache. */
    public DestinationSelection.Outcome selectRocketPad(int index) {
        return selectRocketPad(index, null);
    }

    /** Cycles the docked rocket's docking target like the Rocket screen's cycler (launch mode). */
    public DestinationSelection.Outcome cycleRocketStation(@Nullable Player player) {
        RocketEntity rocket = dockedRocket();
        if (rocket == null) {
            return DestinationSelection.Outcome.NO_ROCKET;
        }
        if (!rocket.mayCommand(player)) {
            return DestinationSelection.Outcome.NOT_PILOT; // 2.1: only the pilot picks the dock
        }
        rocket.cycleStation(player);
        this.cacheTick = -1L;
        return DestinationSelection.Outcome.OK;
    }

    /** {@link #cycleRocketPad()} for {@code player}: only the docked rocket's pilot (or anyone while it is empty). */
    public DestinationSelection.Outcome cycleRocketPad(@Nullable Player player) {
        RocketEntity rocket = dockedRocket();
        if (rocket == null) {
            return DestinationSelection.Outcome.NO_ROCKET;
        }
        if (!rocket.mayCommand(player)) {
            return DestinationSelection.Outcome.NOT_PILOT;
        }
        rocket.cyclePad(player);
        this.cacheTick = -1L;
        return DestinationSelection.Outcome.OK;
    }

    /** Cycles the docked rocket's landing pad like the Rocket screen's cycler (launch mode, API callers). */
    public DestinationSelection.Outcome cycleRocketPad() {
        return cycleRocketPad(null);
    }

    /**
     * Before the console opens for {@code player}: the docked rocket's owner-first default dock and the station/pad
     * labels for its lists (the 2.0.0 controller sent neither). Invalidates the menu cache.
     */
    public void prepareOpen(net.minecraft.server.level.ServerPlayer player) {
        RocketEntity rocket = dockedRocket();
        if (rocket != null) {
            rocket.prepareConsoleFor(player);
        } else {
            za.co.neroland.nerospace.network.StationListSync.send(player);
            if (this.level != null && this.level.getServer() != null) {
                za.co.neroland.nerospace.network.ModNetwork.sendToPlayer(player,
                        za.co.neroland.nerospace.network.PadSyncPayload.of(PadRegistry.get(this.level.getServer()),
                                this.level.dimension(), getBlockPos()));
            }
        }
        this.cacheTick = -1L;
    }

    /** Board the player onto the docked rocket and launch it. @return true if the ascent started. */
    public boolean launchRocket(net.minecraft.server.level.ServerPlayer player) {
        RocketEntity rocket = dockedRocket();
        return rocket != null && rocket.boardAndLaunch(player);
    }

    /** Places every still-missing block of the target formation, consuming from the slots. Server-side. */
    public void build(Player player) {
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        int placed = 0;
        for (Map.Entry<BlockPos, Block> entry : targets(this.targetTier).entrySet()) {
            BlockPos pos = entry.getKey();
            Block block = entry.getValue();
            BlockState current = this.level.getBlockState(pos);
            if (current.is(block) || !canPlaceAt(current)) {
                continue;
            }
            int slot = slotFor(block);
            if (this.inputs.getItem(slot).isEmpty()) {
                continue;
            }
            if (isPadPart(current) && !current.isAir()) {
                // Return the displaced pad-system block instead of destroying it.
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(current.getBlock().asItem()));
            }
            this.level.setBlockAndUpdate(pos, block.defaultBlockState());
            this.inputs.removeItem(slot, 1);
            placed++;
        }
        this.cacheTick = -1L;
        markChangedAndSync();
        if (placed > 0) {
            player.sendSystemMessage(Component.translatable("block.nerospace.launch_controller.built", placed, this.targetTier));
        } else {
            player.sendSystemMessage(Component.translatable("block.nerospace.launch_controller.nothing"));
        }
    }

    // --- Resource hub (fed by pipes/cables; pumped into the docked rocket) ---

    public NerospaceFluidStorage getTank() {
        return this.fuelTank;
    }

    public NerospaceGasStorage getGas() {
        return this.oxygenTank;
    }

    public NerospaceEnergyStorage getEnergy() {
        return this.energy;
    }

    /** Server tick: top up the rocket docked on the pad in front with fuel, oxygen and power. */
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) {
            return;
        }
        // Pick up hand-built pads once a second, so the hologram and menu follow the real formation
        // even when nobody has the console open.
        if (level.getGameTime() % 20L == 0L && syncBuiltTier()) {
            this.cacheTick = -1L;
        }
        Direction facing = state.getValue(LaunchControllerBlock.FACING);
        RocketEntity rocket = LaunchPadMultiblock.dockedRocket(level, pos.relative(facing, PAD_OFFSET));
        if (rocket == null) {
            return;
        }
        int fuel = (int) this.fuelTank.drain(FUEL_RATE, false);
        if (fuel > 0) {
            int overflow = rocket.addFuel(fuel);
            if (overflow > 0) {
                this.fuelTank.fill(ModFluids.ROCKET_FUEL.get(), overflow, false);
            }
        }
        int oxy = (int) this.oxygenTank.drain(OXY_RATE, false);
        if (oxy > 0) {
            int overflow = rocket.addOxygen(oxy);
            if (overflow > 0) {
                this.oxygenTank.fill(GasResource.OXYGEN, overflow, false);
            }
        }
        int power = (int) this.energy.extract(POWER_RATE, true);
        if (power > 0) {
            int used = power - rocket.addPower(power);
            if (used > 0) {
                this.energy.extract(used, false);
            }
        }
    }

    // --- MenuProvider --------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerospace.launch_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LaunchControllerMenu(containerId, playerInventory, this.inputs, this.data, this);
    }

    // --- Removal -------------------------------------------------------------

    /**
     * Breaking the console spills the three building-material slots. Only the core cell carries this
     * block entity, and every way the structure comes down removes the core exactly once — broken
     * directly, or via {@link LaunchControllerBlock#breakStructure} when a {@link LaunchControllerPartBlock}
     * is broken, or by an explosion — so the slots spill exactly once. They are emptied as they drop,
     * so a re-entrant call could find nothing left. World-inert apart from the drops.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = this.inputs.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(this.level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
    }

    // --- Persistence ---------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        output.putInt("TargetTier", this.targetTier);
        output.putBoolean("Hologram", this.hologram);
        output.putInt("Mode", this.mode);
        output.store("Pad", ItemStack.OPTIONAL_CODEC, this.inputs.getItem(SLOT_PAD));
        output.store("Wall", ItemStack.OPTIONAL_CODEC, this.inputs.getItem(SLOT_WALL));
        output.store("Gantry", ItemStack.OPTIONAL_CODEC, this.inputs.getItem(SLOT_GANTRY));
        output.putString("FuelFluid", BuiltInRegistries.FLUID.getKey(this.fuelTank.getRawFluid()).toString());
        output.putInt("FuelAmount", this.fuelTank.getRawAmount());
        output.putString("Gas", this.oxygenTank.getRawGas().getSerializedName());
        output.putInt("GasAmount", this.oxygenTank.getRawAmount());
        output.putInt("Power", this.energy.getRaw());
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        this.targetTier = Math.max(1, Math.min(4, input.getIntOr("TargetTier", 1)));
        this.hologram = input.getBooleanOr("Hologram", false);
        this.mode = input.getIntOr("Mode", 0) == 1 ? 1 : 0;
        this.inputs.setItem(SLOT_PAD, input.read("Pad", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        this.inputs.setItem(SLOT_WALL, input.read("Wall", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        this.inputs.setItem(SLOT_GANTRY, input.read("Gantry", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        Fluid fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(input.getStringOr("FuelFluid", "minecraft:empty")));
        this.fuelTank.setRaw(fluid, input.getIntOr("FuelAmount", 0));
        this.oxygenTank.setRaw(GasResource.byName(input.getStringOr("Gas", "empty")), input.getIntOr("GasAmount", 0));
        this.energy.setRaw(input.getIntOr("Power", 0));
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }
}
