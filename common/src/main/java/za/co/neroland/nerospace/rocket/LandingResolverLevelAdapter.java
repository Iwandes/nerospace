package za.co.neroland.nerospace.rocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerospace.api.PlanetArchetype;
import za.co.neroland.nerospace.registry.ModBlocks;
import za.co.neroland.nerospace.registry.ModDimensions;
import za.co.neroland.nerospace.route.PadRecord;
import za.co.neroland.nerospace.route.RouteRegistry;
import za.co.neroland.nerospace.world.TerraformOverlayState;
import za.co.neroland.nerospace.world.planet.PlanetDefinition;
import za.co.neroland.nerospace.world.planet.Planets;
import za.co.neroland.nerospace.world.station.StationAccess;

/**
 * The level side of {@link LandingResolver}: builds the {@link LandingContext} snapshot from the
 * live world (the only place that loads chunks for a landing), back-fills legacy pad centres once, and applies
 * a decision (places the Landing Pod and registers its {@code POD} node). Exercised by the runtime gate.
 *
 * <p>Nodes are probed lazily ({@link LandingCandidates}: explicit, return, same-name, and outward from
 * the anchor / {@code preferred} to the first usable one, ≤ {@link LandingChunkBudget#MAX_PROBE_CHUNKS} chunks), and
 * the pod search reads only the 4×4-chunk square around {@code preferred}. No chunk is force-loaded or ticketed.</p>
 */
public final class LandingResolverLevelAdapter {

    /** Chunks read by the last {@link #resolve} (server thread only); the dev landing harness reports it. */
    private static final Set<Long> TALLY = new java.util.HashSet<>();
    private static int lastLandingChunks;

    /** What the rocket knows about its flight. {@code rider} may be null (uncrewed / disconnected). */
    public record Flight(ResourceKey<Level> destination, int explicitPad, String returnDim, @Nullable BlockPos returnPos,
            Optional<String> originPadName, boolean originRegistered, @Nullable ServerPlayer rider,
            int stationSlot, @Nullable BlockPos stationCentre) {
    }

    /** The node the rocket launched from, if it was a registered one. */
    public record Origin(boolean registered, Optional<String> name) {
        public static final Origin NONE = new Origin(false, Optional.empty());
    }

    private LandingResolverLevelAdapter() {
    }

    // --- resolve ----------------------------------------------------------------------------------

    /** Gathers the context and resolves (station structure ensured first). */
    public static LandingDecision resolve(ServerLevel level, Flight flight, Set<Integer> excluded) {
        TALLY.clear();
        if (isStation(flight.destination()) && flight.stationCentre() != null) {
            ensureStation(level, flight.stationSlot(), flight.stationCentre());
        }
        LandingDecision decision = LandingResolver.resolve(gather(level, flight, excluded));
        lastLandingChunks = TALLY.size();
        TALLY.clear();
        DestinationTrace.log("landing chunks={} decision={}", lastLandingChunks, decision.getClass().getSimpleName());
        return decision;
    }

    /** Distinct chunks the last landing resolve read (dev harness: "chunks loaded per landing"). */
    public static int lastLandingChunks() {
        return lastLandingChunks;
    }

    private static void touch(ServerLevel level, int blockX, int blockZ) {
        TALLY.add(LandingChunkBudget.chunkKey(blockX >> 4, blockZ >> 4));
        level.getChunk(blockX >> 4, blockZ >> 4);
    }

    public static boolean isStation(@Nullable ResourceKey<Level> dim) {
        PlanetDefinition planet = Planets.get(dim);
        return ModDimensions.STATION_LEVEL.equals(dim)
                || (planet != null && planet.traits().archetype() == PlanetArchetype.ORBITAL);
    }

    /**
     * Makes sure the selected station exists. A founded station goes through {@link StationStructure#build}
     * (guarded by its Station Core); the shared origin platform is only built when it is genuinely empty
     * ({@link OriginPlatformGuard}) — otherwise only its pad node is kept registered.
     */
    public static void ensureStation(ServerLevel level, int slot, BlockPos centre) {
        if (slot == DockSelection.FREE_DOCK_SLOT) {
            return; // a free dock is a hand-built ring: nothing to build, never the origin platform
        }
        if (slot >= 0) {
            StationStructure.build(level, centre);
            return;
        }
        BlockPos padCentre = StationStructure.padCenter(centre);
        touch(level, centre.getX(), centre.getZ());
        touch(level, padCentre.getX(), padCentre.getZ());
        PadRegistry pads = PadRegistry.get(level.getServer());
        boolean hasCore = level.getBlockState(centre).is(ModBlocks.STATION_CORE.get());
        boolean hasNode = pads.at(level.dimension(), padCentre) != null;
        boolean floor = level.getBlockState(centre).is(ModBlocks.STATION_FLOOR.get());
        if (!OriginPlatformGuard.alreadyBuilt(hasCore, hasNode, floor)) {
            StationStructure.build(level, centre);
            return;
        }
        if (!hasNode && level.getBlockState(padCentre).getBlock() instanceof RocketLaunchPadBlock) {
            pads.register("Station Landing", level.dimension(), padCentre);
        }
    }

    /** Builds the resolver's snapshot. */
    public static LandingContext gather(ServerLevel level, Flight flight, Set<Integer> excluded) {
        PadRegistry registry = PadRegistry.get(level.getServer());
        ResourceKey<Level> dest = flight.destination();
        String destId = PadRegistry.dimId(dest);

        Optional<StationTarget> station = Optional.empty();
        List<PadCandidate> pads = new ArrayList<>();
        PlanetDefinition planet = Planets.get(dest);
        BlockPos anchor = planet != null ? planet.arrivalAnchor() : BlockPos.ZERO;
        Optional<ReturnSite> returnSite = Optional.empty();
        if (isStation(dest) && flight.stationCentre() != null) {
            // The nearest dock and the nearest legacy pad inside the claim, chosen as data; each probed once.
            BlockPos centre = flight.stationCentre();
            PadRegistry.PadNode dockNode = null;
            PadRegistry.PadNode padNode = null;
            double dockSq = Double.MAX_VALUE;
            double padSq = Double.MAX_VALUE;
            for (PadRegistry.PadNode node : registry.nodesIn(dest)) {
                if (LandingResolver.chebyshev(node.centerOrPos(), centre) > StationTarget.CLAIM_RADIUS) {
                    continue;
                }
                double sq = node.centerOrPos().distSqr(centre);
                if (node.kind() == PadKind.DOCK && sq < dockSq) {
                    dockSq = sq;
                    dockNode = node;
                } else if (node.kind() == PadKind.LAUNCH_PAD && sq < padSq) {
                    padSq = sq;
                    padNode = node;
                }
            }
            PadCandidate dock = dockNode != null ? candidate(level, registry, dockNode) : null;
            PadCandidate legacy = padNode != null ? candidate(level, registry, padNode) : null;
            // A free dock's "centre" is the ring itself; a pod (ring gone) goes beside it, not 13 blocks east.
            BlockPos podAt = flight.stationSlot() == DockSelection.FREE_DOCK_SLOT ? centre
                    : StationStructure.padCenter(centre);
            station = Optional.of(new StationTarget(flight.stationSlot(), centre, podAt,
                    Optional.ofNullable(dock), Optional.ofNullable(legacy)));
        } else {
            BlockPos rp = flight.returnPos();
            java.util.Map<Integer, PadCandidate> known = new HashMap<>();
            if (rp != null && destId.equals(flight.returnDim())) {
                ReturnSite site = returnSite(level, registry, rp);
                returnSite = Optional.of(site);
                site.registeredNode().ifPresent(n -> known.put(n.id(), n));
            }
            List<PadRegistry.PadNode> registered = registry.inDimension(dest);
            List<LandingCandidates.Node> nodes = new ArrayList<>(registered.size());
            for (PadRegistry.PadNode node : registered) {
                nodes.add(new LandingCandidates.Node(node.id(), node.name(), node.kind(), node.pos(),
                        node.centerOrPos()));
            }
            boolean standing = returnSite.isPresent() && returnSite.get().standing();
            LandingCandidates.Flight plan = new LandingCandidates.Flight(flight.explicitPad(),
                    returnSite.isPresent() ? rp : null, standing, flight.originPadName(), flight.originRegistered(),
                    anchor, excluded, known);
            pads.addAll(LandingCandidates.plan(nodes, plan, i -> candidate(level, registry, registered.get(i))));
        }
        int voidFloorY = station.isPresent() ? StationRegistry.PLATFORM_Y : level.getMinY();
        return new LandingContext(destId, flight.explicitPad(), pads, returnSite, flight.originPadName(),
                flight.originRegistered(), anchor, station, new LevelSampler(level, flight.rider(), voidFloorY),
                excluded);
    }

    // --- candidates -------------------------------------------------------------------------------

    private static PadCandidate candidate(ServerLevel level, PadRegistry registry, PadRegistry.PadNode node) {
        BlockPos pos = node.pos();
        touch(level, pos.getX(), pos.getZ());
        return switch (node.kind()) {
            case DOCK -> {
                BlockPos centre = node.centerOrPos();
                BlockState s = level.getBlockState(centre);
                boolean present = s.getBlock() instanceof DockingRingBlock
                        && s.getValue(DockingRingBlock.PART) == DockingRingPart.CENTER;
                Direction facing = node.facing().orElse(present ? s.getValue(DockingRingBlock.FACING) : null);
                if (present && (node.center().isEmpty() || node.facing().isEmpty())) {
                    registry.fill(node.id(), centre, facing);
                }
                yield new PadCandidate(node.id(), node.name(), PadKind.DOCK, pos, centre, facing, present,
                        present && rocketOn(level, centre, 1));
            }
            case POD -> {
                boolean present = level.getBlockState(pos).is(ModBlocks.LANDING_POD.get());
                yield new PadCandidate(node.id(), node.name(), PadKind.POD, pos, pos, null, present,
                        present && rocketOn(level, pos, 0));
            }
            default -> {
                boolean present = level.getBlockState(pos).getBlock() instanceof RocketLaunchPadBlock;
                Set<BlockPos> cluster = present ? LaunchPadMultiblock.connectedPads(level, pos) : Set.of();
                BlockPos centre = node.center().orElse(present ? LandingPadGeometry.centreOf(cluster, pos) : pos);
                Direction facing = node.facing().orElse(present ? apronFacing(level, cluster, centre) : null);
                if (present && (node.center().isEmpty() || node.facing().isEmpty())) {
                    registry.fill(node.id(), centre, facing); // written back once
                }
                boolean standing = level.getBlockState(centre).getBlock() instanceof RocketLaunchPadBlock;
                yield new PadCandidate(node.id(), node.name(), PadKind.LAUNCH_PAD, pos, centre, facing, standing,
                        standing && LaunchPadMultiblock.rocketAbove(level, cluster.isEmpty()
                                ? LaunchPadMultiblock.connectedPads(level, centre) : cluster) != null);
            }
        };
    }

    private static ReturnSite returnSite(ServerLevel level, PadRegistry registry, BlockPos pos) {
        touch(level, pos.getX(), pos.getZ());
        boolean standing = level.getBlockState(pos).getBlock() instanceof RocketLaunchPadBlock;
        if (!standing) {
            return new ReturnSite(pos, false, false, pos, null, Optional.empty());
        }
        Set<BlockPos> cluster = LaunchPadMultiblock.connectedPads(level, pos);
        BlockPos centre = LandingPadGeometry.centreOf(cluster, pos);
        Direction facing = apronFacing(level, cluster, centre);
        Optional<PadCandidate> registered = Optional.empty();
        for (PadRegistry.PadNode node : registry.inDimension(level.dimension())) {
            if (node.kind() == PadKind.LAUNCH_PAD && cluster.contains(node.pos())) {
                registered = Optional.of(candidate(level, registry, node));
                break;
            }
        }
        boolean occupied = LaunchPadMultiblock.rocketAbove(level, cluster) != null;
        return new ReturnSite(pos, true, occupied, centre, facing, registered);
    }

    /** The side whose first cell beyond the square is air while the opposite one is not. */
    public static Direction apronFacing(Level level, Set<BlockPos> cluster, BlockPos centre) {
        int half = LandingPadGeometry.halfSize(cluster, centre);
        boolean[] open = new boolean[4];
        for (int i = 0; i < 4; i++) {
            open[i] = level.getBlockState(centre.relative(LandingPadGeometry.ORDER[i], half + 1)).isAir();
        }
        return LandingPadGeometry.facingOf(open);
    }

    private static boolean rocketOn(ServerLevel level, BlockPos block, int halfWidth) {
        AABB box = new AABB(block.getX() - halfWidth, block.getY() + 0.05D, block.getZ() - halfWidth,
                block.getX() + 1 + halfWidth, block.getY() + 4.0D, block.getZ() + 1 + halfWidth);
        for (RocketEntity r : level.getEntitiesOfClass(RocketEntity.class, box)) {
            if (!r.isLaunching() && !r.isDescending()) {
                return true;
            }
        }
        return false;
    }

    // --- the rocket's origin node -----------------------------------------------------------------

    /**
     * The registered node the rocket stands on in {@code level} at launch (any node whose pad cluster contains
     * {@code padPos}, or a dock/pod within one block of the rocket). Auto-labels and blank names never pair.
     */
    public static Origin origin(ServerLevel level, @Nullable BlockPos padPos, BlockPos rocketPos) {
        PadRegistry registry = PadRegistry.get(level.getServer());
        Set<BlockPos> cluster = padPos != null && level.getBlockState(padPos).getBlock() instanceof RocketLaunchPadBlock
                ? LaunchPadMultiblock.connectedPads(level, padPos) : Set.of();
        for (PadRegistry.PadNode node : registry.inDimension(level.dimension())) {
            boolean match = cluster.contains(node.pos())
                    || LandingResolver.chebyshev(node.centerOrPos(), rocketPos) <= 1
                            && Math.abs(node.centerOrPos().getY() - rocketPos.getY()) <= 1;
            if (match) {
                String name = node.name() == null ? "" : node.name().strip();
                return new Origin(true, LandingResolver.isAutoLabel(name) ? Optional.empty() : Optional.of(name));
            }
        }
        return Origin.NONE;
    }

    // --- apply ------------------------------------------------------------------------------------

    /**
     * Places the decision's Landing Pod (if new; always empty — the arriving rocket stands on it) and registers
     * its {@code POD} node. Pads/docks need nothing.
     */
    public static void prepare(ServerLevel level, LandingDecision decision) {
        if (decision instanceof LandingDecision.PodLanding pod) {
            if (!pod.existingPodReused()) {
                ReturnSitePlacement.placePod(level, pod.site(), pod.climbUsed() > 0);
            }
            PadRegistry.get(level.getServer()).register(null, level.dimension(), pod.site(), PadKind.POD,
                    pod.site(), null);
        }
    }

    /** The block the rocket stands on: the pad/dock centre, or the pod itself. */
    public static BlockPos standBlock(LandingDecision decision) {
        if (decision instanceof LandingDecision.PodLanding pod) {
            return pod.site();
        }
        if (decision instanceof LandingDecision.StationDock dock) {
            return dock.target().center();
        }
        return ((LandingDecision.PadLanding) decision).pad().center();
    }

    /** The kind of block the rocket lands on, for the descent's pad-still-there check. */
    public static PadKind targetKind(LandingDecision decision) {
        if (decision instanceof LandingDecision.PodLanding) {
            return PadKind.POD;
        }
        if (decision instanceof LandingDecision.StationDock dock) {
            return dock.viaDockingRing() ? PadKind.DOCK : PadKind.LAUNCH_PAD;
        }
        return ((LandingDecision.PadLanding) decision).pad().kind();
    }

    /**
     * Whether a rocket blocks a landing target: any other rocket that is parked there (not lifting off, not itself
     * still descending). The descending rocket never blocks its own target.
     */
    public static boolean blocksLanding(boolean self, boolean launching, boolean descending) {
        return !self && !launching && !descending;
    }

    /**
     * Whether a descending rocket must leave its target: the target block broke, or another parked rocket stands on
     * it. Pod-bound and pad-bound rockets alike: a Landing Pod can hold only one rocket too.
     */
    public static boolean mustLeaveTarget(boolean targetPresent, boolean occupiedByOther) {
        return !targetPresent || occupiedByOther;
    }

    /**
     * Whether another parked rocket stands on the target (the pad cluster, the docking ring's 3×3 or the pod).
     * Entities load after blocks, so the resolver's snapshot of a cold chunk can miss a parked rocket; the descent
     * re-checks with this once the entities are in (see {@link #entitiesReady}).
     */
    public static boolean targetOccupied(ServerLevel level, PadKind kind, BlockPos block, RocketEntity self) {
        AABB box;
        if (kind == PadKind.DOCK || kind == PadKind.POD) {
            int half = kind == PadKind.DOCK ? 1 : 0;
            box = new AABB(block.getX() - half, block.getY() + 0.05D, block.getZ() - half,
                    block.getX() + 1 + half, block.getY() + 4.0D, block.getZ() + 1 + half);
        } else {
            Set<BlockPos> cluster = level.getBlockState(block).getBlock() instanceof RocketLaunchPadBlock
                    ? LaunchPadMultiblock.connectedPads(level, block) : Set.of();
            if (cluster.isEmpty()) {
                return false;
            }
            box = LaunchPadMultiblock.scanBox(cluster);
        }
        for (RocketEntity r : level.getEntitiesOfClass(RocketEntity.class, box)) {
            if (blocksLanding(r == self, r.isLaunching(), r.isDescending())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the entities of the target's chunk have finished loading (they arrive some ticks after the blocks). */
    public static boolean entitiesReady(ServerLevel level, BlockPos block) {
        return level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(block));
    }

    /**
     * The touchdown fallback: a Landing Pod near {@code near}, for a rocket whose target turned out to be taken.
     * {@code flight} is null for a rocket that resumed a descent from disk.
     */
    public static LandingDecision podFallback(ServerLevel level, @Nullable Flight flight, BlockPos near) {
        TALLY.clear();
        Flight f = flight != null ? flight
                : new Flight(level.dimension(), -1, "", null, Optional.empty(), false, null, -1, null);
        LandingDecision decision = LandingResolver.podNear(gather(level, f, Set.of()), near);
        TALLY.clear();
        DestinationTrace.log("landing pod fallback: target occupied");
        return decision;
    }

    /** Whether the block the rocket is descending onto still stands. */
    public static boolean targetPresent(ServerLevel level, PadKind kind, BlockPos block) {
        BlockState s = level.getBlockState(block);
        return switch (kind) {
            case DOCK -> s.getBlock() instanceof DockingRingBlock;
            case POD -> s.is(ModBlocks.LANDING_POD.get());
            default -> s.getBlock() instanceof RocketLaunchPadBlock;
        };
    }

    // --- pod-site sampler -------------------------------------------------------------------------

    /**
     * Samples candidate pod sites from the live level, caching per column. Reads stay inside the
     * 4×4-chunk square around {@code preferred}: a site whose 5×5 footprint leaves it is unsuitable without a read.
     */
    private static final class LevelSampler implements PodSearch.Sampler {

        private final ServerLevel level;
        private final int voidFloorY;
        private final Map<Long, SiteSample> columns = new HashMap<>();
        private final List<int[]> inaccessible = new ArrayList<>(); // x, z, halfBox
        private final List<BlockPos> padCentres = new ArrayList<>();
        private final List<BlockPos> podNodes = new ArrayList<>();
        /** Pod nodes near {@code preferred} whose pod block stands (resolved once in {@link #begin}). */
        private final List<BlockPos> standingPods = new ArrayList<>();
        @Nullable
        private LandingChunkBudget.Square square;

        LevelSampler(ServerLevel level, @Nullable ServerPlayer rider, int voidFloorY) {
            this.level = level;
            this.voidFloorY = voidFloorY;
            String riderId = rider != null ? rider.getUUID().toString() : "";
            boolean op = rider != null && StationRegistry.isOp(rider);
            if (isStation(level.dimension())) {
                for (StationRegistry.StationEntry e : StationRegistry.get(level.getServer()).all()) {
                    if (!StationAccess.canUse(e.owner(), e.members(), riderId, op)) {
                        this.inaccessible.add(new int[] { e.center().getX(), e.center().getZ(), StationTarget.CLAIM_RADIUS });
                    }
                }
            }
            String dimId = PadRegistry.dimId(level.dimension());
            for (PadRecord pad : RouteRegistry.get(level.getServer()).allPads()) {
                if (!pad.dim().equals(dimId)) {
                    continue;
                }
                boolean access = pad.owner().isEmpty() || pad.isPublic() || pad.owner().equals(riderId)
                        || pad.access().contains(riderId);
                if (!access) {
                    this.inaccessible.add(new int[] { pad.pos().getX(), pad.pos().getZ(), 8 });
                }
            }
            for (PadRegistry.PadNode node : PadRegistry.get(level.getServer()).nodesIn(level.dimension())) {
                if (node.kind() == PadKind.POD) {
                    this.podNodes.add(node.pos());
                } else {
                    this.padCentres.add(node.centerOrPos());
                }
            }
        }

        @Override
        public void begin(BlockPos preferred) {
            LandingChunkBudget.Square sq = LandingChunkBudget.podSquare(preferred);
            this.square = sq;
            this.columns.clear();
            this.standingPods.clear();
            int reach = PodSearch.OUTER_RADIUS + PodSiteRules.POD_SPACING;
            for (BlockPos p : this.podNodes) {
                if (LandingResolver.chebyshev(p, preferred) > reach) {
                    continue; // can never be within 32 of a site in the spiral
                }
                int cx = p.getX() >> 4;
                int cz = p.getZ() >> 4;
                boolean standing;
                if (sq.containsChunk(cx, cz)) {
                    touch(this.level, p.getX(), p.getZ());
                    standing = this.level.getBlockState(p).is(ModBlocks.LANDING_POD.get());
                } else if (this.level.hasChunk(cx, cz)) {
                    standing = this.level.getBlockState(p).is(ModBlocks.LANDING_POD.get()); // already loaded
                } else {
                    standing = true; // outside the budget and unloaded: assume it stands (keeps the 32 spacing)
                }
                if (standing) {
                    this.standingPods.add(p);
                }
            }
        }

        /** Whether the 5×5 footprint around {@code (x, z)} lies inside the chunk budget. */
        private boolean inBudget(int x, int z) {
            LandingChunkBudget.Square sq = this.square;
            return sq == null || (sq.containsBlock(x - 2, z - 2) && sq.containsBlock(x + 2, z + 2));
        }

        private static SiteSample unsuitable(BlockPos site) {
            return new SiteSample(site, SiteSample.fill(SiteSample.FOOTPRINT, false),
                    SiteSample.fill(SiteSample.FOOTPRINT, false), SiteSample.fill(SiteSample.BOX, false), false,
                    Integer.MAX_VALUE, false, false, false);
        }

        @Override
        public SiteSample column(int x, int z) {
            return this.columns.computeIfAbsent(BlockPos.asLong(x, 0, z), k -> sampleColumn(x, z));
        }

        private SiteSample sampleColumn(int x, int z) {
            if (!inBudget(x, z)) {
                return unsuitable(new BlockPos(x, this.voidFloorY, z));
            }
            touch(this.level, x - 2, z - 2);
            touch(this.level, x + 2, z + 2);
            touch(this.level, x - 2, z + 2);
            touch(this.level, x + 2, z - 2);
            int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= this.level.getMinY()) {
                y = this.voidFloorY; // a void column (station): the climb builds the floor
            }
            BlockPos site = new BlockPos(x, y, z);
            BlockPos below = site.below();
            if (this.level.getBlockState(below).is(ModBlocks.LANDING_POD.get())) {
                boolean empty = this.level.getBlockEntity(below) instanceof ReturnSiteBlockEntity be && be.isEmpty();
                if (empty && !rocketOn(this.level, below, 0)) {
                    return new SiteSample(below, SiteSample.fill(SiteSample.FOOTPRINT, true),
                            SiteSample.fill(SiteSample.FOOTPRINT, false), SiteSample.fill(SiteSample.BOX, true),
                            false, Integer.MAX_VALUE, false, false, true);
                }
            }
            return at(site);
        }

        @Override
        public SiteSample at(BlockPos site) {
            if (!inBudget(site.getX(), site.getZ())) {
                return unsuitable(site);
            }
            boolean[] solid = new boolean[SiteSample.FOOTPRINT];
            boolean[] forbidden = new boolean[SiteSample.FOOTPRINT];
            boolean[] clear = new boolean[SiteSample.BOX];
            int i = 0;
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    BlockPos f = site.offset(dx, -1, dz);
                    BlockState s = this.level.getBlockState(f);
                    solid[i] = s.isCollisionShapeFullBlock(this.level, f);
                    forbidden[i] = forbiddenFloor(f, s);
                    for (int h = 0; h < 3; h++) {
                        BlockPos c = site.offset(dx, h, dz);
                        BlockState cs = this.level.getBlockState(c);
                        clear[h * SiteSample.FOOTPRINT + i] = (cs.isAir() || cs.canBeReplaced())
                                && cs.getFluidState().isEmpty() && this.level.getBlockEntity(c) == null;
                    }
                    i++;
                }
            }
            return new SiteSample(site, solid, forbidden, clear, inStructure(site), claimDistance(site),
                    overlapsPad(site), podNearby(site), false);
        }

        /** Whether any structure start's bounding box covers the 5×5×3 box (its centre or a corner). */
        private boolean inStructure(BlockPos site) {
            var manager = this.level.structureManager();
            BlockPos[] probes = { site, site.offset(2, 0, 2), site.offset(-2, 0, -2), site.offset(2, 2, -2),
                    site.offset(-2, 2, 2) };
            for (BlockPos p : probes) {
                for (var structure : manager.getAllStructuresAt(p).keySet()) {
                    if (manager.getStructureAt(p, structure).isValid()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean forbiddenFloor(BlockPos pos, BlockState s) {
            return s.is(BlockTags.LEAVES) || !s.getFluidState().isEmpty() || s.is(BlockTags.ICE)
                    || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.MAGMA_BLOCK) || this.level.getBlockEntity(pos) != null;
        }

        private int claimDistance(BlockPos site) {
            int best = Integer.MAX_VALUE;
            for (int[] c : this.inaccessible) {
                int d = Math.max(Math.abs(site.getX() - c[0]), Math.abs(site.getZ() - c[1])) - c[2];
                best = Math.min(best, Math.max(0, d));
            }
            // Terraformed ground: Nerospace's default policy treats every overlay region as inaccessible.
            TerraformOverlayState overlay = TerraformOverlayState.get(this.level);
            int r = PodSiteRules.CLAIM_DISTANCE - 1;
            if (overlay.at(site).isPresent() || overlay.at(site.offset(r, 0, r)).isPresent()
                    || overlay.at(site.offset(-r, 0, r)).isPresent() || overlay.at(site.offset(r, 0, -r)).isPresent()
                    || overlay.at(site.offset(-r, 0, -r)).isPresent()) {
                best = 0;
            }
            return best;
        }

        private boolean overlapsPad(BlockPos site) {
            for (BlockPos c : this.padCentres) {
                if (LandingResolver.chebyshev(c, site) <= 4 && Math.abs(c.getY() - site.getY()) <= 3) {
                    return true;
                }
            }
            return false;
        }

        private boolean podNearby(BlockPos site) {
            if (this.square == null) { // no begin(): read every pod node (not reached through PodSearch)
                for (BlockPos p : this.podNodes) {
                    if (!p.equals(site) && LandingResolver.chebyshev(p, site) < PodSiteRules.POD_SPACING
                            && this.level.getBlockState(p).is(ModBlocks.LANDING_POD.get())) {
                        return true;
                    }
                }
                return false;
            }
            for (BlockPos p : this.standingPods) {
                if (!p.equals(site) && LandingResolver.chebyshev(p, site) < PodSiteRules.POD_SPACING) {
                    return true;
                }
            }
            return false;
        }
    }
}
