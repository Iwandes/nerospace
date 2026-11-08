package za.co.neroland.nerospace.rocket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.nerospace.NerospaceCommon;

/**
 * Server-global registry of landing nodes: name-tagged launch pads, Docking Rings and auto-placed Landing
 * Pods. A rocket arriving in a dimension resolves its landing through {@link LandingResolver}, which reads
 * these nodes. Stored on the overworld via the same {@link SavedDataType} codec pattern as
 * {@link StationRegistry}.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> entries store NO player identity — only an auto id, a label, the
 * dimension id, the block position and (2.0) the node kind, centre and facing. Pads are server-global like
 * stations.</p>
 *
 * <p><b>Compatibility:</b> {@code kind}, {@code center} and {@code facing} are optional on load
 * ({@code LAUNCH_PAD}, empty, empty). {@code pos} stays the node identity and is never rewritten;
 * {@link #fill} back-fills a missing centre/facing exactly once.</p>
 */
public final class PadRegistry extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NerospaceCommon.MOD_ID, "pads");

    /** Hard cap on launch-pad + dock nodes so a grief-spammed registry can't bloat the save. */
    public static final int MAX_PADS = 256;
    /** Separate cap on Landing Pod nodes; the oldest pod node is evicted when full. */
    public static final int MAX_POD_NODES = 128;

    public static final SavedDataType<PadRegistry> TYPE = SavedDataCompat.type(ID, PadRegistry::new, codec());

    /**
     * One landing node: an id (never reused), a label, its dimension id, its block position (identity), and
     * the optional 2.0 fields.
     */
    public record PadNode(int id, String name, String dim, BlockPos pos, PadKind kind,
            Optional<BlockPos> center, Optional<Direction> facing) {

        public static final Codec<PadNode> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("id").forGetter(PadNode::id),
                Codec.STRING.fieldOf("name").forGetter(PadNode::name),
                Codec.STRING.fieldOf("dim").forGetter(PadNode::dim),
                BlockPos.CODEC.fieldOf("pos").forGetter(PadNode::pos),
                Codec.STRING.optionalFieldOf("kind", PadKind.LAUNCH_PAD.name())
                        .forGetter(n -> n.kind().name()),
                BlockPos.CODEC.optionalFieldOf("center").forGetter(PadNode::center),
                Codec.STRING.optionalFieldOf("facing").forGetter(n -> n.facing().map(Direction::getSerializedName))
        ).apply(inst, PadNode::decode));

        public PadNode {
            kind = kind == null ? PadKind.LAUNCH_PAD : kind;
            center = center == null ? Optional.empty() : center.map(BlockPos::immutable);
            facing = facing == null ? Optional.empty() : facing.filter(d -> d.getAxis().isHorizontal());
        }

        /** The 1.6.0 shape: a launch pad with no centre/facing. */
        public PadNode(int id, String name, String dim, BlockPos pos) {
            this(id, name, dim, pos, PadKind.LAUNCH_PAD, Optional.empty(), Optional.empty());
        }

        private static PadNode decode(Integer id, String name, String dim, BlockPos pos, String kind,
                Optional<BlockPos> center, Optional<String> facing) {
            return new PadNode(id.intValue(), name, dim, pos, PadKind.byName(kind), center,
                    facing.map(Direction::byName));
        }

        /** The block the rocket stands on: the resolved centre, else the identity position. */
        public BlockPos centerOrPos() {
            return this.center.orElse(this.pos);
        }
    }

    /** Insertion-ordered (commissioning order). */
    private final Map<Integer, PadNode> pads = new LinkedHashMap<>();
    private int nextId;

    public PadRegistry() {
    }

    /** The codec, exposed for the plain-JVM migration tests. */
    public static Codec<PadRegistry> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                PadNode.CODEC.listOf().fieldOf("pads").forGetter(r -> new ArrayList<>(r.pads.values())),
                Codec.INT.fieldOf("next_id").forGetter(r -> r.nextId)
        ).apply(inst, PadRegistry::fromEntries));
    }

    private static PadRegistry fromEntries(List<PadNode> entries, Integer nextId) {
        PadRegistry registry = new PadRegistry();
        for (PadNode node : entries) {
            registry.pads.put(node.id(), node);
        }
        registry.nextId = nextId.intValue();
        return registry;
    }

    public static PadRegistry get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, PadRegistry::new, "nerospace:pads");
    }

    public static String dimId(ResourceKey<Level> dim) {
        return dim.identifier().toString();
    }

    /**
     * Registers (or renames, if one already exists at this exact spot) a launch-pad node. A blank name
     * auto-labels "Pad N". An existing node keeps its kind, centre and facing.
     *
     * @return the node, or {@code null} when {@link #MAX_PADS} is reached for a brand-new pad.
     */
    @Nullable
    public PadNode register(@Nullable String name, ResourceKey<Level> dim, BlockPos pos) {
        return register(name, dim, pos, PadKind.LAUNCH_PAD, null, null);
    }

    /**
     * Registers a node of {@code kind} (or renames the one already at this spot, filling any missing
     * centre/facing). {@code POD} nodes have their own cap and evict the oldest pod node when full.
     *
     * @return the node, or {@code null} when the launch-pad/dock cap is reached for a brand-new node.
     */
    @Nullable
    public PadNode register(@Nullable String name, ResourceKey<Level> dim, BlockPos pos, PadKind kind,
            @Nullable BlockPos center, @Nullable Direction facing) {
        String dimId = dimId(dim);
        BlockPos at = pos.immutable();
        for (PadNode existing : this.pads.values()) {
            if (existing.dim().equals(dimId) && existing.pos().equals(at)) {
                String renamed = name == null || name.isBlank() ? existing.name() : name;
                PadNode updated = new PadNode(existing.id(), renamed, dimId, at, existing.kind(),
                        existing.center().isPresent() ? existing.center() : Optional.ofNullable(center),
                        existing.facing().isPresent() ? existing.facing() : Optional.ofNullable(facing));
                if (!updated.equals(existing)) {
                    this.pads.put(existing.id(), updated);
                    setDirty();
                }
                return updated;
            }
        }
        PadKind k = kind == null ? PadKind.LAUNCH_PAD : kind;
        if (k == PadKind.POD) {
            if (podCount() >= MAX_POD_NODES) {
                evictOldestPod();
            }
        } else if (selectableCount() >= MAX_PADS) {
            return null;
        }
        int id = this.nextId++;
        String label = name == null || name.isBlank() ? defaultLabel(k, id) : name;
        PadNode node = new PadNode(id, label, dimId, at, k, Optional.ofNullable(center), Optional.ofNullable(facing));
        this.pads.put(id, node);
        setDirty();
        return node;
    }

    private static String defaultLabel(PadKind kind, int id) {
        return switch (kind) {
            case POD -> "Landing Pod " + (id + 1);
            case DOCK -> "Dock " + (id + 1);
            default -> "Pad " + (id + 1);
        };
    }

    /** Whether {@code node} still carries its auto label ("Dock 3", "Pad 7") — nobody named it. */
    public static boolean isDefaultLabel(PadNode node) {
        return defaultLabel(node.kind(), node.id()).equals(node.name());
    }

    private int podCount() {
        int n = 0;
        for (PadNode node : this.pads.values()) {
            if (node.kind() == PadKind.POD) {
                n++;
            }
        }
        return n;
    }

    private int selectableCount() {
        return this.pads.size() - podCount();
    }

    private void evictOldestPod() {
        for (PadNode node : this.pads.values()) {
            if (node.kind() == PadKind.POD) {
                this.pads.remove(node.id());
                setDirty();
                return;
            }
        }
    }

    /**
     * Back-fills a node's missing centre/facing. Sets only empty fields; never touches
     * {@code pos}, the name or the kind. @return whether anything changed.
     */
    public boolean fill(int id, @Nullable BlockPos center, @Nullable Direction facing) {
        PadNode node = this.pads.get(id);
        if (node == null) {
            return false;
        }
        Optional<BlockPos> c = node.center().isPresent() ? node.center() : Optional.ofNullable(center);
        Optional<Direction> f = node.facing().isPresent() ? node.facing() : Optional.ofNullable(facing);
        PadNode updated = new PadNode(node.id(), node.name(), node.dim(), node.pos(), node.kind(), c, f);
        if (updated.equals(node)) {
            return false;
        }
        this.pads.put(id, updated);
        setDirty();
        return true;
    }

    /** Changes the kind of the node at {@code (dim, pos)}, keeping id and name (legacy pad → dock). */
    @Nullable
    public PadNode rekind(ResourceKey<Level> dim, BlockPos pos, PadKind kind) {
        PadNode node = at(dim, pos);
        if (node == null || node.kind() == kind) {
            return node;
        }
        PadNode updated = new PadNode(node.id(), node.name(), node.dim(), node.pos(), kind, node.center(), node.facing());
        this.pads.put(node.id(), updated);
        setDirty();
        return updated;
    }

    /** The node whose identity position is {@code (dim, pos)}, or {@code null}. */
    @Nullable
    public PadNode at(ResourceKey<Level> dim, BlockPos pos) {
        String dimId = dimId(dim);
        for (PadNode node : this.pads.values()) {
            if (node.dim().equals(dimId) && node.pos().equals(pos)) {
                return node;
            }
        }
        return null;
    }

    @Nullable
    public PadNode byId(int id) {
        return this.pads.get(id);
    }

    /** Unregisters the node at {@code (dim, pos)}; @return the removed node, or {@code null}. */
    @Nullable
    public PadNode unregisterAt(ResourceKey<Level> dim, BlockPos pos) {
        PadNode node = at(dim, pos.immutable());
        if (node == null) {
            return null;
        }
        PadNode removed = this.pads.remove(node.id());
        setDirty();
        return removed;
    }

    public List<PadNode> all() {
        return List.copyOf(this.pads.values());
    }

    /**
     * The selectable nodes (launch pads and docks, never pods) in {@code dim}, in commissioning order. This is
     * the list the Rocket screen's pad index addresses; 1.6.0 indices keep their meaning because every 2.0
     * node kind is appended after them or filtered out.
     */
    public List<PadNode> inDimension(ResourceKey<Level> dim) {
        String dimId = dimId(dim);
        List<PadNode> out = new ArrayList<>();
        for (PadNode node : this.pads.values()) {
            if (node.dim().equals(dimId) && node.kind().selectable()) {
                out.add(node);
            }
        }
        return out;
    }

    /** Every node in {@code dim} of any kind, in commissioning order. */
    public List<PadNode> nodesIn(ResourceKey<Level> dim) {
        String dimId = dimId(dim);
        List<PadNode> out = new ArrayList<>();
        for (PadNode node : this.pads.values()) {
            if (node.dim().equals(dimId)) {
                out.add(node);
            }
        }
        return out;
    }

    /** The Landing Pod nodes in {@code dim}. */
    public List<PadNode> podNodesIn(ResourceKey<Level> dim) {
        List<PadNode> out = new ArrayList<>();
        for (PadNode node : nodesIn(dim)) {
            if (node.kind() == PadKind.POD) {
                out.add(node);
            }
        }
        return out;
    }

    /** The selectable node in {@code dim} closest to {@code to}, or {@code null} if none is registered. */
    @Nullable
    public PadNode nearest(ResourceKey<Level> dim, BlockPos to) {
        PadNode best = null;
        double bestSq = Double.MAX_VALUE;
        for (PadNode node : inDimension(dim)) {
            double sq = node.centerOrPos().distSqr(to);
            if (sq < bestSq) {
                bestSq = sq;
                best = node;
            }
        }
        return best;
    }
}
