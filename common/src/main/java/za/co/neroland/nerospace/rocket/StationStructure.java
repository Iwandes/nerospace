package za.co.neroland.nerospace.rocket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.registry.ModBlocks;
import za.co.neroland.nerospace.world.OxygenFieldManager;
import za.co.neroland.nerospace.world.station.ModuleEntry;
import za.co.neroland.nerospace.world.station.ModuleKind;
import za.co.neroland.nerospace.world.station.ModuleTemplates;
import za.co.neroland.nerospace.world.station.StationModules;

/**
 * Builds a station in the void, module-aware. The core is the 1.x 7×7
 * station-floor room (station-wall pillars, glass bands, lit roof) with an airlock corridor east. A <b>new</b>
 * station gets a Docking Ring at the old pad location instead of the 3×3 Tier-2 pad and records the {@code CORE} and
 * {@code DOCKING_RING} modules. An <b>existing</b> station is never rebuilt: a 1.x station (Station Core standing, no
 * core module recorded) gets its {@code CORE_LEGACY} entry and room oxygen on its first 2.0 visit, and its pad or
 * dock node is kept registered. The shared origin platform (no registry entry) counts as built when its pad node is
 * registered or its centre floor is station floor, so an arrival never rewrites it. Idempotent throughout.
 */
public final class StationStructure {

    /** 7×7 room footprint (radius 3). */
    public static final int RADIUS = ModuleTemplates.ROOM_R;
    /** Room wall height: walls at +1..+WALL_H, roof at +WALL_H+1. */
    public static final int WALL_H = ModuleTemplates.WALL_H;
    /** Length of the airlock corridor (blocks east of the room wall). */
    private static final int AIRLOCK_LEN = ModuleTemplates.AIRLOCK_LEN;

    private StationStructure() {
    }

    /** The landing-pad / Docking Ring centre for a station at {@code centre} (deterministic from the layout). */
    public static BlockPos padCenter(BlockPos centre) {
        return new BlockPos(centre.getX() + RADIUS + AIRLOCK_LEN + 2, centre.getY(), centre.getZ());
    }

    /**
     * The origin-platform guard, pure: a station counts as already built when a Station Core stands at
     * its centre, or a pad node is registered at its pad centre, or its centre floor is station floor.
     */
    public static boolean alreadyBuilt(boolean hasCoreAtCentre, boolean hasPadNodeAtPad,
            boolean floorIsStationFloor) {
        return hasCoreAtCentre || hasPadNodeAtPad || floorIsStationFloor;
    }

    /**
     * Generates the station around {@code centre} <b>once</b>, returning the centre of its landing pad / Docking
     * Ring. An already-built station is left untouched — player-placed/-removed blocks are preserved — and only its
     * node is kept registered (plus the one-time {@code CORE_LEGACY} record for a 1.x station).
     */
    public static BlockPos build(ServerLevel level, BlockPos centre) {
        StationModules.loadChunks(level, centre);
        BlockPos padCentre = padCenter(centre);
        StationRegistry registry = StationRegistry.get(level.getServer());
        StationRegistry.StationEntry entry = entryAt(level, centre);

        boolean hasCore = level.getBlockState(centre).is(ModBlocks.STATION_CORE.get());
        if (hasCore) {
            if (entry != null) {
                StationModules.ensureLegacyCore(level, registry, entry);
                StationRegistry.StationEntry now = registry.get(entry.slot());
                if (now != null && now.hasModule(ModuleKind.DOCKING_RING)) {
                    StationModules.placeDock(level, padCentre, now.name() + " Dock");
                    return padCentre;
                }
            }
            registerPad(level, centre, padCentre);
            return padCentre;
        }
        if (entry != null && frameOnly(true, false, !entry.modules().isEmpty(), entry.hasModule(ModuleKind.CORE))) {
            return padCentre; // founded without the starter structure: the owner builds it; never stamp over it
        }
        boolean floor = level.getBlockState(centre).is(ModBlocks.STATION_FLOOR.get());
        if (entry == null && alreadyBuilt(false, hasPadNode(level, padCentre), floor)) {
            registerPad(level, centre, padCentre); // origin platform: re-register only, write nothing
            return padCentre;
        }

        // --- Fresh void: the core room + airlock from the CORE template --------------------------------------
        for (ModuleTemplates.Placement p : ModuleTemplates.template(ModuleKind.CORE, 0, centre)) {
            if (p.cell() == ModuleTemplates.Cell.CORE) {
                continue;
            }
            BlockState state = StationModules.stateFor(p.cell());
            if (state == null) {
                continue;
            }
            if (p.cell() == ModuleTemplates.Cell.FLOOR && level.getBlockEntity(p.pos()) != null) {
                continue; // never clobber a block entity
            }
            level.setBlock(p.pos(), state, Block.UPDATE_ALL);
        }

        if (entry == null) {
            placeLegacyPad(level, padCentre); // origin platform keeps its 3×3 pad
            registerPad(level, centre, padCentre);
            return padCentre;
        }

        // Anchor the founded station: place + bind the (unbreakable) Station Core.
        level.setBlockAndUpdate(centre, ModBlocks.STATION_CORE.get().defaultBlockState());
        if (level.getBlockEntity(centre) instanceof StationCoreBlockEntity core) {
            core.bindStation(entry.slot(), entry.name());
        }
        registry.addModule(entry.slot(), new ModuleEntry(ModuleKind.CORE, 0, centre));
        OxygenFieldManager.get(level).addSource(ModuleTemplates.oxygenCell(ModuleKind.CORE, 0, centre));

        if (StationModules.placeDock(level, padCentre, entry.name() + " Dock")) {
            registry.addModule(entry.slot(), new ModuleEntry(ModuleKind.DOCKING_RING, 0,
                    ModuleTemplates.anchor(ModuleKind.DOCKING_RING, 0, centre)));
        } else {
            // No Docking Ring available: fall back to the 1.x pad so the station is still reachable.
            placeLegacyPad(level, padCentre);
            registerPad(level, centre, padCentre);
        }
        return padCentre;
    }

    /**
     * Whether a founded station was deliberately left without its starter structure ("Build starter structure"
     * unticked): it has modules on record (its Docking Ring) but neither a Station Core block nor a {@code CORE}
     * module. Every 2.0 station records {@code CORE} when built and every 1.x one has its Core block, so neither ever
     * reads as frame-only.
     */
    public static boolean frameOnly(boolean hasEntry, boolean hasCoreBlock, boolean hasAnyModule,
            boolean hasCoreModule) {
        return hasEntry && !hasCoreBlock && hasAnyModule && !hasCoreModule;
    }

    /**
     * Founds the bare frame of a placed station: no room, no Station Core — only a Docking Ring at the usual dock
     * position (recorded as the {@code DOCKING_RING} module so arrivals dock there and {@link #build} never stamps
     * the starter room over the owner's own build). Falls back to the 1.x pad if no ring block exists.
     */
    public static BlockPos buildFrame(ServerLevel level, StationRegistry.StationEntry entry) {
        BlockPos centre = entry.center();
        StationModules.loadChunks(level, centre);
        BlockPos padCentre = padCenter(centre);
        StationRegistry registry = StationRegistry.get(level.getServer());
        if (StationModules.placeDock(level, padCentre, entry.name() + " Dock")) {
            registry.addModule(entry.slot(), new ModuleEntry(ModuleKind.DOCKING_RING, 0,
                    ModuleTemplates.anchor(ModuleKind.DOCKING_RING, 0, centre)));
        } else {
            placeLegacyPad(level, padCentre);
            registerPad(level, centre, padCentre);
        }
        return padCentre;
    }

    private static void placeLegacyPad(ServerLevel level, BlockPos padCentre) {
        BlockState pad = ModBlocks.ROCKET_LAUNCH_PAD.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(padCentre.offset(dx, 0, dz), pad);
            }
        }
    }

    private static boolean hasPadNode(ServerLevel level, BlockPos padCentre) {
        for (PadRegistry.PadNode node : PadRegistry.get(level.getServer()).inDimension(level.dimension())) {
            if (node.pos().equals(padCentre)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static StationRegistry.StationEntry entryAt(ServerLevel level, BlockPos centre) {
        for (StationRegistry.StationEntry e : StationRegistry.get(level.getServer()).all()) {
            if (e.center().equals(centre)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Keeps the station's legacy landing pad registered as a travel node, named after the station — but only when no
     * node exists there yet. An existing node is never renamed (a player may have name-tagged it to pair it with a
     * pad of the same name) and nothing is written on an ordinary arrival.
     */
    private static void registerPad(ServerLevel level, BlockPos centre, BlockPos padCentre) {
        PadRegistry pads = PadRegistry.get(level.getServer());
        if (pads.at(level.dimension(), padCentre) != null) {
            return; // the common case: no lookup of the station, no write
        }
        StationRegistry.StationEntry entry = entryAt(level, centre);
        registerPadIn(pads, level.dimension(), padCentre, entry != null ? entry.name() : "Station");
    }

    /** Registry half of {@link #registerPad}: registers "{@code <label>} Landing" only when no node is there. */
    @Nullable
    static PadRegistry.PadNode registerPadIn(PadRegistry pads,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, BlockPos padCentre, String label) {
        PadRegistry.PadNode existing = pads.at(dim, padCentre);
        if (existing != null) {
            return existing;
        }
        return pads.register(label + " Landing", dim, padCentre);
    }
}
