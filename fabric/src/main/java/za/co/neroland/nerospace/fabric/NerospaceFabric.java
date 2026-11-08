package za.co.neroland.nerospace.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import za.co.neroland.nerolandcore.platform.FabricEnergyLookup;

import za.co.neroland.nerospace.NerospaceCommon;
import za.co.neroland.nerospace.command.NerospaceCommands;
import za.co.neroland.nerospace.energy.NerospaceEnergyStorage;
import za.co.neroland.nerospace.gear.AlienGearAbilities;
import za.co.neroland.nerospace.fluid.NerospaceFluidStorage;
import za.co.neroland.nerospace.gas.NerospaceGasStorage;
import za.co.neroland.nerospace.meteor.MeteorEvents;
import za.co.neroland.nerospace.platform.FabricFluidStorageAdapter;
import za.co.neroland.nerospace.registry.ModBlockEntities;
import za.co.neroland.nerospace.registry.ModBlocks;
import za.co.neroland.nerospace.rocket.RocketPadFluidProxy;
import za.co.neroland.nerospace.rocket.RocketPadGasProxy;
import za.co.neroland.nerospace.rocket.RocketPadItemContainer;
import za.co.neroland.nerospace.world.OxygenFieldEvents;
import za.co.neroland.nerospace.world.PlayerJoinHandler;
import za.co.neroland.nerospace.world.TerraformDrift;
import za.co.neroland.nerospace.world.TerraformManager;
import za.co.neroland.nerospace.world.gravity.GravityManager;
import za.co.neroland.nerospace.registry.ModEntityAttributes;
import za.co.neroland.nerospace.registry.ModSpawnPlacements;
import za.co.neroland.nerospace.telemetry.NerospaceTelemetry;
import za.co.neroland.nerospace.progression.StarGuideGrants;
import za.co.neroland.nerospace.world.OxygenManager;

/**
 * Fabric entry point. Shared init registers content eagerly, then Fabric-side
 * wiring: creative-tab fill (Fabric API creative-tab module) and biome injection
 * of the ore placed-features (Fabric API biome module — the counterpart to the
 * NeoForge {@code biome_modifier} JSON).
 */
public final class NerospaceFabric implements ModInitializer {

    /** Mod-owned energy lookup; mirrors the NeoForge energy BlockCapability of the same id. */
    public static final BlockApiLookup<NerospaceEnergyStorage, Direction> ENERGY =
            BlockApiLookup.get(
                    Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "energy"),
                    NerospaceEnergyStorage.class, Direction.class);

    /** Mod-owned fluid lookup; mirrors the NeoForge fluid BlockCapability of the same id. */
    public static final BlockApiLookup<NerospaceFluidStorage, Direction> FLUID =
            BlockApiLookup.get(
                    Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "fluid"),
                    NerospaceFluidStorage.class, Direction.class);

    /** Mod-owned gas lookup; mirrors the NeoForge gas BlockCapability of the same id. */
    public static final BlockApiLookup<NerospaceGasStorage, Direction> GAS =
            BlockApiLookup.get(
                    Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "gas"),
                    NerospaceGasStorage.class, Direction.class);

    @Override
    public void onInitialize() {
        NerospaceCommon.LOGGER.info("[Nerospace] Fabric bootstrap");
        NerospaceCommon.init();
        // Anonymous, Nerospace-only crash reporting (opt-out via config/nerospace.properties; off in dev).
        NerospaceTelemetry.init();

        // Creative-tab contents are defined once by the cross-loader ModCreativeTab (a dedicated
        // Nerospace tab), so no per-loader creative-tab injection is needed here.

        addOverworldOre("nerosium_ore_placed");
        // Deep, rare overworld nerosteel seam (anti-circular gate: nerosteel gates Greenxertz, so a small
        // overworld supply must exist off-world). Counterpart to the NeoForge add_nerosteel_ore biome_modifier.
        addOverworldOre("nerosteel_ore_overworld_placed");

        // Default attributes for the ported mobs (counterpart to NeoForge's EntityAttributeCreationEvent).
        ModEntityAttributes.forEach(FabricDefaultAttributeRegistry::register);

        // Natural-spawn placement rules (counterpart to NeoForge's RegisterSpawnPlacementsEvent).
        ModSpawnPlacements.registerAll(new ModSpawnPlacements.Sink() {
            @Override
            public <T extends Mob> void register(EntityType<T> type, SpawnPlacementType placementType,
                    Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<T> predicate) {
                SpawnPlacements.register(type, placementType, heightmap, predicate);
            }
        });

        // Oxygen survival: register the attachment + tick each player per world tick (airless-planet drain).
        FabricAttachments.init();
        FabricNetwork.registerCommon();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            server.getPlayerList().getPlayers().forEach(player -> {
                OxygenManager.tick(player);
                StarGuideGrants.tick(player);
                za.co.neroland.nerospace.network.StationListSync.tick(player);
            });
            MeteorEvents.tick(server);
            OxygenFieldEvents.tick(server);
            TerraformDrift.tick(server);
            GravityManager.tick(server);
            // Planet clock + weather + client sync (one common entry point; see docs/MULTILOADER.md).
            za.co.neroland.nerospace.world.planet.PlanetTicks.serverTick(server);
            za.co.neroland.nerospace.route.CargoFlights.tick(server);
            // NeroLink needs its own server handle (Core's SPI hands a provider only a player UUID).
            za.co.neroland.nerospace.link.NerospaceLinkModule.rememberServer(server);
        });
        // Planet sleeping (see docs/MULTILOADER.md): ALLOW_SLEEPING fires from Player.startSleepInBed, after
        // ServerPlayer's own checks have set the respawn point; the end-of-night skip runs before any level ticks.
        net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) ->
                player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                        ? za.co.neroland.nerospace.world.planet.PlanetSleep.refusal(serverPlayer, pos) : null);
        ServerTickEvents.START_SERVER_TICK.register(za.co.neroland.nerospace.world.planet.PlanetSleep::serverTickStart);
        // Creative debug commands (/nerospace gallery).
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                NerospaceCommands.register(dispatcher));
        // One-time welcome on join (counterpart to NeoForge's PlayerLoggedInEvent).
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PlayerJoinHandler.onPlayerJoin(handler.player));
        // Terraform catch-up: convert any in-range columns on chunks that load after the frontier passed.
        // (Fabric's Load SAM passes a third "newly generated" flag, which we don't need.)
        //? if >=26.1 {
        ServerChunkEvents.CHUNK_LOAD.register((serverLevel, chunk, newlyGenerated) ->
                TerraformManager.get(serverLevel).onChunkLoaded(serverLevel, chunk));
        //?} else {
        /*ServerChunkEvents.CHUNK_LOAD.register((serverLevel, chunk) ->
                TerraformManager.get(serverLevel).onChunkLoaded(serverLevel, chunk));
        *///?}
        // Artificer gear: Grav Striders cushion the wearer — cancel fall damage while carried
        // (counterpart to NeoForge's LivingFallEvent; returning false vetoes the damage).
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(source.is(DamageTypes.FALL) && AlienGearAbilities.negatesFall(entity)));

        // The Battery / Fluid Tank / Gas Tank / Item Store endpoints now live in Neroland Core and expose
        // Core's own lookups. Energy crosses for free via Core's energy lookup and the Item Store is a
        // vanilla Container; only fluid/gas need re-exposing on Nerospace's lookups for the Universal Pipe —
        // see the CoreTankBridge registrations below.

        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.COMBUSTION_GENERATOR.get());
        ENERGY.registerForBlockEntity(
                (be, direction) -> be.getEnergy(),
                ModBlockEntities.COMBUSTION_GENERATOR.get());

        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.NEROSIUM_GRINDER.get());
        ENERGY.registerForBlockEntity(
                (be, direction) -> be.getEnergy(),
                ModBlockEntities.NEROSIUM_GRINDER.get());

        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.PASSIVE_GENERATOR.get());
        ENERGY.registerForBlockEntity(
                (be, direction) -> be.getEnergy(),
                ModBlockEntities.PASSIVE_GENERATOR.get());

        ENERGY.registerForBlockEntity(
                (be, direction) -> be.getEnergy(),
                ModBlockEntities.UNIVERSAL_PIPE.get());
        GAS.registerForBlockEntity(
                (be, direction) -> be.getGas(),
                ModBlockEntities.UNIVERSAL_PIPE.get());
        FLUID.registerForBlockEntity(
                (be, direction) -> be.getFluidTank(),
                ModBlockEntities.UNIVERSAL_PIPE.get());
        // Cross-mod fluid EXPORT: every tank published on the mod-private FLUID lookup above is also
        // published on the Transfer API's FluidStorage.SIDED, wrapped by FabricFluidStorageAdapter (which
        // converts millibuckets to droplets and makes the tank transaction-safe). Purely additive — the
        // FLUID registrations are untouched, so Nerospace's own blocks behave exactly as before.
        FluidStorage.SIDED.registerForBlockEntity(
                (be, direction) -> FabricFluidStorageAdapter.of(be.getFluidTank()),
                ModBlockEntities.UNIVERSAL_PIPE.get());
        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.UNIVERSAL_PIPE.get());

        ENERGY.registerForBlockEntity(
                (be, direction) -> be.getEnergy(),
                ModBlockEntities.OXYGEN_GENERATOR.get());
        // Gas is gated by the side config: the gated Core gas view (null on DISABLED/INPUT faces) mapped
        // back onto Nerospace's gas lookup so the Universal Pipe pulls oxygen only from OUTPUT faces.
        GAS.registerForBlockEntity(
                (be, direction) -> za.co.neroland.nerospace.machine.MachineSideConfig.gasView(be.sideConfig(), direction),
                ModBlockEntities.OXYGEN_GENERATOR.get());

        // Launch Controller resource hub: fuel + oxygen + power inputs (pumped into the docked rocket).
        FLUID.registerForBlockEntity((be, direction) -> be.getTank(), ModBlockEntities.LAUNCH_CONTROLLER.get());
        FluidStorage.SIDED.registerForBlockEntity(
                (be, direction) -> FabricFluidStorageAdapter.of(be.getTank()),
                ModBlockEntities.LAUNCH_CONTROLLER.get());
        GAS.registerForBlockEntity((be, direction) -> be.getGas(), ModBlockEntities.LAUNCH_CONTROLLER.get());
        ENERGY.registerForBlockEntity((be, direction) -> be.getEnergy(), ModBlockEntities.LAUNCH_CONTROLLER.get());

        ENERGY.registerForBlockEntity(
                (be, direction) -> be.getEnergy(),
                ModBlockEntities.SOLAR_PANEL.get());

        // Terraformer: grid power in, upgrade slot in.
        ENERGY.registerForBlockEntity(
                (be, direction) -> be.getEnergy(),
                ModBlockEntities.TERRAFORMER.get());
        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.TERRAFORMER.get());

        // Hydration Module: glacite in (no energy of its own).
        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.HYDRATION_MODULE.get());

        // Trash Can now lives in Neroland Core (vanilla Container for items found by item adjacency); only
        // its fluid/gas surfaces need bridging onto Nerospace's lookups so the Universal Pipe still voids into it.
        FLUID.registerForBlockEntity((be, side) -> za.co.neroland.nerospace.storage.CoreTankBridge.fluid(be.getFluid()),
                za.co.neroland.nerolandcore.registry.ModBlockEntities.TRASH_CAN.get());
        FluidStorage.SIDED.registerForBlockEntity(
                (be, side) -> FabricFluidStorageAdapter.of(
                        za.co.neroland.nerospace.storage.CoreTankBridge.fluid(be.getFluid())),
                za.co.neroland.nerolandcore.registry.ModBlockEntities.TRASH_CAN.get());
        GAS.registerForBlockEntity((be, side) -> za.co.neroland.nerospace.storage.CoreTankBridge.gas(be.getGas()),
                za.co.neroland.nerolandcore.registry.ModBlockEntities.TRASH_CAN.get());

        // Re-expose Core's tank block-entities on Nerospace's own fluid/gas lookups so the Universal Pipe
        // still connects to the (now Core-owned) Fluid Tank / Gas Tank and their creative variants.
        FLUID.registerForBlockEntity((be, side) -> za.co.neroland.nerospace.storage.CoreTankBridge.fluid(be.getTank()),
                za.co.neroland.nerolandcore.registry.ModBlockEntities.FLUID_TANK.get());
        FluidStorage.SIDED.registerForBlockEntity(
                (be, side) -> FabricFluidStorageAdapter.of(
                        za.co.neroland.nerospace.storage.CoreTankBridge.fluid(be.getTank())),
                za.co.neroland.nerolandcore.registry.ModBlockEntities.FLUID_TANK.get());
        FLUID.registerForBlockEntity((be, side) -> za.co.neroland.nerospace.storage.CoreTankBridge.fluid(be.getTank()),
                za.co.neroland.nerolandcore.registry.ModBlockEntities.CREATIVE_FLUID_TANK.get());
        FluidStorage.SIDED.registerForBlockEntity(
                (be, side) -> FabricFluidStorageAdapter.of(
                        za.co.neroland.nerospace.storage.CoreTankBridge.fluid(be.getTank())),
                za.co.neroland.nerolandcore.registry.ModBlockEntities.CREATIVE_FLUID_TANK.get());
        GAS.registerForBlockEntity((be, side) -> za.co.neroland.nerospace.storage.CoreTankBridge.gas(be.getTank()),
                za.co.neroland.nerolandcore.registry.ModBlockEntities.GAS_TANK.get());
        GAS.registerForBlockEntity((be, side) -> za.co.neroland.nerospace.storage.CoreTankBridge.gas(be.getTank()),
                za.co.neroland.nerolandcore.registry.ModBlockEntities.CREATIVE_GAS_TANK.get());

        // Cargo Pad: rocket fuel in via the side config (fuel-only buffer), cargo in/out on every face.
        FLUID.registerForBlockEntity(
                (be, direction) -> za.co.neroland.nerospace.machine.MachineSideConfig.fluidView(be.sideConfig(), direction),
                ModBlockEntities.CARGO_PAD.get());
        FluidStorage.SIDED.registerForBlockEntity(
                (be, direction) -> FabricFluidStorageAdapter.of(
                        za.co.neroland.nerospace.machine.MachineSideConfig.fluidView(be.sideConfig(), direction)),
                ModBlockEntities.CARGO_PAD.get());
        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.CARGO_PAD.get());

        // Fuel Tank: fluid in/out via the side config (STORAGE preset, default IO), canister in.
        FLUID.registerForBlockEntity(
                (be, direction) -> za.co.neroland.nerospace.machine.MachineSideConfig.fluidView(be.sideConfig(), direction),
                ModBlockEntities.FUEL_TANK.get());
        // The gated view is null on a face the side config disables; FabricFluidStorageAdapter.of() keeps
        // that null, so the face stays invisible to foreign mods too.
        FluidStorage.SIDED.registerForBlockEntity(
                (be, direction) -> FabricFluidStorageAdapter.of(
                        za.co.neroland.nerospace.machine.MachineSideConfig.fluidView(be.sideConfig(), direction)),
                ModBlockEntities.FUEL_TANK.get());
        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.FUEL_TANK.get());

        // Fuel Refinery: grid power in, refined fuel out, coal + blaze powder in.
        ENERGY.registerForBlockEntity(
                (be, direction) -> be.getEnergy(),
                ModBlockEntities.FUEL_REFINERY.get());
        // Refined fuel out via the side config (gated; null on non-OUTPUT/IO faces).
        FLUID.registerForBlockEntity(
                (be, direction) -> za.co.neroland.nerospace.machine.MachineSideConfig.fluidView(be.sideConfig(), direction),
                ModBlockEntities.FUEL_REFINERY.get());
        FluidStorage.SIDED.registerForBlockEntity(
                (be, direction) -> FabricFluidStorageAdapter.of(
                        za.co.neroland.nerospace.machine.MachineSideConfig.fluidView(be.sideConfig(), direction)),
                ModBlockEntities.FUEL_REFINERY.get());
        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.FUEL_REFINERY.get());

        // Quarry controller: grid power in, mined output + sucked fluid out, frame casings in.
        ENERGY.registerForBlockEntity(
                (be, direction) -> be.getEnergy(),
                ModBlockEntities.QUARRY_CONTROLLER.get());
        FLUID.registerForBlockEntity(
                (be, direction) -> be.getTank(),
                ModBlockEntities.QUARRY_CONTROLLER.get());
        FluidStorage.SIDED.registerForBlockEntity(
                (be, direction) -> FabricFluidStorageAdapter.of(be.getTank()),
                ModBlockEntities.QUARRY_CONTROLLER.get());
        ItemStorage.SIDED.registerForBlockEntity(
                (be, direction) -> ContainerStorage.of(be, direction),
                ModBlockEntities.QUARRY_CONTROLLER.get());

        // Rocket launch pad: a fluid sink forwarding rocket_fuel into a docked rocket (refuelling
        // automation), plus an item sink forwarding fuel canisters into the rocket's intake. Registered on
        // the BLOCK (no block entity) — the proxies find the rocket above the pad.
        FLUID.registerForBlocks(
                (world, pos, state, blockEntity, side) -> new RocketPadFluidProxy(world, pos),
                ModBlocks.ROCKET_LAUNCH_PAD.get());
        // Deliberately NOT exported on FluidStorage.SIDED. A Transfer API Storage must be able to roll
        // back, and FabricFluidStorageAdapter rolls back by draining what it holds and re-filling the
        // snapshot — which is only faithful when fill/drain are inverses. RocketPadFluidProxy is a pure
        // SINK: its drain() always returns 0 while its fill() permanently fuels the docked rocket, so a
        // routine StorageUtil.simulateInsert probe (insert, then ABORT) would fuel the rocket on the way
        // in and fuel it AGAIN on the rollback's re-fill, for free, every tick a foreign pipe probes.
        // Foreign mods can still fuel a rocket by piping into a Nerospace Fuel Tank, and Nerospace's own
        // pipes are unaffected: they reach the pad through the mod-private FLUID lookup above, which is
        // not transactional and calls the proxy exactly once per transfer.
        // Gas sink: forwards oxygen into a docked rocket's onboard life-support tank.
        GAS.registerForBlocks(
                (world, pos, state, blockEntity, side) -> new RocketPadGasProxy(world, pos),
                ModBlocks.ROCKET_LAUNCH_PAD.get());
        ItemStorage.SIDED.registerForBlocks(
                (world, pos, state, blockEntity, side) -> ContainerStorage.of(new RocketPadItemContainer(world, pos), side),
                ModBlocks.ROCKET_LAUNCH_PAD.get());

        registerCoreEnergy();
        registerCoreGas();
    }

    /**
     * Cross-mod gas (Neroland Core): expose the Universal Pipe's gas buffer on Core's shared
     * {@code nerolandcore:gas} lookup too, so a Nero machine that pushes/pulls through Core's gas lookup
     * (NeroTech's Electrolyzer, Chemical Processor, Gas Turbine) reaches the pipe directly and any gas id
     * — not just oxygen — can enter it. Ungated per face, exactly like the pipe's {@link #GAS}
     * registration above, which stays for Nerospace's own {@code GasResource} consumers.
     *
     * <p>Nerospace's side-configured machines are deliberately NOT registered here: the pipe's neighbour
     * lookup ({@code PipeGas.find}) asks Core's lookup first and falls back to {@link #GAS}, where their
     * gated view lives. Registering one here would need its gated {@code sideConfig().gasView(side)}, or
     * the gate could be walked around.</p>
     */
    private static void registerCoreGas() {
        za.co.neroland.nerolandcore.platform.FabricGasLookup.GAS.registerForBlockEntity(
                (be, dir) -> be.getCoreGas(), ModBlockEntities.UNIVERSAL_PIPE.get());
    }

    /**
     * Cross-mod energy network (Neroland Core): expose every Nerospace energy block-entity on Core's
     * shared {@code nerolandcore:energy} lookup too, so machines from any Nero mod interoperate on one
     * power network. {@link NerospaceEnergyStorage} extends {@code NeroEnergyStorage}, so the existing
     * {@code getEnergy()} providers satisfy Core's lookup unchanged. The mod's own {@link #ENERGY} stays
     * registered above for back-compat.
     */
    private static void registerCoreEnergy() {
        // Side-config-integrated machines expose energy through the gated view (null on DISABLED faces);
        // the Universal Pipe queries this Core lookup via Nerospace's delegating energy lookup.
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.sideConfig().energyView(dir), ModBlockEntities.COMBUSTION_GENERATOR.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.sideConfig().energyView(dir), ModBlockEntities.NEROSIUM_GRINDER.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.sideConfig().energyView(dir), ModBlockEntities.PASSIVE_GENERATOR.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.getEnergy(), ModBlockEntities.UNIVERSAL_PIPE.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.sideConfig().energyView(dir), ModBlockEntities.OXYGEN_GENERATOR.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.getEnergy(), ModBlockEntities.LAUNCH_CONTROLLER.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.sideConfig().energyView(dir), ModBlockEntities.SOLAR_PANEL.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.getEnergy(), ModBlockEntities.TERRAFORMER.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.sideConfig().energyView(dir), ModBlockEntities.FUEL_REFINERY.get());
        FabricEnergyLookup.ENERGY.registerForBlockEntity((be, dir) -> be.getEnergy(), ModBlockEntities.QUARRY_CONTROLLER.get());
    }

    private static void addOverworldOre(String placedFeatureName) {
        ResourceKey<PlacedFeature> key = ResourceKey.create(
                Registries.PLACED_FEATURE,
                Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, placedFeatureName));
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                key);
    }
}
